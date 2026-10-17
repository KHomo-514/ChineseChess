package com.khomo.chinesechess.entity;

import com.khomo.chinesechess.block.XiangqiBoardBlock;
import com.khomo.chinesechess.board.Piece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * One playing piece standing on an in-world board.
 *
 * <p>The pieces used to be painted by the board's block entity renderer, which meant they only existed
 * as pixels: they had no presence in the world, no depth, and nothing else could see them. Each piece
 * is now a real entity that the server places on its point, so the board carries actual objects and
 * the drawing code only has to describe one piece at a time.</p>
 *
 * <p>They are deliberately inert. A board is a game of rules, not a physics toy, so a piece has no
 * gravity, no collision, cannot be pushed, picked up, attacked or saved: it is recreated from the
 * match state every time the board refreshes, which is also why {@link #shouldBeSaved()} is false -
 * a position is derived, never stored twice.</p>
 *
 * <p>Single responsibility: be one piece on one board.</p>
 */
public class XiangqiPieceEntity extends Entity {

    private static final String TAG_BOARD = "Board";
    private static final String TAG_SQUARE = "Square";
    private static final String TAG_PIECE = "Piece";
    private static final String TAG_FACING = "Facing";

    /** The encoded piece, exactly as it appears in the board's square array. */
    private static final EntityDataAccessor<Byte> DATA_PIECE =
            SynchedEntityData.defineId(XiangqiPieceEntity.class, EntityDataSerializers.BYTE);
    /** Which way the board faces, so the symbol is printed the same way round as the board. */
    private static final EntityDataAccessor<Byte> DATA_FACING =
            SynchedEntityData.defineId(XiangqiPieceEntity.class, EntityDataSerializers.BYTE);

    /** The board this piece belongs to; bookkeeping only, and never needed on the client. */
    private BlockPos board = BlockPos.ZERO;
    /** Which of the ninety points it stands on; bookkeeping only. */
    private int square = -1;

    /** How often a piece re-checks that its board still exists, in ticks. */
    private static final int BOARD_CHECK_INTERVAL = 20;

    public XiangqiPieceEntity(EntityType<? extends XiangqiPieceEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    // ------------------------------------------------------------------ identity

    public BlockPos board() {
        return board;
    }

    public int square() {
        return square;
    }

    public byte pieceCode() {
        return entityData.get(DATA_PIECE);
    }

    public Piece piece() {
        return Piece.decode(pieceCode());
    }

    public Direction facing() {
        return Direction.from3DDataValue(entityData.get(DATA_FACING));
    }

    /** Prepares this entity for a point: what it shows, and where it belongs. */
    public void place(BlockPos board, int square, byte pieceCode, Direction facing) {
        this.board = board;
        this.square = square;
        this.entityData.set(DATA_PIECE, pieceCode);
        this.entityData.set(DATA_FACING, (byte) facing.get3DDataValue());
    }

    // ------------------------------------------------------------------ entity plumbing

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PIECE, (byte) 0);
        builder.define(DATA_FACING, (byte) Direction.NORTH.get3DDataValue());
    }

    /**
     * Nothing to simulate: the position comes from the board, not from physics.
     *
     * <p>It does however check now and then that its board is still there, and retires itself if it is
     * not. The pieces are entities, so they do not go away with the blocks the way a block entity
     * would, and every path that can remove a board - a player breaking it, a chunk-boundary case, a
     * command - would otherwise have to remember to sweep them. Staggered by entity id so a full board
     * of thirty-two pieces never checks on the same tick, and it is a block lookup, not a search.</p>
     */
    @Override
    public void tick() {
        if (level().isClientSide() || board == BlockPos.ZERO) {
            return;
        }
        if (tickCount % BOARD_CHECK_INTERVAL == getId() % BOARD_CHECK_INTERVAL) {
            if (!(level().getBlockState(board).getBlock() instanceof XiangqiBoardBlock)) {
                discard();
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        board = BlockPos.of(tag.getLong(TAG_BOARD));
        square = tag.getInt(TAG_SQUARE);
        entityData.set(DATA_PIECE, tag.getByte(TAG_PIECE));
        entityData.set(DATA_FACING, tag.getByte(TAG_FACING));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong(TAG_BOARD, board.asLong());
        tag.putInt(TAG_SQUARE, square);
        tag.putByte(TAG_PIECE, pieceCode());
        tag.putByte(TAG_FACING, entityData.get(DATA_FACING));
    }

    /** Derived state: the board rebuilds its pieces, so a chunk never stores them. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** Clicks belong to the board underneath, not to the pieces standing on it. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    /** Pieces are a couple of pixels across, so they are only worth drawing up close. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0D * 64.0D;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }
}
