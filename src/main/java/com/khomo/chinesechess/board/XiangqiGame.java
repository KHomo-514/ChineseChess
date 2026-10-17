package com.khomo.chinesechess.board;

import com.khomo.chinesechess.board.rules.MoveRules;

import java.util.ArrayList;
import java.util.List;

/**
 * A running game of Xiangqi: the board, whose turn it is, and the verdict.
 *
 * <p>All mutation happens through {@link #tryMove(Move)}; nothing outside this class may put the
 * game into an inconsistent state, so a match can never observe a half-applied ply. Terminal
 * detection follows the official rules:</p>
 * <ul>
 *   <li>checkmate (将死) and stalemate (困毙) both lose for the side to move,</li>
 *   <li>a threefold repetition in which the same side was always in check is a loss for the
 *       perpetually checking side (长将), any other threefold repetition is a draw,</li>
 *   <li>{@link RuleSettings#noCaptureDrawPlies()} quiet plies make a draw (自然限着),</li>
 *   <li>when neither side owns a piece that could ever mate, the game is drawn.</li>
 * </ul>
 *
 * <p>The exact, official treatment of perpetual chase (长捉) requires full 棋例 arbitration and is
 * deliberately not attempted; see the README for the reasoning.</p>
 *
 * <p>Single responsibility: hold and advance the state of one game.</p>
 */
public final class XiangqiGame {

    private final RuleSettings settings;
    private final LegalMoveCache cache;
    private final PositionHistory history = new PositionHistory();

    private Board board;
    private Side sideToMove;
    private GameOutcome outcome = GameOutcome.ONGOING;
    private Move lastMove;
    private int plyCount;
    private int pliesSinceCapture;
    private Side checkedSide;

    /** Starts a fresh game from the standard opening position. */
    public XiangqiGame(RuleSettings settings, LegalMoveCache cache) {
        this.settings = settings;
        this.cache = cache;
        this.board = Board.initial();
        this.sideToMove = Side.RED;
        recomputeCheckedSide();
        history.record(positionKey(), checkedSide);
        evaluateOutcome();
    }

    /**
     * Rebuilds a game from persisted data, for example after a server restart.
     *
     * <p>The outcome is never trusted from storage: it is recomputed from the position, which means a
     * damaged or outdated save is silently corrected instead of poisoning the match.</p>
     */
    public XiangqiGame(
            RuleSettings settings,
            LegalMoveCache cache,
            byte[] codes,
            Side sideToMove,
            Move lastMove,
            int plyCount,
            int pliesSinceCapture,
            long[] historyKeys,
            byte[] historyCheckedSides) {
        this.settings = settings;
        this.cache = cache;
        this.board = Board.of(codes);
        this.sideToMove = sideToMove == null ? Side.RED : sideToMove;
        this.lastMove = lastMove;
        this.plyCount = Math.max(0, plyCount);
        this.pliesSinceCapture = Math.max(0, pliesSinceCapture);
        if (historyKeys != null && historyKeys.length > 0) {
            history.restore(historyKeys, historyCheckedSides);
        }
        recomputeCheckedSide();
        if (history.size() == 0) {
            history.record(positionKey(), checkedSide);
        }
        evaluateOutcome();
    }

    public Board board() {
        return board;
    }

    public Side sideToMove() {
        return sideToMove;
    }

    public GameOutcome outcome() {
        return outcome;
    }

    /** @return the last played move, or {@code null} before the first ply. */
    public Move lastMove() {
        return lastMove;
    }

    /** Number of plies played so far. */
    public int plyCount() {
        return plyCount;
    }

    /** Quiet plies since the last capture, used for the natural move limit. */
    public int pliesSinceCapture() {
        return pliesSinceCapture;
    }

    /** @return the side currently in check, or {@code null} when neither general is attacked. */
    public Side checkedSide() {
        return checkedSide;
    }

    public RuleSettings settings() {
        return settings;
    }

    /** Every legal move in the current position. Cached, so callers may use it freely. */
    public List<Move> legalMoves() {
        long key = positionKey();
        long now = System.currentTimeMillis();
        List<Move> cached = cache.get(key, now);
        if (cached != null) {
            return cached;
        }
        List<Move> generated = MoveRules.legalMoves(board, sideToMove);
        cache.put(key, generated, now);
        return generated;
    }

