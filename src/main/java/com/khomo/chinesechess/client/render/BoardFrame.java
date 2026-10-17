package com.khomo.chinesechess.client.render;

import com.khomo.chinesechess.block.BoardStructure;

import net.minecraft.core.Direction;

/**
 * The bridge between board coordinates and the world.
 *
 * <p>Everything the renderer draws is described in <em>board space</em>: {@code a} runs away from
 * the player who placed the board, {@code b} runs to that player's right, both measured in blocks
 * from the near corner of the footprint. This record turns such a point into the local coordinates
 * the block entity renderer's pose already expects, so the drawing code never deals with compass
 * directions, and the board automatically follows the blockstate's {@code facing}.</p>
 *
 * <p>{@code nearX} / {@code nearZ} place the board's near corner on the correct corner of the origin
 * block: a plate is one block wide, and the board may grow towards the negative or the positive side
 * of each axis depending on which way it was placed.</p>
 *
 * <p>Single responsibility: project board space into the world.</p>
 */
public record BoardFrame(float nearX, float nearZ, int forwardX, int forwardZ, int rightX, int rightZ) {

    public static BoardFrame of(Direction facing) {
        Direction forward = BoardStructure.horizontal(facing);
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

    public float x(double a, double b) {
        return (float) (nearX + a * forwardX + b * rightX);
    }

    public float z(double a, double b) {
        return (float) (nearZ + a * forwardZ + b * rightZ);
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
