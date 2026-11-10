package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Team;

/**
 * Position of a living player at a moment of the match (only recorded by the PlayerPositionModule).
 *
 * @param player the player
 * @param team   side the player is on
 * @param x      world coordinate (Hammer units)
 * @param y      world coordinate (Hammer units)
 * @param z      world coordinate of the player's feet (Hammer units)
 * @param pitch  vertical view angle in degrees (positive = looking down)
 * @param yaw    horizontal view angle in degrees
 * @param health remaining health
 */
public record PlayerPositionEvent(
        int tick,
        int round,
        Player player,
        Team team,
        float x,
        float y,
        float z,
        float pitch,
        float yaw,
        int health) implements GameEvent {

    /** Distance to another position sample, e.g. to compute how far a player moved. */
    public double distanceTo(PlayerPositionEvent other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
