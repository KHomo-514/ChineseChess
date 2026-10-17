package com.khomo.chinesechess.block;

import com.khomo.chinesechess.Config;
import com.khomo.chinesechess.blockentity.BoardPartBlockEntity;
import com.khomo.chinesechess.match.MatchService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

/**
 * The physical board block: one of the four flat plates that together make a 2x2 Xiangqi board.
 *
 * <p>The block only knows about its own geometry and about keeping the 2x2 structure intact. All
 * game decisions are delegated to {@link MatchService}, which keeps the block free of rules
 * knowledge and the rules free of world knowledge.</p>
 *
 * <p>Single responsibility: behave as one plate of the board multiblock.</p>
 */
public class XiangqiBoardBlock extends Block implements EntityBlock {

    public static final MapCodec<XiangqiBoardBlock> CODEC = simpleCodec(XiangqiBoardBlock::new);

    /** Which quarter of the board this plate is. */
    public static final EnumProperty<BoardPart> PART =
            EnumProperty.create("part", BoardPart.class);
    /** Which way the board runs; the file axis points to this direction's clockwise neighbour. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** The board is a two-pixel-thick plate that can be walked over. */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);

    /**
     * Guards the four-way removal cascade: positions currently being torn down so that removing one
     * plate cannot recursively try to remove the others again.
     */
    private static final Set<BlockPos> REMOVAL_GUARD = ConcurrentHashMap.newKeySet();

    public XiangqiBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(PART, BoardPart.ORIGIN)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends XiangqiBoardBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        // The four plates of one board should read as a single continuous surface.
        return adjacentState.is(this) && side.getAxis().isHorizontal();
    }

    // ------------------------------------------------------------------ placement

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos origin = context.getClickedPos();
        if (!BoardStructure.canPlace(context.getLevel(), origin, facing)) {
            return null;
        }
        return defaultBlockState()
                .setValue(PART, BoardPart.ORIGIN)
                .setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Direction facing = BoardStructure.horizontal(state.getValue(FACING));
        for (BoardPart part : BoardPart.ALL) {
            if (part == BoardPart.ORIGIN) {
                continue;
            }
            BlockPos target = BoardStructure.positionOf(pos, facing, part);
            BlockState partState = defaultBlockState().setValue(PART, part).setValue(FACING, facing);
            if (!level.setBlock(target, partState, Block.UPDATE_ALL)) {
                // Something changed between the placement check and now: undo the whole board.
                removeAll(level, pos, facing);
                return;
            }
        }
        bindParts(level, pos, facing);
    }

    private static void bindParts(Level level, BlockPos origin, Direction facing) {
        for (BoardPart part : BoardPart.ALL) {
            BlockPos pos = BoardStructure.positionOf(origin, facing, part);
            if (level.getBlockEntity(pos) instanceof BoardPartBlockEntity board) {
                board.bindOrigin(origin);
            }
        }
    }

    // ------------------------------------------------------------------ survival

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            Direction facing = BoardStructure.horizontal(state.getValue(FACING));
            BoardPart part = state.getValue(PART);
            BlockPos origin = BoardStructure.originOf(pos, facing, part);
            removeAll(level, origin, facing);
            if (level instanceof ServerLevel serverLevel) {
                MatchService.onBoardRemoved(serverLevel, origin);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Takes the whole board away.
     *
     * <p>Uses {@code removeBlock} rather than {@code destroyBlock}, so only the plate the player
     * actually broke produces an item - the other three are collateral, exactly like a vanilla
     * multi-part structure.</p>
     */
    public static void removeAll(Level level, BlockPos origin, Direction facing) {
        for (BoardPart part : BoardPart.ALL) {
            BlockPos pos = BoardStructure.positionOf(origin, facing, part);
            if (!REMOVAL_GUARD.add(pos)) {
                continue;
            }
            try {
                if (level.getBlockState(pos).getBlock() instanceof XiangqiBoardBlock) {
                    level.removeBlock(pos, false);
                }
            } finally {
                REMOVAL_GUARD.remove(pos);
            }
        }
    }

    // ------------------------------------------------------------------ interaction

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        return BoardInteractionHandler.onUse(level, pos, player)
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        return BoardInteractionHandler.onUse(level, pos, player)
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }

    // ------------------------------------------------------------------ block entity

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoardPartBlockEntity(com.khomo.chinesechess.registry.ModBlockEntities.XIANGQI_BOARD.get(), pos, state);
    }

    /**
     * Server-side upkeep for every plate: each one re-checks its rendered state against the
     * authoritative game on the interval the operator configured, which is what repairs a board
     * after a chunk reload or a missed packet.
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof BoardPartBlockEntity board) {
                board.serverTick(Config.renderSyncIntervalTicks());
            }
        };
    }
}
