package net.corpwar.lib.corpnet;

/**
 * GhostNet
 * Created by Ghost on 2014-10-16.
 *
 * Type must be between -127 and 127 because it is cast to byte type.
 */
public enum NetworkSendType {
    ERROR(-1), // If something went wrong use this
    ACK(0), // Notification that the reliable packed was sent correctly
    RELIABLE_GAME_DATA(20), // Send reliable game data between server and client
    UNRELIABLE_GAME_DATA(21), // Send unriliable gaem data between server and client

    PING(100); // If you just need to ping and tell you are alive, Or for testing

    private int typeCode;

    private NetworkSendType(int tp) {
        typeCode = tp;
    }

    public int getTypeCode() {
        return typeCode;
    }

    public static NetworkSendType fromByteValue(byte type) {
        switch (type) {
            case 0:
                return ACK;
            case 20:
                return RELIABLE_GAME_DATA;
            case 21:
                return UNRELIABLE_GAME_DATA;
            case 100:
                return PING;
            default:
                return ERROR;
        }
    }
}
