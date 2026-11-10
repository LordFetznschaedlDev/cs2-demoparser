package dev.lordfetznschaedl.entities;

import dev.lordfetznschaedl.entities.events.GameEvent;
import dev.lordfetznschaedl.entities.events.KillEvent;

import java.util.List;
import java.util.Optional;

/**
 * Everything that was extracted from a demo: metadata, players, the final result, the rounds,
 * the scoreboard and a chronological timeline of all game events.
 *
 * @param info       demo metadata (map, tick rate, duration, ...)
 * @param players    all players that took part in the match
 * @param teams      final results of both teams (empty if no team data was found)
 * @param rounds     all rounds in order
 * @param scoreboard the end-of-match scoreboard
 * @param events     timeline of events, ordered by tick
 * @param warnings   problems that were encountered and skipped while parsing
 */
public record Match(
        DemoInfo info,
        List<Player> players,
        List<TeamResult> teams,
        List<Round> rounds,
        Scoreboard scoreboard,
        List<GameEvent> events,
        List<String> warnings) {

    public String mapName() {
        return info.mapName();
    }

    /** All events of the given type, in chronological order. */
    public <T extends GameEvent> List<T> events(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** All events that belong to the given round. */
    public List<GameEvent> eventsInRound(int round) {
        return events.stream().filter(e -> e.round() == round).toList();
    }

    public List<KillEvent> kills() {
        return events(KillEvent.class);
    }

    public Optional<TeamResult> team(Team side) {
        return teams.stream().filter(t -> t.side() == side).findFirst();
    }

    /** The team with more rounds won, empty for a draw or if no result is known. */
    public Optional<TeamResult> winner() {
        if (teams.size() != 2 || teams.get(0).score() == teams.get(1).score()) {
            return Optional.empty();
        }
        return Optional.of(teams.get(0).score() > teams.get(1).score() ? teams.get(0) : teams.get(1));
    }

    /** Final score formatted as {@code T-score:CT-score}, e.g. {@code 13:9}. */
    public String finalScore() {
        int t = team(Team.TERRORIST).map(TeamResult::score).orElse(0);
        int ct = team(Team.COUNTER_TERRORIST).map(TeamResult::score).orElse(0);
        return t + ":" + ct;
    }

    public Optional<Player> playerByName(String name) {
        return players.stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst();
    }
}
