package dev.lordfetznschaedl.entities;

import java.util.List;
import java.util.Optional;

/**
 * The end-of-match scoreboard, ordered by team and then by score (as in game).
 */
public record Scoreboard(List<ScoreboardEntry> entries) {

    public List<ScoreboardEntry> forTeam(Team team) {
        return entries.stream().filter(e -> e.team() == team).toList();
    }

    public Optional<ScoreboardEntry> forPlayer(Player player) {
        return entries.stream().filter(e -> e.player().id().equals(player.id())).findFirst();
    }

    public Optional<ScoreboardEntry> forPlayerName(String name) {
        return entries.stream().filter(e -> e.player().name().equalsIgnoreCase(name)).findFirst();
    }
}
