package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;

/**
 * A grenade went off (molotov / incendiary: the fire started).
 *
 * @param thrower may be {@code null} if it could not be resolved
 */
public record GrenadeDetonateEvent(
        int tick,
        int round,
        GrenadeType type,
        Player thrower,
        float x,
        float y,
        float z) implements GameEvent {
}
