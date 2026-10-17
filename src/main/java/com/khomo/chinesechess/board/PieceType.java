package com.khomo.chinesechess.board;

/**
 * The seven kinds of Xiangqi piece.
 *
 * <p>Single responsibility: enumerate the piece kinds and the purely rule-based facts about them
 * (for example whether a kind can ever attack an enemy general). Movement itself lives in
 * {@code com.khomo.chinesechess.board.rules}.</p>
 */
public enum PieceType {
    /** 帅 / 将 - confined to the palace, moves one orthogonal step, may not face the enemy general. */
    GENERAL,
    /** 仕 / 士 - confined to the palace, moves one diagonal step. */
    ADVISOR,
    /** 相 / 象 - two diagonal steps, blocked by its "eye", may never cross the river. */
    ELEPHANT,
    /** 马 - one orthogonal plus one diagonal step, blocked by its "leg". */
    HORSE,
    /** 车 - slides any distance orthogonally. */
    CHARIOT,
    /** 炮 - slides when quiet, captures only after jumping exactly one screen piece. */
    CANNON,
    /** 兵 / 卒 - one step forward, plus sideways after crossing the river. */
    SOLDIER;

    private static final PieceType[] VALUES = values();

    /** Safe lookup used by the network codec; {@code null} for unknown ordinals. */
    public static PieceType of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }

    /**
     * Whether this kind can ever put an enemy general in check.
     *
     * <p>Advisors and elephants are confined to their own half of the board and therefore can never
     * reach the enemy palace; this fact lets the terminal-condition code skip certain draw tests.</p>
     */
    public boolean isAttacking() {
        return this == CHARIOT || this == HORSE || this == CANNON || this == SOLDIER;
    }
}
