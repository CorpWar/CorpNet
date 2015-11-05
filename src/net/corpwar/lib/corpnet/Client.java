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
import java.math.BigInteger;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Client {

    private static final Logger LOG = Logger.getLogger(Client.class.getName());

    //The name of the protocol version, to sort out incorrect package
    private int protocalVersion = "Protocal 0.1".hashCode();

    // Max size that can be sent in one package
    private final int bufferSize = 512;

    /**
     * This should be same as the server
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

    // Default host address when start a new client
    private final static String defaultHostAddress = "127.0.0.1";

    // Default port when start a new client
    private final static int defaultPort = 7854;

    private Connection connection;
    private DatagramSocket sock = null;
    private DatagramPacket dp;
    private byte[] sendData;
    private boolean running = true;
    private ClientThread clientThread;
    private HandleConnection handleConnection = new HandleConnection();
    private NetworkPackage sendingPackage;
    private final List<DataReceivedListener> dataReceivedListeners = new ArrayList<DataReceivedListener>();
    private ByteBuffer resendByteBuffer;
    private ByteBuffer sendByteBuffer;


    /**
     * Create new client
     */
    public Client() {

        try {
            sock = new DatagramSocket();
        } catch (SocketException e) {
            LOG.log(Level.SEVERE, "Error create client", e);
        }
    }

    /**
     * Create new client and set port and ip to server
     * @param port
     * @param serverIP
     */
    public Client(int port, String serverIP) {
        try {
            sock = new DatagramSocket();
            if (clientThread == null || !clientThread.isAlive()) {
                connection = new Connection(InetAddress.getByName(serverIP), port);
            }
        } catch (SocketException e) {
            LOG.log(Level.SEVERE, "Error create client", e);
        } catch (UnknownHostException e) {
            LOG.log(Level.SEVERE, "Error create client", e);
        }
    }

    /**
     * Create new client and set port, ip to server and protocal version. Must be same on client and server.
     * @param port
     * @param serverIP
     * @param protocalVersionName
     */
    public Client(int port, String serverIP, String protocalVersionName) {
        protocalVersion = protocalVersionName.hashCode();
        try {
            sock = new DatagramSocket();
            if (clientThread == null || !clientThread.isAlive()) {
                connection = new Connection(InetAddress.getByName(serverIP), port);
            }
        } catch (SocketException e) {
            LOG.log(Level.SEVERE, "Error create client", e);
        } catch (UnknownHostException e) {
            LOG.log(Level.SEVERE, "Error create client", e);
        }
    }

    /**
     * Register client listener
     * @param dataReceivedListener
     */
    public void registerClientListerner(DataReceivedListener dataReceivedListener) {
        dataReceivedListeners.add(dataReceivedListener);
    }

    /**
     * Change port and ip the client should connect to
     * @param port
     * @param serverIP
     */
    public void setPortAndIp(int port, String serverIP) {
        try {
            if (clientThread == null || !clientThread.isAlive()) {
                connection = new Connection(InetAddress.getByName(serverIP), port);
            }
        } catch (UnknownHostException e) {
            LOG.log(Level.SEVERE, "Error change port and IP", e);
        }
    }

    /**
     * Start client, if no port and ip have been specified it will use localhost address and 7854 as port
     */
    public void startClient() {
        if (connection == null) {
            try {
                connection = new Connection(InetAddress.getByName(defaultHostAddress), defaultPort);
            } catch (UnknownHostException e) {
                LOG.log(Level.SEVERE, "Error connect to server", e);
            }
        }
        connection.updateTime();
        running = true;
        clientThread = new ClientThread();
        clientThread.start();
        handleConnection.start();
    }

    /**
     * @return connection for client
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * OBS! Must be same on both client and server
     * @param protocalVersion
     */
    public void setProtocalVersion(int protocalVersion) {
        this.protocalVersion = protocalVersion;
    }

    /**
     * How long should the client wait for ack before it resend a message
     * @param millisecondsBetweenResend
     */
    public void setMillisecondsBetweenResend(long millisecondsBetweenResend) {
        this.millisecondsBetweenResend = millisecondsBetweenResend;
    }

    /**
     * How long it should wait before it disconnect from server if no packages are received
     * @param millisecondToTimeout
     */
    public void setMillisecondToTimeout(long millisecondToTimeout) {
        this.millisecondToTimeout = millisecondToTimeout;
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
     * Send a ping to server to check if it is there
     */
    public void sendPing() {
        connection.addToSendQue(new byte[0], NetworkSendType.PING);
    }

    /**
     * Send an unreliable object to server. Just a simpler way then send a byte[]
     * @param sendObjct
     */
    public <T> void sendUnreliableDataObject(T sendObjct) {
        sendUnreliableData(SerializationUtils.getInstance().serialize(sendObjct));
    }

    /**
     * Use this if you don't care if the message get to the server
     * @param sendData
     */
    public void sendUnreliableData(byte[] sendData) {
        connection.addToSendQue(sendData, NetworkSendType.UNRELIABLE_GAME_DATA);
    }

    /**
     * Send a reliable object to server. Just a simpler way then send a byte[]
     * @param sendObjct
     */
    public <T> void sendReliableDataObject(T sendObjct) {
        sendReliableData(SerializationUtils.getInstance().serialize(sendObjct));
    }

    /**
     * Use this if it is rely important the message get to the server
     * @param sendData
     */
    public void sendReliableData(byte[] sendData) {
        connection.addToSendQue(sendData, NetworkSendType.RELIABLE_GAME_DATA);
    }

    /**
     * You can trigger the resend method just to tell it to send messages that have reached the max limits of a message
     */
    public synchronized void resendData() {
        long currentTime = System.currentTimeMillis();
        long smoothTime = connection.getSmoothRoundTripTime();
        for (NetworkPackage networkPackage : connection.getNetworkPackageArrayMap().values()) {
            if ((currentTime - networkPackage.getSentTime() - Math.max(millisecondsBetweenResend, smoothTime * networkPackage.getResent())) > 0) {
                try {
                    networkPackage.resendData(networkPackage.getSequenceNumber());
                    if (networkPackage.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                        resendByteBuffer = ByteBuffer.allocate(byteBufferSize + 4 + networkPackage.getDataSent().length);
                        resendByteBuffer.putInt(protocalVersion).put((byte) networkPackage.getNetworkSendType().getTypeCode()).putInt(networkPackage.getSequenceNumber()).putInt(networkPackage.getSplitSequenceNumber()).put(networkPackage.getDataSent());
                    } else {
                        resendByteBuffer = ByteBuffer.allocate(byteBufferSize + networkPackage.getDataSent().length);
                        resendByteBuffer.putInt(protocalVersion).put((byte) networkPackage.getNetworkSendType().getTypeCode()).putInt(networkPackage.getSequenceNumber()).put(networkPackage.getDataSent());
                    }

                    sendData = resendByteBuffer.array();
                    dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                    sock.send(dp);

                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Error send data", e);
                }
            }
        }
    }

    private synchronized void sendFromQue() {
        if (!running) {
            return;
        }
        if (connection.getNextSendQueData()) {
            Iterator<SendDataQue> iter = connection.getSendDataQueList().iterator();
            while (iter.hasNext()) {
                SendDataQue sendDataQue = iter.next();
                sendData(sendDataQue.getaByte(), sendDataQue.getNetworkSendType());

                connection.getSendDataQuePool().giveBack(sendDataQue);
                iter.remove();

            }
            connection.setLastSentMessageTime(System.currentTimeMillis());
        }
    }

    /**
     * Check if the server have been disconnected
     */
    public synchronized void disconnectInactiveServer() {
        if (clientThread != null && clientThread.isAlive()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime > millisecondToTimeout + connection.getLastRecived()) {
                disconnectedClients(connection.getConnectionId());
                killConnection();
            }
        }
    }

    public synchronized void removeInactiveSplitMessages() {
        if (clientThread != null && clientThread.isAlive()) {
            long currentTime = System.currentTimeMillis();
            Set<Integer> splitMessageKeySet = connection.getSplitMessageData().keySet();
            for (Iterator<Integer> j = splitMessageKeySet.iterator(); j.hasNext();) {
                Integer splitId = j.next();
                if ((connection.getSplitMessageData().get(splitId) != null && connection.getSplitMessageData().get(splitId).get(0) != null) && (connection.getSplitMessageData().get(splitId).get(0).getCreateTime() + 30000 < currentTime)) {
                    j.remove();
                }
            }
        }
    }

    /**
     * If you want to terminate the connection between client and server
     */
    public void killConnection() {
        if (clientThread.isAlive()) {
            running = false;
            sock.close();
            clientThread.interrupt();
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

    private synchronized void sendData(byte[] data, NetworkSendType sendType) {
        try {
            if (data.length + byteBufferSize <= bufferSize) {
                sendByteBuffer = ByteBuffer.allocate(byteBufferSize + data.length);
                sendingPackage = connection.getLastSequenceNumber(data, sendType);
                sendByteBuffer.putInt(protocalVersion).put((byte) sendType.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).put(data);
                sendData = sendByteBuffer.array();
                dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                sock.send(dp);
            } else {
                int len = 0;
                byte[] dataToSend;
                int splitIdSize = 4;
                int splitId = connection.getGlobalSplitSequenceNumber();
                while(true) {
                    if (data.length - len > bufferSize - byteBufferSize - splitIdSize - 4) {
                        dataToSend = Arrays.copyOfRange(data, len, bufferSize - byteBufferSize - splitIdSize - 4 + len);
                        sendByteBuffer = ByteBuffer.allocate(bufferSize - 4);
                        sendByteBuffer.putInt(protocalVersion);
                        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            sendByteBuffer.put((byte)NetworkSendType.RELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.RELIABLE_SPLIT_GAME_DATA);
                        } else {
                            sendByteBuffer.put((byte) NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA);
                        }
                        sendByteBuffer.putInt(sendingPackage.getSequenceNumber()).putInt(splitId).put(dataToSend);
                        len += bufferSize - byteBufferSize - splitIdSize - 4;
                        sendData = sendByteBuffer.array();
                        dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                        sock.send(dp);
                    } else {
                        dataToSend = Arrays.copyOfRange(data, len, data.length);
                        ByteBuffer byteBuffer = ByteBuffer.allocate(byteBufferSize + splitIdSize + 4 + dataToSend.length);
                        byteBuffer.putInt(protocalVersion);
                        if (sendType == NetworkSendType.RELIABLE_GAME_DATA || sendType == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                            byteBuffer.put((byte)NetworkSendType.RELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.RELIABLE_SPLIT_GAME_DATA);
                        } else {
                            byteBuffer.put((byte) NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA.getTypeCode());
                            sendingPackage = connection.getLastSequenceNumber(dataToSend, NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA);
                        }
                        byteBuffer.putInt(sendingPackage.getSequenceNumber()).putInt(splitId).put(dataToSend).putInt(Arrays.hashCode(data));
                        sendData = byteBuffer.array();
                        dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                        sock.send(dp);
                        break;
                    }
                }
            }
            if (sendingPackage != null) {
                LOG.log(Level.FINEST, "SendData: " + sendingPackage.getSequenceNumber() + " type: " + sendingPackage.getNetworkSendType().name());
            } else {
                LOG.log(Level.SEVERE, "ERROR!!!1");
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error send data", e);
        }
    }

    private synchronized void sendAck(int sequenceNumberToAck) {
        try {
//            try {
//                Thread.sleep((long)(Math.random() * 500));
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            connection.setReceivedPackageStack(sequenceNumberToAck);
            ByteBuffer byteBuffer = ByteBuffer.allocate(byteBufferSize + 4);
            sendingPackage = connection.getLastSequenceNumber();
            byteBuffer.putInt(protocalVersion).put((byte) NetworkSendType.ACK.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).putInt(sequenceNumberToAck);
            sendData = byteBuffer.array();
            dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
            sock.send(dp);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error send data", e);
        }
    }

    private class ClientThread extends Thread {
        //private ByteBuffer byteBuffer = ByteBuffer.allocate(byteBufferSize);
        private final ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
        private NetworkPackage tempPackage;
        private final Message message = new Message();

        @Override
        public void run() {
            byte[] buffer = new byte[bufferSize];
            DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);

            while (running) {
                try {
                    sock.receive(incoming);

                    byte[] data = incoming.getData();
                    byteBuffer.clear();
                    byteBuffer.limit(incoming.getLength());
                    byteBuffer.put(incoming.getData(), 0, incoming.getLength());
                    byteBuffer.flip();

//                    byteBuffer.clear();
//                    byteBuffer.put(Arrays.copyOfRange(data, 0, byteBufferSize));
                    if (byteBuffer.getInt(0) != protocalVersion) {
                        continue;
                    }
                    // If we get a wrong network type we ignore everything else
                    if (byteBuffer.get(4) == NetworkSendType.ERROR.getTypeCode()) {
                        LOG.severe("ERROR network type");
                        continue;
                    }
                    // Anyone should be able to ping the connection if they have the correct protocol
                    if (byteBuffer.get(4) == NetworkSendType.PING.getTypeCode()) {
                        sendAck(byteBuffer.getInt(5));
                        connection.updateTime();
                        continue;
                    }
                    // If server use que the client get where in the que it is
                    if (byteBuffer.get(4) == NetworkSendType.QUENUMBER.getTypeCode()) {
                        sendAck(byteBuffer.getInt(5));
                        connection.updateTime();
                        message.setData(Arrays.copyOfRange(data, byteBufferSize, incoming.getLength()));
                        message.setNetworkSendType(NetworkSendType.fromByteValue(byteBuffer.get(4)));
                        recivedMessage(message);
                        continue;
                    }
                    // Check if we have already received data then send another ack and don't do anything else
                    if (connection.isReceivedPackageStack(byteBuffer.getInt(5))) {
                        sendAck(byteBuffer.getInt(5));
                        continue;
                    }
                    // Verify if we have received ack from before otherwise remove so we don't send more request
                    if (byteBuffer.get(4) == NetworkSendType.ACK.getTypeCode()) {
                        if (incoming.getLength() == 13) {
                            verifyAck(byteBuffer.getInt(9));
                        }
                        continue;
                    }
                    message.setNetworkSendType(NetworkSendType.fromByteValue(byteBuffer.get(4)));

                    if (message.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA || message.getNetworkSendType() == NetworkSendType.UNRELIABLE_SPLIT_GAME_DATA) {
                        message.setData(Arrays.copyOfRange(data, byteBufferSize + 4, incoming.getLength()));
                        message.setSequenceId(byteBuffer.getInt(5));
                        message.setSplitMessageId(new BigInteger(Arrays.copyOfRange(data, byteBufferSize, byteBufferSize + 4)).intValue());
                        handleSplitMessages(message);
                    } else {
                        message.setData(Arrays.copyOfRange(data, byteBufferSize, incoming.getLength()));
                        message.setSequenceId(byteBuffer.getInt(5));

                        if (message.getNetworkSendType() == NetworkSendType.RELIABLE_GAME_DATA) {
                            sendAck(byteBuffer.getInt(5));
                        }
                        recivedMessage(message);
                        connection.updateTime();
                    }

                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "Error receive data", e);
                    running = false;
                }
            }
        }

        private void handleSplitMessages(Message message) {
            // If we have started to receive part of split message
            byte[] data = connection.setSplitMessageData(message.getSplitMessageId(), message.getSequenceId(), message.getData());
            if (data.length > 0) {
                message.setData(data);
                recivedMessage(message);
            }
            if (message.getNetworkSendType() == NetworkSendType.RELIABLE_SPLIT_GAME_DATA) {
                sendAck(message.getSequenceId());
            }
            connection.updateTime();
        }

        private void verifyAck(int sequenceNumberWasAcked) {
            tempPackage = connection.getNetworkPackageArrayMap().remove(sequenceNumberWasAcked);
            if (tempPackage != null) {
                long roundTripTime = System.currentTimeMillis() - tempPackage.getSentTime();
                connection.getRoundTripTimes().push(roundTripTime);
                System.out.println("smoothTime: " + connection.getSmoothRoundTripTime());
                if (tempPackage.getNetworkSendType() == NetworkSendType.PING) {
                    connection.setLastPingTime(roundTripTime);
                }
                connection.updateTime();
            }
            tempPackage = null;
        }
    }

    private class HandleConnection extends Thread {

        @Override
        public void run() {
            while (running) {
                resendData();
                sendFromQue();
                disconnectInactiveServer();
                removeInactiveSplitMessages();
                try {
                    Thread.sleep(millisecondsToRecheckConnection);
                } catch (InterruptedException e) {
                    // Ignore error
                }
            }
        }
    }
}
