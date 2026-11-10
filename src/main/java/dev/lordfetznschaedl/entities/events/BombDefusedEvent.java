package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.BombSite;

/**
 * The bomb was defused.
 */
public record BombDefusedEvent(
        int tick,
        int round,
        Player player,
        BombSite site) implements GameEvent {
}
