package com.khomo.chinesechess.match;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.EndReason;
import com.khomo.chinesechess.block.BoardStructure;
import com.khomo.chinesechess.block.XiangqiBoardBlock;
import com.khomo.chinesechess.entity.BoardPieces;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Checks that a board still exists and still makes sense, and repairs it when it does not.
 *
 * <p>Two different kinds of damage are handled here:</p>
 * <ul>
 *   <li><strong>world damage</strong> - half a board was removed by a piston, an explosion or a
 *       {@code /setblock} that bypassed every event. The match is dissolved and the stray plates are
 *       cleaned up.</li>
 *   <li><strong>data damage</strong> - a saved position that no longer parses as a legal array. The
 *       game is rebuilt from the opening array rather than refusing to load, so a world is never
 *       bricked by a bad save.</li>
 * </ul>
 *
 * <p>Single responsibility: verify and repair board integrity.</p>
 */
public final class MatchValidator {

    private MatchValidator() {}

    /**
     * Checks one match against the world.
     *
     * @return {@code true} when the match is healthy and should be kept
     */
    public static boolean validate(ServerLevel level, XiangqiMatch match) {
        Direction facing = BoardStructure.facingOf(level, match.origin());
        if (facing == null || !BoardStructure.isComplete(level, match.origin(), facing)) {
            end(level, match, EndReason.BOARD_DESTROYED, "chinese_chess.close.board_destroyed");
            return false;
        }
        if (!Board.isPlausible(match.game().board().toCodes())) {
            ChineseChess.LOGGER.warn(
                    "Chinese chess board at {} held an impossible position; resetting it to the opening array",
                    match.origin());
            match.reset(level.getGameTime());
            MatchRegistry.get(level).setDirty();
            BoardSync.pushRender(level, match);
            return true;
        }
        return true;
    }

    /** Ends a match, tells everyone why and forgets it. */
    public static void end(ServerLevel level, XiangqiMatch match, EndReason reason, String messageKey) {
        match.dissolve(reason, level.getGameTime());
        BoardSync.notifyFinished(level, match, 0);
        BoardSync.closeScreens(level, match, messageKey);
        BoardPieces.removeAll(level, match.origin());
        MatchRegistry.get(level).remove(match.origin());
        BoardSync.pushIdleRender(level, match.origin(), match.facing());
    }

    /**
     * Cleans up a board whose plates no longer agree.
     *
     * <p>Reached when a player interacts with a plate that cannot resolve to a complete board: the
     * remaining plates of that footprint are taken away and any match that was keyed on the origin is
     * dropped, so the player can simply place a fresh board.</p>
     */
    public static void repairBrokenBoard(ServerLevel level, BlockPos partPos) {
        BlockState state = level.getBlockState(partPos);
        if (!(state.getBlock() instanceof XiangqiBoardBlock)) {
            return;
        }
        Direction facing = BoardStructure.horizontal(state.getValue(XiangqiBoardBlock.FACING));
        BlockPos origin = BoardStructure.originOf(
                partPos, facing, state.getValue(XiangqiBoardBlock.PART));

        XiangqiMatch match = MatchRegistry.get(level).find(origin);
        if (match != null) {
            end(level, match, EndReason.BOARD_DESTROYED, "chinese_chess.close.board_destroyed");
        } else {
            BoardSync.pushIdleRender(level, origin, facing);
        }
        // Whatever happened to the match, the pieces standing on this board have nowhere to stand now.
        BoardPieces.removeAll(level, origin);
        XiangqiBoardBlock.removeAll(level, origin, facing);
        ChineseChess.LOGGER.debug("Removed an inconsistent Chinese chess board at {}", origin);
    }
}
