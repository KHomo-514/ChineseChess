package com.khomo.chinesechess.network;

import com.khomo.chinesechess.ChineseChess;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: "clear this board".
 *
 * <p>Sent by the reset button. It carries nothing but the board, because the only thing the server
 * needs to decide is whether the sender is one of the players - which it already knows.</p>
 *
 * <p>Single responsibility: carry a reset request.</p>
 */
public record ResetBoardPayload(BlockPos origin) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ResetBoardPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ChineseChess.MODID, "reset_board"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResetBoardPayload> STREAM_CODEC =
            StreamCodec.of(ResetBoardPayload::encode, ResetBoardPayload::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ResetBoardPayload payload) {
        buffer.writeBlockPos(payload.origin);
    }

    private static ResetBoardPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ResetBoardPayload(buffer.readBlockPos());
    }
}
