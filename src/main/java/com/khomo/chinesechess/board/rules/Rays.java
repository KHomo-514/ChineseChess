package com.khomo.chinesechess.board.rules;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.Squares;

/**
 * Shared ray-casting helpers for the sliding pieces.
 *
 * <p>Single responsibility: walk straight lines over the board, reporting the empty points and the
 * single blocking piece a slider may stop on.</p>
 */
final class Rays {

    static final int[][] ORTHOGONAL = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

    private Rays() {}

    /**
     * Slides along all four orthogonal rays. Empty points are reported as quiet moves; the first
     * occupied point ends the ray and is reported only when it holds an enemy piece.
     *
     * @return {@code false} if the sink asked to stop
     */
    static boolean castOrthogonal(Board board, int from, Piece piece, MoveSink sink) {
        int file = Squares.file(from);
        int rank = Squares.rank(from);
        for (int[] direction : ORTHOGONAL) {
            for (int f = file + direction[0], r = rank + direction[1];
                    Squares.isInside(f, r);
                    f += direction[0], r += direction[1]) {
                int target = Squares.index(f, r);
                Piece occupant = board.pieceAt(target);
                if (occupant == null) {
                    if (!sink.accept(from, target)) {
                        return false;
                    }
                } else {
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
