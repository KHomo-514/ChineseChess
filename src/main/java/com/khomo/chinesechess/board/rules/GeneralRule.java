package com.khomo.chinesechess.board.rules;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.PieceType;
import com.khomo.chinesechess.board.Squares;

/**
 * 帅 / 将 movement: one orthogonal step inside the palace, plus the flying-general capture.
 *
 * <p>The flying general (对脸 / 白脸将) is modelled as a legal capture down an otherwise empty file.
 * Expressing it as a move - rather than as a special case in the check test - means the same rule
 * automatically forbids any move that would leave the two generals facing each other.</p>
 *
 * <p>Single responsibility: the general's movement.</p>
 */
public final class GeneralRule implements MoveRule {

    private static final int[][] STEPS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

    @Override
    public boolean collect(Board board, int from, Piece piece, MoveSink sink) {
        int file = Squares.file(from);
        int rank = Squares.rank(from);

        for (int[] step : STEPS) {
            int targetFile = file + step[0];
            int targetRank = rank + step[1];
            if (!Squares.isInPalace(targetFile, targetRank, piece.side())) {
                continue;
            }
            int target = Squares.index(targetFile, targetRank);
            Piece occupant = board.pieceAt(target);
            if (occupant != null && occupant.side() == piece.side()) {
                continue;
            }
            if (!sink.accept(from, target)) {
                return false;
            }
        }

        int forward = piece.side().isRed() ? 1 : -1;
        for (int scan = rank + forward; Squares.isInside(file, scan); scan += forward) {
            int target = Squares.index(file, scan);
            Piece occupant = board.pieceAt(target);
            if (occupant == null) {
                continue;
            }
            if (occupant.type() == PieceType.GENERAL && occupant.side() != piece.side()) {
                if (!sink.accept(from, target)) {
                    return false;
                }
            }
            break;
        }
        return true;
    }
}
