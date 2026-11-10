package dev.lordfetznschaedl.businesslogic.entitystate;

import dev.lordfetznschaedl.businesslogic.sendtables.Serializer;

/**
 * A networked entity class, e.g. {@code CCSPlayerController}.
 */
public record ServerClass(int id, String name, Serializer serializer) {
}
