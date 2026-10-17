package com.khomo.chinesechess.board;

import java.util.Random;

/**
 * Zobrist keys used for cheap position identity.
 *
 * <p>The keys are generated once from a fixed seed so that a given position always hashes to the
 * same value inside one session, which is what the repetition counter and the legal-move cache
 * need. The tables are immutable after class initialisation and are safe to share.</p>
 *
 * <p>Single responsibility: hand out the random keys that make up a position hash.</p>
 */
public final class Zobrist {

    private static final long[][][] PIECES =
            new long[PieceType.values().length][Side.values().length][Squares.COUNT];

    /** Key mixed in when it is BLACK's turn, so that "same placement, other side to move" differs. */
    public static final long BLACK_TO_MOVE;

    static {
        Random random = new Random(0x5A17C4E5C0DEL);
        for (int type = 0; type < PIECES.length; type++) {
            for (int side = 0; side < PIECES[type].length; side++) {
                for (int square = 0; square < Squares.COUNT; square++) {
                    PIECES[type][side][square] = random.nextLong();
                }
            }
        }
        BLACK_TO_MOVE = random.nextLong();
    }

    private Zobrist() {}

    public static long piece(Piece piece, int square) {
        return PIECES[piece.type().ordinal()][piece.side().ordinal()][square];
    }

    public static long piece(Piece piece, int file, int rank) {
        return piece(piece, Squares.index(file, rank));
    }
}
