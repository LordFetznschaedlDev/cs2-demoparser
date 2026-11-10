package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;
import dev.lordfetznschaedl.entities.Player;
import dev.lordfetznschaedl.entities.Team;
import dev.lordfetznschaedl.entities.events.PlayerDisconnectEvent;
import dev.lordfetznschaedl.entities.events.PlayerTeamChangeEvent;

/**
 * Team changes and disconnects.
 */
public final class PlayerActivityModule implements ParserModule {

    @Override
    public void onGameEvent(RawGameEvent e, ParserContext ctx) {
        switch (e.name()) {
            case "player_team" -> {
                if (!e.getBoolean("disconnect")) {
                    ctx.emit(new PlayerTeamChangeEvent(e.tick(), ctx.currentRound(),
                            ctx.playerBySlot(e.getInt("userid", -1)),
                            Team.fromNumber(e.getInt("oldteam", 0)),
                            Team.fromNumber(e.getInt("team", 0))));
                }
            }
            case "player_disconnect" -> {
                // the event carries the identity itself; the controller may already be gone
                long xuid = parseXuid(e.data().get("xuid"));
                String name = e.getString("name");
                Player player = name != null ? new Player(xuid, name, xuid == 0) : ctx.playerBySlot(e.getInt("userid", -1));
                ctx.emit(new PlayerDisconnectEvent(e.tick(), ctx.currentRound(), player, e.getInt("reason", 0)));
            }
            default -> {
            }
        }
    }

    private static long parseXuid(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseUnsignedLong(s.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
