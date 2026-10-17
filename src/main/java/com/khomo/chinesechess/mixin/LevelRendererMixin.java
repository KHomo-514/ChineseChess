package com.khomo.chinesechess.mixin;

import com.khomo.chinesechess.blockentity.BoardPartBlockEntity;
import com.khomo.chinesechess.client.render.BoardPartRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Restores distance culling for block entities that asked to be rendered off-screen.
 *
 * <p><strong>Why this exists.</strong> A Chinese chess board is a 2x2 multiblock, so its picture is
 * drawn from a single plate - otherwise a quarter of the board would vanish whenever one of the four
 * chunk sections was culled on its own. Drawing everything from one plate means that plate has to
 * survive section culling, and the supported way to do that is
 * {@code BlockEntityRenderer#shouldRenderOffScreen()}, which moves the block entity into
 * {@code LevelRenderer}'s global pass.</p>
 *
 * <p>That pass is deliberately conservative: it only tests the render bounding box against the
 * frustum. Unlike the per-section pass it never asks the renderer
 * {@link BlockEntityRenderer#shouldRender}, so {@code getViewDistance()} is ignored and every board
 * in view would be redrawn at any distance, however many of them a server has placed.</p>
 *
 * <p>The patch is one condition inserted around NeoForge's own visibility hook, which is the
 * documented extension point for this decision, and it is written with MixinExtras'
 * {@code @WrapOperation} so that another mod wrapping the same call composes with it instead of
 * conflicting. It is a pure performance fix: if the mixin is ever removed, boards still draw
 * correctly, they are merely no longer distance-culled.</p>
 *
 * <p>Single responsibility: apply the missing distance test to the global block entity pass.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @WrapOperation(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/ClientHooks;"
                            + "isBlockEntityRendererVisible(Lnet/minecraft/client/renderer/blockentity/"
                            + "BlockEntityRenderDispatcher;Lnet/minecraft/world/level/block/entity/BlockEntity;"
                            + "Lnet/minecraft/client/renderer/culling/Frustum;)Z"))
    private boolean chineseChess$applyViewDistance(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity,
            Frustum frustum, Operation<Boolean> original) {
        if (blockEntity instanceof BoardPartBlockEntity part) {
            BlockEntityRenderer<?> renderer = dispatcher.getRenderer(part);
            if (renderer instanceof BoardPartRenderer boardRenderer
                    && !boardRenderer.shouldRender(part, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())) {
                return false;
            }
        }
        return original.call(dispatcher, blockEntity, frustum);
    }
}
