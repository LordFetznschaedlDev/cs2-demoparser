package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.entitystate.Entity;
import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;
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
import java.util.List;
import java.util.Set;

/**
 * Tracks match and round boundaries and the round results.
 * <p>
 * CS2 demos no longer contain {@code round_end} game events, so the outcome of a round is read from the
 * game rules entity ({@code m_totalRoundsPlayed}, {@code m_iRoundWinStatus}, {@code m_eRoundWinReason})
 * and the scores from the team entities. Starts come from the {@code round_poststart} and
 * {@code begin_new_match} events.
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
            result.add(new Round(d.number, d.startTick, d.freezeEndTick, d.endTick, d.winner, d.reason,
                    d.terroristScore, d.counterTerroristScore, roundKills));
        }
        context.match().rounds(result);
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
