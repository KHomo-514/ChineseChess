package com.khomo.chinesechess.match;

import com.khomo.chinesechess.board.Side;

/**
 * What happened when a player right-clicked a board.
 *
 * <p>Single responsibility: report the seating decision to the interaction layer, which owns the
 * player-facing wording.</p>
 */
public enum JoinOutcome {
    /** The player took the red seat; the game starts as soon as black arrives. */
    SEATED_RED("chinese_chess.join.seated_red", Side.RED),
    /** The player took the black seat and the game has just started. */
    SEATED_BLACK("chinese_chess.join.seated_black", Side.BLACK),
    /** The player already held a seat and simply reopened the board. */
    RESUMED("chinese_chess.join.resumed", null),
    /** Both seats were taken, so the player is watching. */
    SPECTATING("chinese_chess.join.spectating", null),
    /** The board refused the request (spectators disabled, or the match is being torn down). */
    DISALLOWED("chinese_chess.join.disallowed", null);

    private final String translationKey;
    private final Side side;

    JoinOutcome(String translationKey, Side side) {
        this.translationKey = translationKey;
        this.side = side;
    }

    public String translationKey() {
        return translationKey;
    }

    /** @return the seat this outcome granted, or {@code null} for onlookers. */
    public Side side() {
        return side;
    }

    public boolean seated() {
        return side != null;
    }
}
