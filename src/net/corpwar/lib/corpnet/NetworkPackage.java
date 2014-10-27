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

public class NetworkPackage {

    // Sequence number to keep tracking what package have been sent
    private int sequenceNumber;

    // The data that was sent with this sequence Number so we can resend it if needed
    private byte[] dataSent;

    // When did we send the data
    private long sentTime;

    private NetworkSendType networkSendType;

    public NetworkPackage(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
        sentTime = System.currentTimeMillis();
    }

    public NetworkPackage(int sequenceNumber, byte[] dataSent, NetworkSendType networkSendType) {
        this.sequenceNumber = sequenceNumber;
        this.dataSent = dataSent;
        sentTime = System.currentTimeMillis();
        this.networkSendType = networkSendType;
    }

    public void resendData(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
        sentTime = System.currentTimeMillis();
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public byte[] getDataSent() {
        return dataSent;
    }

    public long getSentTime() {
        return sentTime;
    }

    public NetworkSendType getNetworkSendType() {
        return networkSendType;
    }
}
