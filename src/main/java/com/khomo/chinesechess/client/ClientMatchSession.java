package com.khomo.chinesechess.client;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.LegalMoveCache;
import com.khomo.chinesechess.board.Move;
import com.khomo.chinesechess.board.Side;
import com.khomo.chinesechess.board.Squares;
import com.khomo.chinesechess.board.Zobrist;
import com.khomo.chinesechess.board.rules.MoveRules;
import com.khomo.chinesechess.match.MatchPhase;
import com.khomo.chinesechess.match.MatchSnapshot;
import com.khomo.chinesechess.network.LeaveMatchPayload;
import com.khomo.chinesechess.network.ResetBoardPayload;
import com.khomo.chinesechess.network.MoveRequestPayload;
import com.khomo.chinesechess.network.RequestBoardStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * The client's copy of the board it is looking at.
 *
 * <p>It holds the latest authoritative snapshot and derives everything the screen needs from it:
 * whose turn it is, whether the viewer may move, and the legal destinations of any point. The
 * derivation uses the very same rules engine the server validates with, so the GUI cannot offer a
 * move the server would reject - and because the move lists are cached by position hash, the cost is
 * paid once per ply rather than once per frame.</p>
 *
 * <p>It is also the place where a stale view is detected: a snapshot whose revision has gone
 * backwards is refused and answered with a resynchronisation request.</p>
 *
 * <p>Single responsibility: be the client-side model of the open board.</p>
 */
public final class ClientMatchSession {

    private static final ClientMatchSession INSTANCE = new ClientMatchSession();

    private final LegalMoveCache cache = new LegalMoveCache();

    @Nullable
    private BlockPos origin;
    @Nullable
    private MatchSnapshot snapshot;
    @Nullable
    private Side viewerSide;
    private Board board = Board.initial();
    private List<Move> legalMoves = List.of();

    private ClientMatchSession() {}

    public static ClientMatchSession instance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Adopts a snapshot, opening or refreshing the board. */
    public void apply(MatchSnapshot incoming) {
        if (!incoming.isConsistent()) {
            requestResync(incoming.origin());
            return;
        }
        if (origin != null && origin.equals(incoming.origin())
                && snapshot != null
                && incoming.revision() < snapshot.revision()) {
            // An older packet arrived after a newer one; ask again instead of going backwards.
            requestResync(incoming.origin());
            return;
        }
        this.origin = incoming.origin();
        this.snapshot = incoming;
        this.board = Board.of(incoming.codes());
        this.legalMoves = computeLegalMoves();
    }

    public void setViewerSide(@Nullable Side side) {
        this.viewerSide = side;
    }

    public void close() {
        origin = null;
        snapshot = null;
        viewerSide = null;
        board = Board.initial();
        legalMoves = List.of();
    }

    public boolean isOpen() {
        return origin != null && snapshot != null;
    }

    public boolean isOpenFor(BlockPos boardOrigin) {
        return origin != null && origin.equals(boardOrigin);
    }

    @Nullable
    public BlockPos origin() {
        return origin;
    }

    @Nullable
    public MatchSnapshot snapshot() {
        return snapshot;
    }

    @Nullable
    public Side viewerSide() {
        return viewerSide;
    }

    public Board board() {
        return board;
    }

    public LegalMoveCache cache() {
        return cache;
    }

    // ------------------------------------------------------------------ queries

    /** Whether the viewer is seated and it is their turn. */
    public boolean canMove() {
        MatchSnapshot current = snapshot;
        return current != null
                && current.phase() == MatchPhase.PLAYING
                && viewerSide != null
                && current.sideToMove() == viewerSide;
    }

    public boolean isSeated() {
        return viewerSide != null;
    }

    /** The legal destinations of the point {@code square}; empty when the viewer may not move it. */
    public List<Integer> legalTargetsFrom(int square) {
        if (!canMove() || !Squares.isInsideIndex(square)) {
            return List.of();
        }
        var piece = board.pieceAt(square);
        if (piece == null || piece.side() != viewerSide) {
            return List.of();
        }
        List<Integer> targets = new ArrayList<>(8);
        for (Move move : legalMoves) {
            if (move.from() == square) {
                targets.add(move.to());
            }
        }
        return targets;
    }

    /**
     * Whether the piece on {@code square} has moves of its own kind but none of them are legal.
     *
     * <p>That is the signature of a pin, or of a move that would walk into check - the cases a player
     * reads as "this piece is broken". Answering it lets the screen say why instead of leaving a piece
     * that simply refuses to show any destination.</p>
     */
    public boolean isHeldBackByCheck(int square) {
        if (!canMove() || !Squares.isInsideIndex(square) || snapshot == null) {
            return false;
        }
        var piece = board.pieceAt(square);
        if (piece == null || piece.side() != viewerSide) {
            return false;
        }
        for (Move move : MoveRules.pseudoLegalMoves(board, snapshot.sideToMove())) {
            if (move.from() == square) {
                return true;
            }
        }
        return false;
    }

    private List<Move> computeLegalMoves() {
        MatchSnapshot current = snapshot;
        if (current == null) {
            return List.of();
        }
        long key = board.hash() ^ (current.sideToMove().isRed() ? 0L : Zobrist.BLACK_TO_MOVE);
        long now = System.currentTimeMillis();
        List<Move> cached = cache.get(key, now);
        if (cached != null) {
            return cached;
        }
        List<Move> generated = MoveRules.legalMoves(board, current.sideToMove());
        cache.put(key, generated, now);
        return generated;
    }

    // ------------------------------------------------------------------ requests

    public void requestMove(int from, int to) {
        BlockPos board = origin;
        if (board != null) {
            send(new MoveRequestPayload(board, from, to));
        }
    }

    public void requestLeave(BlockPos board, boolean resign) {
        send(new LeaveMatchPayload(board, resign));
    }

    /** Clears the board back to the opening array for both players. */
    public void requestReset(BlockPos board) {
        if (board != null) {
            send(new ResetBoardPayload(board));
        }
    }

    public void requestResync(BlockPos board) {
        if (board != null) {
            send(new RequestBoardStatePayload(board));
        }
    }

    /**
     * Sends to the server, but only while there is still a connection: leaving a world closes the
     * board screen too, and a packet sent into a closed channel would throw.
     */
    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener listener = minecraft.getConnection();
        if (listener != null && listener.getConnection().isConnected()) {
            PacketDistributor.sendToServer(payload);
        }
    }
}
