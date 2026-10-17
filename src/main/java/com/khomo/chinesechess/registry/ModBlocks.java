package com.khomo.chinesechess.registry;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.block.XiangqiBoardBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's blocks.
 *
 * <p>Single responsibility: declare and register blocks and nothing else - behaviour lives in the
 * block classes, placement rules in {@code BoardStructure}.</p>
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ChineseChess.MODID);

    /**
     * The board plate.
     *
     * <p>Wooden to the touch, two pixels tall, not occluding light (so a board on a glass roof does
     * not blacken the room) and immovable by pistons - a board that a piston could tear in half would
     * break the multiblock invariant.</p>
     */
    public static final DeferredBlock<XiangqiBoardBlock> XIANGQI_BOARD = BLOCKS.registerBlock(
            "xiangqi_board",
            XiangqiBoardBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.6F, 6.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false));

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
