package com.khomo.chinesechess.network;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.board.EndReason;
import com.khomo.chinesechess.board.GameStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: "the game on this board is over".
 *
 * <p>Carries the auto-close delay so the client can keep the result on screen for exactly as long as
 * the server intends to keep the match alive; the two can therefore never disagree about when the
 * screen should disappear.</p>
 *
 * <p>Single responsibility: announce the verdict.</p>
 */
public record MatchFinishedPayload(BlockPos origin, GameStatus status, EndReason reason, int closeAfterTicks)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MatchFinishedPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ChineseChess.MODID, "match_finished"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MatchFinishedPayload> STREAM_CODEC =
            StreamCodec.of(MatchFinishedPayload::encode, MatchFinishedPayload::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MatchFinishedPayload payload) {
        buffer.writeBlockPos(payload.origin);
        buffer.writeByte(payload.status.ordinal());
        buffer.writeByte(payload.reason == null ? -1 : payload.reason.ordinal());
        buffer.writeVarInt(payload.closeAfterTicks);
    }

    private static MatchFinishedPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos origin = buffer.readBlockPos();
        GameStatus status = GameStatus.of(buffer.readByte());
        byte reason = buffer.readByte();
        int closeAfter = buffer.readVarInt();
        return new MatchFinishedPayload(origin, status, reason < 0 ? null : EndReason.of(reason), closeAfter);
    }
}
