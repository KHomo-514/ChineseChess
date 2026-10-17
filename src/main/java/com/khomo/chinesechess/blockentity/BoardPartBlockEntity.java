package com.khomo.chinesechess.blockentity;

import com.khomo.chinesechess.block.BoardStructure;
import com.khomo.chinesechess.block.XiangqiBoardBlock;
import com.khomo.chinesechess.entity.BoardPieces;
import com.khomo.chinesechess.match.MatchService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The block entity of one board plate.
 *
 * <p>Every plate carries the position of the board's {@linkplain #origin() origin} plate, and every
 * plate carries the same {@link BoardRenderState}. Keeping the state replicated on all four plates
 * would be wasteful if only the origin were rendered - but the origin alone is rendered, precisely
 * so that the picture is drawn once and cannot be clipped by chunk-section culling (see
 * {@code BoardPartRenderer}). Replication is therefore limited to keeping the origin's copy fresh,
 * which the periodic {@linkplain #serverTick(int) consistency sweep} guarantees even after a chunk
 * has been unloaded and reloaded.</p>
 *
 * <p>Single responsibility: persist and broadcast one plate's view of its board.</p>
 */
public class BoardPartBlockEntity extends BlockEntity {

    private static final String TAG_ORIGIN = "origin";
    private static final String TAG_RENDER = "render";

    /**
     * How often the pieces on a board are reconciled even when nothing changed.
     *
     * <p>State changes reconcile immediately; this slower cadence only exists so a chunk reload or an
     * out-of-band edit repairs itself without an entity query on every sync interval.</p>
     */
    private static final int PIECE_REPAIR_TICKS = 100;

    private BlockPos origin;
    private BoardRenderState renderState = BoardRenderState.idle();
    private int syncCooldown;
    private int repairCooldown;

    public BoardPartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.origin = pos;
    }

    /** The plate that owns the board; a plate is its own origin until it is bound. */
    public BlockPos origin() {
        return origin;
    }

    public boolean isOrigin() {
        return origin.equals(worldPosition);
    }

    public Direction facing() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof XiangqiBoardBlock) {
            return BoardStructure.horizontal(state.getValue(XiangqiBoardBlock.FACING));
        }
        return Direction.NORTH;
    }

    public BoardRenderState renderState() {
        return renderState;
    }

    /** Called once the whole 2x2 structure exists. */
    public void bindOrigin(BlockPos origin) {
        this.origin = origin;
        setChanged();
    }

    public void applyRenderState(BoardRenderState state) {
        if (state != null) {
            this.renderState = state;
            setChanged();
        }
    }

    /**
     * Server-side upkeep: re-reads the authoritative state from the match store every
     * {@code interval} ticks and pushes a block update when it disagrees with the local copy.
     *
     * <p>This is what makes the world render repair itself after a chunk reload, a match that was
     * edited while the chunk was unloaded, or a client that missed a packet.</p>
     */
    public void serverTick(int interval) {
        if (--syncCooldown > 0) {
            return;
        }
        syncCooldown = Math.max(1, interval);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BoardRenderState current = MatchService.renderStateFor(serverLevel, origin);
        boolean changed = current.revision() != renderState.revision() || current.active() != renderState.active();
        if (isOrigin() && (changed || --repairCooldown <= 0)) {
            // The pieces are entities, so this is also where they come back after a chunk reload or a
            // match that was edited while the chunk was unloaded. Reconciling costs an entity query and
            // a map, so on a board that is simply sitting there it runs on a slow repair cadence rather
            // than on every sync interval - a state change still reconciles immediately.
            repairCooldown = PIECE_REPAIR_TICKS;
            BoardPieces.refresh(serverLevel, origin, facing(), current);
        }
        if (changed) {
            renderState = current;
            setChanged();
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(TAG_ORIGIN, origin.asLong());
        // The render state is derived from the match store, so it is not persisted; it is added to
        // the update tag only, which keeps saved chunks small and always self-consistent.
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_ORIGIN)) {
            origin = BlockPos.of(tag.getLong(TAG_ORIGIN));
        }
        if (tag.contains(TAG_RENDER)) {
            renderState = BoardRenderState.load(tag.getCompound(TAG_RENDER));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Adopt the authoritative state before answering, so a chunk that is being sent for the first
        // time already carries the live position instead of waiting for the next sweep.
        if (level instanceof ServerLevel serverLevel) {
            renderState = MatchService.renderStateFor(serverLevel, origin);
        }
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put(TAG_RENDER, renderState.save());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
