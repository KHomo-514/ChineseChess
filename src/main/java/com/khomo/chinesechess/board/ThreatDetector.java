package com.khomo.chinesechess.board;

import com.khomo.chinesechess.board.rules.MoveRules;
import com.khomo.chinesechess.board.rules.MoveSink;

/**
 * Answers "is this point attacked?" without searching.
 *
 * <p>The test walks every enemy piece once and asks its rule to reach the point of interest,
 * aborting at the first hit through {@link MoveSink}. Because the rules include the flying-general
 * capture, check detection needs no special case.</p>
 *
 * <p>Single responsibility: attack (check) detection.</p>
 */
public final class ThreatDetector {

    private ThreatDetector() {}

    /** @return whether any piece of {@code bySide} could capture on {@code target} right now. */
    public static boolean isAttacked(Board board, int target, Side bySide) {
        if (!Squares.isInsideIndex(target)) {
            return false;
        }
        Probe probe = new Probe(target);
        for (int from = 0; from < Squares.COUNT; from++) {
            Piece piece = board.pieceAt(from);
            if (piece == null || piece.side() != bySide) {
                continue;
            }
            if (!MoveRules.of(piece.type()).collect(board, from, piece, probe)) {
                break;
            }
        }
        return probe.found;
    }

    /** @return whether {@code side}'s general is currently attacked (将军). */
    public static boolean isInCheck(Board board, Side side) {
        int general = board.generalOf(side);
        return general >= 0 && isAttacked(board, general, side.opposite());
    }

    /** Cheap reusable probe: stops the generating rule the moment the wanted point is produced. */
    private static final class Probe implements MoveSink {
        private final int target;
        private boolean found;

        private Probe(int target) {
            this.target = target;
        }

        @Override
        public boolean accept(int from, int to) {
            if (to == target) {
                found = true;
                return false;
            }
            return true;
        }
    }
}
