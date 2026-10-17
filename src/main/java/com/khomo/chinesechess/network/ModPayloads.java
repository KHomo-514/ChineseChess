package com.khomo.chinesechess.network;

import com.khomo.chinesechess.match.MatchService;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registration and dispatch of every packet the mod uses.
 *
 * <p>Server-bound packets run straight into {@link MatchService}, which is the only place allowed to
 * change a match. Client-bound packets are forwarded to {@code ClientPayloadRouter}; that reference
 * is guarded by a physical-side test, and the class is only resolved when the branch is actually
 * taken, so a dedicated server never touches client code.</p>
 *
 * <p>The seven payloads are deliberately coarse-grained - one full board snapshot instead of a stream
 * of deltas - because a snapshot is at most a hundred bytes, arrives rarely (once per ply) and makes
 * re-synchronisation trivial.</p>
 *
 * <p>Single responsibility: wire payload types to their handlers.</p>
 */
public final class ModPayloads {

    /** Network version; a mismatch between client and server payload sets refuses the connection. */
    public static final String VERSION = "1";

    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToServer(MoveRequestPayload.TYPE, MoveRequestPayload.STREAM_CODEC, ModPayloads::onMoveRequest);
        registrar.playToServer(LeaveMatchPayload.TYPE, LeaveMatchPayload.STREAM_CODEC, ModPayloads::onLeave);
        registrar.playToServer(ResetBoardPayload.TYPE, ResetBoardPayload.STREAM_CODEC, ModPayloads::onResetBoard);
        registrar.playToServer(RequestBoardStatePayload.TYPE, RequestBoardStatePayload.STREAM_CODEC,
                ModPayloads::onResyncRequest);

        registrar.playToClient(BoardStatePayload.TYPE, BoardStatePayload.STREAM_CODEC, ModPayloads::onBoardState);
        registrar.playToClient(MatchFinishedPayload.TYPE, MatchFinishedPayload.STREAM_CODEC, ModPayloads::onMatchFinished);
        registrar.playToClient(CloseBoardPayload.TYPE, CloseBoardPayload.STREAM_CODEC, ModPayloads::onCloseBoard);
    }

    // ------------------------------------------------------------------ server-bound

    private static void onMoveRequest(MoveRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MatchService.onMoveRequest(player, payload.origin(), payload.move());
        }
    }

    private static void onLeave(LeaveMatchPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MatchService.onLeave(player, payload.origin(), payload.resign());
        }
    }

    private static void onResyncRequest(RequestBoardStatePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MatchService.onResyncRequest(player, payload.origin());
        }
    }

    private static void onResetBoard(ResetBoardPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            MatchService.onResetRequest(player, payload.origin());
        }
    }

    // ------------------------------------------------------------------ client-bound

    private static void onBoardState(BoardStatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            com.khomo.chinesechess.client.ClientPayloadRouter.onBoardState(payload);
        }
    }

    private static void onMatchFinished(MatchFinishedPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            com.khomo.chinesechess.client.ClientPayloadRouter.onMatchFinished(payload);
        }
    }

    private static void onCloseBoard(CloseBoardPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            com.khomo.chinesechess.client.ClientPayloadRouter.onCloseBoard(payload);
        }
    }
}
