package dev.lordfetznschaedl.businesslogic.sendtables;

import dev.lordfetznschaedl.businesslogic.io.BitReader;

/**
 * Decoder for Valve's quantized float encoding ({@code CNetworkedQuantizedFloat} and bit-limited floats).
 */
final class QuantizedFloatDecoder implements FieldDecoder {

    private static final int ROUND_DOWN = 1;
    private static final int ROUND_UP = 1 << 1;
    private static final int ENCODE_ZERO = 1 << 2;
    private static final int ENCODE_INTEGERS = 1 << 3;

    private final int bitCount;
    private final int flags;
    private final float low;
    private final float high;
    private final float decodeMultiplier;
    private final float highLowMultiplier;

    QuantizedFloatDecoder(int bitCount, int encodeFlags, Float lowValue, Float highValue) {
        float lowV = lowValue != null ? lowValue : 0f;
        float highV = highValue != null ? highValue : 1f;
        int bits = bitCount;
        int f = validateFlags(encodeFlags, lowV, highV);

        long steps = 1L << bits;
        if ((f & ROUND_DOWN) != 0) {
            highV -= (highV - lowV) / steps;
        } else if ((f & ROUND_UP) != 0) {
            lowV += (highV - lowV) / steps;
        }
        if ((f & ENCODE_INTEGERS) != 0) {
            float delta = Math.max(highV - lowV, 1f);
            int deltaLog2 = (int) Math.ceil(Math.log(delta) / Math.log(2));
            long range2 = 1L << deltaLog2;
            int bc = bits;
            while ((1L << bc) <= range2) {
                bc++;
            }
            if (bc > bits) {
                bits = bc;
                steps = 1L << bits;
            }
            float offset = (float) range2 / steps;
            highV = lowV + range2 - offset;
        }
        this.bitCount = bits;
        this.low = lowV;
        this.high = highV;
        this.decodeMultiplier = 1f / (steps - 1);
        this.highLowMultiplier = computeHighLowMultiplier(bits, highV - lowV);

        // flags are only kept when the special value can not be represented exactly
        if ((f & ROUND_DOWN) != 0 && quantize(lowV) == lowV) {
            f &= ~ROUND_DOWN;
        }
        if ((f & ROUND_UP) != 0 && quantize(highV) == highV) {
            f &= ~ROUND_UP;
        }
        if ((f & ENCODE_ZERO) != 0 && quantize(0f) == 0f) {
            f &= ~ENCODE_ZERO;
        }
        this.flags = f;
    }

    private static float computeHighLowMultiplier(int bits, float range) {
        long highValue = bits == 32 ? 0xFFFFFFFEL : (1L << bits) - 1;
        float high = (float) highValue;
        float multiplier = Math.abs(range) <= 0f ? high : high / range;
        if (multiplier * range > high) {
            for (float factor : new float[]{0.9999f, 0.99f, 0.9f, 0.8f, 0.7f}) {
                multiplier = high / range * factor;
                if (!(multiplier * range > high)) {
                    break;
                }
            }
        }
        return multiplier;
    }

    private float quantize(float value) {
        if (value < low) {
            return low;
        }
        if (value > high) {
            return high;
        }
        long i = (long) ((value - low) * highLowMultiplier) & 0xFFFFFFFFL;
        return low + (high - low) * (i * decodeMultiplier);
    }

    private static int validateFlags(int flags, float low, float high) {
        int f = flags;
        if (f == 0) {
            return 0;
        }
        if ((low == 0f && (f & ROUND_DOWN) != 0) || (high == 0f && (f & ROUND_UP) != 0)) {
            f &= ~ENCODE_ZERO;
        }
        if (low == 0f && (f & ENCODE_ZERO) != 0) {
            f |= ROUND_DOWN;
            f &= ~ENCODE_ZERO;
        }
        if (high == 0f && (f & ENCODE_ZERO) != 0) {
            f |= ROUND_UP;
            f &= ~ENCODE_ZERO;
        }
        if (low > 0f || high < 0f) {
            f &= ~ENCODE_ZERO;
        }
        if ((f & ENCODE_INTEGERS) != 0) {
            f &= ~(ROUND_UP | ROUND_DOWN | ENCODE_ZERO);
        }
        return f;
    }

    @Override
    public Object decode(BitReader r) {
        return decodeFloat(r);
    }

    float decodeFloat(BitReader r) {
        if ((flags & ROUND_DOWN) != 0 && r.readBoolean()) {
            return low;
        }
        if ((flags & ROUND_UP) != 0 && r.readBoolean()) {
            return high;
        }
        if ((flags & ENCODE_ZERO) != 0 && r.readBoolean()) {
            return 0f;
        }
        long raw = r.readBits(bitCount) & 0xFFFFFFFFL;
        return low + (high - low) * raw * decodeMultiplier;
    }
}
