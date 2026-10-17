package com.khomo.chinesechess.board;

/**
 * The result of asking a game to play a move.
 *
 * <p>Single responsibility: let the caller react to (and translate) every rejection distinctly.</p>
 */
public enum MoveAttempt {
    /** The move was legal and has been played. */
    ACCEPTED("chinese_chess.move.accepted"),
    /** The game is already decided. */
    GAME_OVER("chinese_chess.move.game_over"),
    /** It is the other player's turn. */
    NOT_YOUR_TURN("chinese_chess.move.not_your_turn"),
    /** The origin does not hold a piece of the side that is to move. */
    NOT_YOUR_PIECE("chinese_chess.move.not_your_piece"),
    /** The move breaks a movement, blocking, self-check or flying-general rule. */
    ILLEGAL("chinese_chess.move.illegal"),
    /** The move could not be decoded at all. */
    MALFORMED("chinese_chess.move.malformed");

    private final String translationKey;

    MoveAttempt(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public boolean accepted() {
        return this == ACCEPTED;
    }
}
