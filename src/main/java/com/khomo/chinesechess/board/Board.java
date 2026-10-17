package com.khomo.chinesechess.board;

import java.util.Arrays;

/**
 * An immutable board placement.
 *
 * <p>A board only knows <em>where the pieces are</em>. Whose turn it is, how often the position has
 * repeated and whether anybody has won are concerns of {@link XiangqiGame}. Because instances never
 * change, hypothetical positions can be built for free (by reference) and shared between the server
 * thread and the cached move lists without any locking.</p>
 *
 * <p>Single responsibility: store a piece placement and produce the placement that results from a
 * pseudo-legal move.</p>
 */
public final class Board {

    private final Piece[] squares;
    private final long hash;
    private final int redGeneral;
    private final int blackGeneral;

    private Board(Piece[] squares, long hash, int redGeneral, int blackGeneral) {
        this.squares = squares;
        this.hash = hash;
        this.redGeneral = redGeneral;
        this.blackGeneral = blackGeneral;
    }

    /** A board with no pieces at all. */
    public static Board empty() {
        return new Board(new Piece[Squares.COUNT], 0L, -1, -1);
    }

    /**
     * The standard Xiangqi opening position: RED on ranks 0..3, BLACK mirrored on ranks 6..9.
     */
    public static Board initial() {
        Piece[] squares = new Piece[Squares.COUNT];
        PieceType[] backRank = {
                PieceType.CHARIOT, PieceType.HORSE, PieceType.ELEPHANT, PieceType.ADVISOR,
                PieceType.GENERAL, PieceType.ADVISOR, PieceType.ELEPHANT, PieceType.HORSE,
                PieceType.CHARIOT
        };
        for (int file = 0; file < Squares.FILES; file++) {
            squares[Squares.index(file, 0)] = new Piece(backRank[file], Side.RED);
            squares[Squares.index(file, Squares.RANKS - 1)] = new Piece(backRank[file], Side.BLACK);
        }
        squares[Squares.index(1, 2)] = new Piece(PieceType.CANNON, Side.RED);
        squares[Squares.index(7, 2)] = new Piece(PieceType.CANNON, Side.RED);
        squares[Squares.index(1, 7)] = new Piece(PieceType.CANNON, Side.BLACK);
        squares[Squares.index(7, 7)] = new Piece(PieceType.CANNON, Side.BLACK);
        for (int file = 0; file < Squares.FILES; file += 2) {
            squares[Squares.index(file, 3)] = new Piece(PieceType.SOLDIER, Side.RED);
            squares[Squares.index(file, 6)] = new Piece(PieceType.SOLDIER, Side.BLACK);
        }
        return build(squares);
    }

    /** Builds a board from a wire/NBT code array, rejecting malformed data instead of crashing later. */
    public static Board of(byte[] codes) {
        if (codes == null || codes.length != Squares.COUNT) {
            throw new IllegalArgumentException("board code array must hold " + Squares.COUNT + " entries");
        }
        Piece[] squares = new Piece[Squares.COUNT];
        for (int index = 0; index < codes.length; index++) {
            squares[index] = Piece.decode(codes[index]);
        }
        return build(squares);
    }

    /**
     * @return whether the codes describe a structurally usable position: exactly one general of each
     *         side, no unknown codes. Used by the consistency check that repairs corrupted matches.
     */
    public static boolean isPlausible(byte[] codes) {
        if (codes == null || codes.length != Squares.COUNT) {
            return false;
        }
        int redGenerals = 0;
        int blackGenerals = 0;
        for (byte code : codes) {
            if (code == Piece.EMPTY_CODE) {
                continue;
            }
            Piece piece = Piece.decode(code);
            if (piece == null) {
                return false;
            }
            if (piece.type() == PieceType.GENERAL) {
                if (piece.side().isRed()) {
                    redGenerals++;
                } else {
                    blackGenerals++;
                }
            }
        }
        return redGenerals == 1 && blackGenerals == 1;
    }

