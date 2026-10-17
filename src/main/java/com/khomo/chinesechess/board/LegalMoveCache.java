package com.khomo.chinesechess.board;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bounded, self-cleaning cache of generated move lists.
 *
 * <p>Keyed by the position hash (placement plus side to move) so identical positions - even across
 * different matches - share one entry. Entries are evicted in least-recently-used order once
 * {@value #MAX_ENTRIES} are held, and {@link #sweep(long)} drops entries that have not been touched
 * for {@link #TIME_TO_LIVE_MILLIS}, which keeps long-running servers from accumulating dead
 * positions.</p>
 *
 * <p>Synchronised because the client may render while the server thread computes, and because the
 * cache is deliberately small and only touched a few times per ply, so the lock is not contended in
 * practice.</p>
 *
 * <p>Single responsibility: remember move lists.</p>
 */
public final class LegalMoveCache {

    /**
     * Upper bound on retained positions.
     *
     * <p>Every entry holds a whole move list - about a kilobyte - so this constant is the cache's
     * memory footprint. Ninety-six positions is still far more than a game reaches back for: the
     * current position is always a hit, and undo or a repetition check looks only a few plies back.</p>
     */
    public static final int MAX_ENTRIES = 96;
    /** Entries untouched for this long are dropped by {@link #sweep(long)}. */
    public static final long TIME_TO_LIVE_MILLIS = 5L * 60L * 1000L;

    private final Map<Long, Entry> entries = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    /** @return the cached move list for {@code key}, or {@code null} when unknown or expired. */
    public synchronized List<Move> get(long key, long nowMillis) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (nowMillis - entry.stamp > TIME_TO_LIVE_MILLIS) {
            entries.remove(key);
            return null;
        }
        entry.stamp = nowMillis;
        return entry.moves;
    }

    public synchronized void put(long key, List<Move> moves, long nowMillis) {
        entries.put(key, new Entry(moves, nowMillis));
    }

    /** Drops every entry; used when the rules change, for example on a config reload. */
    public synchronized void clear() {
        entries.clear();
    }

    /** @return how many entries were evicted for being stale. */
    public synchronized int sweep(long nowMillis) {
        int removed = 0;
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            if (nowMillis - iterator.next().stamp > TIME_TO_LIVE_MILLIS) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public synchronized int size() {
        return entries.size();
    }

    /** Snapshot of the cached positions, for diagnostics. */
    public synchronized List<Long> keys() {
        return new ArrayList<>(entries.keySet());
    }

    private static final class Entry {
        private final List<Move> moves;
        private long stamp;

        private Entry(List<Move> moves, long stamp) {
            this.moves = moves;
            this.stamp = stamp;
        }
    }
}
