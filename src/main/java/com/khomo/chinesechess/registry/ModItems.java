package com.khomo.chinesechess.registry;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.item.XiangqiBoardItem;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's items.
 *
 * <p>Single responsibility: declare and register items.</p>
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChineseChess.MODID);

    /** The board, as an item in the inventory. */
    public static final DeferredItem<XiangqiBoardItem> XIANGQI_BOARD = ITEMS.registerItem(
            "xiangqi_board",
            properties -> new XiangqiBoardItem(ModBlocks.XIANGQI_BOARD.get(), properties),
            new Item.Properties());

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
