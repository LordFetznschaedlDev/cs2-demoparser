package dev.lordfetznschaedl.businesslogic.gameevents;

import java.util.Map;

/**
 * A decoded but untyped game event: its name, the tick it happened at and its key/value data.
 * Values are {@link String}, {@link Float}, {@link Integer}, {@link Long} or {@link Boolean}.
 */
public record RawGameEvent(String name, int tick, Map<String, Object> data) {

    public int getInt(String key, int fallback) {
        return data.get(key) instanceof Number n ? n.intValue() : fallback;
    }

    public long getLong(String key, long fallback) {
        return data.get(key) instanceof Number n ? n.longValue() : fallback;
    }

    public float getFloat(String key, float fallback) {
        return data.get(key) instanceof Number n ? n.floatValue() : fallback;
    }

    public boolean getBoolean(String key) {
        Object value = data.get(key);
        return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() != 0;
    }

    public String getString(String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }
}
