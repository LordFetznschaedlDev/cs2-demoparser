package dev.lordfetznschaedl;

import dev.lordfetznschaedl.businesslogic.DemoAnalyzer;
import dev.lordfetznschaedl.businesslogic.modules.BombModule;
import dev.lordfetznschaedl.businesslogic.modules.CombatModule;
import dev.lordfetznschaedl.businesslogic.modules.GrenadeModule;
import dev.lordfetznschaedl.businesslogic.modules.ParserModule;
import dev.lordfetznschaedl.businesslogic.modules.PlayerActivityModule;
import dev.lordfetznschaedl.businesslogic.modules.RoundModule;
import dev.lordfetznschaedl.businesslogic.modules.ScoreboardModule;
import dev.lordfetznschaedl.entities.Match;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Entry point of the library: parses a Counter-Strike 2 demo file into a {@link Match}.
 *
 * <pre>{@code
 * Match match = DemoParser.create().parse(Path.of("match.dem"));
 * System.out.println(match.mapName() + " " + match.finalScore());
 * match.scoreboard().entries().forEach(System.out::println);
 * }</pre>
 *
 * What is extracted is defined by {@link ParserModule}s. {@link #create()} uses the default set;
 * use the {@link #builder()} to add optional modules (e.g. {@code WeaponFireModule},
 * {@code GenericEventModule}) or your own:
 *
 * <pre>{@code
 * DemoParser parser = DemoParser.builder()
 *         .withDefaultModules()
 *         .module(WeaponFireModule::new)
 *         .build();
 * }</pre>
 *
 * A {@code DemoParser} is immutable and thread safe; every parse gets fresh module instances.
 */
public final class DemoParser {

    private final List<Supplier<? extends ParserModule>> modules;
    private final boolean strict;

    private DemoParser(List<Supplier<? extends ParserModule>> modules, boolean strict) {
        this.modules = List.copyOf(modules);
        this.strict = strict;
    }

    /** A parser with the {@linkplain Builder#withDefaultModules() default modules}. */
    public static DemoParser create() {
        return builder().withDefaultModules().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Match parse(Path demoFile) throws IOException {
        try (InputStream in = Files.newInputStream(demoFile)) {
            return parse(in);
        }
    }

    /** Parses a demo from a stream. The stream is consumed but not closed. */
    public Match parse(InputStream demo) throws IOException {
        List<ParserModule> instances = new ArrayList<>(modules.size());
        for (Supplier<? extends ParserModule> supplier : modules) {
            instances.add(supplier.get());
        }
        return new DemoAnalyzer(instances, strict).analyze(new NonClosingInputStream(demo));
    }

    /** Like {@link #parse(Path)} but throws {@link UncheckedIOException}, convenient in streams. */
    public Match parseUnchecked(Path demoFile) {
        try {
            return parse(demoFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static final class Builder {

        private final List<Supplier<? extends ParserModule>> modules = new ArrayList<>();
        private boolean strict;

        private Builder() {
        }

        /**
         * Adds the default modules: rounds, kills/damage/flashes, bomb, grenades, team changes and
         * disconnects, and the scoreboard.
         */
        public Builder withDefaultModules() {
            modules.add(RoundModule::new);
            modules.add(CombatModule::new);
            modules.add(BombModule::new);
            modules.add(GrenadeModule::new);
            modules.add(PlayerActivityModule::new);
            modules.add(ScoreboardModule::new);
            return this;
        }

        /**
         * Adds a module. A supplier is used because modules keep per-demo state; it is called once per parse.
         * Modules are notified in the order they were added.
         */
        public Builder module(Supplier<? extends ParserModule> module) {
            modules.add(module);
            return this;
        }

        /**
         * In strict mode malformed data aborts the parse with an exception. By default problems are
         * recorded in {@link Match#warnings()} and parsing continues.
         */
        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        public DemoParser build() {
            return new DemoParser(modules, strict);
        }
    }

    private static final class NonClosingInputStream extends java.io.FilterInputStream {
        NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            // the caller owns the stream
        }
    }
}
