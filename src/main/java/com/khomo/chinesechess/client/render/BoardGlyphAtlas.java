package com.khomo.chinesechess.client.render;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.board.PieceType;
import com.khomo.chinesechess.board.Side;

import net.minecraft.resources.ResourceLocation;

/**
 * Where each drawn symbol lives inside the mod's glyph sheet.
 *
 * <p>The sheet is one ordinary PNG - six columns of three rows - holding the fourteen playing pieces
 * and the four river characters (æ¥šæ²³æ±‰ç•Œ). Keeping the symbols in a plain texture rather than
 * rendering strings has three advantages: the glyphs are crisp at any GUI scale, the in-world board
 * and the window draw the very same pixels, and no font or unicode coverage is required at
 * runtime.</p>
 *
 * <p>Single responsibility: map a symbol to its place in the sheet.</p>
 */
public final class BoardGlyphAtlas {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ChineseChess.MODID, "textures/entity/board_glyphs.png");

    /** Layout of the sheet. */
    public static final int COLUMNS = 6;
    /**
     * Three rows of symbols plus one spare row.
     *
     * <p>The spare row holds {@link #SOLID_TILE}: a plain white tile for geometry whose colour comes
     * from its vertices. It lives in this sheet so that such geometry can be drawn through the same
     * <em>lit</em> layer as the symbols - a separate, unlit layer would keep the side of a piece
     * glowing at night while its face went dark.</p>
     */
    public static final int ROWS = 4;
    /**
     * Pixels per symbol.
     *
     * <p>128 rather than the obvious 32: the GUI draws a piece at up to roughly 120 device pixels, and
     * a symbol that is never magnified is the difference between a crisp character and a smeared one.
     * The in-world board renders the same tile at a few pixels, where the mip-free cutout filter keeps
     * it readable.</p>
     */
    public static final int TILE = 96;
    public static final int SHEET_WIDTH = COLUMNS * TILE;
    public static final int SHEET_HEIGHT = ROWS * TILE;

    /** First tile index of the four river characters. */
    private static final int RIVER_FIRST = 14;
    private static final int SOLDIER_RED = 12;
    private static final int SOLDIER_BLACK = 13;
    /** A plain white tile; geometry drawn with it takes its colour from the vertex colours. */
    private static final int SOLID_TILE = 18;

    private static final int[] PIECE_TILES = new int[PieceType.values().length * 2];

    static {
        for (PieceType type : PieceType.values()) {
            if (type == PieceType.SOLDIER) {
                PIECE_TILES[tileKey(type, Side.RED)] = SOLDIER_RED;
                PIECE_TILES[tileKey(type, Side.BLACK)] = SOLDIER_BLACK;
            } else {
                PIECE_TILES[tileKey(type, Side.RED)] = type.ordinal();
                PIECE_TILES[tileKey(type, Side.BLACK)] = type.ordinal() + COLUMNS;
            }
        }
    }

    private BoardGlyphAtlas() {}

    private static int tileKey(PieceType type, Side side) {
        return type.ordinal() * 2 + side.ordinal();
    }

    public static int pieceTile(PieceType type, Side side) {
        return PIECE_TILES[tileKey(type, side)];
    }

    /** {@code slot} 0..3 selects æ¥? æ²? æ±? ç•?in that order. */
    public static int riverTile(int slot) {
        return RIVER_FIRST + Math.floorMod(slot, 4);
    }

    /**
     * The plain white tile, for geometry that carries its own colour.
     *
     * <p>Sampled at a single point by such geometry, so the whole face of a piece's side shows one
     * colour - multiplied by the vertex colour, which is what shades it.</p>
     */
    public static int solidTile() {
        return SOLID_TILE;
    }

    /** Texture-space corner of a tile, in texture pixels. */
    public static int tileU(int tile) {
        return Math.floorMod(tile, COLUMNS) * TILE;
    }

    public static int tileV(int tile) {
        return Math.floorDiv(tile, COLUMNS) * TILE;
    }

    /** Normalised corners of a tile, for the in-world vertex format. */
    public static float uMin(int tile) {
        return tileU(tile) / (float) SHEET_WIDTH;
    }

    public static float vMin(int tile) {
        return tileV(tile) / (float) SHEET_HEIGHT;
    }

    public static float uMax(int tile) {
        return (tileU(tile) + TILE) / (float) SHEET_WIDTH;
    }

    public static float vMax(int tile) {
        return (tileV(tile) + TILE) / (float) SHEET_HEIGHT;
    }
}
