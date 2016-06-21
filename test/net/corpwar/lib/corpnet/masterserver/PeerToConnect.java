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

import net.corpwar.lib.corpnet.*;
import net.corpwar.lib.corpnet.master.Peer;
import net.corpwar.lib.corpnet.master.Peers;

import java.util.Scanner;
import java.util.UUID;

public class PeerToConnect implements PeerReceiverListener {

    private Peers peers;
    private PeerToPeer peerToMaster;

    public PeerToConnect() {
        peerToMaster = new PeerToPeer();
        peerToMaster.registerPeerListerner(this);
        peerToMaster.startPeer();
        peerToMaster.connectToMasterServer("127.0.0.1", 44444);
        peers = peerToMaster.retrieveMasterServerList();
        if (peers != null) {
            for (Peer peer : peers.peers) {
                System.out.println("ip:" + peer.externalIp + " port:" + peer.externalPort);
            }
        }
    }

    public void connectToPeer(Integer number) {
        Peer peer = peers.peers.get(number);
        peerToMaster.connectToPeer(peer.externalPort, peer.externalIp);
        peerToMaster.connectToPeerViaMasterServer(peer.connectionID);
    }

    @Override
    public void connected(Connection connection) {

    }

    @Override
    public void receivedMessage(Message message) {
        System.out.println(new String(message.getData()));
    }

    @Override
    public void disconnected(UUID connectionId) {

    }

    public static void main (String[] args)  {
        PeerToConnect peerToConnect = new PeerToConnect();
        Scanner keyboard = new Scanner(System.in);
        boolean exit = false;
        while (!exit) {
            System.out.println("Enter command (quit to exit):");
            String input = keyboard.nextLine();
            if(input != null) {
                System.out.println("Your input is : " + input);
                if ("quit".equals(input)) {
                    System.out.println("Exit programm");
                    exit = true;
                } else {
                    Integer number = Integer.parseInt(input);
                    peerToConnect.connectToPeer(number);
                }
            }
        }
        keyboard.close();
    }

}
