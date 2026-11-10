package dev.lordfetznschaedl.businesslogic.stringtables;

import java.util.Map;
import java.util.TreeMap;

/**
 * A network string table: indexed entries consisting of a key and optional user data.
 */
public final class StringTable {

    public record Entry(int index, String key, byte[] value) {
    }

    private final String name;
    private final boolean userDataFixedSize;
    private final int userDataSizeBits;
    private final int flags;
    private final boolean varIntBitCounts;
    private final Map<Integer, Entry> entries = new TreeMap<>();

    public StringTable(String name, boolean userDataFixedSize, int userDataSizeBits, int flags, boolean varIntBitCounts) {
        this.name = name;
        this.userDataFixedSize = userDataFixedSize;
        this.userDataSizeBits = userDataSizeBits;
        this.flags = flags;
        this.varIntBitCounts = varIntBitCounts;
    }

    public String name() {
        return name;
    }

    public boolean userDataFixedSize() {
        return userDataFixedSize;
    }

    public int userDataSizeBits() {
        return userDataSizeBits;
    }

    public int flags() {
        return flags;
    }

    public boolean varIntBitCounts() {
        return varIntBitCounts;
    }

    public Map<Integer, Entry> entries() {
        return entries;
    }

    /** Applies an update; a missing key or value keeps the previous one. */
    public Entry put(int index, String key, byte[] value) {
        Entry previous = entries.get(index);
        String newKey = key != null ? key : previous != null ? previous.key() : "";
        byte[] newValue = value != null ? value : previous != null ? previous.value() : null;
        Entry entry = new Entry(index, newKey, newValue);
        entries.put(index, entry);
        return entry;
    }
}
