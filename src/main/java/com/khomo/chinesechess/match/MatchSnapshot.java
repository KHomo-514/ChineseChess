package com.khomo.chinesechess.match;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.EndReason;
import com.khomo.chinesechess.board.GameStatus;
import com.khomo.chinesechess.board.Side;
import com.khomo.chinesechess.board.Squares;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The complete, self-contained state of one board as it travels over the network.
 *
 * <p>It is intentionally a flat value object: the client can rebuild the whole board, decide whose
 * turn it is, highlight legal moves with the shared rules engine and detect a stale picture without
 * asking the server again. Both directions of the protocol use the same record, so a client can
 * always be re-synchronised by resending one packet.</p>
 *
 * <p>Single responsibility: be the wire form of a match.</p>
 */
public record MatchSnapshot(
        BlockPos origin,
        Direction facing,
        byte[] codes,
        Side sideToMove,
        Side checkedSide,
        GameStatus status,
        EndReason reason,
        int lastFrom,
        int lastTo,
        int plyCount,
        int pliesSinceCapture,
        int noCaptureLimit,
        MatchPhase phase,
        int revision) {

    public static final StreamCodec<RegistryFriendlyByteBuf, MatchSnapshot> STREAM_CODEC =
            StreamCodec.of(MatchSnapshot::encode, MatchSnapshot::decode);

    public static MatchSnapshot of(XiangqiMatch match, int noCaptureLimit) {
        var game = match.game();
        var last = game.lastMove();
        return new MatchSnapshot(
                match.origin(),
                match.facing(),
                game.board().toCodes(),
                game.sideToMove(),
                game.checkedSide(),
                game.outcome().status(),
                game.outcome().reason(),
                last == null ? -1 : last.from(),
                last == null ? -1 : last.to(),
                game.plyCount(),
                game.pliesSinceCapture(),
                noCaptureLimit,
                match.phase(),
                match.revision());
    }

    /**
     * Whether the snapshot can be trusted.
     *
     * <p>Both endpoints run this on every arrival: a snapshot that fails is discarded and answered
     * with a resync request instead of being rendered, which is what keeps a corrupted or
     * out-of-date client from showing a board that does not exist.</p>
     */
    public boolean isConsistent() {
        return origin != null
                && facing != null
                && sideToMove != null
                && status != null
                && phase != null
                && codes != null
                && codes.length == Squares.COUNT
                && Board.isPlausible(codes);
    }

    public boolean finished() {
        return status.isFinished();
    }

    public boolean hasLastMove() {
        return lastFrom >= 0 && lastTo >= 0;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MatchSnapshot snapshot) {
        buffer.writeBlockPos(snapshot.origin);
        buffer.writeByte(snapshot.facing.get3DDataValue());
        buffer.writeByteArray(snapshot.codes);
        buffer.writeByte(snapshot.sideToMove.ordinal());
        buffer.writeByte(snapshot.checkedSide == null ? -1 : snapshot.checkedSide.ordinal());
        buffer.writeByte(snapshot.status.ordinal());
        buffer.writeByte(snapshot.reason == null ? -1 : snapshot.reason.ordinal());
        buffer.writeInt(snapshot.lastFrom);
        buffer.writeInt(snapshot.lastTo);
        buffer.writeVarInt(snapshot.plyCount);
        buffer.writeVarInt(snapshot.pliesSinceCapture);
        buffer.writeVarInt(snapshot.noCaptureLimit);
        buffer.writeByte(snapshot.phase.ordinal());
        buffer.writeVarInt(snapshot.revision);
    }

    private static MatchSnapshot decode(RegistryFriendlyByteBuf buffer) {
        BlockPos origin = buffer.readBlockPos();
        Direction facing = Direction.from3DDataValue(buffer.readByte());
        byte[] codes = buffer.readByteArray();
        Side sideToMove = Side.of(buffer.readByte());
        byte checked = buffer.readByte();
        GameStatus status = GameStatus.of(buffer.readByte());
        byte reason = buffer.readByte();
        int lastFrom = buffer.readInt();
        int lastTo = buffer.readInt();
        int plyCount = buffer.readVarInt();
        int pliesSinceCapture = buffer.readVarInt();
        int noCaptureLimit = buffer.readVarInt();
        MatchPhase phase = MatchPhase.of(buffer.readByte());
        int revision = buffer.readVarInt();
        return new MatchSnapshot(
                origin,
                facing == null ? Direction.NORTH : facing,
                codes,
                sideToMove,
                checked < 0 ? null : Side.of(checked),
                status,
                reason < 0 ? null : EndReason.of(reason),
                lastFrom,
                lastTo,
                plyCount,
                pliesSinceCapture,
                noCaptureLimit,
                phase,
                revision);
    }
}
