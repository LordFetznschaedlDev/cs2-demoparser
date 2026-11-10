package dev.lordfetznschaedl.businesslogic.sendtables;

import dev.lordfetznschaedl.businesslogic.io.BitReader;

/**
 * Decodes a single entity property value from the bit stream.
 */
@FunctionalInterface
public interface FieldDecoder {

    Object decode(BitReader reader);
}
