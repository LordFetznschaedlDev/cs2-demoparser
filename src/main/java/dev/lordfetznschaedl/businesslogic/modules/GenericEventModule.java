package dev.lordfetznschaedl.businesslogic.modules;

import dev.lordfetznschaedl.businesslogic.gameevents.RawGameEvent;
import dev.lordfetznschaedl.entities.events.GenericGameEvent;

import java.util.Set;

/**
 * Records game events as untyped {@link GenericGameEvent}s. Not part of the default modules.
 */
public final class GenericEventModule implements ParserModule {

    private final Set<String> names;

    /** Records every game event. */
    public GenericEventModule() {
        this(Set.of());
    }

    /**
     * @param names event names to record (e.g. {@code item_pickup}); empty records all events
     */
    public GenericEventModule(Set<String> names) {
        this.names = Set.copyOf(names);
    }

    @Override
    public void onGameEvent(RawGameEvent e, ParserContext ctx) {
        if (names.isEmpty() || names.contains(e.name())) {
            ctx.emit(new GenericGameEvent(e.tick(), ctx.currentRound(), e.name(), e.data()));
        }
    }
}
