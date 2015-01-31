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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handle every connection so we can send data correct
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
    private int localSequenceNumber = 1;

    // The last sent packages that waiting for ack
    private Map<Integer, NetworkPackage> networkPackageArrayMap = new ConcurrentHashMap<Integer, NetworkPackage>(100);

    // The last 100 received packages
    private SizedStack<Integer> receivedPackageStack = new SizedStack<Integer>(500);

    // If keep alive are enabled use this to check when to send a new ping to this connection
    private long nextKeepAlive;

    // How long have the last 15 round trips taken
    private final SizedStack<Long> roundTripTimes = new SizedStack<Long>(15);

    // How long did the last ping take
    private long lastPingTime;

    // if we need to split a message, what sequence number should the message get
    private int globalSplitSequenceNumber = 1;

    // Temporary byte for split messages
    private Map<Integer, List<SplitMessage>> splitMessageData = new ConcurrentHashMap<Integer, List<SplitMessage>>();

    //
    private ByteArrayOutputStream splitMessageoutputStream = new ByteArrayOutputStream();

    public Connection() {

    }

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
        NetworkPackage networkPackage;
        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.PING) {
            networkPackage = new NetworkPackage(localSequenceNumber, data, sendType);
            networkPackageArrayMap.put(localSequenceNumber, networkPackage);
        } else if (sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
            networkPackage = new NetworkPackage(localSequenceNumber, data, sendType, globalSplitSequenceNumber);
            networkPackageArrayMap.put(localSequenceNumber, networkPackage);
        } else {
            networkPackage = new NetworkPackage(localSequenceNumber);
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

    public long getNextKeepAlive() {
        return nextKeepAlive;
    }

    public void setNextKeepAlive(long nextKeepAlive) {
        this.nextKeepAlive = nextKeepAlive;
    }

    public Map<Integer, NetworkPackage> getNetworkPackageArrayMap() {
        return networkPackageArrayMap;
    }

    public boolean isReceivedPackageStack(Integer receivedPackageSequence) {
        return this.receivedPackageStack.contains(receivedPackageSequence);
    }

    public void setReceivedPackageStack(Integer receivedPackageSequence) {
        this.receivedPackageStack.push(receivedPackageSequence);
    }

    public SizedStack<Long> getRoundTripTimes() {
        return roundTripTimes;
    }

    public long getSmoothRoundTripTime() {
        long totalTime = 0;
        for (int i = roundTripTimes.size() - 1; i >= 0; i--) {
            totalTime += roundTripTimes.get(i);
        }
        if (roundTripTimes.isEmpty()) {
            return 0;
        } else {
            return totalTime / roundTripTimes.size();
        }
    }

    public long getLastPingTime() {
        return lastPingTime;
    }

    public void setLastPingTime(long lastPingTime) {
        this.lastPingTime = lastPingTime;
    }

    public int getGlobalSplitSequenceNumber() {
        if (globalSplitSequenceNumber == Integer.MAX_VALUE)
            globalSplitSequenceNumber = 1;
        return globalSplitSequenceNumber++;
    }

    public ByteArrayOutputStream getSplitMessageoutputStream() {
        return splitMessageoutputStream;
    }

    public void setSplitMessageoutputStream(ByteArrayOutputStream splitMessageoutputStream) {
        this.splitMessageoutputStream = splitMessageoutputStream;
    }

    public byte[] setSplitMessageData(int splitId, int messageId, byte[] data) {
        if (splitMessageData.containsKey(splitId)) {
            SplitMessage splitMessage = new SplitMessage(messageId, data);
            List<SplitMessage> splitMessages = splitMessageData.get(splitId);
            if (splitMessages.contains(splitMessage)) {
                return new byte[0];
            }
            if (!splitMessages.contains(splitMessage)) {
                splitMessages.add(splitMessage);
            }
            Collections.sort(splitMessages);
            splitMessageoutputStream.reset();
            for (int i = splitMessages.size() - 1; i >= 0; i--) {
                try {
                    splitMessageoutputStream.write(splitMessages.get(i).getData());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            byte[] alldata = splitMessageoutputStream.toByteArray();
            int hashCode = ByteBuffer.wrap(alldata, alldata.length - 4, 4).getInt();
            byte[] alldata2 = Arrays.copyOfRange(alldata, 0, alldata.length - 4);
            if (Arrays.hashCode(alldata2) == hashCode) {
                splitMessageData.remove(messageId);
                return alldata2;
            }
        } else {
            SplitMessage splitMessage = new SplitMessage(messageId, data);
            List<SplitMessage> splitMessages = new ArrayList<SplitMessage>();
            splitMessages.add(splitMessage);
            splitMessageData.put(splitId, splitMessages);
        }
        return new byte[0];
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
