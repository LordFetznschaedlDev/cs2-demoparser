package dev.lordfetznschaedl.businesslogic.entitystate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Current state of a networked entity. Properties are addressed by their dotted name,
 * e.g. {@code m_pGameRules.m_totalRoundsPlayed} or {@code m_iszPlayerName}.
 */
public final class Entity {

    private final int index;
    private final int serial;
    private final ServerClass serverClass;
    private final Map<String, Object> properties;

    Entity(int index, int serial, ServerClass serverClass, Map<String, Object> initial) {
        this.index = index;
        this.serial = serial;
        this.serverClass = serverClass;
        this.properties = new HashMap<>(initial);
    }

    public int index() {
        return index;
    }

    public int serial() {
        return serial;
    }

    public ServerClass serverClass() {
        return serverClass;
    }

    public String className() {
        return serverClass.name();
    }

    /** Entity handle as used by {@code CHandle} properties. */
    public int handle() {
        return (serial << EntityManager.INDEX_BITS) | index;
    }

    public Map<String, Object> properties() {
        return Collections.unmodifiableMap(properties);
    }

    void set(String name, Object value) {
        properties.put(name, value);
    }

    public Object get(String name) {
        return properties.get(name);
    }

    public int getInt(String name, int fallback) {
        return properties.get(name) instanceof Number n ? n.intValue() : fallback;
    }

    public long getLong(String name, long fallback) {
        return properties.get(name) instanceof Number n ? n.longValue() : fallback;
    }

    public float getFloat(String name, float fallback) {
        return properties.get(name) instanceof Number n ? n.floatValue() : fallback;
    }

    public boolean getBoolean(String name) {
        return properties.get(name) instanceof Boolean b && b;
    }

    public String getString(String name) {
        return properties.get(name) instanceof String s ? s : null;
    }

    @Override
    public String toString() {
        return className() + "#" + index;
    }
}
