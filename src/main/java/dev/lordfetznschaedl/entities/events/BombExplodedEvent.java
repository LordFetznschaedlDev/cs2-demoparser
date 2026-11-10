package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.BombSite;

/**
 * The bomb exploded.
 */
public record BombExplodedEvent(
        int tick,
        int round,
        BombSite site) implements GameEvent {
}
