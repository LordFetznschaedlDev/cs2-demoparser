package dev.lordfetznschaedl.businesslogic.sendtables;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SendTableParserTest {

    private static Field field(String name, String sendNode) {
        Field field = new Field(name, "int32", 0, null, null, 0, null, null);
        field.sendNode = sendNode;
        return field;
    }

    @Test
    void qualifiesFieldsWithTheSameNameByTheirSendNode() {
        Field list = field("m_Attributes", "m_AttributeManager.m_Item.m_AttributeList");
        Field dynamic = field("m_Attributes", "m_AttributeManager.m_Item.m_NetworkedDynamicAttributes");
        Field unique = field("m_iItemDefinitionIndex", "m_AttributeManager.m_Item");

        SendTableParser.disambiguate(List.of(list, dynamic, unique));

        assertEquals("m_AttributeManager.m_Item.m_AttributeList.m_Attributes", list.name());
        assertEquals("m_AttributeManager.m_Item.m_NetworkedDynamicAttributes.m_Attributes", dynamic.name());
        assertEquals("m_iItemDefinitionIndex", unique.name(), "unique names stay unchanged");
    }

    @Test
    void sharedFieldsAreOnlyQualifiedOnce() {
        Field a = field("m_vecX", "m_vecVelocity");
        Field b = field("m_vecX", "m_vecViewOffset");
        SendTableParser.disambiguate(List.of(a, b));
        // the same field objects are used by several serializers
        SendTableParser.disambiguate(List.of(a, b));
        assertEquals("m_vecVelocity.m_vecX", a.name());
        assertEquals("m_vecViewOffset.m_vecX", b.name());
    }
}
