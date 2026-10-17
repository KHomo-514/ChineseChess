package com.khomo.chinesechess.match;

import com.khomo.chinesechess.Config;
import com.khomo.chinesechess.board.EndReason;
import com.khomo.chinesechess.board.Move;
import com.khomo.chinesechess.board.MoveAttempt;
import com.khomo.chinesechess.board.Side;
import com.khomo.chinesechess.block.BoardStructure;
import com.khomo.chinesechess.blockentity.BoardRenderState;
import com.khomo.chinesechess.entity.BoardPieces;
import com.khomo.chinesechess.i18n.Translations;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * The server-side use cases of a board: joining, moving, resigning, leaving and losing the board.
 *
 * <p>This is the only class that turns a player request into match state changes, which keeps the
 * policy questions - may a spectator watch, what ends a game, how long does a result stay, when is a
 * board cleared - in one readable place instead of scattered through the block, the network layer and
 * the GUI.</p>
 *
 * <p>Every entry point expects to be called on the server thread with a live {@link ServerPlayer},
 * which is exactly what the payload handlers and the block's interaction callbacks guarantee.</p>
 *
 * <p>Single responsibility: apply a player's intent to a match.</p>
 */
public final class MatchService {

    private MatchService() {}

    // ------------------------------------------------------------------ joining

    /** Handles a right-click on any plate of a board. */
    public static void onBoardUsed(ServerPlayer player, BlockPos partPos) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = BoardStructure.resolveOrigin(level, partPos);
        if (origin == null) {
            MatchValidator.repairBrokenBoard(level, partPos);
            player.displayClientMessage(Translations.message("board.inconsistent"), true);
            return;
        }
        Direction facing = BoardStructure.facingOf(level, origin);
        if (facing == null) {
            MatchValidator.repairBrokenBoard(level, partPos);
            player.displayClientMessage(Translations.message("board.inconsistent"), true);
            return;
        }

        MatchRegistry registry = MatchRegistry.get(level);
        UUID playerId = player.getUUID();

        // A player can only ever sit at one board; visiting a second one means giving up the first.
        XiangqiMatch previous = registry.findByPlayer(playerId);
        if (previous != null && !previous.origin().equals(origin)) {
            leave(player, level, previous, false);
        }

        XiangqiMatch match = registry.findOrCreate(origin, facing, Config.ruleSettingsOrFallback());
        if (match == null) {
            player.displayClientMessage(Translations.message("board.too_many"), true);
            return;
        }

        JoinOutcome outcome = match.join(playerId, level.getGameTime(), Config.allowSpectators());
        if (outcome == JoinOutcome.DISALLOWED) {
            player.displayClientMessage(Translations.message("join.disallowed"), true);
            return;
        }

