package net.corpwar.lib.corpnet;

import java.util.UUID;

/**
 * GhostNet
 * Created by Ghost on 2014-10-18.
 */
public class Message {

    private byte[] data;
    private NetworkSendType networkSendType;
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

    public UUID getConnectionID() {
        return connectionID;
    }

    public void setConnectionID(UUID connectionID) {
        this.connectionID = connectionID;
    }
}
