package com.khomo.chinesechess.blockentity;

import com.khomo.chinesechess.board.Board;
import com.khomo.chinesechess.board.Move;
import com.khomo.chinesechess.board.Side;

import net.minecraft.nbt.CompoundTag;

/**
 * The part of a match that the in-world board needs in order to draw itself.
 *
 * <p>It is deliberately tiny - one byte per point plus a handful of flags - because it is shipped in
 * a block entity update packet to every player tracking the chunk, including players who are not
 * taking part in the game.</p>
 *
 * <p>{@link #revision()} is the consistency anchor: the server bumps it on every change, so a client
 * (or a freshly loaded chunk) can tell a stale picture from a current one and ask for a repair
 * without comparing anything expensive.</p>
 *
 * <p>Single responsibility: be the render-facing snapshot of a board.</p>
 */
public record BoardRenderState(
        byte[] codes,
        int lastFrom,
        int lastTo,
        Side checkedSide,
        Side sideToMove,
        boolean active,
        int revision) {

    /** A board with no match on it: the pieces sit in the standard opening position. */
    public static BoardRenderState idle() {
        return new BoardRenderState(Board.initial().toCodes(), -1, -1, null, Side.RED, false, 0);
    }

    public static BoardRenderState of(Board board, Move lastMove, Side checkedSide, Side sideToMove,
            boolean active, int revision) {
        return new BoardRenderState(
                board.toCodes(),
                lastMove == null ? -1 : lastMove.from(),
                lastMove == null ? -1 : lastMove.to(),
                checkedSide,
                sideToMove,
                active,
                revision);
    }

    /** Whether a piece can be picked up at all; a board without a running game is read-only. */
    public boolean playable() {
        return active;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("squares", codes);
        tag.putInt("from", lastFrom);
        tag.putInt("to", lastTo);
        tag.putByte("checked", checkedSide == null ? (byte) -1 : (byte) checkedSide.ordinal());
        tag.putByte("turn", (byte) sideToMove.ordinal());
        tag.putBoolean("active", active);
        tag.putInt("revision", revision);
        return tag;
    }

    public static BoardRenderState load(CompoundTag tag) {
        byte[] codes = tag.getByteArray("squares");
        if (codes.length != com.khomo.chinesechess.board.Squares.COUNT) {
            return idle();
        }
        byte checked = tag.getByte("checked");
        return new BoardRenderState(
                codes,
                tag.getInt("from"),
                tag.getInt("to"),
                checked < 0 ? null : Side.of(checked),
                Side.of(tag.getByte("turn")),
                tag.getBoolean("active"),
                tag.getInt("revision"));
    }
}
