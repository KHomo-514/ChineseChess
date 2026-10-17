package perft;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Move;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.PieceType;
import com.khomo.chinesechess.board.Side;
import com.khomo.chinesechess.board.Squares;
import com.khomo.chinesechess.board.ThreatDetector;
import com.khomo.chinesechess.board.rules.MoveRules;

import java.util.List;

/**
 * Standalone perft for the rules engine, checked against the published Chinese chess counts at
 * https://chessprogramming.org/Chinese_Chess_Perft_Results
 *
 * <p>Run with the engine on the source path; no Minecraft classes are involved.</p>
 */
public final class Perft {

    private static final String INITIAL = "rheakaehr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RHEAKAEHR w - - 0 1";
    private static final String[] POSITION_2 = {
            "r1ea1a3/4kh3/2h1e4/pHp1p1p1p/4c4/6P2/P1P2R2P/1CcC5/9/2EAKAE2 w - - 0 1",
            "38", "1128", "43929", "1339047",
    };
    private static final String[] POSITION_5 = {
            "CRH1k1e2/3ca4/4ea3/9/2hr5/9/9/4E4/4A4/4KA3 w - - 0 1",
            "28", "516", "14808", "395483",
    };

    /** Set to true to mirror the files, for checking which way the FEN letters run. */
    private static final boolean MIRROR_FILES = false;

    private Perft() {}

    public static void main(String[] args) {
        if (args.length >= 2 && args[0].equals("moves")) {
            showMoves(args[1]);
            return;
        }
        long start = System.currentTimeMillis();
        check("initial position", INITIAL, new String[] {"44", "1920", "79666", "3290240"});
        check("position 2 (checks and evasions)", POSITION_2[0],
                new String[] {POSITION_2[1], POSITION_2[2], POSITION_2[3], POSITION_2[4]});
        check("position 5 (checks and evasions)", POSITION_5[0],
                new String[] {POSITION_5[1], POSITION_5[2], POSITION_5[3], POSITION_5[4]});
        System.out.printf("%nfinished in %d ms (mirrorFiles=%s)%n", System.currentTimeMillis() - start, MIRROR_FILES);
    }

    /**
     * Lists every legal move of a FEN's side to move, grouped by the piece that makes it.
     *
     * <p>This is the tool for "the game would not let me play this": it answers with the engine's own
     * opinion instead of a guess, and it names squares so the answer can be checked by eye.</p>
     */
    private static void showMoves(String fen) {
        Board board = fromFen(fen);
        Side side = sideToMove(fen);
        List<Move> legal = MoveRules.legalMoves(board, side);
        System.out.println("fen   " + fen);
        System.out.println("side  " + side + (ThreatDetector.isInCheck(board, side) ? "  (in check)" : ""));
        System.out.println("total " + legal.size() + " legal moves");
        for (int from = 0; from < Squares.COUNT; from++) {
            Piece piece = board.pieceAt(from);
            if (piece == null || piece.side() != side) {
                continue;
            }
            StringBuilder targets = new StringBuilder();
            for (Move move : legal) {
                if (move.from() == from) {
                    targets.append(name(move.to())).append(' ');
                }
            }
            System.out.printf("  %-8s %-6s on %-3s -> %s%n", piece.type(), piece.side(), name(from),
                    targets.length() == 0 ? "(none)" : targets.toString().trim());
        }
        System.out.println("moves that give check: " + legal.stream()
                .filter(move -> ThreatDetector.isInCheck(board.withMove(move), side.opposite()))
                .count());
    }

    /** A square as file letter (a-i) and rank (0-9), both in the engine's own coordinates. */
    private static String name(int square) {
        return "" + (char) ('a' + Squares.file(square)) + Squares.rank(square);
    }

    private static Side sideToMove(String fen) {
        String[] parts = fen.trim().split("\\s+");
        return parts.length > 1 && parts[1].startsWith("b") ? Side.BLACK : Side.RED;
    }

    private static void check(String label, String fen, String[] expected) {
        Board board = fromFen(fen);
        System.out.println("== " + label);
        System.out.println("   fen       " + fen);
        for (int depth = 1; depth <= expected.length; depth++) {
            long nodes = perft(board, Side.RED, depth);
            boolean ok = String.valueOf(nodes).equals(expected[depth - 1]);
            System.out.printf("   depth %d: %-12d expected %-12s %s%n",
                    depth, nodes, expected[depth - 1], ok ? "ok" : "MISMATCH");
        }
        describeRoot(board);
    }

    /** Shows where the side to move may go, which is what a player actually sees. */
    private static void describeRoot(Board board) {
        List<Move> legal = MoveRules.legalMoves(board, Side.RED);
        StringBuilder byPiece = new StringBuilder();
        for (PieceType type : PieceType.values()) {
            int count = 0;
            for (Move move : legal) {
                Piece piece = board.pieceAt(move.from());
                if (piece != null && piece.type() == type) {
                    count++;
                }
            }
            if (count > 0) {
                byPiece.append(type).append('=').append(count).append(' ');
            }
        }
        System.out.println("   root legal moves = " + legal.size() + "  [" + byPiece.toString().trim() + "]");
    }

    private static long perft(Board board, Side side, int depth) {
        List<Move> moves = MoveRules.legalMoves(board, side);
        if (depth <= 1) {
            return moves.size();
        }
        long nodes = 0;
        for (Move move : moves) {
            nodes += perft(board.withMove(move), side.opposite(), depth - 1);
        }
        return nodes;
    }

    /** Minimal Xiangqi FEN reader: the first row is rank 9 (black's back rank). */
    static Board fromFen(String fen) {
        String[] rows = fen.split(" ")[0].split("/");
        byte[] codes = new byte[Squares.COUNT];
        for (int row = 0; row < rows.length; row++) {
            int rank = Squares.RANKS - 1 - row;
            int file = 0;
            for (char c : rows[row].toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                    continue;
                }
                Side side = Character.isUpperCase(c) ? Side.RED : Side.BLACK;
                PieceType type = switch (Character.toUpperCase(c)) {
                    case 'K' -> PieceType.GENERAL;
                    case 'A' -> PieceType.ADVISOR;
                    case 'E' -> PieceType.ELEPHANT;
                    case 'H' -> PieceType.HORSE;
                    case 'R' -> PieceType.CHARIOT;
                    case 'C' -> PieceType.CANNON;
                    case 'P' -> PieceType.SOLDIER;
                    default -> throw new IllegalArgumentException("unknown piece letter: " + c);
                };
                int mapped = MIRROR_FILES ? Squares.FILES - 1 - file : file;
                codes[Squares.index(mapped, rank)] = new Piece(type, side).encode();
                file++;
            }
        }
        return Board.of(codes);
    }
}
