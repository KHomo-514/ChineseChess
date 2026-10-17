package com.khomo.chinesechess.block;

import net.minecraft.util.StringRepresentable;

/**
 * The four blocks that make up one physical board.
 *
 * <p>The board always grows away from the player who placed it: {@link #ORIGIN} is the block that
 * was clicked, {@code rankStep} counts along the placer's line of sight and {@code fileStep} counts
 * to the placer's right. Every other geometry question in the mod - where a piece is drawn, which
 * block a click belongs to - is derived from this single definition.</p>
 *
 * <p>Single responsibility: name the quadrants of the 2x2 board footprint.</p>
 */
public enum BoardPart implements StringRepresentable {
    /** Near corner, the block the player clicked. Holds the match's block entity. */
    ORIGIN(0, 0, 0),
    /** Near edge, one block to the placer's right. */
    FILE(1, 0, 1),
    /** Far edge, one block along the placer's line of sight. */
    RANK(2, 1, 0),
    /** Far corner. */
    CORNER(3, 1, 1);

    /** Every part, in index order; avoids cloning {@code values()} on hot paths. */
    public static final BoardPart[] ALL = values();

    private final int index;
    private final int rankStep;
    private final int fileStep;

    BoardPart(int index, int rankStep, int fileStep) {
        this.index = index;
        this.rankStep = rankStep;
        this.fileStep = fileStep;
    }

    public int index() {
        return index;
    }

    public int rankStep() {
        return rankStep;
    }

    public int fileStep() {
        return fileStep;
    }

    /** The value used in the blockstate and in item model variants. */
    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static BoardPart byIndex(int index) {
        return index >= 0 && index < ALL.length ? ALL[index] : ORIGIN;
    }

    public static BoardPart byName(String name) {
        for (BoardPart part : ALL) {
            if (part.getSerializedName().equals(name)) {
                return part;
            }
        }
        return ORIGIN;
    }
}
