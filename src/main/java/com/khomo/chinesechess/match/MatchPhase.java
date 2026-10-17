package com.khomo.chinesechess.match;

/**
 * Where a board is in its life cycle.
 *
 * <p>Single responsibility: name the phases a match passes through.</p>
 */
public enum MatchPhase {
    /**
     * Nobody has started playing here yet, or one seat (or none) is taken and the board waits.
     *
     * <p>This also covers a <em>suspended</em> game: when a player walks away mid-game the phase
     * returns to waiting while the position stays on the board, so the next pair to sit down resumes
     * it. The board is therefore not necessarily showing the opening array while waiting.</p>
     */
    WAITING("chinese_chess.phase.waiting"),
    /** Both seats are taken and the game is running. */
    PLAYING("chinese_chess.phase.playing"),
    /** Somebody won; the result is shown for a moment before the board clears itself. */
    FINISHED("chinese_chess.phase.finished");

    private final String translationKey;

    MatchPhase(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public static MatchPhase of(int ordinal) {
        MatchPhase[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : WAITING;
    }
}
