package com.khomo.chinesechess.board.rules;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Piece;

/**
 * 车 movement: any distance orthogonally, stopping at the first occupied point.
 *
 * <p>Single responsibility: the chariot's movement.</p>
 */
public final class ChariotRule implements MoveRule {

    @Override
    public boolean collect(Board board, int from, Piece piece, MoveSink sink) {
        return Rays.castOrthogonal(board, from, piece, sink);
    }
}
