package com.khomo.chinesechess.event;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.i18n.Translations;
import com.khomo.chinesechess.match.MatchService;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Keeps a board from being wrecked while a game depends on it.
 *
 * <p>A cancelled break is the only safe answer: destroying a plate would dissolve the running match
 * and force both players out, which is exactly the griefing a competitive board invites.</p>
 *
 * <p>Single responsibility: protect boards that are in use.</p>
 */
@EventBusSubscriber(modid = ChineseChess.MODID)
public final class BoardProtectionEvents {

    private BoardProtectionEvents() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!MatchService.isBoardProtected(level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer player) {
            player.displayClientMessage(Translations.message("board.protected"), true);
        }
    }
}
