package dev.lordfetznschaedl.businesslogic.sendtables;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed form of a networked variable type string such as
 * {@code CNetworkUtlVectorBase< CHandle< CBasePlayerPawn > >} or {@code uint8[4]}.
 *
 * @param baseType    the outer type name
 * @param genericType the template argument, if any
 * @param pointer     whether the type is a pointer
 * @param count       fixed array size, {@code 0} if the type is not an array
 */
public record FieldType(String baseType, FieldType genericType, boolean pointer, int count) {

    private static final Pattern PATTERN = Pattern.compile("([^<\\[*]+)(<\\s(.*)\\s>)?(\\*)?(\\[(.*)])?");

    private static final Map<String, Integer> NAMED_COUNTS = Map.of(
            "MAX_ITEM_STOCKS", 8,
            "MAX_ABILITY_DRAFT_ABILITIES", 48);

    public static FieldType parse(String name) {
        Matcher m = PATTERN.matcher(name.trim());
        if (!m.matches()) {
            return new FieldType(name.trim(), null, false, 0);
        }
        String base = m.group(1).trim();
        FieldType generic = m.group(3) != null ? parse(m.group(3)) : null;
        boolean pointer = m.group(4) != null;
        int count = 0;
        if (m.group(6) != null) {
            String countText = m.group(6).trim();
            Integer named = NAMED_COUNTS.get(countText);
            if (named != null) {
                count = named;
            } else {
                try {
                    count = Integer.parseInt(countText);
                } catch (NumberFormatException e) {
                    count = 1024;
                }
            }
        }
        return new FieldType(base, generic, pointer, count);
    }
}
