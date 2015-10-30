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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Server {

    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    // The name of the protocol version, to sort out incorrect package
    private int protocalVersion = "Protocal 0.1".hashCode();

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

    private boolean running = false;

    private ServerThread serverThread;
    private List<Connection> clients;
    private Connection tempConnection = new Connection();
    private HandleConnection handleConnection = new HandleConnection();


    private final List<DataReceivedListener> dataReceivedListeners = new ArrayList<DataReceivedListener>();
    private final Message message = new Message();


    /**
     * Create new server on port 7854 on localhost with max 8 connections
     */
    public Server() {
        ipAdress = "127.0.0.1";
        port = 7854;
        byteBuffer = ByteBuffer.allocate(byteBufferSize);
        clients = new ArrayList<Connection>(8);
        maxConnections = 8;
    }

    /**
     * Create new server and set port, ip to liston on and max connections
     * @param port
     * @param ipAdress
     * @param maxConnections
     */
    public Server(int port, String ipAdress, int maxConnections) {
        this.port = port;
        this.ipAdress = ipAdress;
        byteBuffer = ByteBuffer.allocate(byteBufferSize);
        clients = new ArrayList<Connection>(maxConnections);
        this.maxConnections = maxConnections;
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
        clients = new ArrayList<Connection>(maxConnections);
        this.maxConnections = maxConnections;
        protocalVersion = protocalVersionName.hashCode();
    }

    /**
     * Return connection from uuid
     * @param uuid
     * @return
     */
    public Connection getConnectionFromUUID(UUID uuid) {
        for (int i = clients.size() - 1; i >= 0; i--) {
            if (clients.get(i).getConnectionId().equals(uuid)) {
                return clients.get(i);
            }
        }
        return null;
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
     * @param protocalVersion
     */
    public void setProtocalVersion(int protocalVersion) {
        this.protocalVersion = protocalVersion;
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
        for (int i = clients.size() - 1; i >= 0; i--) {
            clients.get(i).addToSendQue(dataToSend, NetworkSendType.UNRELIABLE_GAME_DATA);
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
        for (int i = clients.size() - 1; i >= 0; i--) {
            Connection connection = clients.get(i);
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
        for (int i = clients.size() - 1; i >= 0; i--) {
            clients.get(i).addToSendQue(dataToSend, NetworkSendType.RELIABLE_GAME_DATA);
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
    public void sendReliableToAllExcept(byte[] dataToSend, List<UUID> uuid) {
        for (int i = clients.size() - 1; i >= 0; i--) {
            Connection connection = clients.get(i);
            if (!uuid.contains(connection.getConnectionId())) {
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
        for (int i = clients.size() - 1; i >= 0; i--) {
            Connection connection = clients.get(i);
            for (NetworkPackage networkPackage : connection.getNetworkPackageArrayMap().values()) {
                if ((currentTime - networkPackage.getSentTime() - Math.max(milisecoundsBetweenResend, connection.getSmoothRoundTripTime() * 1.1)) > 0) {
                    SendDataQue sendDataQue = connection.getSendDataQuePool().borrow();
                    sendDataQue.setValues(networkPackage.getDataSent(), networkPackage.getNetworkSendType());
                    if (!connection.getSendDataQueList().contains(sendDataQue)) {
                        connection.getSendDataQueList().addFirst(sendDataQue);
                    }
                }
/*                    try {
                        if (networkPackage.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBufferResend = ByteBuffer.allocate(byteBufferSize + 4 + networkPackage.getDataSent().length);
                            byteBufferResend.putInt(protocalVersion).put((byte) networkPackage.getNetworkSendType().getTypeCode()).putInt(networkPackage.getSequenceNumber()).putInt(networkPackage.getSplitSequenceNumber()).put(networkPackage.getDataSent());
                        } else {
                            byteBufferResend = ByteBuffer.allocate(byteBufferSize + networkPackage.getDataSent().length);
                            byteBufferResend.putInt(protocalVersion).put((byte) networkPackage.getNetworkSendType().getTypeCode()).putInt(networkPackage.getSequenceNumber()).put(networkPackage.getDataSent());
                        }
                        byte[] sendData = byteBuffer.array();
                        DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                        datagramSocket.send(dp);
                        networkPackage.resendData(networkPackage.getSequenceNumber());
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "Error resend data", e);
                    }
                }*/
            }
        }
    }

    public synchronized void sendFromQue() {
        if (!running) {
            return;
        }
        for (int i = clients.size() - 1; i >= 0; i--) {
            Connection connection = clients.get(i);
            sendFromQueConnection(connection);
        }
    }

    private synchronized void sendFromQueConnection(Connection connection) {
        if (connection.getNextSendQueData()) {
            Iterator<SendDataQue> iter = connection.getSendDataQueList().iterator();
            while (iter.hasNext()) {
                SendDataQue sendDataQue = iter.next();
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
            for (int i = clients.size() - 1; i >= 0; i--) {
                Connection connection = clients.get(i);
                if ((currentTime - connection.getLastRecived()) > milisecoundToTimeout) {
                    disconnectedClients(connection.getConnectionId());
                    clients.remove(i);

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
            for (int i = clients.size() - 1; i >= 0; i--) {
                Connection connection = clients.get(i);
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
                clients.add(waitingQueArray.poll());
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
            long currentTime = System.currentTimeMillis();
            for (int i = clients.size() - 1; i >= 0; i--) {
                Connection connection = clients.get(i);
                Set<Integer> splitMessageKeySet = connection.getSplitMessageData().keySet();
                for (Iterator<Integer> j = splitMessageKeySet.iterator(); j.hasNext();) {
                    Integer splitId = j.next();
                    if ((connection.getSplitMessageData().get(splitId) != null && connection.getSplitMessageData().get(splitId).get(0) != null) && (connection.getSplitMessageData().get(splitId).get(0).getCreateTime() + 30000 < currentTime)) {
                        j.remove();
                    }
                }
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
        try {
            if (data.length + byteBufferSize <= bufferSize) {
                byteBufferSendData = ByteBuffer.allocate(byteBufferSize + data.length);
                sendingPackage = connection.getLastSequenceNumber(data, sendType);
                byteBufferSendData.putInt(protocalVersion).put((byte) sendType.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).put(data);
                sendData = byteBufferSendData.array();
                DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                datagramSocket.send(dp);
            } else {
                int len = 0;
                byte[] dataToSend;
                int splitIdSize = 4;
                int splitId = connection.getGlobalSplitSequenceNumber();
                while (true) {
                    if (data.length - len > bufferSize - byteBufferSize - splitIdSize - 4) {
                        dataToSend = Arrays.copyOfRange(data, len, bufferSize - byteBufferSize - splitIdSize - 4 + len);
                        byteBufferSendData = ByteBuffer.allocate(bufferSize - 4);
                        byteBufferSendData.putInt(protocalVersion);
                        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBufferSendData.put((byte) NetworkSendType.RELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.RELIABLE_SPLIT_GAME_DATA);
                        } else {
                            byteBufferSendData.put((byte) NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA);
                        }
                        byteBufferSendData.putInt(sendingPackage.getSequenceNumber()).putInt(splitId).put(dataToSend);
                        len += bufferSize - byteBufferSize - splitIdSize - 4;
                        sendData = byteBufferSendData.array();
                        DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                        datagramSocket.send(dp);
                    } else {
                        dataToSend = Arrays.copyOfRange(data, len, data.length);
                        byteBufferSendData = ByteBuffer.allocate(byteBufferSize + splitIdSize + 4 + dataToSend.length);
                        byteBufferSendData.putInt(protocalVersion);
                        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBufferSendData.put((byte) NetworkSendType.RELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.RELIABLE_SPLIT_GAME_DATA);
                        } else {
                            byteBufferSendData.put((byte) NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA);
                        }
                        byteBufferSendData.putInt(sendingPackage.getSequenceNumber()).putInt(splitId).put(dataToSend).putInt(Arrays.hashCode(data));
                        sendData = byteBufferSendData.array();
                        DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                        datagramSocket.send(dp);
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
//            try {
//                Thread.sleep((long)(Math.random() * 100));
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            connection.setReceivedPackageStack(sequenceNumberToAck);
            ByteBuffer byteBufferAck = ByteBuffer.allocate(byteBufferSize + 4);
            NetworkPackage sendingPackage = connection.getLastSequenceNumber();
            byteBufferAck.putInt(protocalVersion).put((byte)NetworkSendType.ACK.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).putInt(sequenceNumberToAck);
            byte[] sendData = byteBufferAck.array();
            DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
            datagramSocket.send(dp);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error send ack", e);
        }
    }

    private void recivedMessage(Message message) {
        for (DataReceivedListener dataReceivedListener : dataReceivedListeners) {
            dataReceivedListener.recivedMessage(message);
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
                    datagramSocket.receive(incoming);
                    byteBuffer.clear();
                    byteBuffer.limit(incoming.getLength());
                    byteBuffer.put(incoming.getData(), 0, incoming.getLength());
                    byteBuffer.flip();
                    if (byteBuffer.getInt(0) != protocalVersion) {
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

                    int workingClient = clients.indexOf(tempConnection);

                    // Check if we have already received data then send another ack and don't do anything else
                    if (workingClient != -1) {
                        if (clients.get(workingClient).isReceivedPackageStack(byteBuffer.getInt(5))) {
                            sendAck(tempConnection, byteBuffer.getInt(5));
                            continue;
                        }
                    // check if we have reached max connections
                    } else if (clients.size() < maxConnections) {
                        Connection newConnection = new Connection(tempConnection);
                        if (keepAlive) {
                            newConnection.setNextKeepAlive(System.currentTimeMillis() + (long)(milisecoundToTimeout * 0.2f));
                        }
                        clients.add(newConnection);
                        workingClient = clients.indexOf(newConnection);
                    // Check if we have que system enabled and if we have reached max in the que
                    } else if (waitingQue && waitingQueArray.size() < maxWaitingConnections) {
                        Connection newConnection = new Connection(tempConnection);
                        if (!waitingQueArray.contains(newConnection)) {
                            waitingQueArray.add(newConnection);
                        }
                        continue;
                    }
                    // Verify that we have received ack otherwise ignore
                    if (byteBuffer.get(4) == NetworkSendType.ACK.getTypeCode()) {
                        if (incoming.getLength() == 13) {
                            if (workingClient != -1) {
                                verifyAck(clients.get(workingClient), byteBuffer.getInt(9)); //ByteBuffer.wrap(data, 9, 13).getInt());
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

                    if (message.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA || message.getNetworkSendType() == NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA) {
                        byte[] tempDataBuffer = new byte[byteBuffer.limit() - 13];
                        message.setSequenceId(byteBuffer.getInt(5));
                        message.setSplitMessageId(byteBuffer.getInt(9)); // new BigInteger(Arrays.copyOfRange(data, byteBufferSize, byteBufferSize + 4)).intValue());
                        message.setConnectionID(clients.get(workingClient).getConnectionId());
                        byteBuffer.position(13);
                        byteBuffer.get(tempDataBuffer, 0, tempDataBuffer.length);
                        message.setData(tempDataBuffer);
                        handleSplitMessages(message, clients.get(workingClient));
                    } else {
                        byte[] tempDataBuffer = new byte[byteBuffer.limit() - 9];
                        byteBuffer.position(9);
                        byteBuffer.get(tempDataBuffer, 0, tempDataBuffer.length);
                        message.setData(tempDataBuffer);
                        message.setNetworkSendType(NetworkSendType.fromByteValue(byteBuffer.get(4)));
                        message.setSequenceId(byteBuffer.getInt(5));
                        message.setConnectionID(clients.get(workingClient).getConnectionId());

                        if (message.getNetworkSendType() == NetworkSendType.RELIABLE_GAME_DATA) {
                            sendAck(tempConnection, byteBuffer.getInt(5));
                        }

                        clients.get(workingClient).updateTime();
                        recivedMessage(message);
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
            if (message.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                sendAck(connection, message.getSequenceId());
            }
            connection.updateTime();
        }

        private void verifyAck(Connection connection, int sequenceNumberWasAcked) {
            tempPackage = connection.getNetworkPackageArrayMap().remove(sequenceNumberWasAcked);
            if (tempPackage != null) {
                long roundTripTime = System.currentTimeMillis() - tempPackage.getSentTime();
                connection.getRoundTripTimes().push(roundTripTime);
                System.out.println("smoothTime: " + connection.getSmoothRoundTripTime());
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
