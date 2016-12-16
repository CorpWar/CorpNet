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
package net.corpwar.lib.corpnet.masterserver;

import net.corpwar.lib.corpnet.Connection;
import net.corpwar.lib.corpnet.Message;
import net.corpwar.lib.corpnet.PeerReceiverListener;
import net.corpwar.lib.corpnet.PeerToPeer;

import java.util.UUID;

public class PeerToMaster implements PeerReceiverListener {

    public PeerToMaster() {
        PeerToPeer peerToMaster = new PeerToPeer();
        peerToMaster.startPeer();
        peerToMaster.connectToMasterServer("127.0.0.1", 44444);
        peerToMaster.registerToMasterServer();
        peerToMaster.registerPeerListerner(this);
        System.out.println("PeerToMaster started!");
        while(true) {
            if (peerToMaster.getPeers().size() > 0) {
                peerToMaster.sendReliableObjectToAllClients("testing222222");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void connected(Connection connection) {
        System.out.println("Connected: " + connection.getPort());
    }

    @Override
    public void receivedMessage(Message message) {
        System.out.println("UUID: " + message.getConnectionID() + " " + new String(message.getData()));
    }

    @Override
    public void disconnected(UUID connectionId) {
        System.out.println("disconnected: " + connectionId);
    }

    public static void main (String[] args)  {

        new PeerToMaster();
    }

}
