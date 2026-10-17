package com.khomo.chinesechess.network;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.board.Side;
import com.khomo.chinesechess.match.MatchSnapshot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: "here is the board, show it".
 *
 * <p>Sent both when the board is opened for the first time ({@code open}) and whenever the position
 * changes, so the client never needs a second packet type to stay in step.</p>
 *
 * <p>Single responsibility: carry a board snapshot to one viewer.</p>
 */
public record BoardStatePayload(MatchSnapshot snapshot, Side viewerSide, boolean open)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BoardStatePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ChineseChess.MODID, "board_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardStatePayload> STREAM_CODEC =
            StreamCodec.of(BoardStatePayload::encode, BoardStatePayload::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, BoardStatePayload payload) {
        buffer.writeBoolean(payload.open);
        buffer.writeByte(payload.viewerSide == null ? -1 : payload.viewerSide.ordinal());
        MatchSnapshot.STREAM_CODEC.encode(buffer, payload.snapshot);
    }

    private static BoardStatePayload decode(RegistryFriendlyByteBuf buffer) {
        boolean open = buffer.readBoolean();
        byte side = buffer.readByte();
        MatchSnapshot snapshot = MatchSnapshot.STREAM_CODEC.decode(buffer);
        return new BoardStatePayload(snapshot, side < 0 ? null : Side.of(side), open);
    }
}
