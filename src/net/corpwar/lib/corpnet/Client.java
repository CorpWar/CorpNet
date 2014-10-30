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

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;


public class Client {

    //The name of the protocol version, to sort out incorrect package
    private int protocalVersion = "Protocal 0.1".hashCode();

    // Max size that can be sent in one package
    private int BUFFER_SIZE = 4096;

    /**
     * This should be same as the server
     * 4 byte protocol
     * 1 byte type of package
     * 4 byte sequence ID
     */
    private int byteBufferSize = 9;

    // How long time in milliseconds it must pass before we try to resend data
    private long millisecondsBetweenResend = 100;

    // How long time in milliseconds it must pass before a disconnect
    private long millisecondToTimeout = 20000;

    // How long it should wait between every check for disconnect and resend data. This should be lower or same as millisecondsBetweenResend
    private long millisecondsToRecheckConnection = 20;

    private Connection connection;
    private DatagramSocket sock = null;
    private DatagramPacket dp;
    private byte[] sendData;
    private boolean running = true;
    private ClientThread clientThread;
    private HandleConnection handleConnection = new HandleConnection();
    private long lastReceivedPackageTime;
    private NetworkPackage sendingPackage;
    private final ArrayList<DataReceivedListener> dataReceivedListeners = new ArrayList<>();
    private Message message = new Message();
    private final SizedStack<Long> pingTime = new SizedStack<>(15);
    private long lastPingTime;

    /**
     * Create new client
     */
    public Client() {
        try {
            sock = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
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
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    /**
     * Start client, if no port and ip have been specified it will use localhost address and 7854 as port
     */
    public void startClient() {
        if (connection == null) {
            try {
                connection = new Connection(InetAddress.getByName("127.0.0.1"), 7854);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        running = true;
        clientThread = new ClientThread();
        clientThread.start();
        handleConnection.start();
        lastReceivedPackageTime = System.currentTimeMillis();
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

    public void sendPing() {
        sendData(new byte[0], NetworkSendType.PING);
    }

    /**
     * The last 15 package times in milli-seconds
     * @return
     */
    public SizedStack<Long> getPingTime() {
        return pingTime;
    }

    public long getLastPingTime() {
        return lastPingTime;
    }

    /**
     * Use this if you don't care if the message get to the server
     * @param sendData
     */
    public void sendUnreliableData(byte[] sendData) {
        sendData(sendData, NetworkSendType.UNRELIABLE_GAME_DATA);
    }

    /**
     * Use this if it is rely important the message get to the server
     * @param sendData
     */
    public void sendReliableData(byte[] sendData) {
        sendData(sendData, NetworkSendType.RELIABLE_GAME_DATA);
    }

    /**
     * You can trigger the resend method just to tell it to send messages that have reached the max limits of a message
     */
    public synchronized void resendData() {
        long currentTime = System.currentTimeMillis();
        for (NetworkPackage networkPackage : connection.getNetworkPackageArrayMap().values()) {
            if ((currentTime - networkPackage.getSentTime() - millisecondsBetweenResend) > 0) {
                try {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(byteBufferSize + networkPackage.getDataSent().length);
                    byteBuffer.putInt(protocalVersion).put((byte) NetworkSendType.RELIABLE_GAME_DATA.getTypeCode()).putInt(networkPackage.getSequenceNumber()).put(networkPackage.getDataSent());
                    sendData = byteBuffer.array();
                    dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
                    sock.send(dp);
                    networkPackage.resendData(networkPackage.getSequenceNumber());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Check if the server have been disconnected
     */
    public synchronized void disconnectInactiveServer() {
        if (clientThread != null && clientThread.isAlive()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime > millisecondToTimeout + lastReceivedPackageTime) {
                disconnectedClients(connection.getConnectionId());
                killConnection();
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

    private void sendData(byte[] data, NetworkSendType sendType) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(byteBufferSize + data.length);
            sendingPackage = connection.getLastSequenceNumber(data, sendType);
            byteBuffer.putInt(protocalVersion).put((byte) sendType.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).put(data);
            sendData = byteBuffer.array();
            dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
            sock.send(dp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendAck(int sequenceNumberToAck) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(byteBufferSize + 4);
            sendingPackage = connection.getLastSequenceNumber();
            byteBuffer.putInt(protocalVersion).put((byte) NetworkSendType.ACK.getTypeCode()).putInt(sendingPackage.getSequenceNumber()).putInt(sequenceNumberToAck);
            sendData = byteBuffer.array();
            dp = new DatagramPacket(sendData, sendData.length, connection.getAddress(), connection.getPort());
            sock.send(dp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientThread extends Thread {
        private ByteBuffer byteBuffer = ByteBuffer.allocate(byteBufferSize);
        private NetworkPackage tempPackage;
        @Override
        public void run() {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);

            while (running) {
                try {
                    sock.receive(incoming);

                    byte[] data = incoming.getData();
                    byteBuffer.clear();
                    byteBuffer.put(Arrays.copyOfRange(data, 0, byteBufferSize));
                    if (byteBuffer.getInt(0) != protocalVersion) {
                        continue;
                    }
                    // Anyone should be able to ping the connection if they have the correct protocol
                    if (byteBuffer.get(4) == NetworkSendType.PING.getTypeCode()) {
                        sendAck(byteBuffer.getInt(5));
                        continue;
                    }
                    // Check if we have already received data then send another ack and don't do anything else
                    if (connection.getNetworkPackageArrayMap().containsKey(byteBuffer.getInt(5))) {
                        sendAck(byteBuffer.getInt(5));
                        continue;
                    }
                    // Verify if we have received ack from before otherwise remove so we don't send more request
                    if (byteBuffer.get(4) == NetworkSendType.ACK.getTypeCode()) {
                        if (incoming.getLength() == 13) {
                            verifyAck(ByteBuffer.wrap(data, 9, 13).getInt());
                        }
                        continue;
                    }
                    message.setData(Arrays.copyOfRange(data, byteBufferSize, incoming.getLength()));
                    message.setNetworkSendType(NetworkSendType.fromByteValue(byteBuffer.get(4)));
                    message.setSequenceId(byteBuffer.getInt(5));

                    if (message.getNetworkSendType() == NetworkSendType.RELIABLE_GAME_DATA) {
                        sendAck(byteBuffer.getInt(5));
                    }

                    recivedMessage(message);
                    connection.updateTime();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void verifyAck(int sequenceNumberWasAcked) {
            tempPackage = connection.getNetworkPackageArrayMap().remove(sequenceNumberWasAcked);
            if (tempPackage != null) {
                long pingTime = System.currentTimeMillis() - tempPackage.getSentTime();
                getPingTime().push(pingTime);
                if (tempPackage.getNetworkSendType() == NetworkSendType.PING) {
                    lastPingTime = pingTime;
                }
            }
        }
    }

    private class HandleConnection extends Thread {

        @Override
        public void run() {
            while (running) {
                resendData();
                disconnectInactiveServer();
                try {
                    Thread.sleep(millisecondsToRecheckConnection);
                } catch (InterruptedException e) {
                    // Ignore error
                }
            }
        }
    }
}
