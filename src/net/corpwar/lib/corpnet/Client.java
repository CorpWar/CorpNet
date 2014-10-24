package net.corpwar.lib.corpnet;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * CorpNet
 * Created by Ghost on 2014-10-05.
 */
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
    private long milisecoundsBetweenResend = 100;

    private Connection connection;
    private DatagramSocket sock = null;
    private DatagramPacket dp;
    private byte[] sendData;
    private boolean running = true;
    private ClientThread clientThread;
    private NetworkPackage sendingPackage;
    private final ArrayList<DataReceivedListener> dataReceivedListeners = new ArrayList<>();
    private Message message = new Message();
    private final SizedStack<Long> pingTime = new SizedStack<>(15);
    private long lastPingTime;

    public Client() {
        try {
            sock = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void registerClientListerner(DataReceivedListener dataReceivedListener) {
        dataReceivedListeners.add(dataReceivedListener);
    }

    public void recivedMessage() {
        for (DataReceivedListener dataReceivedListener : dataReceivedListeners) {
            dataReceivedListener.recivedMessage(message);
        }
    }

    public void setPortAndIp(int port, String serverIP) {
        try {
            if (clientThread == null || !clientThread.isAlive()) {
                connection = new Connection(InetAddress.getByName(serverIP), port);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void startClient() {
        if (connection == null) {
            try {
                connection = new Connection(InetAddress.getByName("127.0.0.1"), 7854);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        clientThread = new ClientThread();
        clientThread.start();
    }

    public void setProtocalVersion(int protocalVersion) {
        this.protocalVersion = protocalVersion;
    }

    public void setMilisecoundsBetweenResend(long milisecoundsBetweenResend) {
        this.milisecoundsBetweenResend = milisecoundsBetweenResend;
    }

    public void sendPing() {
        sendData(new byte[0], NetworkSendType.PING);
    }

    public SizedStack<Long> getPingTime() {
        return pingTime;
    }

    public long getLastPingTime() {
        return lastPingTime;
    }

    public void sendUnreliableData(byte[] sendData) {
        sendData(sendData, NetworkSendType.UNRELIABLE_GAME_DATA);
    }

    public void sendReliableData(byte[] sendData) {
        sendData(sendData, NetworkSendType.RELIABLE_GAME_DATA);
    }

    public void resendData() {
        long currentTime = System.currentTimeMillis();
        for (NetworkPackage networkPackage : connection.getNetworkPackageArrayMap().values()) {
            if ((currentTime - networkPackage.getSentTime() - milisecoundsBetweenResend) > 0) {
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

    public void killConnection() {
        if (clientThread.isAlive()) {
            running = false;
            sock.close();
            clientThread.interrupt();
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

                    recivedMessage();
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
}
