package dev.lordfetznschaedl.businesslogic.sendtables;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldTypeTest {

    @Test
    void parsesSimpleType() {
        FieldType t = FieldType.parse("uint32");
        assertEquals("uint32", t.baseType());
        assertNull(t.genericType());
        assertFalse(t.pointer());
        assertEquals(0, t.count());
    }

    @Test
    void parsesNestedGenerics() {
        FieldType t = FieldType.parse("CNetworkUtlVectorBase< CHandle< CBasePlayerPawn > >");
        assertEquals("CNetworkUtlVectorBase", t.baseType());
        assertEquals("CHandle", t.genericType().baseType());
        assertEquals("CBasePlayerPawn", t.genericType().genericType().baseType());
    }

    @Test
    void parsesPointersAndArrays() {
        assertTrue(FieldType.parse("CCSGameModeRules*").pointer());
        FieldType array = FieldType.parse("int32[30]");
        assertEquals("int32", array.baseType());
        assertEquals(30, array.count());
        assertEquals(8, FieldType.parse("uint32[MAX_ITEM_STOCKS]").count());
    }
}
