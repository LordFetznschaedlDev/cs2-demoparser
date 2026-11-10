package dev.lordfetznschaedl.entities.events;

import java.util.Map;

/**
 * Any game event without a dedicated type (only recorded by the GenericEventModule).
 */
public record GenericGameEvent(
        int tick,
        int round,
        String name,
        Map<String, Object> data) implements GameEvent {
}
