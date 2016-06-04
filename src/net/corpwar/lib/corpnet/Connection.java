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

import net.corpwar.lib.corpnet.util.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

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

    // Pool to handle NetworkPackage objects
    private NetworkPackagePool networkPackagePool = new NetworkPackagePool();

    // The last sent packages that waiting for ack
    private Map<Integer, NetworkPackage> networkPackageArrayMap = new ConcurrentHashMap<Integer, NetworkPackage>(100);

    // The last 500 received packages
    private SizedStack<Integer> receivedPackageStack = new SizedStack<Integer>(500);

    // If keep alive are enabled use this to check when to send a new ping to this connection
    private long nextKeepAlive;

    // How long have the last 40 round trips taken
    private final SizedStack<Long> roundTripTimes = new SizedStack<Long>(40);

    // How long did the last ping take
    private long lastPingTime;

    // To calculate how fast we can send so we don't flood the connection
    private long lastSentMessageTime = 0;

    // If need to drop an unreliable message to flood protection we keep track how many in a row that is happening to
    // let some of them through
    private int droppedUnreliableMessages = 0;

    // When there was an unreliable message sent out
    private long lastAddedUnreliableMessage = 0;

    // All the messages that should be sent
    private Deque<SendDataQue> sendDataQueList = new ConcurrentLinkedDeque<SendDataQue>();

    // Pool to handle all sendDataQues
    private SendDataQuePool sendDataQuePool = new SendDataQuePool();

    // if we need to split a message, what sequence number should the message get
    private int globalSplitSequenceNumber = 1;

    // Temporary byte for split messages
    private Map<Integer, List<SplitMessage>> splitMessageData = new ConcurrentHashMap<Integer, List<SplitMessage>>();

    private ByteArrayOutputStream splitMessageoutputStream = new ByteArrayOutputStream();

    private SplitMessagePool splitMessagePool = new SplitMessagePool();

    private SplitMessageListPool splitMessageListPool = new SplitMessageListPool();

    // A list of split messages that should be removed
    private List<Integer> removeSplitMessages = new ArrayList<Integer>();

    public Connection() {

    }

    public Connection(InetAddress address, int port) {
        this.address = address;
        this.port = port;
        connectionId = UUID.nameUUIDFromBytes((address.toString() + port).getBytes());
    }

    public Connection(Connection connection) {
        this.address = connection.getAddress();
        this.port = connection.getPort();
        this.lastRecived = System.currentTimeMillis();
        connectionId = UUID.nameUUIDFromBytes((address.toString() + port).getBytes());
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public void updateClient(InetAddress adress, int port) {
        this.address = adress;
        this.port = port;
        connectionId = UUID.nameUUIDFromBytes((address.toString() + port).getBytes());
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

    public NetworkPackagePool getNetworkPackagePool() {
        return networkPackagePool;
    }

    public synchronized NetworkPackage getNetworkPackage(byte[] data, NetworkSendType sendType) {
        NetworkPackage networkPackage;
        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.PING) {
            networkPackage = networkPackagePool.borrow();
            networkPackage.setValues(localSequenceNumber, data, sendType);
            networkPackageArrayMap.put(localSequenceNumber, networkPackage);
        } else if (sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
            networkPackage = networkPackagePool.borrow();
            networkPackage.setValues(localSequenceNumber, data, sendType, globalSplitSequenceNumber);
            networkPackageArrayMap.put(localSequenceNumber, networkPackage);
        } else {
            networkPackage = networkPackagePool.borrow();
            networkPackage.setValues(localSequenceNumber, sendType);
        }

        if (localSequenceNumber == Integer.MAX_VALUE) {
            localSequenceNumber = 1;
        } else {
            localSequenceNumber++;
        }
        return networkPackage;
    }

    public NetworkPackage getAckPackage() {
        return getNetworkPackage(new byte[0], NetworkSendType.ACK);
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
            if (roundTripTimes.getMaxSize() < i) {
                continue;
            }
            totalTime += roundTripTimes.get(i);
        }
        if (roundTripTimes.isEmpty()) {
            return 0;
        } else {
            return totalTime / roundTripTimes.size();
        }
    }

    public long getLastSentMessageTime() {
        return lastSentMessageTime;
    }

    public void setLastSentMessageTime(long lastSentMessageTime) {
        this.lastSentMessageTime = lastSentMessageTime;
    }

    public void addToSendQue(byte[] bytes, NetworkSendType networkSendType) {
        SendDataQue sendDataQue = sendDataQuePool.borrow();
        sendDataQue.setValues(bytes, networkSendType);

        // If 2 or less in the que we add the messages else handle specially
        if (sendDataQueList.size() <= 2) {
            sendDataQueList.add(sendDataQue);
            droppedUnreliableMessages = 0;
        } else {
            if (networkSendType == NetworkSendType.RELIABLE_GAME_DATA || networkSendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA || networkSendType == NetworkSendType.PEER_DATA || networkSendType == NetworkSendType.PEER_SPLIT_DATA) {
                sendDataQueList.add(sendDataQue);
            } else if (networkSendType == NetworkSendType.UNRELIABLE_GAME_DATA || networkSendType == NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA) {
                long currentTime = System.currentTimeMillis();
                if (droppedUnreliableMessages > 20 || (currentTime - lastAddedUnreliableMessage - 1000) > 0) {
                    sendDataQueList.add(sendDataQue);
                    droppedUnreliableMessages = 0;
                    lastAddedUnreliableMessage = currentTime;
                } else {
                    droppedUnreliableMessages++;
                }
            } else {
                sendDataQueList.addFirst(sendDataQue);
            }
        }
    }

    public boolean getNextSendQueData() {
        long currentTime = System.currentTimeMillis();
        if (sendDataQueList.size() == 0) {
            return false;
        } else {
            if (lastSentMessageTime - currentTime + getSmoothRoundTripTime() < 0) {
                return true;
            } else {
                return false;
            }
        }
    }

    public Deque<SendDataQue> getSendDataQueList() {
        return sendDataQueList;
    }

    public SendDataQuePool getSendDataQuePool() {
        return sendDataQuePool;
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

    public Map<Integer, List<SplitMessage>> getSplitMessageData() {
        return splitMessageData;
    }

    public synchronized byte[] setSplitMessageData(int splitId, int messageId, byte[] data) {
        if (splitMessageData.containsKey(splitId)) {
            SplitMessage splitMessage = splitMessagePool.borrow();
            splitMessage.setValues(messageId, data);
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
            byte[] receivedData = Arrays.copyOfRange(alldata, 0, alldata.length - 4);
            if (Arrays.hashCode(receivedData) == hashCode) {
                removeSplitMessages.add(splitId);
                removeSplitMessages();
                return receivedData;
            }
        } else {
            SplitMessage splitMessage = splitMessagePool.borrow();
            splitMessage.setValues(messageId, data);
            List<SplitMessage> splitMessages = splitMessageListPool.borrow();
            splitMessages.clear();
            splitMessages.add(splitMessage);
            splitMessageData.put(splitId, splitMessages);
        }
        return new byte[0];
    }

    public synchronized void removeSplitMessages() {
        long currentTime = System.currentTimeMillis();
        Set<Integer> splitMessageKeySet = getSplitMessageData().keySet();
        for (Integer splitId : splitMessageKeySet) {
            if ((getSplitMessageData().size() > 0 &&
                    getSplitMessageData().get(splitId) != null &&
                    getSplitMessageData().get(splitId).get(0) != null) &&
                    (getSplitMessageData().get(splitId).get(0).getCreateTime() + 30000 < currentTime)) {
                removeSplitMessages.add(splitId);
            }
        }
        for (Iterator<Integer> splitId = removeSplitMessages.iterator(); splitId.hasNext();) {
            List<SplitMessage> splitMessageList = splitMessageData.remove(splitId.next());
            if (splitMessageList != null) {
                for (int i = splitMessageList.size() - 1; i >= 0; i--) {
                    splitMessagePool.giveBack(splitMessageList.get(i));
                }
            }
            splitMessageListPool.giveBack(splitMessageList);
            splitId.remove();
        }
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
