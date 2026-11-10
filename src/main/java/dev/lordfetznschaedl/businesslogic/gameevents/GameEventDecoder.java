package dev.lordfetznschaedl.businesslogic.gameevents;

import dev.lordfetznschaedl.businesslogic.io.ProtoReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes {@code CMsgSource1LegacyGameEventList} and {@code CMsgSource1LegacyGameEvent} messages.
 */
public final class GameEventDecoder {

    private final Map<Integer, GameEventDescriptor> descriptors = new HashMap<>();

    public boolean hasDescriptors() {
        return !descriptors.isEmpty();
    }

    public Map<Integer, GameEventDescriptor> descriptors() {
        return Collections.unmodifiableMap(descriptors);
    }

    /** Reads the event list message, replacing any previously known descriptors. */
    public void readDescriptorList(byte[] message) {
        descriptors.clear();
        ProtoReader list = new ProtoReader(message);
        while (list.next()) {
            if (list.fieldNumber() != 1) {
                list.skip();
                continue;
            }
            ProtoReader descriptor = list.readMessage();
            int id = -1;
            String name = "";
            List<String> keys = new ArrayList<>();
            while (descriptor.next()) {
                switch (descriptor.fieldNumber()) {
                    case 1 -> id = descriptor.readInt32();
                    case 2 -> name = descriptor.readString();
                    case 3 -> {
                        ProtoReader key = descriptor.readMessage();
                        String keyName = "";
                        while (key.next()) {
                            if (key.fieldNumber() == 2) {
                                keyName = key.readString();
                            } else {
                                key.skip();
                            }
                        }
                        keys.add(keyName);
                    }
                    default -> descriptor.skip();
                }
            }
            descriptors.put(id, new GameEventDescriptor(id, name, List.copyOf(keys)));
        }
    }

    /**
     * Decodes a single game event.
     *
     * @return the event, or {@code null} if its descriptor is unknown
     */
    public RawGameEvent readEvent(byte[] message, int tick) {
        ProtoReader reader = new ProtoReader(message);
        int eventId = -1;
        String eventName = null;
        List<Object> values = new ArrayList<>();
        while (reader.next()) {
            switch (reader.fieldNumber()) {
                case 1 -> eventName = reader.readString();
                case 2 -> eventId = reader.readInt32();
                case 3 -> values.add(readKeyValue(reader.readMessage()));
                default -> reader.skip();
            }
        }
        GameEventDescriptor descriptor = descriptors.get(eventId);
        if (descriptor == null) {
            return null;
        }
        Map<String, Object> data = LinkedHashMap.newLinkedHashMap(values.size());
        List<String> keyNames = descriptor.keyNames();
        for (int i = 0; i < values.size() && i < keyNames.size(); i++) {
            data.put(keyNames.get(i), values.get(i));
        }
        String name = eventName != null && !eventName.isEmpty() ? eventName : descriptor.name();
        return new RawGameEvent(name, tick, Collections.unmodifiableMap(data));
    }

    /**
     * key_t: type=1, val_string=2, val_float=3, val_long=4, val_short=5, val_byte=6, val_bool=7, val_uint64=8.
     * Whatever value field is present is used; this tolerates Valve reusing value slots for new key types.
     */
    private static Object readKeyValue(ProtoReader key) {
        int type = 0;
        Object value = null;
        while (key.next()) {
            switch (key.fieldNumber()) {
                case 1 -> type = key.readInt32();
                case 2 -> value = key.readString();
                case 3 -> value = key.readFloat();
                case 4, 5, 6 -> value = (int) key.readAnyLong();
                case 7 -> value = key.readBool();
                case 8 -> value = key.readAnyLong();
                default -> key.skip();
            }
        }
        if (value != null) {
            return value;
        }
        // protobuf omits default values, so restore the zero value of the declared type
        return switch (type) {
            case 1 -> "";
            case 2 -> 0f;
            case 6 -> false;
            case 7 -> 0L;
            default -> 0;
        };
    }
}
