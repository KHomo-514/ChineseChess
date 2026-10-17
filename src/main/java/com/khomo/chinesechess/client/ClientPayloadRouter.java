package com.khomo.chinesechess.client;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.client.screen.XiangqiBoardScreen;
import com.khomo.chinesechess.match.MatchSnapshot;
import com.khomo.chinesechess.network.BoardStatePayload;
import com.khomo.chinesechess.network.CloseBoardPayload;
import com.khomo.chinesechess.network.MatchFinishedPayload;

import net.minecraft.client.Minecraft;

/**
 * Turns packets from the server into client-side effects.
 *
 * <p>Kept apart from exactly one thing: the physical-side test. {@code ModPayloads} owns registration
 * for both sides and forwards to this class only when the game is running on a client, so no
 * dedicated-server code path ever loads a client class.</p>
 *
 * <p>Every entry point starts with validation. A snapshot that does not describe a usable position is
 * dropped and answered with a resynchronisation request; a packet for a board the player is not
 * looking at is ignored. The client therefore never renders a board it cannot trust, and it never
 * has to guess what the server meant.</p>
 *
 * <p>Single responsibility: apply server packets to the client.</p>
 */
public final class ClientPayloadRouter {

    private ClientPayloadRouter() {}

    /** A board snapshot, either opening the window or refreshing it. */
    public static void onBoardState(BoardStatePayload payload) {
        MatchSnapshot snapshot = payload.snapshot();
        ClientMatchSession session = ClientMatchSession.instance();

        if (!snapshot.isConsistent()) {
            ChineseChess.LOGGER.warn("Dropped an unusable board snapshot for {}", snapshot.origin());
            session.requestResync(snapshot.origin());
            return;
        }

        session.setViewerSide(payload.viewerSide());
        session.apply(snapshot);

        if (!payload.open() || !session.isOpenFor(snapshot.origin())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof XiangqiBoardScreen screen
                && screen.boardOrigin().equals(snapshot.origin())) {
            return;
        }
        minecraft.setScreen(new XiangqiBoardScreen(snapshot.origin()));
    }

    /** The verdict; the board stays on screen until the server closes it. */
    public static void onMatchFinished(MatchFinishedPayload payload) {
        XiangqiBoardScreen screen = currentBoardScreen(payload.origin());
        if (screen != null) {
            screen.showResult(payload);
        }
    }

    /** The server is taking the board away; close the window without answering. */
    public static void onCloseBoard(CloseBoardPayload payload) {
        XiangqiBoardScreen screen = currentBoardScreen(payload.origin());
        if (screen != null) {
            screen.closeFromServer(payload.messageKey());
        }
    }

    /** @return the board window currently showing {@code origin}, or {@code null}. */
    private static XiangqiBoardScreen currentBoardScreen(net.minecraft.core.BlockPos origin) {
        if (Minecraft.getInstance().screen instanceof XiangqiBoardScreen screen
                && screen.boardOrigin().equals(origin)) {
            return screen;
        }
        return null;
    }
}
