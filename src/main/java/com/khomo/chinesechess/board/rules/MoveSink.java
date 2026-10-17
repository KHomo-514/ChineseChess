package com.khomo.chinesechess.board.rules;

/**
 * Receiver for the moves a rule produces.
 *
 * <p>Returning {@code false} asks the rule to stop early, which is what the search-free threat
 * check uses: it only wants to know whether one specific point is attacked, so it aborts the very
 * moment the point is reached.</p>
 *
 * <p>Single responsibility: decouple move producers from move consumers.</p>
 */
@FunctionalInterface
public interface MoveSink {

    /**
     * @return {@code true} to keep producing moves, {@code false} to stop immediately
     */
    boolean accept(int from, int to);
}
