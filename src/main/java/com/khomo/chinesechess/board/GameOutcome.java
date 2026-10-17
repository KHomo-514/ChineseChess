package com.khomo.chinesechess.board;

/**
 * The verdict of a game: who won and why.
 *
 * <p>Single responsibility: pair a {@link GameStatus} with the {@link EndReason} that produced it.</p>
 */
public record GameOutcome(GameStatus status, EndReason reason) {

    /** A game that is still being played. */
    public static final GameOutcome ONGOING = new GameOutcome(GameStatus.ONGOING, null);

    public boolean isFinished() {
        return status.isFinished();
    }

    public Side winner() {
        return status.winner();
    }

    public static GameOutcome victory(Side winner, EndReason reason) {
        return new GameOutcome(GameStatus.victoryOf(winner), reason);
    }

    public static GameOutcome draw(EndReason reason) {
        return new GameOutcome(GameStatus.DRAW, reason);
    }
}
