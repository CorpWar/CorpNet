package net.corpwar.lib.corpnet;

/**
 * GhostNet
 * Created by Ghost on 2014-10-09.
 */
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
