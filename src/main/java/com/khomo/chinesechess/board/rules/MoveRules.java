package com.khomo.chinesechess.board.rules;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Move;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.PieceType;
import com.khomo.chinesechess.board.Side;
import com.khomo.chinesechess.board.Squares;
import com.khomo.chinesechess.board.ThreatDetector;

import java.util.ArrayList;
import java.util.List;

/**
 * The single entry point to move generation.
 *
 * <p>Pseudo-legal moves come from one {@link MoveRule} per piece kind; a move becomes legal when the
 * mover's own general is not attacked afterwards. Filtering this way automatically covers pins,
 * moves that fail to answer a check, and the flying-general prohibition.</p>
 *
 * <p>Single responsibility: turn the per-kind rules into legal move lists.</p>
 */
public final class MoveRules {

    private static final MoveRule[] RULES = new MoveRule[PieceType.values().length];

    static {
        RULES[PieceType.GENERAL.ordinal()] = new GeneralRule();
        RULES[PieceType.ADVISOR.ordinal()] = new AdvisorRule();
        RULES[PieceType.ELEPHANT.ordinal()] = new ElephantRule();
        RULES[PieceType.HORSE.ordinal()] = new HorseRule();
        RULES[PieceType.CHARIOT.ordinal()] = new ChariotRule();
        RULES[PieceType.CANNON.ordinal()] = new CannonRule();
        RULES[PieceType.SOLDIER.ordinal()] = new SoldierRule();
    }

    private MoveRules() {}

    public static MoveRule of(PieceType type) {
        return RULES[type.ordinal()];
    }

    /**
     * Feeds every pseudo-legal move of {@code side} into {@code sink}.
     *
     * @return {@code false} if the sink stopped the scan early
     */
    public static boolean collectPseudoLegal(Board board, Side side, MoveSink sink) {
        for (int from = 0; from < Squares.COUNT; from++) {
            Piece piece = board.pieceAt(from);
            if (piece == null || piece.side() != side) {
                continue;
            }
            if (!of(piece.type()).collect(board, from, piece, sink)) {
                return false;
            }
        }
        return true;
    }

    public static List<Move> pseudoLegalMoves(Board board, Side side) {
        List<Move> moves = new ArrayList<>(64);
        collectPseudoLegal(board, side, (from, to) -> {
            moves.add(new Move(from, to));
            return true;
        });
        return List.copyOf(moves);
    }

    /** Every move {@code side} may legally play in {@code board}, including answers to a check. */
    public static List<Move> legalMoves(Board board, Side side) {
        List<Move> moves = new ArrayList<>(64);
        collectPseudoLegal(board, side, (from, to) -> {
            if (isSafeAfter(board, side, from, to)) {
                moves.add(new Move(from, to));
            }
            return true;
        });
        return List.copyOf(moves);
    }

    /** Every legal move of the single piece standing on {@code from}. */
    public static List<Move> legalMovesFrom(Board board, int from, Side side) {
        if (!Squares.isInsideIndex(from)) {
            return List.of();
        }
        Piece piece = board.pieceAt(from);
        if (piece == null || piece.side() != side) {
            return List.of();
        }
        List<Move> moves = new ArrayList<>(16);
        of(piece.type()).collect(board, from, piece, (origin, target) -> {
            if (isSafeAfter(board, side, origin, target)) {
                moves.add(new Move(origin, target));
            }
            return true;
        });
        return List.copyOf(moves);
    }

    /** Whether {@code move} is a legal move for {@code side}; the authoritative server-side check. */
    public static boolean isLegal(Board board, Side side, Move move) {
        if (move == null || !move.isValid()) {
            return false;
        }
        for (Move candidate : legalMovesFrom(board, move.from(), side)) {
            if (candidate.to() == move.to()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether playing {@code from -> to} leaves {@code side}'s own general unattacked. A missing
     * general means the position is already decided, so the move cannot make it worse.
     */
    public static boolean isSafeAfter(Board board, Side side, int from, int to) {
        Board next = board.withMove(new Move(from, to));
        int general = next.generalOf(side);
        return general < 0 || !ThreatDetector.isAttacked(next, general, side.opposite());
    }
}
