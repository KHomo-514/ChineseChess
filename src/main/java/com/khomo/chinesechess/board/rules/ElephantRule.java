package com.khomo.chinesechess.board.rules;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.Squares;

/**
 * 相 / 象 movement: two diagonal steps, blocked by the "elephant eye" (象眼), never crossing the
 * river.
 *
 * <p>Single responsibility: the elephant's movement.</p>
 */
public final class ElephantRule implements MoveRule {

    private static final int[][] STEPS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    @Override
    public boolean collect(Board board, int from, Piece piece, MoveSink sink) {
        int file = Squares.file(from);
        int rank = Squares.rank(from);
        for (int[] step : STEPS) {
            int targetFile = file + step[0] * 2;
            int targetRank = rank + step[1] * 2;
            if (!Squares.isInside(targetFile, targetRank)) {
                continue;
            }
            if (Squares.hasCrossedRiver(targetRank, piece.side())) {
                continue;
            }
            int eyeFile = file + step[0];
            int eyeRank = rank + step[1];
            if (!board.isEmpty(Squares.index(eyeFile, eyeRank))) {
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
