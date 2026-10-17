package com.khomo.chinesechess.match;

import com.khomo.chinesechess.block.BoardStructure;
import com.khomo.chinesechess.board.EndReason;
import com.khomo.chinesechess.board.GameOutcome;
import com.khomo.chinesechess.board.LegalMoveCache;
import com.khomo.chinesechess.board.Move;
import com.khomo.chinesechess.board.MoveAttempt;
import com.khomo.chinesechess.board.RuleSettings;
import com.khomo.chinesechess.board.Side;
import com.khomo.chinesechess.board.XiangqiGame;
import com.khomo.chinesechess.blockentity.BoardRenderState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One board's game: the two seats, the running {@link XiangqiGame}, and the bookkeeping the server
 * needs to keep clients honest.
 *
 * <p>The class is a state machine and nothing else. Whether a departure ends a game, when a board is
 * cleared, pushing packets and reading configuration all live in {@link MatchService} and
 * {@link BoardSync}; this class only records what happened. That split keeps the persistence format
 * stable and independent of the server's policy switches.</p>
 *
 * <p>Single responsibility: own the state of one match.</p>
 */
public final class XiangqiMatch {

    private final BlockPos origin;
    private final Direction facing;
    private final RuleSettings settings;
    private final LegalMoveCache cache;

    private UUID redPlayer;
    private UUID blackPlayer;
    private final Set<UUID> viewers = new LinkedHashSet<>();

    private MatchPhase phase = MatchPhase.WAITING;
    private XiangqiGame game;
    private GameOutcome finalOutcome;
    private int revision;
    private long lastActivityTick;
    /**
     * When something last happened here, on the wall clock rather than the world clock.
     *
     * <p>Abandonment is a real-world thing, and the world clock cannot measure it: a paused
     * single-player or LAN host stops advancing {@code getGameTime()} entirely, so a board could sit
     * untouched for an hour and its tick-based age would not move at all. Both clocks are therefore
     * kept - ticks for the game, real time for the idle sweep.</p>
     */
    private long lastActivityMillis;
    private long finishedTick;

    public XiangqiMatch(BlockPos origin, Direction facing, RuleSettings settings, LegalMoveCache cache) {
        this.origin = origin;
        this.facing = BoardStructure.horizontal(facing);
        this.settings = settings;
        this.cache = cache;
        this.game = new XiangqiGame(settings, cache);
    }

    // ------------------------------------------------------------------ identity

    public BlockPos origin() {
        return origin;
    }

    public Direction facing() {
        return facing;
    }

    public MatchPhase phase() {
        return phase;
    }

    public XiangqiGame game() {
        return game;
    }

    public RuleSettings settings() {
        return settings;
    }

    /**
     * Monotonic counter of everything a client can see. Clients compare it to detect that they are
     * displaying a stale board.
     */
    public int revision() {
        return revision;
    }

    public long lastActivityTick() {
        return lastActivityTick;
    }

    /** When this board was last touched, in milliseconds since the epoch. */
    public long lastActivityMillis() {
        return lastActivityMillis;
    }

    public long finishedTick() {
        return finishedTick;
    }

    public GameOutcome finalOutcome() {
        return finalOutcome;
    }

    // ------------------------------------------------------------------ seats

    /** @return the seat held by {@code player}, or {@code null} for spectators and strangers. */
    public Side sideOf(UUID player) {
        if (player == null) {
            return null;
        }
        if (player.equals(redPlayer)) {
            return Side.RED;
        }
        if (player.equals(blackPlayer)) {
            return Side.BLACK;
        }
        return null;
    }

    public UUID playerOf(Side side) {
        return side.isRed() ? redPlayer : blackPlayer;
    }

    public boolean isParticipant(UUID player) {
        return sideOf(player) != null;
    }

    public boolean hasParticipants() {
        return redPlayer != null || blackPlayer != null;
    }

    public boolean seatsFull() {
        return redPlayer != null && blackPlayer != null;
    }

    public boolean isViewer(UUID player) {
        return viewers.contains(player);
    }

    public Collection<UUID> viewers() {
        return Set.copyOf(viewers);
    }

    /** Everybody who should receive board updates: both players plus anyone watching. */
    public Set<UUID> audience() {
        Set<UUID> audience = new LinkedHashSet<>(viewers);
        if (redPlayer != null) {
            audience.add(redPlayer);
        }
        if (blackPlayer != null) {
            audience.add(blackPlayer);
        }
        return audience;
    }

    public void addViewer(UUID player) {
        if (viewers.add(player)) {
            revision++;
        }
    }

    public void removeViewer(UUID player) {
        viewers.remove(player);
    }

    public void clearViewers() {
        viewers.clear();
    }

    // ------------------------------------------------------------------ transitions

