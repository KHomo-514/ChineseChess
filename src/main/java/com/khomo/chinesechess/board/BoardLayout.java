package com.khomo.chinesechess.board;

/**
 * The normalised drawing geometry of the board, shared by every renderer.
 *
 * <p>Coordinates are expressed in the unit square of the board footprint: {@code u} grows towards
 * the placing player's right, {@code v} grows away from the placing player. Both the in-world block
 * entity renderer and the GUI project these values through their own axis (blocks and pixels
 * respectively), which guarantees that the picture and the clickable points always agree.</p>
 *
 * <p>The grid uses a single cell size for both axes, so the points form true squares exactly like a
 * real Xiangqi board.</p>
 *
 * <p>Single responsibility: own the board's drawing proportions.</p>
 */
public final class BoardLayout {

    /** Distance between two neighbouring points, in board-footprint units. */
    public static final double CELL = 0.1D;
    /** Position of file 0. */
    public static final double U0 = 0.1D;
    /** Position of rank 0. */
    public static final double V0 = 0.05D;
    /** Thickness of a printed line, in board-footprint units. */
    public static final double LINE_WIDTH = 0.006D;
    /** Radius of a rendered playing piece, in board-footprint units. */
    public static final double PIECE_RADIUS = 0.042D;
    /**
     * Where the printed disc's edge actually is, as a fraction of {@link #pieceHalfSize()}.
     *
     * <p>The cylinder that forms a piece's side uses this radius, so its wall is flush with the disc
     * printed on top instead of showing a step. Measured from the sheet at the alpha threshold the
     * cutout layer draws with: the disc is 121 of 128 pixels across, which is 0.945 of the half-tile.
     * (An earlier 0.97 was a guess and left the wall standing about two millimetres proud of the disc,
     * which read as a seam all the way round the piece.)</p>
     */
    public static final double PIECE_DISC_RATIO = 0.945D;
    /** Height of the plate's top surface above its block's bottom, in blocks (the plate is 2 px thick). */
    public static final double SURFACE_Y = 2.0D / 16.0D;
    /**
     * Height of a piece above that surface, in blocks: the thickness of the disc.
     *
     * <p>A real counter is roughly a quarter of its diameter thick, and this matches that proportion -
     * which is what stops the pieces from looking like painted circles on the wood.</p>
     */
    public static final double PIECE_HEIGHT = 0.04D;

    private BoardLayout() {}

    public static double u(int file) {
        return U0 + file * CELL;
    }

    public static double v(int rank) {
        return V0 + rank * CELL;
    }

    public static double uOf(int index) {
        return u(Squares.file(index));
    }

    public static double vOf(int index) {
        return v(Squares.rank(index));
    }

    // ---------------------------------------------------------------- block space
    //
    // The renderer works in the board's own block space: the footprint is two blocks across, so a
    // normalised coordinate of 1.0 is two blocks. Keeping the doubling here means neither the
    // renderer nor the frame has to know the board is 2x2 blocks.

    /** Board-space coordinate (in blocks) of a file, measured towards the placer's right. */
    public static double blockB(int file) {
        return u(file) * 2.0D;
    }

    /** Board-space coordinate (in blocks) of a rank, measured away from the placer. */
    public static double blockA(int rank) {
        return v(rank) * 2.0D;
    }

    public static double blockBOf(int index) {
        return blockB(Squares.file(index));
    }

    public static double blockAOf(int index) {
        return blockA(Squares.rank(index));
    }

    /** Half the width of a playing piece, in blocks. */
    public static double pieceHalfSize() {
        return PIECE_RADIUS * 2.0D;
    }

    /** Half the thickness of a printed line, in blocks. */
    public static double lineHalfWidth() {
        return LINE_WIDTH * 2.0D;
    }

    /** Half the size of a river character, in blocks. */
    public static double riverGlyphHalfSize() {
        return 0.055D * 2.0D;
    }

    /** Half the height of the printed board (used to size hit boxes and backgrounds). */
    public static double halfHeight() {
        return (Squares.RANKS * CELL) / 2.0D;
    }

    /** Half the width of the printed board. */
    public static double halfWidth() {
        return (Squares.FILES * CELL) / 2.0D;
    }
}
