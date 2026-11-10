package dev.lordfetznschaedl.businesslogic.stringtables;

import dev.lordfetznschaedl.businesslogic.io.BitReader;
import dev.lordfetznschaedl.businesslogic.io.ProtoReader;
import dev.lordfetznschaedl.businesslogic.io.Snappy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Keeps track of all network string tables and decodes their create/update messages.
 * Listeners are notified about every changed entry.
 */
public final class StringTableManager {

    private static final int KEY_HISTORY_SIZE = 32;
    private static final int FLAG_VALUE_COMPRESSION = 0x1;

    private final List<StringTable> tables = new ArrayList<>();
    private final List<BiConsumer<StringTable, StringTable.Entry>> listeners = new ArrayList<>();

    public void addListener(BiConsumer<StringTable, StringTable.Entry> listener) {
        listeners.add(listener);
    }

    public List<StringTable> tables() {
        return tables;
    }

    public StringTable byName(String name) {
        for (StringTable table : tables) {
            if (table.name().equals(name)) {
                return table;
            }
        }
        return null;
    }

    public void clear() {
        tables.clear();
    }

    /** Handles {@code CSVCMsg_CreateStringTable}. */
    public void onCreate(byte[] message) {
        String name = "";
        int numEntries = 0;
        boolean fixedSize = false;
        int sizeBits = 0;
        int flags = 0;
        byte[] stringData = new byte[0];
        boolean compressed = false;
        boolean varIntBitCounts = false;
        ProtoReader r = new ProtoReader(message);
        while (r.next()) {
            switch (r.fieldNumber()) {
                case 1 -> name = r.readString();
                case 2 -> numEntries = r.readInt32();
                case 3 -> fixedSize = r.readBool();
                case 5 -> sizeBits = r.readInt32();
                case 6 -> flags = r.readInt32();
                case 7 -> stringData = r.readBytes();
                case 9 -> compressed = r.readBool();
                case 10 -> varIntBitCounts = r.readBool();
                default -> r.skip();
            }
        }
        if (compressed) {
            stringData = Snappy.decompress(stringData);
        }
        StringTable table = new StringTable(name, fixedSize, sizeBits, flags, varIntBitCounts);
        tables.add(table);
        apply(table, stringData, numEntries);
    }

    /** Handles {@code CSVCMsg_UpdateStringTable}. */
    public void onUpdate(byte[] message) {
        int tableId = -1;
        int changed = 0;
        byte[] stringData = new byte[0];
        ProtoReader r = new ProtoReader(message);
        while (r.next()) {
            switch (r.fieldNumber()) {
                case 1 -> tableId = r.readInt32();
                case 2 -> changed = r.readInt32();
                case 3 -> stringData = r.readBytes();
                default -> r.skip();
            }
        }
        if (tableId >= 0 && tableId < tables.size()) {
            apply(tables.get(tableId), stringData, changed);
        }
    }

    /**
     * Handles a {@code CDemoStringTables} snapshot (part of full packets): its items are plain protobuf,
     * keyed by position in the table.
     */
    public void onSnapshot(byte[] message) {
        ProtoReader r = new ProtoReader(message);
        while (r.next()) {
            if (r.fieldNumber() != 1) {
                r.skip();
                continue;
            }
            ProtoReader tableReader = r.readMessage();
            String name = "";
            List<String> keys = new ArrayList<>();
            List<byte[]> values = new ArrayList<>();
            while (tableReader.next()) {
                switch (tableReader.fieldNumber()) {
                    case 1 -> name = tableReader.readString();
                    case 2 -> {
                        ProtoReader item = tableReader.readMessage();
                        String key = "";
                        byte[] value = null;
                        while (item.next()) {
                            switch (item.fieldNumber()) {
                                case 1 -> key = item.readString();
                                case 2 -> value = item.readBytes();
                                default -> item.skip();
                            }
                        }
                        keys.add(key);
                        values.add(value);
                    }
                    default -> tableReader.skip();
                }
            }
            StringTable table = byName(name);
            if (table == null) {
                continue;
            }
            for (int i = 0; i < keys.size(); i++) {
                notify(table, table.put(i, keys.get(i), values.get(i)));
            }
        }
    }

    private void apply(StringTable table, byte[] data, int numUpdates) {
        if (data.length == 0 || numUpdates <= 0) {
            return;
        }
        BitReader reader = new BitReader(data);
        int index = -1;
        List<String> history = new ArrayList<>(KEY_HISTORY_SIZE);
        for (int i = 0; i < numUpdates; i++) {
            if (reader.readBoolean()) {
                index++;
            } else {
                index = reader.readVarInt32() + 1;
            }

            String key = null;
            if (reader.readBoolean()) {
                if (reader.readBoolean()) {
                    int position = reader.readBits(5);
                    int length = reader.readBits(5);
                    if (position >= history.size()) {
                        key = reader.readNullTerminatedString();
                    } else {
                        String base = history.get(position);
                        key = (length > base.length() ? base : base.substring(0, length)) + reader.readNullTerminatedString();
                    }
                } else {
                    key = reader.readNullTerminatedString();
                }
                if (history.size() >= KEY_HISTORY_SIZE) {
                    history.removeFirst();
                }
                history.add(key);
            }

            byte[] value = null;
            if (reader.readBoolean()) {
                int bitSize;
                boolean valueCompressed = false;
                if (table.userDataFixedSize()) {
                    bitSize = table.userDataSizeBits();
                } else {
                    if ((table.flags() & FLAG_VALUE_COMPRESSION) != 0) {
                        valueCompressed = reader.readBoolean();
                    }
                    bitSize = (table.varIntBitCounts() ? reader.readUBitVar() : reader.readBits(17)) * 8;
                }
                value = reader.readBitsAsBytes(bitSize);
                if (valueCompressed) {
                    value = Snappy.decompress(value);
                }
            }
            notify(table, table.put(index, key, value));
        }
    }

    private void notify(StringTable table, StringTable.Entry entry) {
        for (var listener : listeners) {
            listener.accept(table, entry);
        }
    }
}
