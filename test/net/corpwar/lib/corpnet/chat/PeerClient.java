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
package net.corpwar.lib.corpnet.chat;

import net.corpwar.lib.corpnet.*;
import net.corpwar.lib.corpnet.master.RetrievePeerList;
import net.corpwar.lib.corpnet.util.PeerConnected;
import net.corpwar.lib.corpnet.util.PeerList;
import net.corpwar.lib.corpnet.util.SerializationUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.UUID;


public class PeerClient {

    private PeerToPeer peer;
    private Random random = new Random();

    public PeerClient() {
        peer = new PeerToPeer();
        peer.startPeer();
        peer.connectToPeer(20000, "127.0.0.1");
        try {
            Connection connection = new Connection(InetAddress.getByName("127.0.0.1"), 20000);
            connection = peer.getPeers().get(connection.getConnectionId());
            connection.addToSendQue(SerializationUtils.getInstance().serialize(new RetrievePeerList()), NetworkSendType.PEER_DATA);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        peer.registerPeerListerner(new PeerReceiverListener() {

            @Override
            public void connected(Connection connection) {
                System.out.println(connection.getAddress().getHostAddress() + ":" + connection.getPort());
            }

            @Override
            public void receivedMessage(Message message) {
                System.out.println("receivedMessage " + message.getConnectionID() + " : " + new String(message.getData()));
                if (message.getNetworkSendType() == NetworkSendType.PEER_DATA) {
                    Object object = SerializationUtils.getInstance().deserialize(message.getData());
                    if (object instanceof PeerList) {
                        PeerList peerList = (PeerList) object;
                        for (PeerConnected peerConnected : peerList.peerConnected) {
                            System.out.println(peerConnected.ipToPeer + ":" + peerConnected.peerPort + ":" + peerConnected.pingTime);
                        }
                    }
                }
            }

            @Override
            public void disconnected(UUID connectionId) {
                System.out.println("disconnected: " + connectionId);
            }
        });

        for (int i = 0; i < 1000; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            peer.sendReliableObjectToAllClients("Testing" + random.nextInt());
        }
    }

    public static void main (String[] args)  {

        new PeerClient();
    }

}
