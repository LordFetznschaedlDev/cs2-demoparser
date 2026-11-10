package dev.lordfetznschaedl.businesslogic.sendtables;

import dev.lordfetznschaedl.businesslogic.io.BitReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuantizedFloatDecoderTest {

    @Test
    void dropsRoundDownFlagWhenLowIsExact() {
        // m_flFriction: 8 bits, low 0 (implicit), high 4, ROUND_DOWN. Low quantizes exactly, so no flag bit is sent.
        QuantizedFloatDecoder d = new QuantizedFloatDecoder(8, 1, null, 4f);
        BitReader r = new BitReader(new byte[]{64});
        float value = d.decodeFloat(r);
        assertEquals(1f, value, 0.01f);
        assertEquals(0, r.remainingBits());
    }

    @Test
    void keepsEncodeZeroFlagWhenZeroIsNotExact() {
        // positions: 20 bits in [-16384, 16384] with ENCODE_ZERO; a set flag bit means exactly 0
        QuantizedFloatDecoder d = new QuantizedFloatDecoder(20, 4, -16384f, 16384f);
        BitReader r = new BitReader(new byte[]{1});
        assertEquals(0f, d.decodeFloat(r));
        assertEquals(7, r.remainingBits());
    }

    @Test
    void decodesRange() {
        QuantizedFloatDecoder d = new QuantizedFloatDecoder(8, 0, 0f, 255f);
        assertEquals(255f, d.decodeFloat(new BitReader(new byte[]{(byte) 0xFF})), 0.001f);
        assertEquals(0f, d.decodeFloat(new BitReader(new byte[]{0})), 0.001f);
    }
}
