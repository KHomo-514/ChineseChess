package com.khomo.chinesechess.registry;

import com.khomo.chinesechess.ChineseChess;
import com.khomo.chinesechess.entity.XiangqiPieceEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's entity types.
 *
 * <p>The piece type is registered as a misc entity with a hit box smaller than one board cell, no
 * saving and no summoning: it is created only by the board that owns it, and rebuilt from the match
 * state rather than loaded from a chunk.</p>
 *
 * <p>Single responsibility: declare and register entity types.</p>
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ChineseChess.MODID);

    /** Every playing piece on every board, in the world and on the move. */
    public static final DeferredHolder<EntityType<?>, EntityType<XiangqiPieceEntity>> XIANGQI_PIECE =
            ENTITY_TYPES.register("xiangqi_piece", ModEntities::createPieceType);

    private ModEntities() {}

    private static EntityType<XiangqiPieceEntity> createPieceType() {
        return EntityType.Builder.<XiangqiPieceEntity>of(XiangqiPieceEntity::new, MobCategory.MISC)
                // A little smaller than one cell of the board (a cell is 0.25 blocks) and thin.
                .sized(0.17F, 0.06F)
                // Tracked for four chunks, which matches how far they are worth drawing.
                .clientTrackingRange(4)
                .updateInterval(20)
                .noSave()
                .noSummon()
                .build("xiangqi_piece");
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
