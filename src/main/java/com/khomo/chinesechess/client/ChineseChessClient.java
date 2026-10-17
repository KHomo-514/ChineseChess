package com.khomo.chinesechess.client;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.client.render.BoardPartRenderer;
import com.khomo.chinesechess.client.render.XiangqiPieceRenderer;
import com.khomo.chinesechess.registry.ModBlockEntities;
import com.khomo.chinesechess.registry.ModEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * The client entry point.
 *
 * <p>Nothing here is reachable on a dedicated server: the class is loaded only for the physical
 * client, and both of its jobs are client-only by nature - the board's renderer and the config
 * screen.</p>
 *
 * <p>Single responsibility: client-side setup.</p>
 */
@Mod(value = ChineseChess.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ChineseChess.MODID, value = Dist.CLIENT)
public class ChineseChessClient {

    public ChineseChessClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /** Registers the renderers that paint boards and their pieces in the world. */
    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.XIANGQI_BOARD.get(), BoardPartRenderer::new);
        event.registerEntityRenderer(ModEntities.XIANGQI_PIECE.get(), XiangqiPieceRenderer::new);
    }
}
