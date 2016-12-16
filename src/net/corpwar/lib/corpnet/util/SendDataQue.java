/**************************************************************************
 * CorpNet
 * Copyright (C) 2016 Daniel Ekedahl
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
package net.corpwar.lib.corpnet.util;

import net.corpwar.lib.corpnet.NetworkSendType;

public class SendDataQue {

    // Data to send
    private byte[] aByte;

    // What kind of message to send
    private NetworkSendType networkSendType;

    // When this data was added for sending
    private long addedTime;

    public SendDataQue() {
    }

    public SendDataQue setValues(byte[] bytes, NetworkSendType networkSendType) {
        this.aByte = bytes;
        this.networkSendType = networkSendType;
        this.addedTime = System.currentTimeMillis();
        return this;
    }

    public byte[] getaByte() {
        return aByte;
    }

    public NetworkSendType getNetworkSendType() {
        return networkSendType;
    }

    public long getAddedTime() {
        return addedTime;
    }
}