    private static Board build(Piece[] squares) {
        long hash = 0L;
        int redGeneral = -1;
        int blackGeneral = -1;
        for (int index = 0; index < squares.length; index++) {
            Piece piece = squares[index];
            if (piece == null) {
                continue;
            }
            hash ^= Zobrist.piece(piece, index);
            if (piece.type() == PieceType.GENERAL) {
                if (piece.side().isRed()) {
                    redGeneral = index;
                } else {
                    blackGeneral = index;
                }
            }
        }
        return new Board(squares, hash, redGeneral, blackGeneral);
    }

    public Piece pieceAt(int index) {
        return Squares.isInsideIndex(index) ? squares[index] : null;
    }

    public Piece pieceAt(int file, int rank) {
        return Squares.isInside(file, rank) ? squares[Squares.index(file, rank)] : null;
    }

    public boolean isEmpty(int index) {
        return pieceAt(index) == null;
    }

    /** Placement-only hash; combine with {@link Zobrist#BLACK_TO_MOVE} to include the side to move. */
    public long hash() {
        return hash;
    }

    /** @return the square of {@code side}'s general, or {@code -1} if it has been captured. */
    public int generalOf(Side side) {
        return side.isRed() ? redGeneral : blackGeneral;
    }

    /**
     * Whether {@code side} still owns any piece that can threaten the enemy general. When neither
     * side does, the game can never be won and is drawn (双方均无取胜可能).
     */
    public boolean hasAttackingMaterial(Side side) {
        for (Piece piece : squares) {
            if (piece != null && piece.side() == side && piece.type().isAttacking()) {
                return true;
            }
        }
        return false;
    }

    public int count(PieceType type, Side side) {
        int total = 0;
        for (Piece piece : squares) {
            if (piece != null && piece.side() == side && piece.type() == type) {
                total++;
            }
        }
        return total;
    }

    /**
     * The placement produced by a move. The move is assumed to be pseudo-legal for the piece on
     * {@code from}; that is guaranteed by {@link com.khomo.chinesechess.board.rules.MoveRules}.
     */
    public Board withMove(Move move) {
        Piece mover = squares[move.from()];
        if (mover == null) {
            return this;
        }
        Piece captured = squares[move.to()];
        Piece[] next = squares.clone();
        next[move.from()] = null;
        next[move.to()] = mover;

        long nextHash = hash ^ Zobrist.piece(mover, move.from()) ^ Zobrist.piece(mover, move.to());
        if (captured != null) {
            nextHash ^= Zobrist.piece(captured, move.to());
        }

        int nextRedGeneral = redGeneral;
        int nextBlackGeneral = blackGeneral;
        if (mover.type() == PieceType.GENERAL) {
            if (mover.side().isRed()) {
                nextRedGeneral = move.to();
            } else {
                nextBlackGeneral = move.to();
            }
        }
        return new Board(next, nextHash, nextRedGeneral, nextBlackGeneral);
    }

    /** Writes the placement into {@code target} using the compact piece codes. */
    public void copyTo(byte[] target) {
        if (target.length != Squares.COUNT) {
            throw new IllegalArgumentException("board code array must hold " + Squares.COUNT + " entries");
        }
        for (int index = 0; index < squares.length; index++) {
            Piece piece = squares[index];
            target[index] = piece == null ? Piece.EMPTY_CODE : piece.encode();
        }
    }

    public byte[] toCodes() {
        byte[] codes = new byte[Squares.COUNT];
        copyTo(codes);
        return codes;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Board board && board.hash == hash && Arrays.equals(board.squares, squares);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hash);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Board[");
        for (int rank = Squares.RANKS - 1; rank >= 0; rank--) {
            for (int file = 0; file < Squares.FILES; file++) {
                Piece piece = pieceAt(file, rank);
                builder.append(piece == null ? '.' : piece.type().name().charAt(0));
            }
            builder.append('/');
        }
        return builder.append(']').toString();
    }
}
