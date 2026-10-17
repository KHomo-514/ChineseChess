package com.khomo.chinesechess.network;

import com.khomo.chinesechess.ChineseChess;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: "close the board screen".
 *
 * <p>Sent when a match is dissolved - the board was destroyed, the world is unloading, or the player
 * was evicted - so the GUI can never be left open on a board that no longer exists.</p>
 *
 * <p>Single responsibility: tell the client to stop showing a board.</p>
 */
public record CloseBoardPayload(BlockPos origin, String messageKey) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CloseBoardPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ChineseChess.MODID, "close_board"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseBoardPayload> STREAM_CODEC =
            StreamCodec.of(CloseBoardPayload::encode, CloseBoardPayload::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, CloseBoardPayload payload) {
        buffer.writeBlockPos(payload.origin);
        buffer.writeUtf(payload.messageKey, 128);
    }

    private static CloseBoardPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos origin = buffer.readBlockPos();
        return new CloseBoardPayload(origin, buffer.readUtf(128));
    }
}
