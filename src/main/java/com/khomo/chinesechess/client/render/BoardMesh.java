package com.khomo.chinesechess.client.render;

import com.khomo.chinesechess.board.BoardFrame;

import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

/**
 * The small set of primitives the board is drawn from.
 *
 * <p>Two kinds of geometry are needed and they use different vertex formats, so the file keeps them
 * strictly apart: flat coloured quads go into a position/colour buffer (the grid, the highlights)
 * and textured quads go into a cutout entity buffer (the pieces, the river characters). Mixing the
 * two would corrupt the vertex data, which is why the textured helpers are the only ones that write
 * a UV, a lightmap and an overlay.</p>
 *
 * <p>Upward-facing winding is guaranteed by construction: in board space the corners are always
 * emitted near-left, near-right, far-right, far-left, and the board's own axes are
 * orientation-preserving, so the normal always points at the sky.</p>
 *
 * <p>Single responsibility: emit board geometry.</p>
 */
public final class BoardMesh {

    private BoardMesh() {}

    /**
     * A textured, upward-facing quad filling the board-space rectangle
     * ({@code a0..a1}) x ({@code b0..b1}).
     *
     * <p>{@code vMin} is the top edge of the symbol in the sheet, so text reads the right way up for
     * a player standing at the near side.</p>
     */
    public static void glyph(VertexConsumer consumer, Matrix4f pose, BoardFrame frame,
            double a0, double b0, double a1, double b1, float y,
            float uMin, float vMin, float uMax, float vMax,
            int light, int overlay) {
        float x0 = frame.x(a0, b0);
        float z0 = frame.z(a0, b0);
        float x1 = frame.x(a0, b1);
        float z1 = frame.z(a0, b1);
        float x2 = frame.x(a1, b1);
        float z2 = frame.z(a1, b1);
        float x3 = frame.x(a1, b0);
        float z3 = frame.z(a1, b0);

        textured(consumer, pose, x0, y, z0, uMin, vMax, light, overlay);
        textured(consumer, pose, x1, y, z1, uMax, vMax, light, overlay);
        textured(consumer, pose, x2, y, z2, uMax, vMin, light, overlay);
        textured(consumer, pose, x3, y, z3, uMin, vMin, light, overlay);
    }

    /** A flat coloured rectangle in board space, used for highlights and marks. */
    public static void rect(VertexConsumer consumer, Matrix4f pose, BoardFrame frame,
            double a0, double b0, double a1, double b1, float y, int argb) {
        quad(consumer, pose,
                frame.x(a0, b0), frame.z(a0, b0),
                frame.x(a0, b1), frame.z(a0, b1),
                frame.x(a1, b1), frame.z(a1, b1),
                frame.x(a1, b0), frame.z(a1, b0),
                y, argb);
    }

    /**
     * A printed line of the given half-width between two board-space points.
     *
     * <p>The perpendicular is taken in board space, so the thickness stays uniform no matter which
     * way the board was placed.</p>
     */
    public static void line(VertexConsumer consumer, Matrix4f pose, BoardFrame frame,
            double a0, double b0, double a1, double b1, float y, double halfWidth, int argb) {
        double da = a1 - a0;
        double db = b1 - b0;
        double length = Math.sqrt(da * da + db * db);
        if (length < 1.0E-6D) {
            return;
        }
        double na = -db / length * halfWidth;
        double nb = da / length * halfWidth;

        quad(consumer, pose,
                frame.x(a0 + na, b0 + nb), frame.z(a0 + na, b0 + nb),
                frame.x(a0 - na, b0 - nb), frame.z(a0 - na, b0 - nb),
                frame.x(a1 - na, b1 - nb), frame.z(a1 - na, b1 - nb),
                frame.x(a1 + na, b1 + nb), frame.z(a1 + na, b1 + nb),
                y, argb);
    }

    /**
     * Emits four world-space corners as an upward-facing quad, flipping the winding when the given
     * order would face downwards.
     */
    public static void quad(VertexConsumer consumer, Matrix4f pose,
            float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3,
            float y, int argb) {
        float normalY = (z1 - z0) * (x2 - x0) - (x1 - x0) * (z2 - z0);
        if (normalY >= 0.0F) {
            coloured(consumer, pose, x0, y, z0, argb);
            coloured(consumer, pose, x1, y, z1, argb);
            coloured(consumer, pose, x2, y, z2, argb);
            coloured(consumer, pose, x3, y, z3, argb);
        } else {
            coloured(consumer, pose, x3, y, z3, argb);
            coloured(consumer, pose, x2, y, z2, argb);
            coloured(consumer, pose, x1, y, z1, argb);
            coloured(consumer, pose, x0, y, z0, argb);
        }
    }

    private static void coloured(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, int argb) {
        consumer.addVertex(pose, x, y, z).setColor(argb);
    }

    private static void textured(VertexConsumer consumer, Matrix4f pose, float x, float y, float z,
            float u, float v, int light, int overlay) {
        consumer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setLight(light)
                .setOverlay(overlay)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
