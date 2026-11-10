package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.BombSite;

/**
 * A player started planting.
 */
public record BombBeginPlantEvent(
        int tick,
        int round,
        Player player,
        BombSite site) implements GameEvent {
}
