package dev.lordfetznschaedl.entities.events;

import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Team;

/**
 * A player joined a team.
 */
public record PlayerTeamChangeEvent(
        int tick,
        int round,
        Player player,
        Team oldTeam,
        Team newTeam) implements GameEvent {
}
