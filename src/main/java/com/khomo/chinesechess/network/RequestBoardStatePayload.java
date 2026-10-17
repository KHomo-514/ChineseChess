package com.khomo.chinesechess.network;

import com.khomo.chinesechess.ChineseChess;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: "my copy of the board does not add up, send it again".
 *
 * <p>The repair half of the consistency contract. A client sends this when a snapshot fails
 * validation, when its revision moves backwards, or when the player opens a board whose screen it
 * somehow lost. The server answers with a fresh {@link BoardStatePayload} and no other state change,
 * which makes resynchronisation idempotent and therefore safe to trigger from anywhere.</p>
 *
 * <p>Single responsibility: request a resynchronisation.</p>
 */
public record RequestBoardStatePayload(BlockPos origin) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestBoardStatePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ChineseChess.MODID, "request_board_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestBoardStatePayload> STREAM_CODEC =
            StreamCodec.of(RequestBoardStatePayload::encode, RequestBoardStatePayload::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RequestBoardStatePayload payload) {
        buffer.writeBlockPos(payload.origin);
    }

    private static RequestBoardStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new RequestBoardStatePayload(buffer.readBlockPos());
    }
}
