package com.khomo.chinesechess.board.rules;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.Squares;

/**
 * 炮 movement: slides like a chariot when quiet, but captures only by jumping over exactly one
 * screen piece (炮架) and then travelling over empty points to the first piece beyond it.
 *
 * <p>Single responsibility: the cannon's movement.</p>
 */
public final class CannonRule implements MoveRule {

    @Override
    public boolean collect(Board board, int from, Piece piece, MoveSink sink) {
        int file = Squares.file(from);
        int rank = Squares.rank(from);
        for (int[] direction : Rays.ORTHOGONAL) {
            boolean screenFound = false;
            for (int f = file + direction[0], r = rank + direction[1];
                    Squares.isInside(f, r);
                    f += direction[0], r += direction[1]) {
                int target = Squares.index(f, r);
                Piece occupant = board.pieceAt(target);
                if (!screenFound) {
                    if (occupant == null) {
                        if (!sink.accept(from, target)) {
                            return false;
                        }
                    } else {
                        screenFound = true;
                    }
                } else if (occupant != null) {
                    if (occupant.side() != piece.side() && !sink.accept(from, target)) {
                        return false;
                    }
                    break;
                }
            }
        }
        return true;
    }
}
