package com.khomo.chinesechess.match;

import com.khomo.chinesechess.Config;
import com.khomo.chinesechess.ChineseChess;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Low-frequency housekeeping for every board on the server.
 *
 * <p>Runs on a fixed cadence rather than every tick, and spreads work out so that a world with many
 * boards still pays a constant price per sweep:</p>
 * <ul>
 *   <li>stale entries are evicted from the shared move cache,</li>
 *   <li>a small round-robin slice of matches is re-validated against the world,</li>
 *   <li>finished matches are cleared once their result has been displayed for the configured time -
 *       this is what automatically takes players out of a game that is over,</li>
 *   <li>boards that have been left alone while waiting for an opponent are cleared after the idle
 *       delay, which bounds memory use on a long-running server and returns an abandoned board to the
 *       opening array.</li>
 * </ul>
 *
 * <p>These are the only two things that ever clear a board, together with a resignation (which is a
 * finished game) - a player pressing escape deliberately does not.</p>
 *
 * <p>Single responsibility: keep the match table clean and self-consistent.</p>
 */
public final class MatchSweeper {

    /** How often the sweep runs. Five seconds is far below any human-noticeable latency. */
    private static final int INTERVAL_TICKS = 100;
    /** How many boards are structurally re-validated per sweep. */
    private static final int VALIDATIONS_PER_SWEEP = 8;

    private static int cursor;

    private MatchSweeper() {}

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getGameTime() % INTERVAL_TICKS != 0) {
                continue;
            }
            sweep(level);
        }
    }

    private static void sweep(ServerLevel level) {
        long now = level.getGameTime();
        MatchRegistry registry = MatchRegistry.get(level);
        registry.moveCache().sweep(System.currentTimeMillis());

        List<XiangqiMatch> matches = new ArrayList<>(registry.all());
        if (matches.isEmpty()) {
            return;
        }

        int validations = Math.min(VALIDATIONS_PER_SWEEP, matches.size());
        for (int index = 0; index < validations; index++) {
            cursor = (cursor + 1) % matches.size();
            XiangqiMatch candidate = matches.get(cursor);
            if (registry.find(candidate.origin()) == candidate) {
                MatchValidator.validate(level, candidate);
            }
        }

        int closeAfter = Config.autoCloseDelayTicks();
        long idleMillis = Config.idleTimeoutMillis();
        for (XiangqiMatch match : new ArrayList<>(registry.all())) {
            if (match.phase() == MatchPhase.FINISHED && now - match.finishedTick() >= closeAfter) {
                // A decided game - checkmate, stalemate, a resignation - is the first thing that clears
                // a board, once the result has been on screen long enough.
                ChineseChess.LOGGER.debug("Auto-closing finished Chinese chess board at {}", match.origin());
                BoardSync.closeScreens(level, match, "chinese_chess.close.finished");
                registry.remove(match.origin());
                BoardSync.pushIdleRender(level, match.origin(), match.facing());
            } else if (isAbandoned(match, idleMillis)) {
                // Nobody has played here for a long time: clear the board so an abandoned position does
                // not sit on the world forever.
                ChineseChess.LOGGER.debug("Clearing idle Chinese chess board at {}", match.origin());
                BoardSync.closeScreens(level, match, "chinese_chess.close.idle");
                registry.remove(match.origin());
                BoardSync.pushIdleRender(level, match.origin(), match.facing());
            } else if (match.hasParticipants()) {
                // Reconciliation. The authoritative state is pushed to both players on a slow timer so
                // that a client which missed a transition - a seat being taken, a game starting, a
                // verdict - is pulled back in step instead of being left unable to play. The packet is
                // a hundred bytes and the client ignores revisions it already has.
                BoardSync.updateScreens(level, match);
            }
        }
    }

    /**
     * Whether a board has been left alone long enough to be cleared.
     *
     * <p>Only a board that is <em>waiting</em> can be abandoned this way, which covers the three ways a
     * board ends up idle: nobody is seated, one player is waiting for an opponent, or a game was
     * suspended when somebody pressed escape. A running game is never cleared this way, so thinking
     * about a move for a long time can never cost anybody the game; a decided game has its own, shorter
     * timer above.</p>
     *
     * <p>The age is measured on the wall clock. A world clock stops when a single-player or LAN host is
     * paused - the window loses focus, the menu is open - and a board abandoned in exactly that state
     * would otherwise keep its position forever, because its tick-based age would never grow.</p>
     */
    private static boolean isAbandoned(XiangqiMatch match, long idleMillis) {
        return match.phase() == MatchPhase.WAITING
                && System.currentTimeMillis() - match.lastActivityMillis() >= idleMillis;
    }
}
