package dev.lordfetznschaedl;

import dev.lordfetznschaedl.businesslogic.io.DemoFormatException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void rejectsEmptyInput() {
        assertThrows(java.io.EOFException.class, () -> DemoParser.create().parse(new ByteArrayInputStream(new byte[0])));
    }
}
