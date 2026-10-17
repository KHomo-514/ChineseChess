package com.khomo.chinesechess;

import com.khomo.chinesechess.board.RuleSettings;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's configuration, split into the two lifecycles NeoForge expects.
 *
 * <p>The common file holds everything that changes how a game is played or how the server behaves,
 * so an operator can tune the rules per world. The client file holds presentation preferences only;
 * it is read exclusively from client code, which is why no dedicated-server code path touches those
 * fields.</p>
 *
 * <p>Every accessor is defensive: rules may be asked for before the file has been read (during world
 * loading, for example), so the accessors fall back to the documented defaults instead of throwing.
 * That is also what keeps a world loadable after an operator deletes the config file.</p>
 *
 * <p>Single responsibility: own the mod's settings.</p>
 */
public final class Config {

    // ------------------------------------------------------------------ common

    private static final ModConfigSpec.Builder COMMON = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_REPETITION_RULES = COMMON
            .comment("Apply the repetition rules (long-check / threefold repetition). Disable for a purely casual game.")
            .define("rules.repetition.enabled", true);

    public static final ModConfigSpec.BooleanValue PERPETUAL_CHECK_LOSES = COMMON
            .comment("When a position repeats three times and one side was in check every time,",
                    "that side's opponent loses (perpetual check) instead of the game being drawn.")
            .define("rules.repetition.perpetualCheckLoses", true);

    public static final ModConfigSpec.IntValue NO_CAPTURE_DRAW_PLIES = COMMON
            .comment("Natural move limit: plies without any capture before the game is drawn.",
                    "The official value is 120 plies (60 rounds). Set to 0 to switch the rule off.")
            .defineInRange("rules.noCaptureDrawPlies", 120, 0, 2000);

    public static final ModConfigSpec.BooleanValue INSUFFICIENT_MATERIAL_DRAW = COMMON
            .comment("Draw when neither side owns a chariot, horse, cannon or soldier.")
            .define("rules.insufficientMaterialDraw", true);

    public static final ModConfigSpec.BooleanValue ALLOW_SPECTATORS = COMMON
            .comment("Let further players open a running board in read-only mode.")
            .define("match.allowSpectators", true);

    public static final ModConfigSpec.BooleanValue PROTECT_BOARD_DURING_MATCH = COMMON
            .comment("Prevent players from breaking a board while a game is running on it.")
            .define("match.protectDuringMatch", true);

    public static final ModConfigSpec.IntValue AUTO_CLOSE_SECONDS = COMMON
            .comment("How long the result stays on screen after a game ends before the board clears itself.",
                    "0 closes it immediately. This is the automatic exit required after a match is over.",
                    "A resignation is a finished game, so it clears the board this way too.")
            .defineInRange("match.autoCloseSeconds", 5, 0, 120);

    public static final ModConfigSpec.IntValue IDLE_TIMEOUT_SECONDS = COMMON
            .comment("How long a board may sit with nobody playing on it before it returns to the opening array.",
                    "A running game (both seats taken) is never cleared this way, however long a player thinks;",
                    "this covers a board nobody sits at, one player waiting for an opponent, and a game that was",
                    "suspended by pressing escape. Default is three minutes.")
            .defineInRange("match.idleTimeoutSeconds", 180, 10, 86400);

    public static final ModConfigSpec.IntValue MAX_MATCHES_PER_WORLD = COMMON
            .comment("Hard cap on remembered boards per dimension. 0 means unlimited.")
            .defineInRange("match.maxMatchesPerWorld", 1024, 0, 1_000_000);

    public static final ModConfigSpec.IntValue SYNC_INTERVAL_TICKS = COMMON
            .comment("How often a board re-checks its rendered state against the authoritative game.")
            .defineInRange("match.renderSyncIntervalTicks", 40, 5, 1200);

    public static final ModConfigSpec COMMON_SPEC = COMMON.build();

    // ------------------------------------------------------------------ client

    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ROTATE_BOARD_FOR_BLACK = CLIENT
            .comment("Show the board from your own side, so black sees the black pieces at the bottom.")
            .define("display.rotateBoardForBlack", true);

    public static final ModConfigSpec.BooleanValue HIGHLIGHT_LEGAL_MOVES = CLIENT
            .comment("Mark the points the selected piece may legally move to.")
            .define("display.highlightLegalMoves", true);

    public static final ModConfigSpec.BooleanValue SHOW_LAST_MOVE = CLIENT
            .comment("Mark the origin and destination of the last played ply.")
            .define("display.showLastMove", true);

    public static final ModConfigSpec.BooleanValue RENDER_BOARD_IN_WORLD = CLIENT
            .comment("Draw the grid and the pieces on the block itself.")
            .define("display.renderBoardInWorld", true);

    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    private Config() {}

    // ------------------------------------------------------------------ accessors

    /** The rules a newly created game should follow. */
    public static RuleSettings ruleSettings() {
        return new RuleSettings(
                ENABLE_REPETITION_RULES.get(),
                PERPETUAL_CHECK_LOSES.get(),
                NO_CAPTURE_DRAW_PLIES.get(),
                INSUFFICIENT_MATERIAL_DRAW.get());
    }

    /** As {@link #ruleSettings()}, but never throws when the file has not been read yet. */
    public static RuleSettings ruleSettingsOrFallback() {
        try {
            return ruleSettings();
        } catch (RuntimeException exception) {
            return RuleSettings.DEFAULT;
        }
    }

    public static boolean allowSpectators() {
        return booleanOr(ALLOW_SPECTATORS, true);
    }

    public static boolean protectBoardDuringMatch() {
        return booleanOr(PROTECT_BOARD_DURING_MATCH, true);
    }

    public static int autoCloseDelayTicks() {
        return intOr(AUTO_CLOSE_SECONDS, 5) * 20;
    }

    /**
     * The idle timeout in milliseconds, which is what the sweep actually measures.
     *
     * <p>Real time rather than game time: a paused single-player or LAN host stops advancing the world
     * clock, so a tick-based timeout would never expire on exactly the boards this is meant to clear -
     * the ones everybody has walked away from.</p>
     */
    public static long idleTimeoutMillis() {
        return intOr(IDLE_TIMEOUT_SECONDS, 180) * 1000L;
    }

    public static int maxMatchesPerWorld() {
        return intOr(MAX_MATCHES_PER_WORLD, 1024);
    }

    public static int renderSyncIntervalTicks() {
        return intOr(SYNC_INTERVAL_TICKS, 40);
    }

    public static boolean rotateBoardForBlack() {
        return booleanOr(ROTATE_BOARD_FOR_BLACK, true);
    }

    public static boolean highlightLegalMoves() {
        return booleanOr(HIGHLIGHT_LEGAL_MOVES, true);
    }

    public static boolean showLastMove() {
        return booleanOr(SHOW_LAST_MOVE, true);
    }

    public static boolean renderBoardInWorld() {
        return booleanOr(RENDER_BOARD_IN_WORLD, true);
    }

    private static boolean booleanOr(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static int intOr(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }
}
