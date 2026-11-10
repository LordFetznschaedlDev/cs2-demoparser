package dev.lordfetznschaedl.businesslogic.gameevents;

import java.util.List;

/**
 * Describes the layout of one game event type as announced by the server in
 * {@code CMsgSource1LegacyGameEventList}.
 *
 * @param id       numeric event id used by subsequent events
 * @param name     event name, e.g. {@code player_death}
 * @param keyNames ordered key names; the n-th key of an event belongs to the n-th name
 */
public record GameEventDescriptor(int id, String name, List<String> keyNames) {
}
