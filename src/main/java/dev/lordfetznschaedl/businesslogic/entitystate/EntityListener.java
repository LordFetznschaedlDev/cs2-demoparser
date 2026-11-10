package dev.lordfetznschaedl.businesslogic.entitystate;

import java.util.Set;

/**
 * Receives entity lifecycle notifications. All methods default to no-ops.
 */
public interface EntityListener {

    default void onEntityCreated(Entity entity, int tick) {
    }

    /**
     * @param changed names of the properties that were written by this update
     */
    default void onEntityUpdated(Entity entity, Set<String> changed, int tick) {
    }

    default void onEntityDeleted(Entity entity, int tick) {
    }
}
