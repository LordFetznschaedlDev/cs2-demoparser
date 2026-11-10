package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.entitystate.Entity;
import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;

import java.util.Set;

/**
 * A pluggable unit of analysis. Modules receive the decoded demo data (game events and entity
 * changes) and contribute to the resulting match through the {@link ParserContext}, typically by
 * {@linkplain ParserContext#emit emitting} typed events or filling the {@link MatchBuilder}.
 * <p>
 * All callbacks have empty default implementations, so a module only overrides what it needs.
 */
public interface ParserModule {

    /** Called once before the first frame is read. */
    default void onStart(ParserContext context) {
    }

    /** Called when the demo advances to a new tick, before that tick's data is processed. */
    default void onTick(int tick, ParserContext context) {
    }

    /** Called for every game event (e.g. {@code player_death}). */
    default void onGameEvent(RawGameEvent event, ParserContext context) {
    }

    default void onEntityCreated(Entity entity, ParserContext context) {
    }

    /**
     * @param changed names of the properties written by this update
     */
    default void onEntityUpdated(Entity entity, Set<String> changed, ParserContext context) {
    }

    default void onEntityDeleted(Entity entity, ParserContext context) {
    }

    /** Called once after the last frame; the place to write final results into the {@link MatchBuilder}. */
    default void onFinish(ParserContext context) {
    }
}