        registry.setDirty();
        player.displayClientMessage(Component.translatable(outcome.translationKey()), true);
        BoardSync.pushRender(level, match);
        // The joiner needs the screen opened, but the seat change also alters the state everybody
        // else is looking at: the second player sitting down is what starts the game, and without
        // this broadcast the first player's client would stay convinced the board is still waiting
        // - and refuse to let them move, because a waiting board is not playable.
        BoardSync.openScreen(player, match);
        BoardSync.updateScreens(level, match);
    }

    // ------------------------------------------------------------------ playing

    /** Applies a move request coming from the GUI. */
    public static void onMoveRequest(ServerPlayer player, BlockPos origin, Move move) {
        ServerLevel level = player.serverLevel();
        XiangqiMatch match = MatchRegistry.get(level).find(origin);
        if (match == null) {
            player.displayClientMessage(Translations.message("board.gone"), true);
            BoardSync.closeScreen(player, origin, "chinese_chess.close.gone");
            return;
        }
        Side side = match.sideOf(player.getUUID());
        if (side == null) {
            player.displayClientMessage(Translations.message("move.not_playing"), true);
            return;
        }
        if (move == null) {
            player.displayClientMessage(Component.translatable(MoveAttempt.MALFORMED.translationKey()), true);
            BoardSync.sendSnapshot(player, match);
            return;
        }

        MoveAttempt attempt = match.tryMove(side, move, level.getGameTime());
        if (attempt == MoveAttempt.GAME_OVER && match.phase() == MatchPhase.WAITING) {
            player.displayClientMessage(Translations.message("move.waiting_for_opponent"), true);
            BoardSync.sendSnapshot(player, match);
            return;
        }
        if (!attempt.accepted()) {
            player.displayClientMessage(Component.translatable(attempt.translationKey()), true);
            // The client thought this was legal; hand it the authoritative board so it repairs itself.
            BoardSync.sendSnapshot(player, match);
            return;
        }

        MatchRegistry.get(level).setDirty();
        BoardSync.pushRender(level, match);
        if (match.phase() == MatchPhase.FINISHED) {
            BoardSync.notifyFinished(level, match, Config.autoCloseDelayTicks());
        } else {
            BoardSync.updateScreens(level, match);
        }
    }

    // ------------------------------------------------------------------ resetting

    /** Clears the board back to the opening array, asked for by one of the two players. */
    public static void onResetRequest(ServerPlayer player, BlockPos origin) {
        ServerLevel level = player.serverLevel();
        XiangqiMatch match = MatchRegistry.get(level).find(origin);
        if (match == null) {
            player.displayClientMessage(Translations.message("board.gone"), true);
            BoardSync.closeScreen(player, origin, "chinese_chess.close.gone");
            return;
        }
        if (match.sideOf(player.getUUID()) == null) {
            // Only the two players may clear a board; an onlooker's click changes nothing.
            player.displayClientMessage(Translations.message("board.reset_refused"), true);
            return;
        }

        match.restart(level.getGameTime());
        MatchRegistry.get(level).setDirty();
        BoardSync.pushRender(level, match);
        BoardSync.updateScreens(level, match);
        player.displayClientMessage(Translations.message("board.reset_done"), true);
        // Tell the other side why their pieces just jumped back, so it does not look like a glitch.
        UUID playerId = player.getUUID();
        for (UUID viewer : match.audience()) {
            if (viewer.equals(playerId)) {
                continue;
            }
            ServerPlayer other = level.getServer().getPlayerList().getPlayer(viewer);
            if (other != null) {
                other.displayClientMessage(Translations.message("board.reset_opponent"), false);
            }
        }
    }

    // ------------------------------------------------------------------ leaving

    /** Handles the escape key, the exit button and the resign button. */
    public static void onLeave(ServerPlayer player, BlockPos origin, boolean resign) {
        ServerLevel level = player.serverLevel();
        XiangqiMatch match = MatchRegistry.get(level).find(origin);
        if (match == null) {
            return;
        }
        leave(player, level, match, resign);
    }

    private static void leave(ServerPlayer player, ServerLevel level, XiangqiMatch match, boolean resign) {
        long now = level.getGameTime();
        UUID playerId = player.getUUID();
        Side side = match.sideOf(playerId);
        match.removeViewer(playerId);

        if (side == null) {
            // A spectator simply stops watching.
            BoardSync.updateScreens(level, match);
            return;
        }

        if (resign && match.phase() == MatchPhase.PLAYING) {
            // Conceding ends the game, and the player keeps their seat: the verdict has to reach the
            // player who lost it exactly as it reaches the winner, and the board is cleared for both by
            // the usual auto-close afterwards. Freeing the seat here would drop them out of the audience
            // before the result was ever sent - which is why the loser's screen used to vanish while the
            // winner watched the result.
            match.resign(side, now);
        } else {
            // Any other departure frees the seat but never clears the board: the position stays where it
            // is, the match goes back to waiting, and whoever sits down next resumes that same game. The
            // board is only ever cleared by a decided game, a resignation, or the idle sweep.
            match.vacate(playerId);
            match.suspend(now);
        }

        MatchRegistry registry = MatchRegistry.get(level);
        registry.setDirty();
        BoardSync.pushRender(level, match);
        if (match.phase() == MatchPhase.FINISHED) {
            BoardSync.notifyFinished(level, match, Config.autoCloseDelayTicks());
        } else {
            BoardSync.updateScreens(level, match);
        }
    }

    /** Drops a disconnecting player from every board on the server. */
    public static void onPlayerDisconnected(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID playerId = player.getUUID();
        for (ServerLevel level : server.getAllLevels()) {
            MatchRegistry registry = MatchRegistry.get(level);
            for (XiangqiMatch match : registry.all()) {
                match.removeViewer(playerId);
            }
            XiangqiMatch match = registry.findByPlayer(playerId);
            if (match != null) {
                // Losing connection is a departure, not a resignation: the position is kept.
                leave(player, level, match, false);
            }
        }
    }

    // ------------------------------------------------------------------ maintenance

    /** Answers a client that noticed it was out of step. */
    public static void onResyncRequest(ServerPlayer player, BlockPos origin) {
        ServerLevel level = player.serverLevel();
        XiangqiMatch match = MatchRegistry.get(level).find(origin);
        if (match == null) {
            BoardSync.closeScreen(player, origin, "chinese_chess.close.gone");
            return;
        }
        BoardSync.pushRender(level, match);
        BoardSync.sendSnapshot(player, match);
    }

    /** Called when a plate of a board is destroyed, however it happened. */
    public static void onBoardRemoved(ServerLevel level, BlockPos origin) {
        MatchRegistry registry = MatchRegistry.get(level);
        XiangqiMatch match = registry.find(origin);
        if (match != null) {
            match.dissolve(EndReason.BOARD_DESTROYED, level.getGameTime());
            BoardSync.notifyFinished(level, match, 0);
            BoardSync.closeScreens(level, match, "chinese_chess.close.board_destroyed");
            registry.remove(origin);
        }
        // The pieces are entities rather than part of the block, so they have to leave with the board
        // whether or not anybody ever sat down at it: a board nobody has played on still stands there
        // showing the opening position, and its pieces are as real as any other.
        BoardPieces.removeAll(level, origin);
    }

    /**
     * The render state a plate should display.
     *
     * <p>This is the single derivation used by both the block entity update tag and the periodic
     * consistency sweep, which is why the in-world board can never disagree with the server's game.</p>
     */
    public static BoardRenderState renderStateFor(ServerLevel level, BlockPos origin) {
        XiangqiMatch match = MatchRegistry.get(level).find(origin);
        return match == null ? BoardRenderState.idle() : match.renderState();
    }

    /** Whether a running game makes a board worth protecting from being broken. */
    public static boolean isBoardProtected(ServerLevel level, BlockPos partPos) {
        if (!Config.protectBoardDuringMatch()) {
            return false;
        }
        BlockPos origin = BoardStructure.resolveOrigin(level, partPos);
        if (origin == null) {
            return false;
        }
        return isMatchRunningAt(level, origin);
    }

    /** Whether a live game is being played on the board whose origin plate is {@code origin}. */
    public static boolean isMatchRunningAt(ServerLevel level, BlockPos origin) {
        XiangqiMatch match = MatchRegistry.get(level).find(origin);
        return match != null && match.phase() == MatchPhase.PLAYING && match.hasParticipants();
    }

    /** @return the match on a board, used by the maintenance sweeps. */
    @Nullable
    public static XiangqiMatch find(ServerLevel level, BlockPos origin) {
        return MatchRegistry.get(level).find(origin);
    }
}
