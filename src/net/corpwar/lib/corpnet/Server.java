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

import net.corpwar.lib.corpnet.util.SendDataQue;
import net.corpwar.lib.corpnet.util.SerializationUtils;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Server {

    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    // Default protocal version name
    private static final String protocalVersion = "Protocal 0.1";

    // Hash value of the protocal, to sort out incorrect package, should be same on client
    private int protocalVersionHash = protocalVersion.hashCode();

    // Max size that can be sent in one package
    private int bufferSize = 512;

    /**
     * This should be same as client
     * 4 byte protocol
     * 1 byte type of package
     * 4 byte sequence ID
     */
    private final static int byteBufferSize = 9;

    // How long time in milliseconds it must pass before we try to resend data
    private long milisecoundsBetweenResend = 80;

    // How long time in milliseconds it must pass before a disconnect
    private long milisecoundToTimeout = 20000;

    // How long it should wait between every check for disconnect and resend data. This should be lower or same as millisecondsBetweenResend
    private long millisecondsToRecheckConnection = 20;

    // Port the server will liston on
    private int port;

    // Ip the server will listen on
    private String ipAdress;

    // How many concurrent connection the server can handle default is 8
    private int maxConnections;

    // Keep the connection alive if possible
    private boolean keepAlive = true;

    // Waiting que
    private boolean waitingQue = false;

    // The que with all waiting connections
    private Deque<Connection> waitingQueArray = new ConcurrentLinkedDeque<Connection>();

    // How many are allowed to be in the que
    private int maxWaitingConnections = 10;


    private byte[] buffer = new byte[bufferSize];
    private ByteBuffer byteBuffer;
    private DatagramSocket datagramSocket = null;
    private DatagramPacket incoming = null;
    private NetworkPackage sendingPackage;

    // If the server are running
    private boolean running = false;

    private ServerThread serverThread;
    private Map<UUID, Connection> clients;
    private Connection tempConnection = new Connection();
    private HandleConnection handleConnection = new HandleConnection();


    private final List<DataReceivedListener> dataReceivedListeners = new ArrayList<DataReceivedListener>();
    private final Message message = new Message();

    // If we want to simulate delay when we try internaly
    private boolean simulateDelay = false;

    // How much delay should we simulate
    private long simulateDelayTimeMin = 100, simulateDelayTimeMax = 500, simulatedDelay = 0;


    /**
     * Create new server on port 7854 on localhost with max 8 connections
     */
    public Server() {
        this(7854, "127.0.0.1", 8);
    }

    /**
     * Create new server and set port, ip to liston on and max connections
     * @param port
     * @param ipAdress
     * @param maxConnections
     */
    public Server(int port, String ipAdress, int maxConnections) {
        this(port, ipAdress, maxConnections, protocalVersion);
    }

    /**
     * Create new server and set port, ip to liston on, max connections and protocal version name, version must be same on server and client.
     * @param port
     * @param ipAdress
     * @param maxConnections
     * @param protocalVersionName
     */
    public Server(int port, String ipAdress, int maxConnections, String protocalVersionName) {
        this.port = port;
        this.ipAdress = ipAdress;
        byteBuffer = ByteBuffer.allocate(byteBufferSize);
        clients = new ConcurrentHashMap<UUID, Connection>(maxConnections);
        this.maxConnections = maxConnections;
        protocalVersionHash = protocalVersionName.hashCode();
    }

    /**
     * Return connection from uuid
     * @param uuid
     * @return
     */
    public Connection getConnectionFromUUID(UUID uuid) {
        return clients.get(uuid);
    }

    /**
     * Register server listener
     * @param dataReceivedListener
     */
    public void registerServerListerner(DataReceivedListener dataReceivedListener) {
        dataReceivedListeners.add(dataReceivedListener);
    }

    /**
     * Change the port and ip the server should listen on
     * @param port
     * @param ipAdress
     */
    public void setPortAndIp(int port, String ipAdress) {
        this.port = port;
        this.ipAdress = ipAdress;
    }

    /**
     * OBS! Must be same on both client and server
     * @param protocalVersionHash
     */
    public void setProtocalVersionHash(int protocalVersionHash) {
        this.protocalVersionHash = protocalVersionHash;
    }

    /**
     * How long should server wait before it resend a message. Default are 100 milliseconds
     * @param milisecoundsBetweenResend
     */
    public void setMilisecoundsBetweenResend(long milisecoundsBetweenResend) {
        this.milisecoundsBetweenResend = milisecoundsBetweenResend;
    }

    /**
     * How long the server should wait for a message from a client before it disconnect the client. Default are 20000 milliseconds
     * @param milisecoundToTimeout
     */
    public void setMilisecoundToTimeout(long milisecoundToTimeout) {
        this.milisecoundToTimeout = milisecoundToTimeout;
    }

    /**
     * How long it should wait between every check for disconnect and resend data
     * Default are 20 milliseconds
     * @param millisecondsToRecheckConnection
     */
    public void setMillisecondsToRecheckConnection(long millisecondsToRecheckConnection) {
        this.millisecondsToRecheckConnection = millisecondsToRecheckConnection;
    }

    /**
     * Set how many connections the server can have
     * @param maxConnections
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /**
     * Set if there should be a waiting que for client to get into the real connection
     * @param waitingQue
     */
    public void setWaitingQue(boolean waitingQue) {
        this.waitingQue = waitingQue;
    }

    /**
     * Set how many waiting the server can handle
     * @param maxWaitingConnections
     */
    public void setMaxWaitingConnections(int maxWaitingConnections) {
        this.maxWaitingConnections = maxWaitingConnections;
    }

    /**
     * Check if keep connection are enabled
     * @return
     */
    public boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * Set if keep alive should be enabled, default are on
     * @param keepAlive
     */
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    /**
     * Switch simulated delay on and of
     * @param simulateDelay
     */
    public void setSimulateDelay(boolean simulateDelay) {
        this.simulateDelay = simulateDelay;
        simulatedDelay = ThreadLocalRandom.current().nextLong(simulateDelayTimeMin, simulateDelayTimeMax);
    }

    /**
     * Set how much simulated delay should be used
     * @param simulateDelayTimeMin
     * @param simulateDelayTimeMax
     */
    public void setSimulateDelayTime(long simulateDelayTimeMin, long simulateDelayTimeMax) {
        this.simulateDelayTimeMin = simulateDelayTimeMin;
        this.simulateDelayTimeMax = simulateDelayTimeMax;
    }

    /**
     * Start server if it's not running
     */
    public void startServer() {
        if (serverThread == null || !serverThread.isAlive()) {
            running = true;
            serverThread = new ServerThread();
            serverThread.start();
        }
        if (!handleConnection.isAlive()) {
            handleConnection.start();
        }
    }

    /**
     * Kill the server if it's running
     */
    public void killServer() {
        if (serverThread.isAlive() ) {
            running = false;
            datagramSocket.close();
            serverThread.interrupt();
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
        for (Connection connection : clients.values()) {
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
        if (clients.containsKey(clientID)) {
            clients.get(clientID).addToSendQue(dataToSend, NetworkSendType.UNRELIABLE_GAME_DATA);
        }
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
     * @param uuid
     */
    public void sendUnreliableToAllExcept(byte[] dataToSend, List<UUID> exceptClients) {
        for (Connection connection : clients.values()) {
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
        for (Connection connection : clients.values()) {
            connection.addToSendQue(dataToSend, NetworkSendType.RELIABLE_GAME_DATA);
        }
    }

    public <T> void sendReliableObjectToClient(T sendObject, UUID clientID) {
        sendReliableToClient(SerializationUtils.getInstance().serialize(sendObject), clientID);
    }

    public void sendReliableToClient(byte[] sendObject, UUID clientID) {
        if (clients.containsKey(clientID)) {
            clients.get(clientID).addToSendQue(sendObject, NetworkSendType.RELIABLE_GAME_DATA);
        }
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
     * @param uuid
     */
    public void sendReliableToAllExcept(byte[] dataToSend, List<UUID> exceptClients) {
        for (Connection connection : clients.values()) {
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
        for (Connection connection : clients.values()) {
            long smoothTime = connection.getSmoothRoundTripTime();
            for (NetworkPackage networkPackage : connection.getNetworkPackageArrayMap().values()) {
                if ((currentTime - networkPackage.getSentTime() - (Math.max(milisecoundsBetweenResend, smoothTime) * networkPackage.getResent())) > 0) {
                    try {
                        networkPackage.resendData(networkPackage.getSequenceNumber());
                        if (networkPackage.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBufferResend = ByteBuffer.allocate(byteBufferSize + 4 + networkPackage.getDataSent().length);
                            byteBufferResend.putInt(protocalVersionHash).put((byte) networkPackage.getNetworkSendType().getTypeCode()).putInt(networkPackage.getSequenceNumber()).putInt(networkPackage.getSplitSequenceNumber()).put(networkPackage.getDataSent());
                        } else {
                            byteBufferResend = ByteBuffer.allocate(byteBufferSize + networkPackage.getDataSent().length);
                            byteBufferResend.putInt(protocalVersionHash).put((byte) networkPackage.getNetworkSendType().getTypeCode()).putInt(networkPackage.getSequenceNumber()).put(networkPackage.getDataSent());
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

    public synchronized void sendFromQue() {
        if (!running || !serverThread.isAlive() || datagramSocket == null) {
            return;
        }
        for (Connection connection : clients.values()) {
            sendFromQueConnection(connection);
        }
    }

    private synchronized void sendFromQueConnection(Connection connection) {
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

    /**
     * Remove clients that haven't send a message for some time
     */
    public synchronized void removeInactiveClients() {
        if (serverThread != null && serverThread.isAlive()) {
            long currentTime = System.currentTimeMillis();
            Iterator<Connection> it = clients.values().iterator();
            while (it.hasNext()) {
                Connection connection = it.next();
                if ((currentTime - connection.getLastRecived()) > milisecoundToTimeout) {
                    disconnectedClients(connection.getConnectionId());
                    it.remove();
                }
            }
        }
    }

    /**
     * Keep all connections alive
     */
    public synchronized void keepConnectionsAlive() {
        if (serverThread != null && serverThread.isAlive()) {
            long currentTime = System.currentTimeMillis();
            for (Connection connection : clients.values()) {
                if (currentTime > connection.getNextKeepAlive()) {
                    connection.addToSendQue(new byte[0], NetworkSendType.PING);
                    connection.setNextKeepAlive(System.currentTimeMillis() + (long) (milisecoundToTimeout * 0.2f));
                }
            }
        }
    }

    /**
     * Check if there been a spot free
     */
    public synchronized void checkQue() {
        if (serverThread != null && serverThread.isAlive()) {
            if (clients.size() < maxConnections && !waitingQueArray.isEmpty()) {
                Connection connection = waitingQueArray.poll();
                clients.put(connection.getConnectionId(), connection);
            }
            Integer queNumber = 1;
            long currentTime = System.currentTimeMillis();
            for (Connection connection : waitingQueArray) {
                if (currentTime > connection.getNextKeepAlive()) {
                    sendData(connection, SerializationUtils.getInstance().serialize(queNumber++), NetworkSendType.QUENUMBER);
                    connection.setNextKeepAlive(System.currentTimeMillis() + (long) (milisecoundToTimeout * 0.2f));
                }
            }
        }
    }

    public synchronized void removeInactiveSplitMessages() {
        if (serverThread != null && serverThread.isAlive()) {
            for (Connection connection : clients.values()) {
                connection.removeSplitMessages();
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
                byteBufferSendData.putInt(protocalVersionHash).put((byte) sendType.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).put(data);
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
                        byteBufferSendData.putInt(protocalVersionHash);
                        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBufferSendData.put((byte) NetworkSendType.RELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getNetworkPackage(dataToSend, NetworkSendType.RELIABLE_SPLIT_GAME_DATA);
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
                        byteBufferSendData.putInt(protocalVersionHash);
                        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBufferSendData.put((byte) NetworkSendType.RELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getNetworkPackage(dataToSend, NetworkSendType.RELIABLE_SPLIT_GAME_DATA);
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
            byteBufferAck.putInt(protocalVersionHash).put((byte)NetworkSendType.ACK.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).putInt(sequenceNumberToAck);
            byte[] sendData = byteBufferAck.array();
            DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
            datagramSocket.send(dp);
            connection.getNetworkPackagePool().giveBack(sendingPackage);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error send ack", e);
        }
    }

    private void recivedMessage(Message message) {
        for (DataReceivedListener dataReceivedListener : dataReceivedListeners) {
            dataReceivedListener.receivedMessage(message);
        }
    }

    private void disconnectedClients(UUID clientId) {
        for (DataReceivedListener dataReceivedListener : dataReceivedListeners) {
            dataReceivedListener.disconnected(clientId);
        }
    }

    private class ServerThread extends Thread {

        private NetworkPackage tempPackage;
        private final ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);

        @Override
        public void run() {

            Connection client = null;
            try {
                if (ipAdress == null) {
                    datagramSocket = new DatagramSocket(port);
                } else {
                    datagramSocket = new DatagramSocket(port, InetAddress.getByName(ipAdress));
                }
                incoming = new DatagramPacket(buffer, buffer.length);

            } catch (SocketException e) {
                LOG.log(Level.SEVERE, "Error open socket", e);
            } catch (UnknownHostException e) {
                LOG.log(Level.SEVERE, "Error with host", e);
            }

            while(running) {
                try {
                    client = null;
                    datagramSocket.receive(incoming);
                    byteBuffer.clear();
                    byteBuffer.limit(incoming.getLength());
                    byteBuffer.put(incoming.getData(), 0, incoming.getLength());
                    byteBuffer.flip();
                    if (byteBuffer.getInt(0) != protocalVersionHash) {
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
                    client = clients.get(tempConnection.getConnectionId());


                    // Check if we have already received data then send another ack and don't do anything else
                    if (client != null) {
                        if (byteBuffer.get(4) != NetworkSendType.ACK.getTypeCode() && client.isReceivedPackageStack(byteBuffer.getInt(5))) {
                            sendAck(tempConnection, byteBuffer.getInt(5));
                            continue;
                        }

                    // check if we have reached max connections
                    } else if (client == null && clients.size() < maxConnections) {
                        Connection newConnection = new Connection(tempConnection);
                        if (keepAlive) {
                            newConnection.setNextKeepAlive(System.currentTimeMillis() + (long)(milisecoundToTimeout * 0.2f));
                        }
                        clients.put(newConnection.getConnectionId(), newConnection);
                        for (DataReceivedListener dataReceivedListener : dataReceivedListeners) {
                            dataReceivedListener.connected(newConnection);
                        }
                        client = newConnection;

                    // Check if we have que system enabled and if we have reached max in the que
                    } else if (client == null && waitingQue && waitingQueArray.size() < maxWaitingConnections) {
                        Connection newConnection = new Connection(tempConnection);
                        if (!waitingQueArray.contains(newConnection)) {
                            waitingQueArray.add(newConnection);
                        }
                        continue;
                    }


                    if (client != null) {

                        // Verify that we have received ack otherwise ignore
                        if (byteBuffer.get(4) == NetworkSendType.ACK.getTypeCode()) {
                            if (incoming.getLength() == 13) {
                                if (client != null) {
                                    verifyAck(client, byteBuffer.getInt(9)); //ByteBuffer.wrap(data, 9, 13).getInt());
                                }

                                // Must be a better way here, change from Deque to ????
                                if (waitingQue) {
                                    for (Connection connection : waitingQueArray) {
                                        if (connection.equals(tempConnection)) {
                                            verifyAck(connection, byteBuffer.getInt(9)); //ByteBuffer.wrap(data, 9, 13).getInt());
                                            break;
                                        }
                                    }
                                }

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
                            message.setConnectionID(client.getConnectionId());
                            byteBuffer.position(13);
                            byteBuffer.get(tempDataBuffer, 0, tempDataBuffer.length);
                            message.setData(tempDataBuffer);

                            if (message.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA || message.getNetworkSendType() == NetworkSendType.PEER_SPLIT_DATA) {
                                sendAck(tempConnection, byteBuffer.getInt(5));
                            }

                            handleSplitMessages(message, client);
                        } else {
                            byte[] tempDataBuffer = new byte[byteBuffer.limit() - 9];
                            byteBuffer.position(9);
                            byteBuffer.get(tempDataBuffer, 0, tempDataBuffer.length);
                            message.setData(tempDataBuffer);
                            message.setNetworkSendType(NetworkSendType.fromByteValue(byteBuffer.get(4)));
                            message.setSequenceId(byteBuffer.getInt(5));
                            message.setConnectionID(client.getConnectionId());

                            if (message.getNetworkSendType() == NetworkSendType.RELIABLE_GAME_DATA
                                    || message.getNetworkSendType() == NetworkSendType.PEER_DATA
                                    || message.getNetworkSendType() == NetworkSendType.INITSIGNAL ) {
                                sendAck(tempConnection, byteBuffer.getInt(5));
                            }

                            if (message.getNetworkSendType() != NetworkSendType.INITSIGNAL) {
                                recivedMessage(message);
                            }
                        }
                        client.updateTime();
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
                recivedMessage(message);
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
                resendData();
                sendFromQue();
                removeInactiveClients();
                keepConnectionsAlive();
                if (waitingQue) {
                    checkQue();
                }
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
