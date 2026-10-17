package com.khomo.chinesechess.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The placeable board.
 *
 * <p>Placement is inherited from {@link BlockItem} on purpose: the block itself supplies the 2x2
 * placement state and builds the rest of the structure in {@code setPlacedBy}, so the item only adds
 * the explanation a player needs - which saves duplicating the whole vanilla placement pipeline and
 * keeps client-side prediction identical to the server for free.</p>
 *
 * <p>Single responsibility: describe the board to the player.</p>
 */
public class XiangqiBoardItem extends BlockItem {

    public XiangqiBoardItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.chinese_chess.xiangqi_board.hint.place")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.chinese_chess.xiangqi_board.hint.play")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.chinese_chess.xiangqi_board.hint.leave")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
