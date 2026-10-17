package com.khomo.chinesechess.board.rules;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.Squares;

/**
 * 马 movement: one orthogonal step plus one diagonal step ("日"), blocked by the "horse leg"
 * (蹩马腿) - the orthogonally adjacent point in the direction of travel.
 *
 * <p>Single responsibility: the horse's movement.</p>
 */
public final class HorseRule implements MoveRule {

    /** {deltaFile, deltaRank, legDeltaFile, legDeltaRank} */
    private static final int[][] MOVES = {
            {1, 2, 0, 1}, {-1, 2, 0, 1},
            {1, -2, 0, -1}, {-1, -2, 0, -1},
            {2, 1, 1, 0}, {2, -1, 1, 0},
            {-2, 1, -1, 0}, {-2, -1, -1, 0}
    };

    @Override
    public boolean collect(Board board, int from, Piece piece, MoveSink sink) {
        int file = Squares.file(from);
        int rank = Squares.rank(from);
        for (int[] move : MOVES) {
            int targetFile = file + move[0];
            int targetRank = rank + move[1];
            if (!Squares.isInside(targetFile, targetRank)) {
                continue;
            }
            int legFile = file + move[2];
            int legRank = rank + move[3];
            if (!board.isEmpty(Squares.index(legFile, legRank))) {
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
