package com.khomo.chinesechess.board;

/**
 * An immutable piece: a kind owned by a side.
 *
 * <p>Single responsibility: represent one piece and its compact wire/NBT encoding.</p>
 */
public record Piece(PieceType type, Side side) {

    /** Code used for "no piece" on the wire and in NBT. */
    public static final byte EMPTY_CODE = 0;

    /**
     * Compact code: {@code 1 + type.ordinal() * 2 + side.ordinal()}, so every value in
     * {@code 1..14} identifies a piece uniquely and {@code 0} means "empty".
     */
    public byte encode() {
        return (byte) (1 + type.ordinal() * 2 + side.ordinal());
    }

    /** Inverse of {@link #encode()}; returns {@code null} for empty or unknown codes. */
    public static Piece decode(byte code) {
        if (code <= EMPTY_CODE) {
            return null;
        }
        int value = code - 1;
        PieceType type = PieceType.of(value >> 1);
        if (type == null) {
            return null;
        }
        return new Piece(type, Side.of(value & 1));
    }

    public boolean belongsTo(Side other) {
        return side == other;
    }
}
