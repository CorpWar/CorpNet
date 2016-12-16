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
package net.corpwar.lib.corpnet;

import net.corpwar.lib.corpnet.master.*;
import net.corpwar.lib.corpnet.util.PeerConnected;
import net.corpwar.lib.corpnet.util.PeerList;
import net.corpwar.lib.corpnet.util.SendDataQue;
import net.corpwar.lib.corpnet.util.SerializationUtils;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PeerToPeer {

    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    private Random random = new Random();

    // Default protocol version name
    private static final String protocalVersion = "Protocal 0.1";

    // Hash value of the protocal, to sort out incorrect package, should be same on client
    private int protocolVersionHash = protocalVersion.hashCode();

    // Max size that can be sent in one package
    private final int bufferSize = 512;

    /**
     * This should be same on all clients
     * 4 byte protocol
     * 1 byte type of package
     * 4 byte sequence ID
     */
    private final static int byteBufferSize = 9;

    // How long time in milliseconds it must pass before we try to resend data
    private long millisecondsBetweenResend = 500;

    // How long time in milliseconds it must pass before a disconnect
    private long millisecondToTimeout = 20000;

    // How long it should wait between every check for disconnect and resend data. This should be lower or same as millisecondsBetweenResend
    private long millisecondsToRecheckConnection = 20;

    // Port the peer should use
    private int port = 7854;

    // Ip the peer will listen on
    private String ipAddress = "127.0.0.1";

    // If we should randomly create a port to peer
    private final boolean randomPeerPort = true;

    // What port to start random from
    private final int randomPeerPortStart = 20000;

    // Amount of ports to use when random a port
    private final int randomPeerPortToUse = 10000;

    // How many concurrent connection the peer can handle default is 16
    private int maxConnections;

    // Keep the connection alive if possible
    private boolean keepAlive = true;

    // If the peer are running
    private boolean running = false;

    private byte[] buffer = new byte[bufferSize];
    private ByteBuffer byteBuffer;
    private DatagramSocket datagramSocket = null;
    private DatagramPacket incoming = null;
    private NetworkPackage sendingPackage;

    private PeerToPeer.PeerThread peerThread;
    private Map<UUID, Connection> peers;
    private Connection tempConnection = new Connection();
    private PeerToPeer.HandleConnection handleConnection = new PeerToPeer.HandleConnection();

    private final List<PeerReceiverListener> peerReceiverListeners = new ArrayList<>();
    private final Message message = new Message();

    // If we want to simulate delay when we try internaly
    private boolean simulateDelay = false;

    // How much delay should we simulate
    private long simulateDelayTimeMin = 100, simulateDelayTimeMax = 500, simulatedDelay = 0;


    // Handle if connecting to a master server to handle hole punching
    private Connection masterServer;
    private Peers masterServerPeerList = null;
    private PeerList peerList = new PeerList();

    public PeerToPeer() {
        if (randomPeerPort) {
            port = random.nextInt(randomPeerPortToUse) + randomPeerPortStart;
        }
        byteBuffer = ByteBuffer.allocate(byteBufferSize);
        peers = new ConcurrentHashMap<>(maxConnections);
        maxConnections = 20;
        protocolVersionHash = protocalVersion.hashCode();
    }

    public PeerToPeer(int port, String ipAddress, int maxConnections) {
        this.port = port;
        this.ipAddress = ipAddress;
        byteBuffer = ByteBuffer.allocate(byteBufferSize);
        peers = new ConcurrentHashMap<>(maxConnections);
        this.maxConnections = maxConnections;
        protocolVersionHash = protocalVersion.hashCode();
    }

    /**
     * Start server if it's not running
     */
    public void startPeer() {
        if (peerThread == null || !peerThread.isAlive()) {
            running = true;
            peerThread = new PeerThread();
            peerThread.start();
        }
        if (!handleConnection.isAlive()) {
            handleConnection.start();
        }
    }

    public void connectToPeer(int port, String ipAddress) {
        try {
            Connection connection = new Connection(InetAddress.getByName(ipAddress), port);
            connection.updateTime();
            peers.put(connection.getConnectionId(), connection);
            connection.addToSendQue(SerializationUtils.getInstance().serialize("Knock knock peer"), NetworkSendType.PEER_DATA);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void connectToMasterServer(String ipNumber, int port) {
        try {
            masterServer = new Connection(InetAddress.getByName(ipNumber), port);
            masterServer.updateTime();
            masterServer.addToSendQue(SerializationUtils.getInstance().serialize("Knock knock master server"), NetworkSendType.PEER_DATA);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void registerToMasterServer() {
        if (masterServer != null) {
            masterServer.addToSendQue(SerializationUtils.getInstance().serialize(new RegisterPeer()), NetworkSendType.PEER_DATA);
        }
    }

    public void testNatViaMasterServer() {
        if (masterServer != null) {
            masterServer.addToSendQue(SerializationUtils.getInstance().serialize(new TestNat()), NetworkSendType.PEER_DATA);
        }
    }

    public void requestPeerList() {
        if (masterServer != null) {
            masterServer.addToSendQue(SerializationUtils.getInstance().serialize(new RetrievePeerList()), NetworkSendType.PEER_DATA);
        }
    }

    public void connectToPeerViaMasterServer(UUID peerToConnect) {
        if (masterServer != null) {
            masterServer.addToSendQue(SerializationUtils.getInstance().serialize(new ConnectToPeer(peerToConnect)), NetworkSendType.PEER_DATA);
        }
    }

    public Peers retrieveMasterServerList() {
        if (masterServer != null) {
            requestPeerList();
            masterServerPeerList = null;
            int i = 0;
            while (i < 15) {
                if (masterServerPeerList != null) {
                    break;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                i++;
            }
        }
        return masterServerPeerList;
    }


    /**
     * Kill the server if it's running
     */
    public void killServer() {
        if (peerThread.isAlive() ) {
            running = false;
            datagramSocket.close();
            peerThread.interrupt();
            handleConnection.interrupt();
        }
    }


    /**
     * Send an unreliable message to all clients that is connected
     * @param sendObject
     */
    public <T> void sendUnreliableObjectToAllClients(T sendObject) {
        sendUnreliableToAllClients(SerializationUtils.getInstance().serialize(sendObject));
    }

    /**
     * Use this if you don't care if the message get to the client
     * @param dataToSend
     */
    public void sendUnreliableToAllClients(byte[] dataToSend) {
        for (Connection connection : peers.values()) {
            connection.addToSendQue(dataToSend, NetworkSendType.UNRELIABLE_GAME_DATA);
        }
    }

    public <T> void sendUnreliableObjectToClient(T sendObject, UUID clientID) {
        sendUnreliableToClient(SerializationUtils.getInstance().serialize(sendObject), clientID);
    }

    /**
     * Use this if you don't care if the message get to the client
     * @param dataToSend
     * @param clientID
     */
    public void sendUnreliableToClient(byte[] dataToSend, UUID clientID) {
        peers.get(clientID).addToSendQue(dataToSend, NetworkSendType.UNRELIABLE_GAME_DATA);
    }

    /**
     * Send unreliable messages to all clients except those in the list
     * @param sendObject
     * @param excludedClients
     */
    public <T> void sendUnreliableObjectToAllExcept(T sendObject, List<UUID> excludedClients) {
        sendUnreliableToAllExcept(SerializationUtils.getInstance().serialize(sendObject), excludedClients);
    }

    /**
     * Send unreliable messages to all except those in the listUse this if it is rely important the message get to the client except the client with the uuid
     * @param dataToSend
     * @param exceptClients
     */
    public void sendUnreliableToAllExcept(byte[] dataToSend, List<UUID> exceptClients) {
        for (Connection connection : peers.values()) {
            if (!exceptClients.contains(connection.getConnectionId())) {
                connection.addToSendQue(dataToSend, NetworkSendType.UNRELIABLE_GAME_DATA);
            }
        }
    }

    /**
     * Send an reliable message to all clients that is connected
     * @param sendObject
     */
    public <T> void sendReliableObjectToAllClients(T sendObject) {
        sendReliableToAllClients(SerializationUtils.getInstance().serialize(sendObject));
    }

    /**
     * Use this if it is rely important the message get to the client
     * @param dataToSend
     */
    public void sendReliableToAllClients(byte[] dataToSend) {
        for (Connection connection : peers.values()) {
            connection.addToSendQue(dataToSend, NetworkSendType.RELIABLE_GAME_DATA);
        }
    }

    public <T> void sendReliableObjectToClient(T sendObject, UUID clientID) {
        sendReliableToClient(SerializationUtils.getInstance().serialize(sendObject), clientID);
    }

    public void sendReliableToClient(byte[] sendObject, UUID clientID) {
        peers.get(clientID).addToSendQue(sendObject, NetworkSendType.RELIABLE_GAME_DATA);
    }

    /**
     * Send Reliable messages to all clients except those in the list
     * @param sendObject
     * @param excludedClients
     */
    public <T> void sendReliableObjectToAllExcept(T sendObject, List<UUID> excludedClients) {
        sendReliableToAllExcept(SerializationUtils.getInstance().serialize(sendObject), excludedClients);
    }

    /**
     * Use this if it is rely important the message get to the client except the client with the uuid
     * @param dataToSend
     * @param exceptClients
     */
    public void sendReliableToAllExcept(byte[] dataToSend, List<UUID> exceptClients) {
        for (Connection connection : peers.values()) {
            if (!exceptClients.contains(connection.getConnectionId())) {
                connection.addToSendQue(dataToSend, NetworkSendType.RELIABLE_GAME_DATA);
            }
        }
    }

    /**
     * You can trigger the resend method just to tell it to send messages that have reached the max limits of a message
     */
    public synchronized void resendData() {
        ByteBuffer byteBufferResend;

        long currentTime = System.currentTimeMillis();
        for (Connection connection : peers.values()) {
            long smoothTime = connection.getSmoothRoundTripTime();
            for (NetworkPackage networkPackage : connection.getNetworkPackageArrayMap().values()) {
                if ((currentTime - networkPackage.getSentTime() - (Math.max(millisecondsBetweenResend, smoothTime) * networkPackage.getResent())) > 0) {
                    try {
                        networkPackage.resendData(networkPackage.getSequenceNumber());
                        if (networkPackage.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBufferResend = ByteBuffer.allocate(byteBufferSize + 4 + networkPackage.getDataSent().length);
                            byteBufferResend.putInt(protocolVersionHash).put((byte) networkPackage.getNetworkSendType().getTypeCode()).putInt(networkPackage.getSequenceNumber()).putInt(networkPackage.getSplitSequenceNumber()).put(networkPackage.getDataSent());
                        } else {
                            byteBufferResend = ByteBuffer.allocate(byteBufferSize + networkPackage.getDataSent().length);
                            byteBufferResend.putInt(protocolVersionHash).put((byte) networkPackage.getNetworkSendType().getTypeCode()).putInt(networkPackage.getSequenceNumber()).put(networkPackage.getDataSent());
                        }
                        byte[] sendData = byteBuffer.array();
                        DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                        datagramSocket.send(dp);
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "Error resend data", e);
                    }
                }
            }
        }
    }

    private synchronized void sendFromQue() {
        if (!running || !peerThread.isAlive() || datagramSocket == null) {
            return;
        }
        for (Connection connection : peers.values()) {
            if (connection.getNextSendQueData()) {
                Iterator<SendDataQue> iter = connection.getSendDataQueList().iterator();
                while (iter.hasNext()) {
                    SendDataQue sendDataQue = iter.next();
                    if (simulateDelay) {
                        if ((System.currentTimeMillis() - sendDataQue.getAddedTime() - simulatedDelay) < 0) {
                            continue;
                        } else {
                            simulatedDelay = ThreadLocalRandom.current().nextLong(simulateDelayTimeMin, simulateDelayTimeMax);
                        }
                    }
                    sendData(connection, sendDataQue.getaByte(), sendDataQue.getNetworkSendType());
                    connection.getSendDataQuePool().giveBack(sendDataQue);
                    iter.remove();
                }
                connection.setLastSentMessageTime(System.currentTimeMillis());
            }
        }
        if (masterServer != null) {
            if (masterServer.getNextSendQueData()) {
                Iterator<SendDataQue> iter = masterServer.getSendDataQueList().iterator();
                while (iter.hasNext()) {
                    SendDataQue sendDataQue = iter.next();
                    sendData(masterServer, sendDataQue.getaByte(), sendDataQue.getNetworkSendType());
                    masterServer.getSendDataQuePool().giveBack(sendDataQue);
                    iter.remove();
                }
                masterServer.setLastSentMessageTime(System.currentTimeMillis());
            }
        }
    }

    /**
     * Send a message to a specific client
     * @param connection
     * @param data
     * @param sendType
     */
    public synchronized void sendData(Connection connection, byte[] data, NetworkSendType sendType) {
        byte[] sendData;
        ByteBuffer byteBufferSendData;
        LOG.log(Level.FINEST, "DataSent: " + data.length + " SendType: " + sendType.name());
        try {
            if (data.length + byteBufferSize <= bufferSize) {
                byteBufferSendData = ByteBuffer.allocate(byteBufferSize + data.length);
                sendingPackage = connection.getNetworkPackage(data, sendType);
                byteBufferSendData.putInt(protocolVersionHash).put((byte) sendType.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).put(data);
                sendData = byteBufferSendData.array();
                DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                datagramSocket.send(dp);
                connection.getNetworkPackagePool().giveBack(sendingPackage);
            } else {
                int len = 0;
                byte[] dataToSend;
                int splitIdSize = 4;
                int splitId = connection.getGlobalSplitSequenceNumber();
                while (true) {
                    if (data.length - len > bufferSize - byteBufferSize - splitIdSize - 4) {
                        dataToSend = Arrays.copyOfRange(data, len, bufferSize - byteBufferSize - splitIdSize - 4 + len);
                        byteBufferSendData = ByteBuffer.allocate(bufferSize - 4);
                        byteBufferSendData.putInt(protocolVersionHash);
                        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBufferSendData.put((byte) NetworkSendType.RELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getNetworkPackage(dataToSend, NetworkSendType.RELIABLE_SPLIT_GAME_DATA);
                        } else if (sendType == NetworkSendType.PEER_DATA || sendType == NetworkSendType.PEER_SPLIT_DATA) {
                            byteBufferSendData.put((byte) NetworkSendType.PEER_SPLIT_DATA.getTypeCode());
                            sendingPackage = connection.getNetworkPackage(dataToSend, NetworkSendType.PEER_SPLIT_DATA);
                        } else {
                            byteBufferSendData.put((byte) NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getNetworkPackage(dataToSend, NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA);
                        }
                        byteBufferSendData.putInt(sendingPackage.getSequenceNumber()).putInt(splitId).put(dataToSend);
                        len += bufferSize - byteBufferSize - splitIdSize - 4;
                        sendData = byteBufferSendData.array();
                        DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                        datagramSocket.send(dp);
                        connection.getNetworkPackagePool().giveBack(sendingPackage);
                    } else {
                        dataToSend = Arrays.copyOfRange(data, len, data.length);
                        byteBufferSendData = ByteBuffer.allocate(byteBufferSize + splitIdSize + 4 + dataToSend.length);
                        byteBufferSendData.putInt(protocolVersionHash);
                        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBufferSendData.put((byte) NetworkSendType.RELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getNetworkPackage(dataToSend, NetworkSendType.RELIABLE_SPLIT_GAME_DATA);
                        } else if (sendType == NetworkSendType.PEER_DATA || sendType == NetworkSendType.PEER_SPLIT_DATA) {
                            byteBufferSendData.put((byte) NetworkSendType.PEER_SPLIT_DATA.getTypeCode());
                            sendingPackage = connection.getNetworkPackage(dataToSend, NetworkSendType.PEER_SPLIT_DATA);
                        } else {
                            byteBufferSendData.put((byte) NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getNetworkPackage(dataToSend, NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA);
                        }
                        byteBufferSendData.putInt(sendingPackage.getSequenceNumber()).putInt(splitId).put(dataToSend).putInt(Arrays.hashCode(data));
                        sendData = byteBufferSendData.array();
                        DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                        datagramSocket.send(dp);
                        connection.getNetworkPackagePool().giveBack(sendingPackage);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error send data", e);
        }
    }

    private synchronized void sendAck(Connection connection, int sequenceNumberToAck) {
        try {
            connection.setReceivedPackageStack(sequenceNumberToAck);
            ByteBuffer byteBufferAck = ByteBuffer.allocate(byteBufferSize + 4);
            NetworkPackage sendingPackage = connection.getAckPackage();
            byteBufferAck.putInt(protocolVersionHash).put((byte)NetworkSendType.ACK.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).putInt(sequenceNumberToAck);
            byte[] sendData = byteBufferAck.array();
            DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
            datagramSocket.send(dp);
            connection.getNetworkPackagePool().giveBack(sendingPackage);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error send ack", e);
        }
    }

    private void receivedMessage(Message message) {
        if (message.getNetworkSendType() == NetworkSendType.PEER_DATA || message.getNetworkSendType() == NetworkSendType.PEER_SPLIT_DATA) {
            Object data = SerializationUtils.getInstance().deserialize(message.getData());
            if (data instanceof Peers) {
                masterServerPeerList = (Peers)data;
            } else if (data instanceof ConnectToPeer) {
                connectToPeer(((ConnectToPeer) data).externalPort, ((ConnectToPeer) data).externalIp);
            } else if (data instanceof RetrievePeerList) {
                sendPeerList(peers.get(message.getConnectionID()));
            } else if (data instanceof TestNat) {
                if (((TestNat)data).getWorkingNat()) {
                    peers.get(message.getConnectionID()).addToSendQue(SerializationUtils.getInstance().serialize(new TestMessage()), NetworkSendType.PEER_DATA);
                }
            }
        }
        for (PeerReceiverListener peerReceiverListener : peerReceiverListeners) {
            peerReceiverListener.receivedMessage(message);
        }
    }

    private void disconnectedClients(UUID clientId) {
        for (PeerReceiverListener peerReceiverListener : peerReceiverListeners) {
            peerReceiverListener.disconnected(clientId);
        }
    }

    /**
     * Register peer listener
     * @param peerReceiverListener
     */
    public void registerPeerListerner(PeerReceiverListener peerReceiverListener) {
        peerReceiverListeners.add(peerReceiverListener);
    }

    public Map<UUID, Connection> getPeers() {
        return peers;
    }

    /**
     * Remove clients that haven't send a message for some time
     */
    private synchronized void removeInactiveClients() {
        if (peerThread != null && peerThread.isAlive()) {
            long currentTime = System.currentTimeMillis();
            Iterator<Connection> it = peers.values().iterator();
            while (it.hasNext()) {
                Connection connection = it.next();
                if ((currentTime - connection.getLastRecived()) > millisecondToTimeout) {
                    disconnectedClients(connection.getConnectionId());
                    it.remove();
                }
            }
        }
    }

    /**
     * Keep all connections alive
     */
    private synchronized void keepConnectionsAlive() {
        if (peerThread != null && peerThread.isAlive()) {
            long currentTime = System.currentTimeMillis();
            for (Connection connection : peers.values()) {
                if (currentTime > connection.getNextKeepAlive()) {
                    connection.addToSendQue(new byte[0], NetworkSendType.PING);
                    connection.setNextKeepAlive(System.currentTimeMillis() + (long) (millisecondToTimeout * 0.2f));
                }
            }
        }
    }

    private synchronized void removeInactiveSplitMessages() {
        if (peerThread != null && peerThread.isAlive()) {
            for (Connection connection : peers.values()) {
                connection.removeSplitMessages();
            }
        }
    }

    private synchronized void sendPeerList(Connection connectingPeer) {
        peerList.peerConnected.clear();
        for (Connection connection : peers.values()) {
            peerList.peerConnected.add(new PeerConnected(connection.getAddress().getHostAddress(), connection.getPort(), connection.getSmoothRoundTripTime()));
        }
        if (peerList.peerConnected.size() > 0) {
            connectingPeer.addToSendQue(SerializationUtils.getInstance().serialize(peerList), NetworkSendType.PEER_DATA);
        }
    }

    // Handle all incoming packages
    private class PeerThread extends Thread {

        private NetworkPackage tempPackage;
        private final ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);

        @Override
        public void run() {

            Connection peer;
            try {
                if (ipAddress == null) {
                    datagramSocket = new DatagramSocket(port);
                } else {
                    datagramSocket = new DatagramSocket(port, InetAddress.getByName(ipAddress));
                }
                incoming = new DatagramPacket(buffer, buffer.length);
                LOG.log(Level.FINEST, "Started peer: on port " + port);
            } catch (SocketException e) {
                LOG.log(Level.SEVERE, "Error open socket", e);
            } catch (UnknownHostException e) {
                LOG.log(Level.SEVERE, "Error with host", e);
            }

            while(running) {
                try {
                    datagramSocket.receive(incoming);
                    LOG.log(Level.FINEST, "incoming: " + incoming.getPort() + " type: " + NetworkSendType.fromByteValue(byteBuffer.get(4)));
                    byteBuffer.clear();
                    byteBuffer.limit(incoming.getLength());
                    byteBuffer.put(incoming.getData(), 0, incoming.getLength());
                    byteBuffer.flip();
                    if (byteBuffer.getInt(0) != protocolVersionHash) {
                        continue;
                    }
                    tempConnection.updateClient(incoming.getAddress(), incoming.getPort());
                    // If we get a wrong network type we ignore everything else
                    if (byteBuffer.get(4) == NetworkSendType.ERROR.getTypeCode()) {
                        LOG.severe("ERROR network type from: " + incoming.getAddress() + " port: " + incoming.getPort());
                        continue;
                    }
                    // Anyone should be able to ping the connection if they have the correct protocol
                    if (byteBuffer.get(4) == NetworkSendType.PING.getTypeCode()) {
                        sendAck(tempConnection, byteBuffer.getInt(5));
                        continue;
                    }

                    peer = peers.get(tempConnection.getConnectionId());


                    // Check if we have already received data then send another ack and don't do anything else
                    if (peer != null) {
                        if (byteBuffer.get(4) != NetworkSendType.ACK.getTypeCode() && peer.isReceivedPackageStack(byteBuffer.getInt(5))) {
                            sendAck(tempConnection, byteBuffer.getInt(5));
                            continue;
                        }

                        // check if we have reached max connections
                    } else if (peers.size() < maxConnections ) {
                        Connection newConnection = new Connection(tempConnection);
                        if (keepAlive) {
                            newConnection.setNextKeepAlive(System.currentTimeMillis() + (long)(millisecondToTimeout * 0.2f));
                        }
                        if (masterServer == null || (masterServer != null && !masterServer.equals(tempConnection))) {
                            peers.put(newConnection.getConnectionId(), newConnection);
                            for (PeerReceiverListener peerReceiverListener : peerReceiverListeners) {
                                peerReceiverListener.connected(newConnection);
                            }
                        }
                        peer = newConnection;

                    }

                    if (peer != null) {

                        // Verify that we have received ack otherwise ignore
                        if (byteBuffer.get(4) == NetworkSendType.ACK.getTypeCode()) {
                            if (incoming.getLength() == 13) {
                                verifyAck(peer, byteBuffer.getInt(9)); //ByteBuffer.wrap(data, 9, 13).getInt());
                            }
                            continue;
                        }

                        message.setNetworkSendType(NetworkSendType.fromByteValue(byteBuffer.get(4)));

                        if (message.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA ||
                                message.getNetworkSendType() == NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA ||
                                message.getNetworkSendType() == NetworkSendType.PEER_SPLIT_DATA) {
                            byte[] tempDataBuffer = new byte[byteBuffer.limit() - 13];
                            message.setSequenceId(byteBuffer.getInt(5));
                            message.setSplitMessageId(byteBuffer.getInt(9)); // new BigInteger(Arrays.copyOfRange(data, byteBufferSize, byteBufferSize + 4)).intValue());
                            message.setConnectionID(peer.getConnectionId());
                            byteBuffer.position(13);
                            byteBuffer.get(tempDataBuffer, 0, tempDataBuffer.length);
                            message.setData(tempDataBuffer);

                            if (message.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA || message.getNetworkSendType() == NetworkSendType.PEER_SPLIT_DATA) {
                                sendAck(tempConnection, byteBuffer.getInt(5));
                            }

                            handleSplitMessages(message, peer);
                        } else {
                            byte[] tempDataBuffer = new byte[byteBuffer.limit() - 9];
                            byteBuffer.position(9);
                            byteBuffer.get(tempDataBuffer, 0, tempDataBuffer.length);
                            message.setData(tempDataBuffer);
                            message.setNetworkSendType(NetworkSendType.fromByteValue(byteBuffer.get(4)));
                            message.setSequenceId(byteBuffer.getInt(5));
                            message.setConnectionID(peer.getConnectionId());

                            if (message.getNetworkSendType() == NetworkSendType.RELIABLE_GAME_DATA || message.getNetworkSendType() == NetworkSendType.PEER_DATA) {
                                sendAck(tempConnection, byteBuffer.getInt(5));
                            }

                            receivedMessage(message);
                        }
                        peer.updateTime();
                    }
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Error resend data", e);
                    running = false;
                }
            }
        }

        private void handleSplitMessages(Message message, Connection connection) {
            // If we have started to receive part of split message
            byte[] data = connection.setSplitMessageData(message.getSplitMessageId(), message.getSequenceId(), message.getData());
            if (data.length > 0) {
                message.setData(data);
                receivedMessage(message);
            }
            connection.updateTime();
        }

        private void verifyAck(Connection connection, int sequenceNumberWasAcked) {
            tempPackage = connection.getNetworkPackageArrayMap().remove(sequenceNumberWasAcked);
            if (tempPackage != null) {
                long roundTripTime = System.currentTimeMillis() - tempPackage.getSentTime();
                connection.getRoundTripTimes().push(roundTripTime);
                if (tempPackage.getNetworkSendType() == NetworkSendType.PING) {
                    connection.setLastPingTime(roundTripTime);
                }
                connection.updateTime();
                tempPackage = null;
            }
        }
    }

    private class HandleConnection extends Thread {

        @Override
        public void run() {
            while (running) {
                if (peerThread.isAlive()) {
                    resendData();
                    sendFromQue();
                    removeInactiveClients();
                    keepConnectionsAlive();
                    removeInactiveSplitMessages();
                    try {
                        Thread.sleep(millisecondsToRecheckConnection);
                    } catch (InterruptedException e) {
                        LOG.log(Level.FINE, "Error when sleeping");
                    }
                }
            }
        }
    }

}
