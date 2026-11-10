package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.entitystate.Entity;
import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Scoreboard;
import dev.lordfetznschaedl.entities.ScoreboardEntry;
import dev.lordfetznschaedl.entities.Team;
import dev.lordfetznschaedl.entities.TeamResult;
import dev.lordfetznschaedl.entities.events.GameEvent;
import dev.lordfetznschaedl.entities.events.KillEvent;
import dev.lordfetznschaedl.entities.events.PlayerHurtEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the player list, the team results and the scoreboard from the final state of the
 * player controller and team entities.
 * <p>
 * The stats come from the match statistics the game itself networks for each player
 * ({@code m_pActionTrackingServices.*}). If a demo lacks them, kills, deaths, assists, headshots and
 * damage are counted from the recorded events instead.
 */
public final class ScoreboardModule implements ParserModule {

    private static final String STATS = "m_pActionTrackingServices.";

    /** Players in the order they first appeared on a playing side. */
    private final Map<String, Player> participants = new LinkedHashMap<>();
    /** Last playing side of every participant. */
    private final Map<String, Team> lastSide = new HashMap<>();

    @Override
    public void onEntityCreated(Entity entity, ParserContext context) {
        track(entity, context);
    }

    @Override
    public void onEntityUpdated(Entity entity, java.util.Set<String> changed, ParserContext context) {
        if (changed.contains("m_iTeamNum") || changed.contains("m_iszPlayerName") || changed.contains("m_steamID")) {
            track(entity, context);
        }
    }

    private void track(Entity entity, ParserContext context) {
        if (!entity.className().equals(ParserContext.CONTROLLER_CLASS)) {
            return;
        }
        Team side = Team.fromNumber(entity.getInt("m_iTeamNum", 0));
        if (!side.isPlaying()) {
            return;
        }
        Player player = context.player(entity);
        participants.put(player.id(), player);
        lastSide.put(player.id(), side);
    }

    @Override
    public void onFinish(ParserContext context) {
        int roundsPlayed = Math.max(context.totalRoundsPlayed(), context.match().rounds().size());

        List<ScoreboardEntry> entries = new ArrayList<>();
        Map<String, Entity> controllers = new HashMap<>();
        for (Entity controller : context.controllers()) {
            Player player = context.player(controller);
            Team side = Team.fromNumber(controller.getInt("m_iTeamNum", 0));
            if (side.isPlaying()) {
                participants.put(player.id(), player);
                lastSide.put(player.id(), side);
            }
            controllers.put(player.id(), controller);
        }

        boolean networkedStats = controllers.values().stream().anyMatch(c -> c.get(STATS + "m_iKills") != null);
        Map<String, int[]> counted = networkedStats ? Map.of() : countFromEvents(context.match().events());

        for (Player player : participants.values()) {
            Team side = lastSide.getOrDefault(player.id(), Team.UNASSIGNED);
            Entity c = controllers.get(player.id());
            if (networkedStats && c != null) {
                entries.add(new ScoreboardEntry(player, side,
                        c.getInt(STATS + "m_iKills", 0),
                        c.getInt(STATS + "m_iDeaths", 0),
                        c.getInt(STATS + "m_iAssists", 0),
                        c.getInt(STATS + "m_iHeadShotKills", 0),
                        c.getInt(STATS + "m_iDamage", 0),
                        c.getInt(STATS + "m_iUtilityDamage", 0),
                        c.getInt(STATS + "m_iEnemiesFlashed", 0),
                        c.getInt("m_iMVPs", 0),
                        c.getInt("m_iScore", 0),
                        c.getInt(STATS + "m_iEnemy3Ks", 0),
                        c.getInt(STATS + "m_iEnemy4Ks", 0),
                        c.getInt(STATS + "m_iEnemy5Ks", 0),
                        roundsPlayed));
            } else {
                int[] s = counted.getOrDefault(player.id(), new int[5]);
                int mvps = c == null ? 0 : c.getInt("m_iMVPs", 0);
                int score = c == null ? 0 : c.getInt("m_iScore", 0);
                entries.add(new ScoreboardEntry(player, side, s[0], s[1], s[2], s[3], s[4], 0, 0, mvps, score,
                        0, 0, 0, roundsPlayed));
            }
        }
        entries.sort(Comparator.comparing((ScoreboardEntry e) -> e.team().ordinal())
                .thenComparing(Comparator.comparingInt(ScoreboardEntry::score).reversed())
                .thenComparing(Comparator.comparingInt(ScoreboardEntry::kills).reversed()));

        context.match().players(List.copyOf(participants.values()));
        context.match().scoreboard(new Scoreboard(List.copyOf(entries)));
        context.match().teams(teamResults(context));
    }

    private List<TeamResult> teamResults(ParserContext context) {
        List<TeamResult> results = new ArrayList<>();
        for (Team side : List.of(Team.TERRORIST, Team.COUNTER_TERRORIST)) {
            Entity team = context.teamEntity(side);
            if (team == null) {
                continue;
            }
            String clan = team.getString("m_szClanTeamname");
            String name = clan != null && !clan.isBlank() ? clan
                    : side == Team.TERRORIST ? "Terrorists" : "Counter-Terrorists";
            int score = team.getInt("m_iScore", 0);
            int first = team.getInt("m_scoreFirstHalf", 0);
            int second = team.getInt("m_scoreSecondHalf", 0);
            int overtime = team.getInt("m_scoreOvertime", Math.max(0, score - first - second));
            List<Player> members = participants.values().stream()
                    .filter(p -> lastSide.get(p.id()) == side).toList();
            results.add(new TeamResult(side, name, score, first, second, overtime, members));
        }
        return results;
    }

    /** kills, deaths, assists, headshots, damage per player id, only for live rounds. */
    private static Map<String, int[]> countFromEvents(List<GameEvent> events) {
        Map<String, int[]> stats = new HashMap<>();
        for (GameEvent event : events) {
            if (event.round() <= 0) {
                continue;
            }
            if (event instanceof KillEvent k) {
                if (k.victim() != null) {
                    stats.computeIfAbsent(k.victim().id(), id -> new int[5])[1]++;
                }
                if (k.attacker() != null && !k.isSuicide() && !k.isTeamKill()) {
                    int[] s = stats.computeIfAbsent(k.attacker().id(), id -> new int[5]);
                    s[0]++;
                    if (k.headshot()) {
                        s[3]++;
                    }
                }
                if (k.assister() != null) {
                    stats.computeIfAbsent(k.assister().id(), id -> new int[5])[2]++;
                }
            } else if (event instanceof PlayerHurtEvent h && h.attacker() != null && h.victim() != null
                    && !h.attacker().id().equals(h.victim().id())) {
                stats.computeIfAbsent(h.attacker().id(), id -> new int[5])[4] += h.healthDamage();
            }
        }
        return stats;
    }
}
