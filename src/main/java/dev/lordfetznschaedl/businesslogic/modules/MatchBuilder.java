package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.entities.DemoInfo;
import dev.lordfetznschaedl.entities.Match;
import dev.lordfetznschaedl.entities.MatchHalf;
import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Round;
import dev.lordfetznschaedl.entities.Scoreboard;
import dev.lordfetznschaedl.entities.TeamResult;
import dev.lordfetznschaedl.entities.events.GameEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mutable collector for the parts of a {@link Match}; filled by the parser core and the modules.
 */
public final class MatchBuilder {

    private DemoInfo info;
    private final List<GameEvent> events = new ArrayList<>();
    private List<Player> players = List.of();
    private List<TeamResult> teams = List.of();
    private List<Round> rounds = List.of();
    private List<MatchHalf> halves = List.of();
    private Scoreboard scoreboard = new Scoreboard(List.of());
    private final List<String> warnings = new ArrayList<>();
    private Duration parseDuration = Duration.ZERO;

    public void addEvent(GameEvent event) {
        events.add(event);
    }

    /** Events collected so far (live view, in emission order). */
    public List<GameEvent> events() {
        return events;
    }

    public void info(DemoInfo info) {
        this.info = info;
    }

    public DemoInfo info() {
        return info;
    }

    public void players(List<Player> players) {
        this.players = List.copyOf(players);
    }

    public void teams(List<TeamResult> teams) {
        this.teams = List.copyOf(teams);
    }

    public void rounds(List<Round> rounds) {
        this.rounds = List.copyOf(rounds);
    }

    public List<Round> rounds() {
        return rounds;
    }

    public void halves(List<MatchHalf> halves) {
        this.halves = List.copyOf(halves);
    }

    public List<MatchHalf> halves() {
        return halves;
    }

    public void scoreboard(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public int warningCount() {
        return warnings.size();
    }

    public void parseDuration(Duration parseDuration) {
        this.parseDuration = parseDuration;
    }

    public Match build() {
        List<GameEvent> timeline = new ArrayList<>(events);
        timeline.sort(Comparator.comparingInt(GameEvent::tick)); // stable: keeps emission order per tick
        return new Match(info, players, teams, rounds, halves, scoreboard, List.copyOf(timeline), List.copyOf(warnings),
                parseDuration);
    }
}
