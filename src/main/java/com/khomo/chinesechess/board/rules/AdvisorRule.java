package com.khomo.chinesechess.board.rules;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.Squares;

/**
 * 仕 / 士 movement: one diagonal step, never leaving the palace.
 *
 * <p>Single responsibility: the advisor's movement.</p>
 */
public final class AdvisorRule implements MoveRule {

    private static final int[][] STEPS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

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
        return true;
    }
}