    /**
     * The destinations legal for the piece on {@code from}.
     *
     * <p>The GUI uses this for highlighting and hit-testing, which keeps the client free of any
     * duplicated rule code: both sides of the network read the very same engine.</p>
     */
    public List<Integer> legalTargets(int from) {
        if (outcome.isFinished()) {
            return List.of();
        }
        List<Integer> targets = new ArrayList<>(8);
        for (Move move : legalMoves()) {
            if (move.from() == from) {
                targets.add(move.to());
            }
        }
        return targets;
    }

    public boolean isLegal(Move move) {
        return !outcome.isFinished() && legalMoves().contains(move);
    }

    /**
     * Plays {@code move} when it is legal for the side to move.
     *
     * @return why the move was accepted or rejected; the caller turns that into player feedback
     */
    public MoveAttempt tryMove(Move move) {
        if (outcome.isFinished()) {
            return MoveAttempt.GAME_OVER;
        }
        if (move == null || !move.isValid()) {
            return MoveAttempt.MALFORMED;
        }
        Piece piece = board.pieceAt(move.from());
        if (piece == null || piece.side() != sideToMove) {
            return MoveAttempt.NOT_YOUR_PIECE;
        }
        if (!isLegal(move)) {
            return MoveAttempt.ILLEGAL;
        }
        apply(move);
        return MoveAttempt.ACCEPTED;
    }

    /** Ends the game because {@code side} gave up. */
    public void resign(Side side) {
        if (!outcome.isFinished()) {
            outcome = GameOutcome.victory(side.opposite(), EndReason.RESIGN);
        }
    }

    /** Ends the game for an external reason, for example the board block being destroyed. */
    public void terminate(GameOutcome finalOutcome) {
        if (!outcome.isFinished() && finalOutcome != null && finalOutcome.isFinished()) {
            outcome = finalOutcome;
        }
    }

    /** The key that identifies this position including the side to move. */
    public long positionKey() {
        return board.hash() ^ (sideToMove.isRed() ? 0L : Zobrist.BLACK_TO_MOVE);
    }

    private void apply(Move move) {
        boolean capture = board.pieceAt(move.to()) != null;
        board = board.withMove(move);
        sideToMove = sideToMove.opposite();
        plyCount++;
        pliesSinceCapture = capture ? 0 : pliesSinceCapture + 1;
        lastMove = move;
        recomputeCheckedSide();
        history.record(positionKey(), checkedSide);
        evaluateOutcome();
    }

    private void recomputeCheckedSide() {
        checkedSide = ThreatDetector.isInCheck(board, sideToMove) ? sideToMove : null;
    }

    private void evaluateOutcome() {
        if (board.generalOf(Side.RED) < 0) {
            outcome = GameOutcome.victory(Side.BLACK, EndReason.CHECKMATE);
            return;
        }
        if (board.generalOf(Side.BLACK) < 0) {
            outcome = GameOutcome.victory(Side.RED, EndReason.CHECKMATE);
            return;
        }

        if (legalMoves().isEmpty()) {
            // Both checkmate and stalemate are losses for the side that has to move.
            outcome = GameOutcome.victory(
                    sideToMove.opposite(),
                    checkedSide != null ? EndReason.CHECKMATE : EndReason.STALEMATE);
            return;
        }

        long key = positionKey();
        if (settings.repetitionEnabled() && history.occurrences(key) >= 3) {
            Side defender = history.constantlyCheckedSide(key);
            if (settings.perpetualCheckLoses() && defender != null) {
                outcome = GameOutcome.victory(defender, EndReason.PERPETUAL_CHECK);
            } else {
                outcome = GameOutcome.draw(EndReason.REPETITION_DRAW);
            }
            return;
        }

        if (settings.hasNoCaptureLimit() && pliesSinceCapture >= settings.noCaptureDrawPlies()) {
            outcome = GameOutcome.draw(EndReason.NO_CAPTURE_DRAW);
            return;
        }

        if (settings.insufficientMaterialDraw()
                && !board.hasAttackingMaterial(Side.RED)
                && !board.hasAttackingMaterial(Side.BLACK)) {
            outcome = GameOutcome.draw(EndReason.INSUFFICIENT_MATERIAL_DRAW);
            return;
        }

        outcome = GameOutcome.ONGOING;
    }

    /** Persisted repetition keys, oldest first. */
    public long[] historyKeys() {
        return history.recentKeys();
    }

    /** Persisted "who was in check" flags matching {@link #historyKeys()}. */
    public byte[] historyCheckedSides() {
        return history.recentCheckedSides();
    }
}
