package dev.lordfetznschaedl.businesslogic.demo;

/**
 * Ids of the network messages embedded in demo packets that the parser handles
 * (from Valve's netmessages.proto / gameevents.proto).
 */
public final class NetMessageType {

    public static final int NET_TICK = 4;
    public static final int SVC_SERVER_INFO = 40;
    public static final int SVC_CREATE_STRING_TABLE = 44;
    public static final int SVC_UPDATE_STRING_TABLE = 45;
    public static final int SVC_CLEAR_ALL_STRING_TABLES = 51;
    public static final int SVC_PACKET_ENTITIES = 55;
    public static final int GE_SOURCE1_LEGACY_GAME_EVENT_LIST = 205;
    public static final int GE_SOURCE1_LEGACY_GAME_EVENT = 207;

    private NetMessageType() {
    }
}
