package com.khomo.chinesechess.board;

/**
 * Who is winning, if anybody.
 *
 * <p>Single responsibility: name the possible verdicts of a finished or running game.</p>
 */
public enum GameStatus {
    ONGOING("chinese_chess.status.ongoing"),
    RED_WIN("chinese_chess.status.red_win"),
    BLACK_WIN("chinese_chess.status.black_win"),
    DRAW("chinese_chess.status.draw");

    private final String translationKey;

    GameStatus(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public boolean isFinished() {
        return this != ONGOING;
    }

    /** @return the winning side, or {@code null} for a draw or a running game. */
    public Side winner() {
        return switch (this) {
            case RED_WIN -> Side.RED;
            case BLACK_WIN -> Side.BLACK;
            default -> null;
        };
    }

    public static GameStatus of(int ordinal) {
        GameStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ONGOING;
    }

    /** The status that records {@code winner} as the victor. */
    public static GameStatus victoryOf(Side winner) {
        return winner.isRed() ? RED_WIN : BLACK_WIN;
    }
}
