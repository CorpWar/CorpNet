package net.corpwar.lib.corpnet.util;

import net.corpwar.lib.corpnet.NetworkSendType;

/**
 * corpnet
 * Created by Ghost on 2015-10-25.
 */
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
