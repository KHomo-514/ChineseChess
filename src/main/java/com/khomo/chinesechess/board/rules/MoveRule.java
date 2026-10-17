package com.khomo.chinesechess.board.rules;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Piece;

/**
 * One piece kind's movement rule.
 *
 * <p>Implementations produce <em>pseudo-legal</em> moves: moves that respect the piece's own
 * geometry and blocking rules. Whether the mover's own general is left in check is decided
 * afterwards by {@link MoveRules}, so every rule stays a small, independently testable class.</p>
 *
 * <p>Single responsibility: answer "where may this piece go, ignoring self-check?".</p>
 */
public interface MoveRule {

    /**
     * Feeds every pseudo-legal destination of the piece on {@code from} into {@code sink}.
     *
     * @return {@code false} if the sink requested an early stop, {@code true} if the rule ran to
     *         completion. The distinction matters for the threat check, which stops on a hit.
     */
    boolean collect(Board board, int from, Piece piece, MoveSink sink);
}
