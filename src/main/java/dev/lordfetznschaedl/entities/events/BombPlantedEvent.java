package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.BombSite;

/**
 * The bomb was planted.
 */
public record BombPlantedEvent(
        int tick,
        int round,
        Player player,
        BombSite site) implements GameEvent {
}
