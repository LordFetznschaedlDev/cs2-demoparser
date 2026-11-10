package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.entitystate.Entity;
import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;
import dev.lordfetznschaedl.entities.MatchHalf;
import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Round;
import dev.lordfetznschaedl.entities.RoundEndReason;
import dev.lordfetznschaedl.entities.Team;
import dev.lordfetznschaedl.entities.events.GameEvent;
import dev.lordfetznschaedl.entities.events.KillEvent;
import dev.lordfetznschaedl.entities.events.MatchEndEvent;
import dev.lordfetznschaedl.entities.events.MatchStartEvent;
import dev.lordfetznschaedl.entities.events.RoundEndEvent;
import dev.lordfetznschaedl.entities.events.RoundFreezeEndEvent;
import dev.lordfetznschaedl.entities.events.RoundStartEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks match and round boundaries and the round results.
 * <p>
 * CS2 demos no longer contain {@code round_end} game events, so the outcome of a round is read from the
 * game rules entity ({@code m_totalRoundsPlayed}, {@code m_iRoundWinStatus}, {@code m_eRoundWinReason})
 * and the scores from the team entities. Starts come from the {@code round_poststart} and
 * {@code begin_new_match} events.
 * <p>
 * The players of each side are recorded when the freeze time ends (or, if that was missed, when the
 * round ends). From that the halves are derived: a new half starts whenever most players swapped sides.
 * <p>
 * This module also maintains {@link ParserContext#currentRound()}, so it should be registered
 * before modules that emit round-related events.
 */
public final class RoundModule implements ParserModule {

    /** A round start this close before {@code begin_new_match} belongs to the first round. */
    private static final int MATCH_START_TOLERANCE_TICKS = 256;

    private static final class Draft {
        final int number;
        final int startTick;
        int freezeEndTick = -1;
        int endTick = -1;
        Team winner = Team.UNASSIGNED;
        RoundEndReason reason = RoundEndReason.UNKNOWN;
        int terroristScore;
        int counterTerroristScore;
        List<Player> terrorists;
        List<Player> counterTerrorists;

        Draft(int number, int startTick) {
            this.number = number;
            this.startTick = startTick;
        }
    }

    private final List<Draft> rounds = new ArrayList<>();
    private Draft current;
    private boolean matchStarted;
    private int lastRoundStartTick = Integer.MIN_VALUE;
    private int lastTotalRoundsPlayed = -1;
    /** Round end detected but scores are read one tick later, once the team entities are updated. */
    private Draft pendingEnd;
    private int pendingMatchEndTick = -1;
    private boolean matchEnded;

    @Override
    public void onTick(int tick, ParserContext context) {
        if (pendingEnd != null && tick > pendingEnd.endTick) {
            finishRoundEnd(context);
        }
        if (pendingMatchEndTick >= 0 && tick > pendingMatchEndTick) {
            finishMatchEnd(context);
        }
    }

    @Override
    public void onGameEvent(RawGameEvent event, ParserContext context) {
        switch (event.name()) {
            case "begin_new_match" -> startMatch(event.tick(), context);
            case "round_poststart" -> {
                lastRoundStartTick = event.tick();
                if (matchStarted || isLive(context)) {
                    matchStarted = true;
                    startRound(event.tick(), context);
                }
            }
            case "round_freeze_end" -> {
                if (current != null && current.freezeEndTick < 0) {
                    current.freezeEndTick = event.tick();
                    recordSides(current, context);
                    context.emit(new RoundFreezeEndEvent(event.tick(), current.number));
                }
            }
            case "cs_win_panel_match" -> {
                if (!matchEnded) {
                    pendingMatchEndTick = event.tick();
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void onEntityUpdated(Entity entity, Set<String> changed, ParserContext context) {
        if (!entity.className().equals(ParserContext.GAME_RULES_CLASS)
                || !changed.contains(ParserContext.GAME_RULES_PREFIX + "m_totalRoundsPlayed")) {
            return;
        }
        int total = context.totalRoundsPlayed();
        int previous = lastTotalRoundsPlayed;
        lastTotalRoundsPlayed = total;
        if (previous < 0 || total <= previous || !matchStarted) {
            return; // first observation, or reset by a match (re)start
        }
        if (current == null || current.endTick >= 0) {
            // the demo started in the middle of a round
            current = new Draft(total, context.tick());
            rounds.add(current);
        }
        current.endTick = context.tick();
        if (current.terrorists == null) {
            recordSides(current, context);
        }
        current.winner = Team.fromNumber(context.gameRule("m_iRoundWinStatus") instanceof Number n ? n.intValue() : 0);
        current.reason = RoundEndReason.fromCode(context.gameRule("m_eRoundWinReason") instanceof Number n ? n.intValue() : 0);
        pendingEnd = current;
    }

    @Override
    public void onEntityCreated(Entity entity, ParserContext context) {
        if (entity.className().equals(ParserContext.GAME_RULES_CLASS)) {
            lastTotalRoundsPlayed = context.totalRoundsPlayed();
        }
    }

    @Override
    public void onFinish(ParserContext context) {
        if (pendingEnd != null) {
            finishRoundEnd(context);
        }
        if (pendingMatchEndTick >= 0) {
            finishMatchEnd(context);
        }
        List<KillEvent> kills = context.match().events().stream()
                .filter(KillEvent.class::isInstance).map(KillEvent.class::cast).toList();
        List<Round> result = new ArrayList<>();
        for (Draft d : rounds) {
            List<KillEvent> roundKills = kills.stream().filter(k -> k.round() == d.number).toList();
            if (d.terrorists == null) {
                recordSides(d, context); // unfinished last round of a cut-off demo
            }
            result.add(new Round(d.number, d.startTick, d.freezeEndTick, d.endTick, d.winner, d.reason,
                    d.terroristScore, d.counterTerroristScore, roundKills, d.terrorists, d.counterTerrorists));
        }
        context.match().rounds(result);
        context.match().halves(halves(result));
    }

    private static void recordSides(Draft round, ParserContext context) {
        List<Player> terrorists = new ArrayList<>();
        List<Player> counterTerrorists = new ArrayList<>();
        for (Entity controller : context.controllers()) {
            switch (Team.fromNumber(controller.getInt("m_iTeamNum", 0))) {
                case TERRORIST -> terrorists.add(context.player(controller));
                case COUNTER_TERRORIST -> counterTerrorists.add(context.player(controller));
                default -> {
                }
            }
        }
        round.terrorists = List.copyOf(terrorists);
        round.counterTerrorists = List.copyOf(counterTerrorists);
    }

    /** Groups consecutive rounds into halves; a half ends where most players swapped sides. */
    static List<MatchHalf> halves(List<Round> rounds) {
        List<List<Round>> groups = new ArrayList<>();
        Round previous = null;
        for (Round round : rounds) {
            if (previous == null || sidesSwapped(previous, round)) {
                groups.add(new ArrayList<>());
            }
            groups.getLast().add(round);
            previous = round;
        }
        List<MatchHalf> halves = new ArrayList<>();
        for (List<Round> group : groups) {
            halves.add(new MatchHalf(halves.size() + 1,
                    group.getFirst().number(), group.getLast().number(),
                    playersOfSide(group, Team.TERRORIST),
                    playersOfSide(group, Team.COUNTER_TERRORIST),
                    (int) group.stream().filter(r -> r.winner() == Team.TERRORIST).count(),
                    (int) group.stream().filter(r -> r.winner() == Team.COUNTER_TERRORIST).count()));
        }
        return halves;
    }

    private static boolean sidesSwapped(Round before, Round after) {
        Map<String, Team> sides = new HashMap<>();
        before.terrorists().forEach(p -> sides.put(p.id(), Team.TERRORIST));
        before.counterTerrorists().forEach(p -> sides.put(p.id(), Team.COUNTER_TERRORIST));
        int common = 0;
        int swapped = 0;
        for (Team side : List.of(Team.TERRORIST, Team.COUNTER_TERRORIST)) {
            for (Player p : after.players(side)) {
                Team old = sides.get(p.id());
                if (old != null) {
                    common++;
                    if (old != side) {
                        swapped++;
                    }
                }
            }
        }
        return common > 0 && swapped * 2 > common;
    }

    /**
     * Players that played on {@code side} during the half. A player who changed teams within the half
     * is assigned to the side they played most rounds on.
     */
    private static List<Player> playersOfSide(List<Round> half, Team side) {
        Map<String, Player> players = new LinkedHashMap<>();
        Map<String, int[]> roundsPerSide = new HashMap<>();
        for (Round round : half) {
            for (Team s : List.of(Team.TERRORIST, Team.COUNTER_TERRORIST)) {
                for (Player p : round.players(s)) {
                    players.putIfAbsent(p.id(), p);
                    roundsPerSide.computeIfAbsent(p.id(), id -> new int[2])[s == Team.TERRORIST ? 0 : 1]++;
                }
            }
        }
        List<Player> result = new ArrayList<>();
        for (Player p : players.values()) {
            int[] counts = roundsPerSide.get(p.id());
            Team majority = counts[0] >= counts[1] ? Team.TERRORIST : Team.COUNTER_TERRORIST;
            if (majority == side) {
                result.add(p);
            }
        }
        return List.copyOf(result);
    }

    private boolean isLive(ParserContext context) {
        return context.gameRules() != null && !context.isWarmup();
    }

    private void startMatch(int tick, ParserContext context) {
        // a (re)start discards everything recorded before
        context.match().events().removeIf(e -> e.round() > 0 && isRoundEvent(e));
        rounds.clear();
        current = null;
        pendingEnd = null;
        matchStarted = true;
        matchEnded = false;
        lastTotalRoundsPlayed = context.totalRoundsPlayed();
        context.emit(new MatchStartEvent(tick, 1));
        boolean roundJustStarted = tick - lastRoundStartTick <= MATCH_START_TOLERANCE_TICKS;
        startRound(roundJustStarted ? lastRoundStartTick : tick, context);
    }

    private static boolean isRoundEvent(GameEvent e) {
        return e instanceof RoundStartEvent || e instanceof RoundEndEvent || e instanceof RoundFreezeEndEvent;
    }

    private void startRound(int tick, ParserContext context) {
        if (pendingEnd != null) {
            finishRoundEnd(context);
        }
        if (current != null && current.startTick == tick) {
            return; // already started (begin_new_match right after round_poststart)
        }
        int number = rounds.isEmpty() ? context.totalRoundsPlayed() + 1 : rounds.getLast().number + 1;
        current = new Draft(number, tick);
        rounds.add(current);
        context.currentRound(number);
        context.emit(new RoundStartEvent(tick, number));
    }

    private void finishRoundEnd(ParserContext context) {
        Draft d = pendingEnd;
        pendingEnd = null;
        d.terroristScore = context.teamScore(Team.TERRORIST);
        d.counterTerroristScore = context.teamScore(Team.COUNTER_TERRORIST);
        context.emit(new RoundEndEvent(d.endTick, d.number, d.winner, d.reason, d.terroristScore, d.counterTerroristScore));
    }

    private void finishMatchEnd(ParserContext context) {
        int tick = pendingMatchEndTick;
        pendingMatchEndTick = -1;
        matchEnded = true;
        int t = context.teamScore(Team.TERRORIST);
        int ct = context.teamScore(Team.COUNTER_TERRORIST);
        Team winner = t > ct ? Team.TERRORIST : ct > t ? Team.COUNTER_TERRORIST : Team.UNASSIGNED;
        context.emit(new MatchEndEvent(tick, context.currentRound(), winner, t, ct));
    }
}
