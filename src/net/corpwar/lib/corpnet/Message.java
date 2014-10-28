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

import java.util.UUID;

/**
 * Receiving message with data, who sent, sequence number and what kind of network type
 */
public class Message {

    // The data that was received
    private byte[] data;

    // Network type that was sent
    private NetworkSendType networkSendType;

    // Sequence id of message
    private int sequenceId;

    // connection id
    private UUID connectionID;

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public NetworkSendType getNetworkSendType() {
        return networkSendType;
    }

    public void setNetworkSendType(NetworkSendType networkSendType) {
        this.networkSendType = networkSendType;
    }

    public int getSequenceId() {
        return sequenceId;
    }

    public void setSequenceId(int sequenceId) {
        this.sequenceId = sequenceId;
    }

    public UUID getConnectionID() {
        return connectionID;
    }

    public void setConnectionID(UUID connectionID) {
        this.connectionID = connectionID;
    }
}
