package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.entitystate.Entity;
import dev.lordfetznschaedl.businesslogic.entitystate.EntityManager;
import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Team;
import dev.lordfetznschaedl.entities.events.GameEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared state that modules use while the demo is being parsed: the current tick and round,
 * the entity state, player lookup and the {@link MatchBuilder} the result is collected in.
 */
public final class ParserContext {

    public static final String CONTROLLER_CLASS = "CCSPlayerController";
    public static final String GAME_RULES_CLASS = "CCSGameRulesProxy";
    public static final String TEAM_CLASS = "CCSTeam";
    public static final String GAME_RULES_PREFIX = "m_pGameRules.";

    /** Value used by game events for "no player". */
    private static final int NO_USER = 0xFFFF;

    private final EntityManager entities;
    private final MatchBuilder match;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private int tick;
    private int currentRound;

    public ParserContext(EntityManager entities, MatchBuilder match) {
        this.entities = entities;
        this.match = match;
    }

    public int tick() {
        return tick;
    }

    public void tick(int tick) {
        this.tick = tick;
    }

    /** Round number events are currently attributed to ({@code 0} = warmup / not started). */
    public int currentRound() {
        return currentRound;
    }

    public void currentRound(int round) {
        this.currentRound = round;
    }

    public EntityManager entities() {
        return entities;
    }

    public MatchBuilder match() {
        return match;
    }

    /** Adds an event to the match timeline. */
    public void emit(GameEvent event) {
        match.addEvent(event);
    }

    public void warn(String message) {
        match.addWarning("tick " + tick + ": " + message);
    }

    // ---------------------------------------------------------------- game rules

    /** The game rules entity, or {@code null} before it was created. */
    public Entity gameRules() {
        List<Entity> rules = entities.byClass(GAME_RULES_CLASS);
        return rules.isEmpty() ? null : rules.getFirst();
    }

    /** Reads a property of {@code m_pGameRules}, e.g. {@code gameRule("m_totalRoundsPlayed")}. */
    public Object gameRule(String name) {
        Entity rules = gameRules();
        return rules == null ? null : rules.get(GAME_RULES_PREFIX + name);
    }

    public boolean isWarmup() {
        return gameRule("m_bWarmupPeriod") instanceof Boolean b && b;
    }

    public int totalRoundsPlayed() {
        return gameRule("m_totalRoundsPlayed") instanceof Number n ? n.intValue() : 0;
    }

    /** The {@code CCSTeam} entity for a side, or {@code null}. */
    public Entity teamEntity(Team side) {
        for (Entity team : entities.byClass(TEAM_CLASS)) {
            if (team.getInt("m_iTeamNum", -1) == side.number()) {
                return team;
            }
        }
        return null;
    }

    public int teamScore(Team side) {
        Entity team = teamEntity(side);
        return team == null ? 0 : team.getInt("m_iScore", 0);
    }

    // ---------------------------------------------------------------- players

    /** All player controllers currently known. */
    public List<Entity> controllers() {
        return entities.byClass(CONTROLLER_CLASS);
    }

    /**
     * Resolves the {@code userid} key of game events. In CS2 this is the player slot, and the
     * controller of a slot is the entity with index {@code slot + 1}.
     */
    public Player playerBySlot(int userId) {
        Entity controller = controllerBySlot(userId);
        return controller == null ? null : player(controller);
    }

    public Entity controllerBySlot(int userId) {
        if (userId < 0 || userId >= NO_USER) {
            return null;
        }
        Entity controller = entities.get(userId + 1);
        return controller != null && controller.className().equals(CONTROLLER_CLASS) ? controller : null;
    }

    /** Side of the player in the given slot at this moment. */
    public Team teamBySlot(int userId) {
        Entity controller = controllerBySlot(userId);
        return controller == null ? null : Team.fromNumber(controller.getInt("m_iTeamNum", 0));
    }

    /** Resolves a pawn handle (e.g. {@code userid_pawn}) to the player controlling it. */
    public Player playerByPawnHandle(long handle) {
        Entity pawn = entities.byHandle(handle);
        if (pawn == null) {
            return null;
        }
        for (Entity controller : controllers()) {
            long pawnHandle = controller.getLong("m_hPlayerPawn", EntityManager.INVALID_HANDLE);
            if ((pawnHandle & EntityManager.INDEX_MASK) == pawn.index()) {
                return player(controller);
            }
        }
        return null;
    }

    /** Builds (and registers) the {@link Player} for a controller entity. */
    public Player player(Entity controller) {
        long steamId = controller.getLong("m_steamID", 0);
        String name = controller.getString("m_iszPlayerName");
        if (name == null) {
            name = "";
        }
        Player player = new Player(steamId, name, steamId == 0);
        Player known = players.get(player.id());
        if (known != null && known.equals(player)) {
            return known;
        }
        players.put(player.id(), player);
        return player;
    }

    /** Players that were resolved so far, without spectators/recorders unless they were referenced. */
    public List<Player> knownPlayers() {
        return new ArrayList<>(players.values());
    }
}
