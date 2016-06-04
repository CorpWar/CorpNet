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

import net.corpwar.lib.corpnet.DataReceivedListener;
import net.corpwar.lib.corpnet.Message;
import net.corpwar.lib.corpnet.PeerToPeer;

import java.util.Random;
import java.util.UUID;


public class PeerClient {

    private PeerToPeer peer;
    private Random random = new Random();

    public PeerClient() {
        peer = new PeerToPeer();
        peer.startPeer();
        peer.connectToPeer(20000, "127.0.0.1");

        peer.registerClientListerner(new DataReceivedListener() {
            @Override
            public void receivedMessage(Message message) {
                System.out.println("receivedMessage " + message.getConnectionID() + " : " + new String(message.getData()));
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
