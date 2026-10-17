package com.khomo.chinesechess.network;

import com.khomo.chinesechess.ChineseChess;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: "I am leaving this board".
 *
 * <p>Sent when the player closes the board screen - by pressing escape, or because the screen was
 * replaced by a death or disconnect screen. Closing the screen only frees the seat and leaves the
 * position alone; the {@code resign} flag is set by the resign button alone, which is the one
 * departure that decides the game.</p>
 *
 * <p>Single responsibility: carry a departure.</p>
 */
public record LeaveMatchPayload(BlockPos origin, boolean resign) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LeaveMatchPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ChineseChess.MODID, "leave_match"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LeaveMatchPayload> STREAM_CODEC =
            StreamCodec.of(LeaveMatchPayload::encode, LeaveMatchPayload::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, LeaveMatchPayload payload) {
        buffer.writeBlockPos(payload.origin);
        buffer.writeBoolean(payload.resign);
    }

    private static LeaveMatchPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos origin = buffer.readBlockPos();
        return new LeaveMatchPayload(origin, buffer.readBoolean());
    }
}
