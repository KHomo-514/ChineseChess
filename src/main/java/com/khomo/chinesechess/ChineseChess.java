package com.khomo.chinesechess;

import com.khomo.chinesechess.network.ModPayloads;
import com.khomo.chinesechess.registry.ModBlockEntities;
import com.khomo.chinesechess.registry.ModBlocks;
import com.khomo.chinesechess.registry.ModCreativeTabs;
import com.khomo.chinesechess.registry.ModEntities;
import com.khomo.chinesechess.registry.ModItems;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import org.slf4j.Logger;

/**
 * The mod entry point.
 *
 * <p>It does exactly two things: hand each registry and listener class to the mod event bus, and
 * register the two configuration files. Everything the mod does lives in a class that names itself;
 * keeping this one free of behaviour is what lets the rest of the mod be reasoned about without
 * wondering where the load order matters.</p>
 *
 * <p>Single responsibility: wire the mod together.</p>
 */
@Mod(ChineseChess.MODID)
public class ChineseChess {

    /** Registry namespace of every block, item and packet in the mod. */
    public static final String MODID = "chinese_chess";

    public static final Logger LOGGER = LogUtils.getLogger();

    public ChineseChess(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModEntities.register(modBus);
        ModCreativeTabs.register(modBus);

        modBus.addListener(ModPayloads::register);

        container.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }
}
