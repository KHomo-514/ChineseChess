package com.khomo.chinesechess.entity;

import com.khomo.chinesechess.blockentity.BoardRenderState;
import com.khomo.chinesechess.board.BoardFrame;
import com.khomo.chinesechess.board.BoardLayout;
import com.khomo.chinesechess.board.Squares;
import com.khomo.chinesechess.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the piece entities standing on a board identical to the board's render state.
 *
 * <p>This is a reconciliation rather than a spawn/despawn protocol: whoever refreshes a board hands
 * over the authoritative state and this class makes the world match it - spawning what is missing,
 * retiring what is left over and re-lettering a piece whose square was captured. That means the
 * caller never has to know whether a piece moved, was taken, or was never there, and a board that
 * comes back from an unloaded chunk repairs itself the same way.</p>
 *
 * <p>Only the layout is decided here; the symbols and the placing itself are the entity's and the
 * board's business.</p>
 *
 * <p>Single responsibility: make the pieces on a board match the board's state.</p>
 */
public final class BoardPieces {

    /**
     * How far around the origin plate to look for pieces that are already there.
     *
     * <p>The footprint is two blocks across and may grow towards either side, so three blocks covers
     * every placement with room to spare; the query is a small one and the entity sections make it
     * cheap.</p>
     */
    private static final double SEARCH_RADIUS = 3.0D;

    private BoardPieces() {}

    /**
     * Makes the pieces on one board match {@code state}.
     *
     * @param origin the board's origin plate
     * @param facing the direction the board faces, as its blockstate carries it
     */
    public static void refresh(ServerLevel level, BlockPos origin, Direction facing, BoardRenderState state) {
        byte[] codes = state.codes();
        if (codes == null || codes.length != Squares.COUNT) {
            return;
        }
        BoardFrame frame = BoardFrame.of(facing);
        Map<Integer, XiangqiPieceEntity> existing = index(level, origin);

        for (int square = 0; square < Squares.COUNT; square++) {
            byte code = codes[square];
            XiangqiPieceEntity entity = existing.remove(square);
            if (code == 0) {
                if (entity != null) {
                    entity.discard();
                }
                continue;
            }
            if (entity == null) {
                spawn(level, origin, facing, frame, square, code);
            } else if (entity.pieceCode() != code) {
                // Same point, different piece: a capture replaced whatever stood there.
                entity.place(origin, square, code, facing);
            }
        }
        for (XiangqiPieceEntity leftover : existing.values()) {
            leftover.discard();
        }
    }

    /** Removes every piece of a board, for when the board itself goes away. */
    public static void removeAll(ServerLevel level, BlockPos origin) {
        for (XiangqiPieceEntity entity : pieces(level, origin)) {
            entity.discard();
        }
    }

    private static Map<Integer, XiangqiPieceEntity> index(ServerLevel level, BlockPos origin) {
        Map<Integer, XiangqiPieceEntity> bySquare = new HashMap<>();
        for (XiangqiPieceEntity entity : pieces(level, origin)) {
            if (bySquare.putIfAbsent(entity.square(), entity) != null) {
                // Two entities claiming one point can only be a leftover; keep the first.
                entity.discard();
            }
        }
        return bySquare;
    }

    private static List<XiangqiPieceEntity> pieces(ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(XiangqiPieceEntity.class,
                new AABB(origin).inflate(SEARCH_RADIUS),
                entity -> origin.equals(entity.board()));
    }

    private static void spawn(ServerLevel level, BlockPos origin, Direction facing, BoardFrame frame,
            int square, byte code) {
        XiangqiPieceEntity entity = new XiangqiPieceEntity(ModEntities.XIANGQI_PIECE.get(), level);
        entity.place(origin, square, code, facing);
        double a = BoardLayout.blockAOf(square);
        double b = BoardLayout.blockBOf(square);
        entity.moveTo(
                origin.getX() + frame.x(a, b),
                origin.getY() + BoardLayout.SURFACE_Y + BoardLayout.PIECE_HEIGHT,
                origin.getZ() + frame.z(a, b),
                0.0F,
                0.0F);
        level.addFreshEntity(entity);
    }
}
