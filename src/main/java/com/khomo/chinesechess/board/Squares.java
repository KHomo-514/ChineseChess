package com.khomo.chinesechess.board;

/**
 * Board geometry: the pure index arithmetic shared by every rules class.
 *
 * <p>The board is stored as a flat array of {@value #COUNT} entries. Index {@code rank * 9 + file}
 * is used everywhere, with file 0 on the left and rank 0 nearest to RED.</p>
 *
 * <p>Single responsibility: translate between (file, rank) and a flat index and answer the two
 * static geography questions of the game (palace membership, river crossing).</p>
 */
public final class Squares {

    /** Number of files (columns) on a Xiangqi board. */
    public static final int FILES = 9;
    /** Number of ranks (rows) on a Xiangqi board. */
    public static final int RANKS = 10;
    /** Total number of playable points. */
    public static final int COUNT = FILES * RANKS;
    /** Highest rank that belongs to RED's half; the river sits between this rank and the next. */
    public static final int RED_RIVER_RANK = 4;

    private Squares() {}

    public static int index(int file, int rank) {
        return rank * FILES + file;
    }

    public static int file(int index) {
        return index % FILES;
    }

    public static int rank(int index) {
        return index / FILES;
    }

    public static boolean isInside(int file, int rank) {
        return file >= 0 && file < FILES && rank >= 0 && rank < RANKS;
    }

    public static boolean isInsideIndex(int index) {
        return index >= 0 && index < COUNT;
    }

    /** The three central files that make up both palaces. */
    public static boolean isPalaceFile(int file) {
        return file >= 3 && file <= 5;
    }

    /** Whether the point lies inside {@code side}'s 3x3 palace. */
    public static boolean isInPalace(int file, int rank, Side side) {
        if (!isPalaceFile(file)) {
            return false;
        }
        return side.isRed() ? rank >= 0 && rank <= 2 : rank >= 7 && rank <= 9;
    }

    public static boolean isInPalace(int index, Side side) {
        return isInPalace(file(index), rank(index), side);
    }

    /** Whether a piece standing on {@code rank} has already crossed the river for {@code side}. */
    public static boolean hasCrossedRiver(int rank, Side side) {
        return side.isRed() ? rank > RED_RIVER_RANK : rank < RANKS - 1 - RED_RIVER_RANK;
    }
}
