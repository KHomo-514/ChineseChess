package com.khomo.chinesechess.block;

import com.khomo.chinesechess.blockentity.BoardPartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * All arithmetic and world queries about the 2x2 multiblock.
 *
 * <p>Nothing else in the mod is allowed to compute part positions itself; keeping that knowledge in
 * one class is what makes the blockstate, the block entity, the renderer and the GUI agree on where
 * the board is and which way round it lies.</p>
 *
 * <p>Single responsibility: map between "a board" and the four blocks it occupies.</p>
 */
public final class BoardStructure {

    /** Number of blocks one board occupies. */
    public static final int PART_COUNT = BoardPart.ALL.length;

    private BoardStructure() {}

    /** The horizontal direction to the placer's right; the board's file axis. */
    public static Direction rightOf(Direction facing) {
        return facing.getClockWise();
    }

    /** Normalises an arbitrary direction to the horizontal facing stored in the blockstate. */
    public static Direction horizontal(Direction direction) {
        return direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
    }

    public static BlockPos positionOf(BlockPos origin, Direction facing, BoardPart part) {
        return origin.relative(facing, part.rankStep()).relative(rightOf(facing), part.fileStep());
    }

    public static BlockPos originOf(BlockPos partPos, Direction facing, BoardPart part) {
        return partPos.relative(facing, -part.rankStep()).relative(rightOf(facing), -part.fileStep());
    }

    public static List<BlockPos> positions(BlockPos origin, Direction facing) {
        List<BlockPos> positions = new ArrayList<>(PART_COUNT);
        for (BoardPart part : BoardPart.ALL) {
            positions.add(positionOf(origin, facing, part));
        }
        return positions;
    }

    /**
     * Whether a whole board may be placed with {@code origin} as its near corner: every one of the
     * four blocks must be replaceable and must rest on a full, sturdy top face. This is what makes
     * "placeable on any full block" true while still refusing to float.
     */
    public static boolean canPlace(BlockGetter level, BlockPos origin, Direction facing) {
        for (BoardPart part : BoardPart.ALL) {
            BlockPos pos = positionOf(origin, facing, part);
            BlockState existing = level.getBlockState(pos);
            if (!existing.canBeReplaced()) {
                return false;
            }
            BlockPos below = pos.below();
            BlockState support = level.getBlockState(below);
            if (!support.isFaceSturdy(level, below, Direction.UP)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the player is allowed to place a board at all, including the clicked position. */
    public static boolean canPlaceFrom(LevelReader level, BlockPos origin, Direction facing) {
        return canPlace(level, origin, facing);
    }

    /**
     * Whether all four blocks are still our block, correctly oriented, with a block entity that
     * points back at {@code origin}. Used by the consistency check that repairs damaged boards.
     */
    public static boolean isComplete(BlockGetter level, BlockPos origin, Direction facing) {
        for (BoardPart part : BoardPart.ALL) {
            BlockPos pos = positionOf(origin, facing, part);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof XiangqiBoardBlock block)) {
                return false;
            }
            if (state.getValue(XiangqiBoardBlock.PART) != part
                    || state.getValue(XiangqiBoardBlock.FACING) != facing) {
                return false;
            }
            if (level instanceof Level realLevel) {
                BlockEntity blockEntity = realLevel.getBlockEntity(pos);
                if (!(blockEntity instanceof BoardPartBlockEntity board) || !origin.equals(board.origin())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** @return the part occupying {@code pos}, or {@code null} when {@code pos} is not part of this board. */
    @Nullable
    public static BoardPart partAt(BlockPos origin, Direction facing, BlockPos pos) {
        for (BoardPart part : BoardPart.ALL) {
            if (positionOf(origin, facing, part).equals(pos)) {
                return part;
            }
        }
        return null;
    }

    /**
     * Resolves which board a clicked block belongs to.
     *
     * <p>The block entity is trusted first because it also survives the case where a neighbouring
     * part was replaced by a rotated board; the blockstate is the fallback.</p>
     */
    @Nullable
    public static BlockPos resolveOrigin(Level level, BlockPos partPos) {
        BlockState state = level.getBlockState(partPos);
        if (!(state.getBlock() instanceof XiangqiBoardBlock)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(partPos);
        if (blockEntity instanceof BoardPartBlockEntity board) {
            BlockPos origin = board.origin();
            if (origin != null && isComplete(level, origin, board.facing())) {
                return origin;
            }
        }
        Direction facing = state.getValue(XiangqiBoardBlock.FACING);
        return originOf(partPos, facing, state.getValue(XiangqiBoardBlock.PART));
    }

    /** @return the facing stored by the board whose origin is {@code origin}, or {@code null}. */
    @Nullable
    public static Direction facingOf(Level level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        if (state.getBlock() instanceof XiangqiBoardBlock) {
            return state.getValue(XiangqiBoardBlock.FACING);
        }
        BlockEntity blockEntity = level.getBlockEntity(origin);
        if (blockEntity instanceof BoardPartBlockEntity board) {
            BlockPos resolved = board.origin();
            if (resolved != null) {
                BlockState originState = level.getBlockState(resolved);
                if (originState.getBlock() instanceof XiangqiBoardBlock) {
                    return originState.getValue(XiangqiBoardBlock.FACING);
                }
            }
        }
        return null;
    }
}
