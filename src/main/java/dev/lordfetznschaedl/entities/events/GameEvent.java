package dev.lordfetznschaedl.entities.events;

/**
 * Something that happened at a specific moment of the match.
 * <p>
 * Every event knows the {@link #tick()} it happened at and the {@link #round()} it belongs to
 * ({@code 0} = warmup / before the match started). Custom modules may define their own event types
 * by implementing this interface.
 */
public interface GameEvent {

    int tick();

    int round();
}
