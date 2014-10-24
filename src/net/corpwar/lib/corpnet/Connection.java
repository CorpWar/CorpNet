package net.corpwar.lib.corpnet;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.UUID;

/**
 * CorpNet
 * Created by Ghost on 2014-10-07.
 */
public class Connection {

    // ID that identify the connection instead of address and port
    private UUID connectionId = UUID.randomUUID();

    // Address the client are having
    private InetAddress address;

    // Port the client are using
    private int port;

    // When was the last time the client received an response
    private long lastRecived;

    // Unique number for every new packet that is sent
    private Integer localSequenceNumber = 1;

    // The last sent packages that waiting for ack
    private HashMap<Integer, NetworkPackage> networkPackageArrayMap = new HashMap<>(100);

    public Connection() {}

    public Connection(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    public Connection(Connection connection) {
        this.address = connection.getAddress();
        this.port = connection.getPort();
        this.lastRecived = System.currentTimeMillis();
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public void updateClient(InetAddress adress, int port) {
        this.address = adress;
        this.port = port;
    }

    public void updateTime() {
        this.lastRecived = System.currentTimeMillis();
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public long getLastRecived() {
        return lastRecived;
    }

    public NetworkPackage getLastSequenceNumber(byte[] data, NetworkSendType sendType) {
        NetworkPackage networkPackage = new NetworkPackage(localSequenceNumber, data, sendType);
        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.PING) {
            networkPackageArrayMap.put(localSequenceNumber, networkPackage);
        }

        if (localSequenceNumber == Integer.MAX_VALUE) {
            localSequenceNumber = 1;
        } else {
            localSequenceNumber++;
        }
        return networkPackage;
    }

    public NetworkPackage getLastSequenceNumber() {
        return getLastSequenceNumber(new byte[0], NetworkSendType.UNRELIABLE_GAME_DATA);
    }

    public HashMap<Integer, NetworkPackage> getNetworkPackageArrayMap() {
        return networkPackageArrayMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Connection connection = (Connection) o;

        if (port != connection.port) return false;
        if (!address.equals(connection.address)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + port;
        return result;
    }
}