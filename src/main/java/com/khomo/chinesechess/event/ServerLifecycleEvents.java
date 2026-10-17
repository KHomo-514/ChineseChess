package com.khomo.chinesechess.event;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.match.MatchSweeper;
import com.khomo.chinesechess.match.MatchService;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus listeners that keep the match table honest.
 *
 * <p>Single responsibility: react to server-wide life cycle moments.</p>
 */
@EventBusSubscriber(modid = ChineseChess.MODID)
public final class ServerLifecycleEvents {

    private ServerLifecycleEvents() {}

    /** Drives the periodic cleanup and integrity sweep. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MatchSweeper.tick(event.getServer());
    }

    /**
     * A player who disconnects has left their game.
     *
     * <p>Handled here rather than from the packet layer because a client that has been killed never
     * sends its leave packet, and a board stuck on a phantom player would be unplayable.</p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MatchService.onPlayerDisconnected(player);
        }
    }
}