    /**
     * Seats {@code player}, or reports that they are watching.
     *
     * <p>A finished match is reused rather than kept in limbo: the first player to arrive after the
     * result has been shown clears the board and takes the red seat.</p>
     */
    public JoinOutcome join(UUID player, long now, boolean allowSpectators) {
        markActivity(now);

        if (phase == MatchPhase.FINISHED && !isParticipant(player)) {
            reset(now);
        }
        if (player.equals(redPlayer)) {
            return JoinOutcome.RESUMED;
        }
        if (player.equals(blackPlayer)) {
            return JoinOutcome.RESUMED;
        }
        if (phase == MatchPhase.FINISHED) {
            return JoinOutcome.RESUMED;
        }

        if (redPlayer == null) {
            redPlayer = player;
            viewers.remove(player);
            seatsTook(now);
            return JoinOutcome.SEATED_RED;
        }
        if (blackPlayer == null) {
            blackPlayer = player;
            viewers.remove(player);
            seatsTook(now);
            return JoinOutcome.SEATED_BLACK;
        }
        if (!allowSpectators) {
            return JoinOutcome.DISALLOWED;
        }
        addViewer(player);
        return JoinOutcome.SPECTATING;
    }

    /**
     * Applies the consequence of a seat being filled.
     *
     * <p>A full pair of seats starts a fresh game only when there is nothing on the board. If the
     * position is past the opening array, this pair is picking up the game an earlier player walked
     * away from, and it continues from there - which is the whole point of a board that is never
     * cleared merely because somebody pressed escape.</p>
     */
    private void seatsTook(long now) {
        revision++;
        markActivity(now);
        if (!seatsFull() || phase != MatchPhase.WAITING) {
            return;
        }
        if (game.plyCount() > 0) {
            // Resume: the clock restarts, the position, the history and the side to move all stay.
            phase = MatchPhase.PLAYING;
        } else {
            startGame(now);
        }
        revision++;
    }

    /** Gives up a seat without deciding the game; the caller has already handled the consequences. */
    public Side vacate(UUID player) {
        Side side = sideOf(player);
        if (side == null) {
            return null;
        }
        if (side.isRed()) {
            redPlayer = null;
        } else {
            blackPlayer = null;
        }
        revision++;
        return side;
    }

    /**
     * Stops the clock without clearing the board, used when a player walks away from a running game.
     *
     * <p>The phase returns to {@code WAITING} while the position stays exactly as it was. The next pair
     * to sit down resumes this very game, and a client needs no new state to understand any of it,
     * because "waiting for an opponent" is precisely what this is.</p>
     */
    public void suspend(long now) {
        if (phase == MatchPhase.PLAYING) {
            this.phase = MatchPhase.WAITING;
            markActivity(now);
            this.revision++;
        }
    }

    public void startGame(long now) {
        this.game = new XiangqiGame(settings, cache);
        this.phase = MatchPhase.PLAYING;
        this.finalOutcome = null;
        this.finishedTick = 0L;
        markActivity(now);
        this.revision++;
    }

    /** Empties the board, resets the seats and waits for two fresh players. */
    public void reset(long now) {
        this.game = new XiangqiGame(settings, cache);
        this.phase = MatchPhase.WAITING;
        this.redPlayer = null;
        this.blackPlayer = null;
        this.viewers.clear();
        this.finalOutcome = null;
        this.finishedTick = 0L;
        markActivity(now);
        this.revision++;
    }

    /**
     * Clears the position back to the opening array but keeps whoever is sitting here.
     *
     * <p>This is what the reset button does, so it is a deliberate act by a player rather than
     * housekeeping: unlike {@link #reset}, both seats survive, and a full pair simply carries on - a
     * rematch without anybody having to sit down again.</p>
     */
    public void restart(long now) {
        this.game = new XiangqiGame(settings, cache);
        this.finalOutcome = null;
        this.finishedTick = 0L;
        markActivity(now);
        this.phase = seatsFull() ? MatchPhase.PLAYING : MatchPhase.WAITING;
        this.revision++;
    }

    public MoveAttempt tryMove(Side side, Move move, long now) {
        if (phase != MatchPhase.PLAYING) {
            return MoveAttempt.GAME_OVER;
        }
        if (game.sideToMove() != side) {
            return MoveAttempt.NOT_YOUR_TURN;
        }
        MoveAttempt attempt = game.tryMove(move);
        markActivity(now);
        if (attempt.accepted()) {
            revision++;
            if (game.outcome().isFinished()) {
                markFinished(now);
            }
        }
        return attempt;
    }

    public void resign(Side side, long now) {
        if (phase == MatchPhase.PLAYING) {
            game.resign(side);
            markFinished(now);
        }
    }

    /** Ends the match without a winner, for example because the board was destroyed. */
    public void dissolve(EndReason reason, long now) {
        if (phase != MatchPhase.FINISHED) {
            game.terminate(GameOutcome.draw(reason));
            markFinished(now);
        }
    }

