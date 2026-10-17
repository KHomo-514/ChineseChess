package com.khomo.chinesechess.board;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers the positions a game has passed through, so repetition can be judged.
 *
 * <p>Besides the position key, every entry stores which side (if any) stood in check there. That
 * single extra byte is what lets the arbiter distinguish a neutral threefold repetition (draw) from
 * perpetual check (长将), where the checking side must vary or lose.</p>
 *
 * <p>Single responsibility: repetition bookkeeping.</p>
 */
public final class PositionHistory {

    /** Positions retained when serialising, so a saved match stays a reasonable size. */
    public static final int MAX_PERSISTED = 256;

    private final List<Entry> entries = new ArrayList<>(128);

    /** @param key position identity, {@code boardHash ^ sideKey} */
    public void record(long key, Side checkedSide) {
        entries.add(new Entry(key, checkedSide));
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    /** How often {@code key} has been reached, including the current occurrence. */
    public int occurrences(long key) {
        int total = 0;
        for (Entry entry : entries) {
            if (entry.key == key) {
                total++;
            }
        }
        return total;
    }

    /**
     * @return the side that was in check in <em>every</em> occurrence of {@code key}, or {@code null}
     *         when the occurrences disagree or none of them was a check. A non-null answer means the
     *         other side has been repeating a check and therefore loses the repetition ruling.
     */
    public Side constantlyCheckedSide(long key) {
        Side common = null;
        boolean any = false;
        for (Entry entry : entries) {
            if (entry.key != key) {
                continue;
            }
            if (entry.checkedSide == null) {
                return null;
            }
            if (!any) {
                common = entry.checkedSide;
                any = true;
            } else if (common != entry.checkedSide) {
                return null;
            }
        }
        return any ? common : null;
    }

    /** Position keys of the most recent entries, oldest first. */
    public long[] recentKeys() {
        int from = Math.max(0, entries.size() - MAX_PERSISTED);
        long[] keys = new long[entries.size() - from];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = entries.get(from + index).key;
        }
        return keys;
    }

    /** Checked-side ordinals of the most recent entries; {@code -1} means "nobody was in check". */
    public byte[] recentCheckedSides() {
        int from = Math.max(0, entries.size() - MAX_PERSISTED);
        byte[] sides = new byte[entries.size() - from];
        for (int index = 0; index < sides.length; index++) {
            Side side = entries.get(from + index).checkedSide;
            sides[index] = side == null ? (byte) -1 : (byte) side.ordinal();
        }
        return sides;
    }

    /** Restores a truncated history after loading a saved match. */
    public void restore(long[] keys, byte[] checkedSides) {
        entries.clear();
        if (keys == null) {
            return;
        }
        for (int index = 0; index < keys.length; index++) {
            Side side = null;
            if (checkedSides != null && index < checkedSides.length && checkedSides[index] >= 0) {
                side = Side.ofNullable(checkedSides[index]);
            }
            entries.add(new Entry(keys[index], side));
        }
    }

    private static final class Entry {
        private final long key;
        private final Side checkedSide;

        private Entry(long key, Side checkedSide) {
            this.key = key;
            this.checkedSide = checkedSide;
        }
    }
}
