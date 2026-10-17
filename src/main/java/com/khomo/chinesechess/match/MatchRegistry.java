package com.khomo.chinesechess.match;

import com.khomo.chinesechess.Config;
import com.khomo.chinesechess.board.LegalMoveCache;
import com.khomo.chinesechess.board.RuleSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Every board in one dimension, persisted with the world.
 *
 * <p>Storing matches in {@link SavedData} rather than in the block entities is what makes a game
 * survive a chunk unload, a server restart or a player walking a thousand blocks away: the board's
 * four plates can be unloaded and reloaded freely because none of them owns the game - they only
 * point at the origin, and the origin is just a key into this table.</p>
 *
 * <p>The registry also owns the dimension's {@link LegalMoveCache}, so the cache lives exactly as
 * long as the level it belongs to and is swept with it.</p>
 *
 * <p>Single responsibility: store and load matches.</p>
 */
public final class MatchRegistry extends SavedData {

    public static final String DATA_NAME = "chinese_chess_matches";

    private final Map<BlockPos, XiangqiMatch> matches = new HashMap<>();
    private final LegalMoveCache moveCache = new LegalMoveCache();

    public MatchRegistry() {}

    public static MatchRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MatchRegistry::new, MatchRegistry::load), DATA_NAME);
    }

    /** @return the match on the board whose origin plate is {@code origin}, or {@code null}. */
    @Nullable
    public XiangqiMatch find(BlockPos origin) {
        return matches.get(origin);
    }

    /** @return the match {@code player} currently holds a seat in, or {@code null}. */
    @Nullable
    public XiangqiMatch findByPlayer(UUID player) {
        for (XiangqiMatch match : matches.values()) {
            if (match.isParticipant(player)) {
                return match;
            }
        }
        return null;
    }

    /**
     * Returns the match for {@code origin}, creating it if this board has never been used.
     *
     * <p>Creation is refused once {@link Config#maxMatchesPerWorld()} boards exist, so a griefed
     * world cannot grow the table without bound.</p>
     */
    @Nullable
    public XiangqiMatch findOrCreate(BlockPos origin, net.minecraft.core.Direction facing, RuleSettings settings) {
        XiangqiMatch existing = matches.get(origin);
        if (existing != null) {
            return existing;
        }
        int limit = Config.maxMatchesPerWorld();
        if (limit > 0 && matches.size() >= limit) {
            return null;
        }
        XiangqiMatch created = new XiangqiMatch(origin, facing, settings, moveCache);
        matches.put(origin, created);
        setDirty();
        return created;
    }

    public void put(XiangqiMatch match) {
        matches.put(match.origin(), match);
        setDirty();
    }

    public void remove(BlockPos origin) {
        if (matches.remove(origin) != null) {
            setDirty();
        }
    }

    public Collection<XiangqiMatch> all() {
        return Set.copyOf(matches.values());
    }

    public int size() {
        return matches.size();
    }

    public LegalMoveCache moveCache() {
        return moveCache;
    }

    /** Replaces the whole table, used by the validator when a board turns out to be broken. */
    public Set<BlockPos> origins() {
        return new HashSet<>(matches.keySet());
    }

    // ------------------------------------------------------------------ persistence

    public static MatchRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        MatchRegistry registry = new MatchRegistry();
        registry.read(tag);
        return registry;
    }

    private void read(CompoundTag tag) {
        RuleSettings settings = Config.ruleSettingsOrFallback();
        ListTag list = tag.getList("matches", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            try {
                XiangqiMatch match = XiangqiMatch.load(entry, settings, moveCache);
                matches.put(match.origin(), match);
            } catch (RuntimeException exception) {
                // A single damaged match must never prevent the rest of the world from loading.
                com.khomo.chinesechess.ChineseChess.LOGGER.warn(
                        "Discarding unreadable Chinese chess match entry {}", entry, exception);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (XiangqiMatch match : matches.values()) {
            list.add(match.save());
        }
        tag.put("matches", list);
        return tag;
    }
}
