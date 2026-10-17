package com.khomo.chinesechess.registry;

import com.khomo.chinesechess.ChineseChess;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's creative tab.
 *
 * <p>Single responsibility: declare and register the creative tab.</p>
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ChineseChess.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.chinese_chess"))
                    .icon(() -> new ItemStack(ModItems.XIANGQI_BOARD.get()))
                    .displayItems((parameters, output) -> output.accept(ModItems.XIANGQI_BOARD.get()))
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
