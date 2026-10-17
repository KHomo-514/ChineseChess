package com.khomo.chinesechess.client.render;

import com.khomo.chinesechess.Config;
import com.khomo.chinesechess.board.BoardFrame;
import com.khomo.chinesechess.board.BoardLayout;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.entity.XiangqiPieceEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix4f;

/**
 * Draws one playing piece entity.
 *
 * <p>A piece is a cylinder: a round side with the symbol printed on top, taken from the same sheet the
 * board and the interface use. Because the entity sits exactly on its point, the drawing here is done
 * in <em>board space relative to the piece</em> - every corner is some fraction of a piece away from
 * the origin - which is why it needs the board's facing rather than a compass direction, and why it
 * lines up with the printed grid no matter which way the board was placed.</p>
 *
 * <p>The layer order matters for the same reason it does in {@code BoardPartRenderer}: a buffer source
 * hands out builders over one shared buffer, and asking for the next render type closes the previous
 * builder. All the side quads are therefore written out before the symbol layer is requested.</p>
 *
 * <p>Single responsibility: paint one piece.</p>
 */
public class XiangqiPieceRenderer extends EntityRenderer<XiangqiPieceEntity> {

    /** The symbol layer, built once and shared rather than re-created every frame. */
    private static final RenderType GLYPH_LAYER = RenderType.entityCutoutNoCull(BoardGlyphAtlas.TEXTURE);

    /**
     * How many quads make up the round side.
     *
     * <p>A piece is sixteen pixels across, so a segment is about one pixel wide here: past this the
     * silhouette stops improving and only costs vertices.</p>
     */
    private static final int SIDE_SEGMENTS = 16;

    /** The side of the disc, shaded top to bottom so the cylinder reads as a solid object. */
    private static final int SIDE_TOP = 0xFFE4D1AA;
    private static final int SIDE_BOTTOM = 0xFFC6B28C;
    /** A hair above the disc, so the symbol never z-fights with the face it is printed on. */
    private static final float SYMBOL_Y = 0.002F;

    public XiangqiPieceRenderer(EntityRendererProvider.Context context) {
        super(context);
        // A two-pixel piece casting a shadow would only be noise.
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(XiangqiPieceEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        if (!Config.renderBoardInWorld()) {
            return;
        }
        Piece piece = entity.piece();
        if (piece == null) {
            return;
        }

        BoardFrame frame = BoardFrame.of(entity.facing());
        double half = BoardLayout.pieceHalfSize();
        float height = (float) BoardLayout.PIECE_HEIGHT;
        Matrix4f pose = poseStack.last().pose();

        // One layer for the whole piece. The side used to be drawn through an unlit debug layer, which
        // left it at full brightness while the face beside it went dark - a glowing rim at night. It is
        // the same lit layer as the symbol now, painted with the sheet's plain white tile and coloured
        // per vertex, so the world's light reaches it exactly as it reaches the face.
        VertexConsumer consumer = buffers.getBuffer(GLYPH_LAYER);
        int solid = BoardGlyphAtlas.solidTile();
        float solidU = (BoardGlyphAtlas.uMin(solid) + BoardGlyphAtlas.uMax(solid)) / 2.0F;
        float solidV = (BoardGlyphAtlas.vMin(solid) + BoardGlyphAtlas.vMax(solid)) / 2.0F;
        cylinder(consumer, pose, frame, half * BoardLayout.PIECE_DISC_RATIO, height,
                solidU, solidV, packedLight);

        int tile = BoardGlyphAtlas.pieceTile(piece.type(), piece.side());
        double a0 = -half;
        double b0 = -half;
        double a1 = half;
        double b1 = half;
        // Same corner-to-UV assignment as the board mesh: +b is the texture's x, +a is its top edge,
        // so the symbol reads the right way up for a player standing at the near side.
        textured(consumer, pose, frame, a0, b0, BoardGlyphAtlas.uMin(tile), BoardGlyphAtlas.vMax(tile), packedLight);
        textured(consumer, pose, frame, a0, b1, BoardGlyphAtlas.uMax(tile), BoardGlyphAtlas.vMax(tile), packedLight);
        textured(consumer, pose, frame, a1, b1, BoardGlyphAtlas.uMax(tile), BoardGlyphAtlas.vMin(tile), packedLight);
        textured(consumer, pose, frame, a1, b0, BoardGlyphAtlas.uMin(tile), BoardGlyphAtlas.vMin(tile), packedLight);
    }

    /**
     * The round side of the piece: a ring of upright quads from the board up to the printed face.
     *
     * <p>Going round in board space rather than in screen space is what keeps the circle round on a
     * board placed at any angle - the frame turns each point into the world, so the silhouette is a
     * circle in the world and not an ellipse in the texture's plane.</p>
     *
     * <p>Every corner samples the same texel of the white tile, so the side is one flat colour shaded
     * by its vertices, and the light of the spot the piece stands on darkens it like anything else.</p>
     */
    private static void cylinder(VertexConsumer consumer, Matrix4f pose, BoardFrame frame,
            double radius, float height, float u, float v, int light) {
        // The wall stops at the plane the symbol is printed on, not at zero: leaving the last two
        // millimetres empty put a dark sliver all the way round the join between face and side.
        float top = SYMBOL_Y;
        for (int segment = 0; segment < SIDE_SEGMENTS; segment++) {
            double angle0 = Math.PI * 2.0D * segment / SIDE_SEGMENTS;
            double angle1 = Math.PI * 2.0D * (segment + 1) / SIDE_SEGMENTS;
            float x0 = frame.dx(Math.cos(angle0) * radius, Math.sin(angle0) * radius);
            float z0 = frame.dz(Math.cos(angle0) * radius, Math.sin(angle0) * radius);
            float x1 = frame.dx(Math.cos(angle1) * radius, Math.sin(angle1) * radius);
            float z1 = frame.dz(Math.cos(angle1) * radius, Math.sin(angle1) * radius);
            shaded(consumer, pose, x0, -height, z0, u, v, light, SIDE_BOTTOM);
            shaded(consumer, pose, x1, -height, z1, u, v, light, SIDE_BOTTOM);
            shaded(consumer, pose, x1, top, z1, u, v, light, SIDE_TOP);
            shaded(consumer, pose, x0, top, z0, u, v, light, SIDE_TOP);
        }
    }

    private static void shaded(VertexConsumer consumer, Matrix4f pose, float x, float y, float z,
            float u, float v, int light, int colour) {
        consumer.addVertex(pose, x, y, z)
                .setColor(colour)
                .setUv(u, v)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static void textured(VertexConsumer consumer, Matrix4f pose, BoardFrame frame,
            double a, double b, float u, float v, int light) {
        consumer.addVertex(pose, frame.dx(a, b), SYMBOL_Y, frame.dz(a, b))
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setLight(light)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(XiangqiPieceEntity entity) {
        return BoardGlyphAtlas.TEXTURE;
    }
}
