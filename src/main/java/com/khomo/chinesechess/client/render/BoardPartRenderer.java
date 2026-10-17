package com.khomo.chinesechess.client.render;

import com.khomo.chinesechess.Config;
import com.khomo.chinesechess.board.BoardFrame;
import com.khomo.chinesechess.board.BoardLayout;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.PieceType;
import com.khomo.chinesechess.board.Squares;
import com.khomo.chinesechess.block.BoardPart;
import com.khomo.chinesechess.block.BoardStructure;
import com.khomo.chinesechess.blockentity.BoardPartBlockEntity;
import com.khomo.chinesechess.blockentity.BoardRenderState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * Draws a whole board from its origin plate.
 *
 * <p>The choice to draw everything from one plate - rather than giving each of the four plates a
 * quarter of the picture - is what makes the board robust: a 2x2 footprint can straddle four chunk
 * sections, and a plate whose section happens to be culled would otherwise make a quarter of the
 * board disappear. Instead the renderer advertises {@link #shouldRenderOffScreen} so the origin is
 * rendered from the global pass, and it asks for a view distance so distant boards cost nothing.</p>
 *
 * <p>The grid, the palace diagonals and the river characters are geometry rather than baked into the
 * block texture. That keeps the printed lines exactly aligned with the points pieces stand on, and it
 * lets the same proportions drive the GUI.</p>
 *
 * <p>Single responsibility: paint one board in the world.</p>
 */
public class BoardPartRenderer implements BlockEntityRenderer<BoardPartBlockEntity> {

    /** The plate is two pixels thick. */
    private static final float SURFACE_Y = (float) BoardLayout.SURFACE_Y;
    /** Marks sit closest to the surface, then the printed lines. Pieces are entities and sit above. */
    private static final float MARK_Y = SURFACE_Y + 0.0016F;
    private static final float LINE_Y = SURFACE_Y + 0.0028F;

    private static final int LINE_COLOUR = 0xF03A2814;
    private static final int SELECTED_COLOUR = 0x88FFD54A;
    private static final int TARGET_COLOUR = 0x8817C24A;
    private static final int LAST_MOVE_COLOUR = 0x604C8DFF;
    private static final int CHECK_COLOUR = 0x88FF3B30;

    /** How far a board is drawn; boards are small, so this can be modest. */
    private static final int VIEW_DISTANCE = 64;

    /**
     * The glyph layer.
     *
     * <p>Built once and shared: {@code entityCutoutNoCull} produces a fresh render type on every
     * call, and the buffer source keys its open builders by render type, so re-creating it per frame
     * would churn that map for no reason.</p>
     */
    /**
     * The layer the printed symbols are drawn on.
     *
     * <p>The Z-offset variant matters: the symbols lie flat on the board, a couple of thousandths of a
     * block above its face, and from a player's eye - low and far, so the board is seen at a grazing
     * angle - a decal that thin loses the depth test against the surface it is printed on and simply
     * disappears. Polygon offset pushes it in front of the board at any angle.</p>
     */
    private static final RenderType GLYPH_LAYER =
            RenderType.entityCutoutNoCullZOffset(BoardGlyphAtlas.TEXTURE);

    /** File positions (in board space, halves allowed) of 楚 河 汉 界 inside the river gap. */
    private static final double[] RIVER_B = {0.5D, 0.7D, 1.3D, 1.5D};

    public BoardPartRenderer(BlockEntityRendererProvider.Context context) {
        // No baked models are needed; the whole board is generated geometry.
    }

    @Override
    public void render(BoardPartBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!Config.renderBoardInWorld() || !blockEntity.isOrigin()) {
            return;
        }
        BoardRenderState state = blockEntity.renderState();
        byte[] codes = state.codes();
        if (codes == null || codes.length != Squares.COUNT) {
            return;
        }

        BoardFrame frame = BoardFrame.of(blockEntity.facing());
        Matrix4f matrix = poseStack.last().pose();

        // A buffer source hands out builders over a single shared buffer, and asking it for the next
        // render type finishes the previous one. Each layer must therefore be written out completely
        // before the next consumer is requested; holding two consumers at once would leave us writing
        // into a builder that has already been closed.
        VertexConsumer marks = buffers.getBuffer(RenderType.debugQuads());
        drawGrid(marks, matrix, frame);
        drawHighlights(marks, matrix, frame, state, codes);

        VertexConsumer glyphs = buffers.getBuffer(GLYPH_LAYER);
        drawRiver(glyphs, matrix, frame, packedLight, packedOverlay);
        // The grid was drawn only because requesting this layer closed the previous one. Nothing is
        // written after the river, so this layer would be left open and would depend on the caller's
        // final flush to ever be drawn - and the global block entity pass flushes a fixed list of
        // layers that a mod's own texture is not on. Closing it here draws it while it is still the
        // most recently opened builder, which is the order the shared buffer requires.
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(GLYPH_LAYER);
        }
    }

    // ------------------------------------------------------------------ board furniture

    private void drawGrid(VertexConsumer consumer, Matrix4f matrix, BoardFrame frame) {
        double half = BoardLayout.lineHalfWidth();
        double left = BoardLayout.blockB(0);
        double right = BoardLayout.blockB(Squares.FILES - 1);
        double bottom = BoardLayout.blockA(0);
        double top = BoardLayout.blockA(Squares.RANKS - 1);

        for (int rank = 0; rank < Squares.RANKS; rank++) {
            double a = BoardLayout.blockA(rank);
            BoardMesh.line(consumer, matrix, frame, a, left, a, right, LINE_Y, half, LINE_COLOUR);
        }
        for (int file = 0; file < Squares.FILES; file++) {
            double b = BoardLayout.blockB(file);
            if (file == 0 || file == Squares.FILES - 1) {
                BoardMesh.line(consumer, matrix, frame, bottom, b, top, b, LINE_Y, half, LINE_COLOUR);
            } else {
                // The river interrupts the inner files.
                BoardMesh.line(consumer, matrix, frame, bottom, b, BoardLayout.blockA(4), b, LINE_Y, half, LINE_COLOUR);
                BoardMesh.line(consumer, matrix, frame, BoardLayout.blockA(5), b, top, b, LINE_Y, half, LINE_COLOUR);
            }
        }

        drawDiagonal(consumer, matrix, frame, 3, 0, 5, 2, half);
        drawDiagonal(consumer, matrix, frame, 5, 0, 3, 2, half);
        drawDiagonal(consumer, matrix, frame, 3, 7, 5, 9, half);
        drawDiagonal(consumer, matrix, frame, 5, 7, 3, 9, half);
    }

    private void drawDiagonal(VertexConsumer consumer, Matrix4f matrix, BoardFrame frame,
            int fileA, int rankA, int fileB, int rankB, double half) {
        BoardMesh.line(consumer, matrix, frame,
                BoardLayout.blockA(rankA), BoardLayout.blockB(fileA),
                BoardLayout.blockA(rankB), BoardLayout.blockB(fileB),
                LINE_Y, half, LINE_COLOUR);
    }

    private void drawRiver(VertexConsumer consumer, Matrix4f matrix, BoardFrame frame, int light, int overlay) {
        double a = (BoardLayout.blockA(4) + BoardLayout.blockA(5)) / 2.0D;
        double half = BoardLayout.riverGlyphHalfSize();
        for (int slot = 0; slot < RIVER_B.length; slot++) {
            double b = RIVER_B[slot];
            int tile = BoardGlyphAtlas.riverTile(slot);
            BoardMesh.glyph(consumer, matrix, frame,
                    a - half, b - half, a + half, b + half, LINE_Y,
                    BoardGlyphAtlas.uMin(tile), BoardGlyphAtlas.vMin(tile),
                    BoardGlyphAtlas.uMax(tile), BoardGlyphAtlas.vMax(tile),
                    light, overlay);
        }
    }

    // ------------------------------------------------------------------ state marks

    private void drawHighlights(VertexConsumer consumer, Matrix4f matrix, BoardFrame frame,
            BoardRenderState state, byte[] codes) {
        if (Config.showLastMove() && state.lastFrom() >= 0 && state.lastTo() >= 0) {
            mark(consumer, matrix, frame, state.lastFrom(), 0.088D, LAST_MOVE_COLOUR);
            mark(consumer, matrix, frame, state.lastTo(), 0.088D, LAST_MOVE_COLOUR);
        }
        if (state.checkedSide() != null) {
            for (int square = 0; square < Squares.COUNT; square++) {
                Piece piece = Piece.decode(codes[square]);
                if (piece != null
                        && piece.type() == PieceType.GENERAL
                        && piece.side() == state.checkedSide()) {
                    mark(consumer, matrix, frame, square, 0.096D, CHECK_COLOUR);
                }
            }
        }
    }

    private void mark(VertexConsumer consumer, Matrix4f matrix, BoardFrame frame, int square,
            double half, int colour) {
        double a = BoardLayout.blockAOf(square);
        double b = BoardLayout.blockBOf(square);
        BoardMesh.rect(consumer, matrix, frame, a - half, b - half, a + half, b + half, MARK_Y, colour);
    }

    // ------------------------------------------------------------------ culling

    /**
     * A board is always rendered from the global pass so that its four plates cannot be culled
     * independently; the distance test then lives in {@link #shouldRender}.
     */
    @Override
    public boolean shouldRenderOffScreen(BoardPartBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public boolean shouldRender(BoardPartBlockEntity blockEntity, Vec3 cameraPos) {
        return blockEntity.isOrigin()
                && Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPos, VIEW_DISTANCE);
    }

    /**
     * The box the frustum test uses for this renderer.
     *
     * <p>The whole picture is drawn from the origin plate, but it covers the full two-by-two footprint.
     * The default box is the single block the block entity sits in, so as soon as the origin plate
     * leaves the frustum - turn to face the board's far half and the origin is behind you - the global
     * block entity pass culls the board and the entire board vanishes even though most of it is still
     * in view. Covering all four plates keeps it drawn while any part of it can be seen.</p>
     */
    @Override
    public AABB getRenderBoundingBox(BoardPartBlockEntity blockEntity) {
        BlockPos origin = blockEntity.getBlockPos();
        Direction facing = blockEntity.facing();
        AABB box = new AABB(origin);
        for (BoardPart part : BoardPart.ALL) {
            box = box.minmax(new AABB(BoardStructure.positionOf(origin, facing, part)));
        }
        // Room for the printed grid and the pieces standing on the board.
        return box.inflate(0.5D, 1.0D, 0.5D);
    }

    /** Exposed so the mixin that restores distance culling knows which renderer asked for it. */
    public static int viewDistance() {
        return VIEW_DISTANCE;
    }
}
