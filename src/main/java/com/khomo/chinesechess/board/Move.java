package com.khomo.chinesechess.board;

/**
 * A move between two board points, expressed as square indices (see {@link Squares}).
 *
 * <p>Single responsibility: carry the origin and destination of a single ply.</p>
 */
public record Move(int from, int to) {

    /** Sentinel meaning "no move yet" (for example before the first ply or in a fresh snapshot). */
    public static final short NONE = -1;

    public boolean isValid() {
        return Squares.isInsideIndex(from) && Squares.isInsideIndex(to) && from != to;
    }

    /** Packs the pair into a short so it can travel over the network in two bytes. */
    public short encode() {
        return (short) ((from << 8) | (to & 0xFF));
    }

    /** Inverse of {@link #encode()}; {@code null} for {@link #NONE} or out-of-range values. */
    public static Move decode(short packed) {
        if (packed < 0) {
            return null;
        }
        Move move = new Move((packed >> 8) & 0xFF, packed & 0xFF);
        return move.isValid() ? move : null;
    }

    @Override
    public String toString() {
        return "Move[" + Coordinates.name(from) + "->" + Coordinates.name(to) + "]";
    }

    /** Human readable point names, used only for logging and crash reports. */
    private static final class Coordinates {
        private static String name(int index) {
            if (!Squares.isInsideIndex(index)) {
                return "??";
            }
            return (char) ('a' + Squares.file(index)) + Integer.toString(Squares.rank(index));
        }
    }
}
