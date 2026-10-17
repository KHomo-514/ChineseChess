package com.khomo.chinesechess.client.render;

import com.khomo.chinesechess.ChineseChess;

import net.minecraft.resources.ResourceLocation;

/**
 * The standalone sprites the board screen draws.
 *
 * <p>They live here rather than in {@link BoardGlyphAtlas} because they are not symbols: nothing maps
 * a piece to them, they are simply pictures of a shape the interface needs and the font cannot draw.</p>
 *
 * <p>Single responsibility: name the interface sprites and their sizes.</p>
 */
public final class BoardSprites {

    /**
     * The ring drawn around a general that is in check.
     *
     * <p>A sprite rather than a run of filled rectangles: a circle built from axis-aligned fills is
     * visibly scalloped, its line width is quantised to whole pixels, and it costs dozens of draws.
     * This is anti-aliased at generation time, scales smoothly to any cell size, and costs one.</p>
     */
    public static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath(ChineseChess.MODID, "textures/gui/halo.png");

    /** Pixels per side of the halo sprite. */
    public static final int HALO_PIXELS = 128;

    /**
     * The halo's width as a fraction of a board cell.
     *
     * <p>The sprite's ring sits at 51..58 of its 64 pixel half width, and 1.2 cells puts that just
     * outside the printed disc of a piece (whose painted edge reaches 0.472 of a cell) - close enough
     * to hug it, far enough not to clip the ring.</p>
     */
    public static final float HALO_SPAN = 1.2F;

    private BoardSprites() {}
}
