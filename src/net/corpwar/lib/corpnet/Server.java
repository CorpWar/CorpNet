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
    private int bufferSize = 4096;

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
     * Use this if you don't care if the message get to the client
     * @param dataToSend
     */
    public void sendUnreliableToAllClients(byte[] dataToSend) {
        for (int i = clients.size() - 1; i >= 0; i--) {
            sendData(clients.get(i), dataToSend, NetworkSendType.UNRELIABLE_GAME_DATA);
        }
    }

    /**
     * Use this if it is rely important the message get to the client
     * @param dataToSend
     */
    public void sendReliableToAllClients(byte[] dataToSend) {
        for (int i = clients.size() - 1; i >= 0; i--) {
            sendData(clients.get(i), dataToSend, NetworkSendType.RELIABLE_GAME_DATA);
        }
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
                sendData(connection, dataToSend, NetworkSendType.RELIABLE_GAME_DATA);
            }
        }
    }

    /**
     * You can trigger the resend method just to tell it to send messages that have reached the max limits of a message
     */
    public synchronized void resendData() {
        long currentTime = System.currentTimeMillis();
        for (int i = clients.size() - 1; i >= 0; i--) {
            Connection connection = clients.get(i);
            for (NetworkPackage networkPackage : connection.getNetworkPackageArrayMap().values()) {
                if ((currentTime - networkPackage.getSentTime() - Math.max(milisecoundsBetweenResend, connection.getSmoothRoundTripTime() * 1.1)) > 0) {
                    try {
                        byteBuffer = ByteBuffer.allocate(byteBufferSize + networkPackage.getDataSent().length);
                        byteBuffer.putInt(protocalVersion).put((byte) networkPackage.getNetworkSendType().getTypeCode()).putInt(networkPackage.getSequenceNumber()).put(networkPackage.getDataSent());
                        byte[] sendData = byteBuffer.array();
                        DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                        datagramSocket.send(dp);
                        networkPackage.resendData(networkPackage.getSequenceNumber());
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "Error resend data", e);
                    }
                }
            }
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
                    sendData(connection, new byte[0], NetworkSendType.PING);
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

    /**
     * Send a message to a specific client
     * @param connection
     * @param data
     * @param sendType
     */
    public synchronized void sendData(Connection connection, byte[] data, NetworkSendType sendType) {
        try {
            if (data.length + byteBufferSize <= bufferSize) {
                byteBuffer = ByteBuffer.allocate(byteBufferSize + data.length);
                sendingPackage = connection.getLastSequenceNumber(data, sendType);
                byteBuffer.putInt(protocalVersion).put((byte) sendType.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).put(data);
                byte[] sendData = byteBuffer.array();
                DatagramPacket dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                datagramSocket.send(dp);
            } else {
                int len = 0;
                byte[] dataToSend;
                int splitIdSize = 4;
                Integer splitId = connection.getGlobalSplitSequenceNumber();
                while (true) {
                    if (data.length > len + bufferSize + byteBufferSize + splitIdSize) {
                        dataToSend = Arrays.copyOfRange(data, len, bufferSize - byteBufferSize - splitIdSize + len);
                        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
                        byteBuffer.putInt(protocalVersion);
                        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBuffer.put((byte) NetworkSendType.RELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.RELIABLE_SPLIT_GAME_DATA);
                        } else {
                            byteBuffer.put((byte) NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA);
                        }
                        byteBuffer.putInt(sendingPackage.getSequenceNumber()).putInt(splitId).put(dataToSend);
                        len += bufferSize - byteBufferSize - splitIdSize;
                    } else {
                        dataToSend = Arrays.copyOfRange(data, len, data.length);
                        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
                        byteBuffer.putInt(protocalVersion);
                        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBuffer.put((byte) NetworkSendType.RELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.RELIABLE_SPLIT_GAME_DATA);
                        } else {
                            byteBuffer.put((byte) NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA);
                        }
                        byteBuffer.putInt(sendingPackage.getSequenceNumber()).putInt(splitId).put(dataToSend).putInt(Arrays.hashCode(data));
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
            byteBuffer = ByteBuffer.allocate(byteBufferSize + 4);
            NetworkPackage sendingPackage = connection.getLastSequenceNumber();
            byteBuffer.putInt(protocalVersion).put((byte)NetworkSendType.ACK.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).putInt(sequenceNumberToAck);
            byte[] sendData = byteBuffer.array();
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
                    byte[] data = incoming.getData();
                    byteBuffer.clear();
                    byteBuffer.put(Arrays.copyOfRange(data, 0, byteBufferSize));
                    if (byteBuffer.getInt(0) != protocalVersion) {
                        continue;
                    }

                    tempConnection.updateClient(incoming.getAddress(), incoming.getPort());
                    // Anyone should be able to ping the connection if they have the correct protocol
                    if (byteBuffer.get(4) == NetworkSendType.PING.getTypeCode()) {
                        sendAck(tempConnection, byteBuffer.getInt(5));
                        continue;
                    }

                    int workingClient = clients.indexOf(tempConnection);
                    // Check if we have already received data then send another ack and don't do anything else
                    if (workingClient != -1) {
                        if (clients.get(workingClient).getNetworkPackageArrayMap().containsKey(byteBuffer.getInt(5))) {
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
                                verifyAck(clients.get(workingClient), ByteBuffer.wrap(data, 9, 13).getInt());
                            }

                            // Must be a better way here, change from Deque to ????
                            if (waitingQue) {
                                for (Connection connection : waitingQueArray) {
                                    if (connection.equals(tempConnection)) {
                                        verifyAck(connection, ByteBuffer.wrap(data, 9, 13).getInt());
                                        break;
                                    }
                                }
                            }

                        }
                        continue;
                    }

                    message.setData(Arrays.copyOfRange(data, byteBufferSize, incoming.getLength()));
                    message.setNetworkSendType(NetworkSendType.fromByteValue(byteBuffer.get(4)));
                    message.setSequenceId(byteBuffer.getInt(5));
                    message.setConnectionID(clients.get(workingClient).getConnectionId());

                    if (message.getNetworkSendType() == NetworkSendType.RELIABLE_GAME_DATA) {
                        sendAck(tempConnection, byteBuffer.getInt(5));
                    }

                    clients.get(workingClient).updateTime();
                    recivedMessage(message);
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Error resend data", e);
                }
            }
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
            }
        }
    }

    private class HandleConnection extends Thread {

        @Override
        public void run() {
            while (running) {
                resendData();
                removeInactiveClients();
                keepConnectionsAlive();
                if (waitingQue) {
                    checkQue();
                }
                try {
                    Thread.sleep(millisecondsToRecheckConnection);
                } catch (InterruptedException e) {
                    LOG.log(Level.FINE, "Error when sleeping");
                }
            }
        }
    }

}
