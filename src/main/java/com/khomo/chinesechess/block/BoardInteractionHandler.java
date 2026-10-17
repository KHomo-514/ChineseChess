package com.khomo.chinesechess.block;

import com.khomo.chinesechess.match.MatchService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The single place where "a player right-clicked a board" turns into a game action.
 *
 * <p>It exists so that the block class holds no opinion about games at all: the block reports the
 * interaction, this class decides what it means, and {@link MatchService} performs it. The client
 * half is deliberately trivial - the client only has to claim the click so that vanilla does not try
 * to place a held block on top of the board, and let the server, which owns the game, answer with the
 * board screen.</p>
 *
 * <p>Single responsibility: interpret an interaction with a board.</p>
 */
public final class BoardInteractionHandler {

    private BoardInteractionHandler() {}

    /**
     * @return {@code true} when the interaction belongs to the board and vanilla should stop looking
     *         for something else to do with it
     */
    public static boolean onUse(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            // Client prediction: claim the click so the held item is not used, and swing the arm.
            return true;
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && player instanceof ServerPlayer serverPlayer) {
            MatchService.onBoardUsed(serverPlayer, pos);
        }
        return true;
    }
}
