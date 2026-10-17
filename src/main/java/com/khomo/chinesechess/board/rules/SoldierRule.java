package com.khomo.chinesechess.board.rules;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.Squares;

/**
 * 兵 / 卒 movement: always one step forward, plus one step sideways once the river has been
 * crossed. Soldiers never move backwards and never move diagonally.
 *
 * <p>Single responsibility: the soldier's movement.</p>
 */
public final class SoldierRule implements MoveRule {

    @Override
    public boolean collect(Board board, int from, Piece piece, MoveSink sink) {
        int file = Squares.file(from);
        int rank = Squares.rank(from);

        int forwardRank = rank + (piece.side().isRed() ? 1 : -1);
        if (Squares.isInside(file, forwardRank)) {
            int target = Squares.index(file, forwardRank);
            Piece occupant = board.pieceAt(target);
            if (occupant == null || occupant.side() != piece.side()) {
                if (!sink.accept(from, target)) {
                    return false;
                }
            }
        }

        if (Squares.hasCrossedRiver(rank, piece.side())) {
            for (int deltaFile = -1; deltaFile <= 1; deltaFile += 2) {
                int targetFile = file + deltaFile;
                if (!Squares.isInside(targetFile, rank)) {
                    continue;
                }
                int target = Squares.index(targetFile, rank);
                Piece occupant = board.pieceAt(target);
                if (occupant != null && occupant.side() == piece.side()) {
                    continue;
                }
                if (!sink.accept(from, target)) {
                    return false;
                }
            }
        }
        return true;
    }
}
