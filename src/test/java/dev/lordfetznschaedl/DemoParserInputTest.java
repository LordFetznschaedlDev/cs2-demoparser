package dev.lordfetznschaedl;

import dev.lordfetznschaedl.businesslogic.io.DemoFormatException;
import dev.lordfetznschaedl.entities.Match;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link DemoParser} that do not need the demo files and therefore run everywhere.
 */
class DemoParserInputTest {

    @Test
    void rejectsNonDemoFiles() {
        byte[] garbage = "definitely not a demo file".getBytes(StandardCharsets.US_ASCII);
        assertThrows(DemoFormatException.class, () -> DemoParser.create().parse(new ByteArrayInputStream(garbage)));
    }

    @Test
    void measuresParseDuration() throws java.io.IOException {
        // a minimal valid demo: header magic, the two offsets and a DEM_Stop frame
        byte[] demo = {'P', 'B', 'D', 'E', 'M', 'S', '2', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Match match = DemoParser.create().parse(new ByteArrayInputStream(demo));
        assertTrue(match.parseDuration().isPositive());
        assertTrue(match.events().isEmpty());
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(java.io.EOFException.class, () -> DemoParser.create().parse(new ByteArrayInputStream(new byte[0])));
    }
}
