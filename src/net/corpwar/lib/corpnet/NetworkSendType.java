/**************************************************************************
 * CorpNet
 * Copyright (C) 2014 Daniel Ekedahl
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 **************************************************************************/
package net.corpwar.lib.corpnet;

/**
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