    private void markFinished(long now) {
        this.phase = MatchPhase.FINISHED;
        this.finalOutcome = game.outcome();
        this.finishedTick = now;
        markActivity(now);
        this.revision++;
    }

    public void touch(long now) {
        markActivity(now);
    }

    /**
     * Records that something happened, on both clocks.
     *
     * <p>Every transition that counts as activity goes through here, so the world-clock and the
     * wall-clock stamps can never drift apart.</p>
     */
    private void markActivity(long now) {
        this.lastActivityTick = now;
        this.lastActivityMillis = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ rendering

    /** The snapshot the in-world board draws. */
    public BoardRenderState renderState() {
        if (phase == MatchPhase.WAITING) {
            // Before the game starts the board simply shows the standard opening array.
            return new BoardRenderState(
                    game.board().toCodes(), -1, -1, null, Side.RED, false, revision);
        }
        return BoardRenderState.of(
                game.board(),
                game.lastMove(),
                game.checkedSide(),
                game.sideToMove(),
                phase == MatchPhase.PLAYING,
                revision);
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("origin", origin.asLong());
        tag.putByte("facing", (byte) facing.get3DDataValue());
        if (redPlayer != null) {
            tag.putUUID("red", redPlayer);
        }
        if (blackPlayer != null) {
            tag.putUUID("black", blackPlayer);
        }
        tag.putByte("phase", (byte) phase.ordinal());
        tag.putInt("revision", revision);
        tag.putLong("activity", lastActivityTick);
        tag.putLong("activityMillis", lastActivityMillis);
        tag.putLong("finished", finishedTick);
        tag.put("game", saveGame());
        return tag;
    }

    private CompoundTag saveGame() {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("squares", game.board().toCodes());
        tag.putByte("turn", (byte) game.sideToMove().ordinal());
        Move last = game.lastMove();
        tag.putInt("from", last == null ? -1 : last.from());
        tag.putInt("to", last == null ? -1 : last.to());
        tag.putInt("plies", game.plyCount());
        tag.putInt("quiet", game.pliesSinceCapture());
        tag.put("history", new LongArrayTag(game.historyKeys()));
        tag.put("checks", new ByteArrayTag(game.historyCheckedSides()));
        return tag;
    }

    /**
     * Rebuilds a match from disk, repairing anything that does not add up.
     *
     * <p>The game is always re-derived from the stored position: outcomes, check state and legal
     * moves are recomputed rather than trusted, so an old save can never lock a board into a wrong
     * verdict.</p>
     */
    public static XiangqiMatch load(CompoundTag tag, RuleSettings settings, LegalMoveCache cache) {
        BlockPos origin = BlockPos.of(tag.getLong("origin"));
        Direction facing = BoardStructure.horizontal(Direction.from3DDataValue(tag.getByte("facing")));
        XiangqiMatch match = new XiangqiMatch(origin, facing, settings, cache);
        if (tag.hasUUID("red")) {
            match.redPlayer = tag.getUUID("red");
        }
        if (tag.hasUUID("black")) {
            match.blackPlayer = tag.getUUID("black");
        }
        match.phase = MatchPhase.of(tag.getByte("phase"));
        match.revision = tag.getInt("revision");
        match.lastActivityTick = tag.getLong("activity");
        // A save from before the wall clock existed (or a hand-edited one) gets a fresh stamp, so a
        // board that has just been loaded is never cleared the instant it appears.
        match.lastActivityMillis = tag.contains("activityMillis")
                ? tag.getLong("activityMillis")
                : System.currentTimeMillis();
        match.finishedTick = tag.getLong("finished");

        CompoundTag gameTag = tag.getCompound("game");
        byte[] codes = gameTag.getByteArray("squares");
        if (!com.khomo.chinesechess.board.Board.isPlausible(codes)) {
            codes = com.khomo.chinesechess.board.Board.initial().toCodes();
        }
        long[] keys = null;
        byte[] checks = null;
        if (gameTag.contains("history", Tag.TAG_LONG_ARRAY)) {
            keys = gameTag.getLongArray("history");
        }
        if (gameTag.contains("checks", Tag.TAG_BYTE_ARRAY)) {
            checks = gameTag.getByteArray("checks");
        }
        int from = gameTag.getInt("from");
        int to = gameTag.getInt("to");
        Move lastMove = from >= 0 && to >= 0 ? new Move(from, to) : null;
        match.game = new XiangqiGame(
                settings,
                cache,
                codes,
                Side.of(gameTag.getByte("turn")),
                lastMove,
                gameTag.getInt("plies"),
                gameTag.getInt("quiet"),
                keys,
                checks);
        match.finalOutcome = match.game.outcome().isFinished() ? match.game.outcome() : null;
        if (match.finalOutcome != null) {
            match.phase = MatchPhase.FINISHED;
        }
        return match;
    }
}
