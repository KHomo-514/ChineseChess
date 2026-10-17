package com.khomo.chinesechess.client.screen;

import com.khomo.chinesechess.board.Squares;

/**
 * Where the board screen puts everything.
 *
 * <p>The layout is expressed in GUI pixels and is deliberately arithmetic only, so the same numbers
 * can be replayed outside the game (see {@code tools/preview_gui.py}) to check a design before it is
 * compiled.</p>
 *
 * <p>The board keeps <em>square</em> cells - a real Xiangqi grid is square - so the cell size is the
 * smaller of what the width and the height allow, and the grid is then centred. Because the grid is
 * nine cells tall and eight wide but a window is wider than it is tall, the height always ends up
 * being the binding constraint; that is why the board is centred horizontally and the surplus width
 * is used by the information bar rather than by enlarging the cells.</p>
 *
 * <p>Single responsibility: turn a window size into board geometry.</p>
 */
public final class BoardGuiLayout {

    /** Height of the information strip along the top: two rows of 2x text plus padding. */
    public static final int TOP_BAR = 44;
    /** Vertical offset of the second information row. */
    public static final int SECOND_ROW = 24;
    /** Vertical offset of the first information row. */
    public static final int FIRST_ROW = 4;
    /** Gap kept between the board panel and the window edges. */
    private static final int MARGIN = 8;
    /** Margin baked into the panel around the grid, in cells. */
    private static final double CELL_MARGIN = 0.55D;
    private static final int MIN_CELL = 8;

    private final int width;
    private final int height;
    private final int cell;
    private final int pad;
    private final int panelX;
    private final int panelY;
    private final int panelWidth;
    private final int panelHeight;
    private final int gridX;
    private final int gridY;
    private final boolean flipped;

    private BoardGuiLayout(int width, int height, int cell, int pad, int panelX, int panelY,
            int panelWidth, int panelHeight, int gridX, int gridY, boolean flipped) {
        this.width = width;
        this.height = height;
        this.cell = cell;
        this.pad = pad;
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.gridX = gridX;
        this.gridY = gridY;
        this.flipped = flipped;
    }

    public static BoardGuiLayout create(int screenWidth, int screenHeight, boolean flipped) {
        int availableWidth = Math.max(MIN_CELL, screenWidth - 2 * MARGIN);
        int availableHeight = Math.max(MIN_CELL, screenHeight - TOP_BAR - 2 * MARGIN);
        double spanX = (Squares.FILES - 1) + 2 * CELL_MARGIN;
        double spanY = (Squares.RANKS - 1) + 2 * CELL_MARGIN;
        int cell = Math.max(MIN_CELL,
                (int) Math.min(availableWidth / spanX, availableHeight / spanY));

        int pad = (int) Math.round(CELL_MARGIN * cell);
        int panelWidth = (Squares.FILES - 1) * cell + 2 * pad;
        int panelHeight = (Squares.RANKS - 1) * cell + 2 * pad;
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = TOP_BAR + MARGIN + (availableHeight - panelHeight) / 2;
        return new BoardGuiLayout(screenWidth, screenHeight, cell, pad, panelX, panelY, panelWidth, panelHeight,
                panelX + pad, panelY + pad, flipped);
    }

    /**
     * Whether this layout still matches the drawing area.
     *
     * <p>The screen rebuilds itself the moment this stops holding. A layout computed for a smaller
     * window draws a board that is off-centre and clipped, which is far worse than recomputing a
     * handful of integers.</p>
     */
    public boolean matches(int screenWidth, int screenHeight) {
        return width == screenWidth && height == screenHeight;
    }

    public int height() {
        return height;
    }

    // ------------------------------------------------------------------ geometry

    public int cell() {
        return cell;
    }

    public boolean flipped() {
        return flipped;
    }

    public int width() {
        return width;
    }

    public int panelX() {
        return panelX;
    }

    public int panelY() {
        return panelY;
    }

    public int panelWidth() {
        return panelWidth;
    }

    public int panelHeight() {
        return panelHeight;
    }

    public int panelRight() {
        return panelX + panelWidth;
    }

    public int panelBottom() {
        return panelY + panelHeight;
    }

    /** Screen x of a grid line. */
    public int x(int file) {
        int effective = flipped ? Squares.FILES - 1 - file : file;
        return gridX + effective * cell;
    }

    /** Screen y of a grid line; rank 0 is nearest to RED. */
    public int y(int rank) {
        int effective = flipped ? rank : Squares.RANKS - 1 - rank;
        return gridY + effective * cell;
    }

    public int xOf(int square) {
        return x(Squares.file(square));
    }

    public int yOf(int square) {
        return y(Squares.rank(square));
    }

    /** The top edge of the grid, whichever way round the board is drawn. */
    public int gridTop() {
        return gridY;
    }

    public int gridBottom() {
        return gridY + (Squares.RANKS - 1) * cell;
    }

    public int gridLeft() {
        return gridX;
    }

    public int gridRight() {
        return gridX + (Squares.FILES - 1) * cell;
    }

    /**
     * @return the point nearest to the cursor, or {@code -1} when the cursor is not close enough to
     *         any of them, which is how a click outside the pieces clears a selection
     */
    public int squareAt(double mouseX, double mouseY) {
        double relativeX = (mouseX - gridX) / cell;
        double relativeY = (mouseY - gridY) / cell;
        int file = (int) Math.round(relativeX);
        int rank = Squares.RANKS - 1 - (int) Math.round(relativeY);
        if (!Squares.isInside(file, rank)) {
            return -1;
        }
        if (flipped) {
            file = Squares.FILES - 1 - file;
            rank = Squares.RANKS - 1 - rank;
        }
        double dx = (mouseX - x(file)) / cell;
        double dy = (mouseY - y(rank)) / cell;
        if (dx * dx + dy * dy > 0.45D * 0.45D) {
            return -1;
        }
        return Squares.index(file, rank);
    }

    /** Unused pad accessor kept for the preview tool's benefit. */
    public int pad() {
        return pad;
    }
}
