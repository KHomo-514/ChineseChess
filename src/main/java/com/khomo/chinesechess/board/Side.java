package com.khomo.chinesechess.board;

/**
 * The two opposing sides of a Xiangqi game.
 *
 * <p>RED owns files 0..8 / ranks 0..4 (the "home" half below the river) and always moves first.
 * BLACK owns ranks 5..9. A side is defined purely by the rules of the game and therefore carries
 * no presentation data: translation keys and textures live in the presentation packages.</p>
 *
 * <p>Single responsibility: model which of the two players a piece, a seat or a move belongs to.</p>
 */
public enum Side {
    RED,
    BLACK;

    private static final Side[] VALUES = values();

    /** The opposing side. */
    public Side opposite() {
        return this == RED ? BLACK : RED;
    }

    public boolean isRed() {
        return this == RED;
    }

    /** Safe lookup for network / NBT decoding, defaulting to {@link #RED}. */
    public static Side of(int ordinal) {
        return ordinal == BLACK.ordinal() ? BLACK : RED;
    }

    /** Safe lookup that maps every out-of-range value to {@code null} (used for "no side"). */
    public static Side ofNullable(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }
}
