package net.corpwar.lib.corpnet.masterserver;

import net.corpwar.lib.corpnet.*;
import net.corpwar.lib.corpnet.master.TestMessage;
import net.corpwar.lib.corpnet.util.SerializationUtils;

import java.util.UUID;

/**
 * corpnet
 * Created by Ghost on 2016-12-15.
 */
public class PeerNatServerTest implements PeerReceiverListener {

    private PeerToPeer peerToMaster;

    public PeerNatServerTest() {
        peerToMaster = new PeerToPeer();
        peerToMaster.registerPeerListerner(this);
        peerToMaster.startPeer();
        System.out.println("Nat test client started");
        peerToMaster.connectToMasterServer("127.0.0.1", 44444);
        System.out.println("Peer amount: " + peerToMaster.getPeers().size());
        peerToMaster.testNatViaMasterServer();
    }


    @Override
    public void connected(Connection connection) {
        System.out.println("Connected: " + connection.getConnectionId() + " " + connection.getAddress().getHostAddress() + ":" + connection.getPort());
    }

    @Override
    public void receivedMessage(Message message) {
        System.out.println(new String(message.getData()) + "\nPeer amount: " + peerToMaster.getPeers().size());
        peerToMaster.getPeers().get(message.getConnectionID()).addToSendQue(SerializationUtils.getInstance().serialize(new TestMessage()), NetworkSendType.PEER_DATA);
    }

    @Override
    public void disconnected(UUID connectionId) {
        System.out.println("disconnected: " + connectionId);
    }

    public static void main (String[] args)  {
        new PeerNatServerTest();

    }
}
