package com.khomo.chinesechess.network;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.board.Move;
import com.khomo.chinesechess.board.Squares;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: "play this move".
 *
 * <p>Only a request: the server revalidates it against the authoritative game, so a modified client
 * can no more cheat here than it could with any other interaction packet.</p>
 *
 * <p>Single responsibility: carry one requested ply.</p>
 */
public record MoveRequestPayload(BlockPos origin, int from, int to) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MoveRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ChineseChess.MODID, "move_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoveRequestPayload> STREAM_CODEC =
            StreamCodec.of(MoveRequestPayload::encode, MoveRequestPayload::decode);

    public MoveRequestPayload(BlockPos origin, Move move) {
        this(origin, move.from(), move.to());
    }

    /** Clamps anything that is not a real point to {@code -1}, so a bad packet becomes a no-op. */
    public MoveRequestPayload {
        from = Squares.isInsideIndex(from) ? from : -1;
        to = Squares.isInsideIndex(to) ? to : -1;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** @return the requested move, or {@code null} when the packet was malformed. */
    public Move move() {
        Move move = new Move(from, to);
        return move.isValid() ? move : null;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MoveRequestPayload payload) {
        buffer.writeBlockPos(payload.origin);
        buffer.writeByte(payload.from);
        buffer.writeByte(payload.to);
    }

    private static MoveRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos origin = buffer.readBlockPos();
        int from = buffer.readByte();
        int to = buffer.readByte();
        return new MoveRequestPayload(origin, from, to);
    }
}
