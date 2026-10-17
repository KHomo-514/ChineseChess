package com.khomo.chinesechess.match;

import com.khomo.chinesechess.Config;
import com.khomo.chinesechess.board.EndReason;
import com.khomo.chinesechess.board.Side;
import com.khomo.chinesechess.block.BoardPart;
import com.khomo.chinesechess.block.BoardStructure;
import com.khomo.chinesechess.blockentity.BoardPartBlockEntity;
import com.khomo.chinesechess.blockentity.BoardRenderState;
import com.khomo.chinesechess.entity.BoardPieces;
import com.khomo.chinesechess.network.BoardStatePayload;
import com.khomo.chinesechess.network.CloseBoardPayload;
import com.khomo.chinesechess.network.MatchFinishedPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Everything that travels from the server towards the players about a board.
 *
 * <p>Two channels have to stay in step and they carry different audiences:</p>
 * <ul>
 *   <li>the <em>world render</em>, which is pushed through the four block entities' own update
 *       packets so that every player tracking the chunk sees the board - including people who never
 *       opened it;</li>
 *   <li>the <em>GUI</em>, which is pushed to the seated players and the spectators only.</li>
 * </ul>
 *
 * <p>Both are built from the same {@link XiangqiMatch#renderState()} and {@link MatchSnapshot}
 * derivation, so the picture on the ground and the picture in the window can never disagree.</p>
 *
 * <p>Single responsibility: publish a match to clients.</p>
 */
public final class BoardSync {

    private BoardSync() {}

    // ------------------------------------------------------------------ world render

    /** Replicates the current position onto all four plates and tells clients to re-read them. */
    public static void pushRender(ServerLevel level, XiangqiMatch match) {
        pushRender(level, match.renderState(), match.origin(), match.facing());
    }

    /** Resets a board that has no match on it back to the opening array. */
    public static void pushIdleRender(ServerLevel level, BlockPos origin, Direction facing) {
        pushRender(level, BoardRenderState.idle(), origin, facing);
    }

    private static void pushRender(ServerLevel level, BoardRenderState state, BlockPos origin, Direction facing) {
        // The pieces standing on the board are entities now, so they are reconciled here - at the one
        // point every state change passes through - rather than being drawn from the block entity.
        BoardPieces.refresh(level, origin, facing, state);
        for (BoardPart part : BoardPart.ALL) {
            BlockPos pos = BoardStructure.positionOf(origin, facing, part);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof BoardPartBlockEntity board) {
                board.applyRenderState(state);
                BlockState blockState = level.getBlockState(pos);
                level.sendBlockUpdated(pos, blockState, blockState, Block.UPDATE_CLIENTS);
            }
        }
    }

    // ------------------------------------------------------------------ GUI

    /**
     * Whether a player can actually be sent to.
     *
     * <p>Guards every outbound packet: a seat can be held by a player whose connection is already
     * gone (mid-disconnect) or by a non-networked stand-in, and a packet sent into a dead or absent
     * channel throws rather than being ignored.</p>
     */
    private static boolean reachable(ServerPlayer player) {
        return player.connection != null
                && player.connection.getConnection() != null
                && player.connection.getConnection().isConnected();
    }

    /** Opens (or re-opens) the board screen for one player, seated or watching. */
    public static void openScreen(ServerPlayer player, XiangqiMatch match) {
        sendState(player, match, match.sideOf(player.getUUID()), true);
    }

    /** Refreshes the screen of everybody involved in the match. */
    public static void updateScreens(ServerLevel level, XiangqiMatch match) {
        for (UUID viewer : match.audience()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(viewer);
            if (player != null && reachable(player)) {
                sendState(player, match, match.sideOf(viewer), false);
            }
        }
    }

    /** Sends the authoritative snapshot to a single player, used both for updates and for repairs. */
    public static void sendSnapshot(ServerPlayer player, XiangqiMatch match) {
        sendState(player, match, match.sideOf(player.getUUID()), false);
    }

    private static void sendState(ServerPlayer player, XiangqiMatch match, Side viewerSide, boolean open) {
        if (!reachable(player)) {
            return;
        }
        MatchSnapshot snapshot = MatchSnapshot.of(match, match.settings().noCaptureDrawPlies());
        PacketDistributor.sendToPlayer(player, new BoardStatePayload(snapshot, viewerSide, open));
    }

    /** Announces the verdict; the board keeps showing the final position until the auto-close fires. */
    public static void notifyFinished(ServerLevel level, XiangqiMatch match, int closeAfterTicks) {
        EndReason reason = match.finalOutcome() == null ? null : match.finalOutcome().reason();
        MatchFinishedPayload payload = new MatchFinishedPayload(
                match.origin(), match.game().outcome().status(), reason, closeAfterTicks);
        for (UUID viewer : match.audience()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(viewer);
            if (player != null && reachable(player)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        updateScreens(level, match);
    }

    /** Closes the screen for everybody watching a match, used when a match is dissolved. */
    public static void closeScreens(ServerLevel level, XiangqiMatch match, String messageKey) {
        CloseBoardPayload payload = new CloseBoardPayload(match.origin(), messageKey);
        for (UUID viewer : match.audience()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(viewer);
            if (player != null && reachable(player)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void closeScreen(ServerPlayer player, BlockPos origin, String messageKey) {
        if (reachable(player)) {
            PacketDistributor.sendToPlayer(player, new CloseBoardPayload(origin, messageKey));
        }
    }

    /** The auto-close delay currently configured, in ticks. */
    public static int autoCloseTicks() {
        return Config.autoCloseDelayTicks();
    }
}
