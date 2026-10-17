package com.khomo.chinesechess.registry;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.blockentity.BoardPartBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's block entity types.
 *
 * <p>Single responsibility: declare and register block entity types.</p>
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChineseChess.MODID);

    /** One type shared by all four plates of a board. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoardPartBlockEntity>> XIANGQI_BOARD =
            BLOCK_ENTITIES.register("xiangqi_board", ModBlockEntities::createBoardType);

    private ModBlockEntities() {}

    /**
     * Built through a method rather than inline so that the block entity can name its own type: the
     * reference is only resolved when a plate is actually created, long after registration.
     */
    private static BlockEntityType<BoardPartBlockEntity> createBoardType() {
        return BlockEntityType.Builder
                .of((pos, state) -> new BoardPartBlockEntity(XIANGQI_BOARD.get(), pos, state),
                        ModBlocks.XIANGQI_BOARD.get())
                .build(null);
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
