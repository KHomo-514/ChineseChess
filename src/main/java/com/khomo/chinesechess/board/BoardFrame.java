package com.khomo.chinesechess.board;

import com.khomo.chinesechess.block.BoardStructure;

import net.minecraft.core.Direction;

/**
 * The bridge between board coordinates and the world.
 *
 * <p>Everything about a board is described in <em>board space</em>: {@code a} runs away from the
 * player who placed the board, {@code b} runs to that player's right, both measured in blocks. This
 * record turns such a point into world offsets, so neither the renderer nor the code that places
 * pieces on the board has to deal with compass directions.</p>
 *
 * <p>It lives in the shared half of the mod rather than next to the renderer because both sides need
 * it: the client draws the board from it, and the server places the piece entities with it. One
 * definition therefore keeps the picture and the pieces exactly on the same points.</p>
 *
 * <p>{@code nearX} / {@code nearZ} place the board's near corner on the correct corner of the origin
 * block: a plate is one block wide, and the board may grow towards the negative or the positive side
 * of each axis depending on which way it was placed. {@link #dx} / {@link #dz} skip that part, for
 * callers that already start from a point on the board.</p>
 *
 * <p>Single responsibility: project board space into the world.</p>
 */
public record BoardFrame(float nearX, float nearZ, int forwardX, int forwardZ, int rightX, int rightZ) {

    /** The six horizontal facings, resolved once: this is asked for once per piece, per frame. */
    private static final BoardFrame[] BY_FACING = new BoardFrame[6];

    public static BoardFrame of(Direction facing) {
        Direction forward = BoardStructure.horizontal(facing);
        int slot = forward.get3DDataValue();
        BoardFrame cached = BY_FACING[slot];
        if (cached == null) {
            cached = build(forward);
            BY_FACING[slot] = cached;
        }
        return cached;
    }

    /**
     * The value for one direction.
     *
     * <p>A record is immutable, so the six possible frames are shared rather than rebuilt: the piece
     * renderer asks for one for every piece it draws, every frame, and a board's worth of pieces is
     * thirty-two allocations a frame for a value with only six possible answers.</p>
     */
    private static BoardFrame build(Direction forward) {
        Direction right = BoardStructure.rightOf(forward);
        int fx = forward.getStepX();
        int fz = forward.getStepZ();
        int rx = right.getStepX();
        int rz = right.getStepZ();

        // For each horizontal axis exactly one of the two axes is non-zero; the near corner is the
        // block corner the board grows away from.
        float nearX = fx != 0 ? (fx > 0 ? 0.0F : 1.0F) : (rx > 0 ? 0.0F : 1.0F);
        float nearZ = fz != 0 ? (fz > 0 ? 0.0F : 1.0F) : (rz > 0 ? 0.0F : 1.0F);
        return new BoardFrame(nearX, nearZ, fx, fz, rx, rz);
    }

    /** The local x of a board-space point, measured from the origin block's corner. */
    public float x(double a, double b) {
        return (float) (nearX + a * forwardX + b * rightX);
    }

    /** The local z of a board-space point, measured from the origin block's corner. */
    public float z(double a, double b) {
        return (float) (nearZ + a * forwardZ + b * rightZ);
    }

    /**
     * The local x offset of a board-space point, without the near-corner placement.
     *
     * <p>Used for anything expressed <em>relative</em> to a point already on the board - the four
     * corners of a piece, for instance - where adding {@code nearX} again would shift it by a block.</p>
     */
    public float dx(double a, double b) {
        return (float) (a * forwardX + b * rightX);
    }

    /** The local z offset of a board-space point, without the near-corner placement. */
    public float dz(double a, double b) {
        return (float) (a * forwardZ + b * rightZ);
    }

    /** Board space of a layout coordinate: the footprint is two blocks across. */
    public static double a(double layoutV) {
        return layoutV * 2.0D;
    }

    public static double b(double layoutU) {
        return layoutU * 2.0D;
    }

    /** The world-space direction of the board's "away" axis, as a unit vector. */
    public double forwardUnitX() {
        return forwardX;
    }

    public double forwardUnitZ() {
        return forwardZ;
    }

    public double rightUnitX() {
        return rightX;
    }

    public double rightUnitZ() {
        return rightZ;
    }
}
