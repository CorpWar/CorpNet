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
package net.corpwar.lib.corpnet.master;

import net.corpwar.lib.corpnet.*;
import net.corpwar.lib.corpnet.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class MasterServer implements DataReceivedListener {

    private static final Logger LOG = Logger.getLogger(MasterServer.class.getName());

    // Primary server to register to
    private Server masterServer;

    // Port on master server
    private int port;

    // IP on master server
    private String ipAddress;

    // Max connections on master server
    private int maxConnections;

    // Secondary server to try for symmetric NAT
    private Server secondServer;

    // All the peers that can be connected to
    private List<Peer> peers = new ArrayList<>();


    public MasterServer() {
        port = 44444;
        ipAddress = "127.0.0.1";
        maxConnections = 8;
    }

    public MasterServer(String ipAddress, int port, int maxConnections) {
        this.port = port;
        this.ipAddress = ipAddress;
        this.maxConnections = maxConnections;
    }

    public void startMasterServer(boolean testSymmetricNat) {
        masterServer = new Server(port, ipAddress, maxConnections);
        masterServer.registerServerListerner(this);
        masterServer.startServer();
        if (testSymmetricNat) {
            secondServer = new Server(port + 1, ipAddress, maxConnections);
        }
    }


    @Override
    public void receivedMessage(Message message) {
        if (message.getNetworkSendType().equals(NetworkSendType.PEER_DATA)) {
            if (SerializationUtils.getInstance().deserialize(message.getData()) instanceof RegisterPeer) {
                RegisterPeer registerPeer = SerializationUtils.getInstance().deserialize(message.getData());
                Connection connection = masterServer.getConnectionFromUUID(message.getConnectionID());
                Peer peer = new Peer(connection.getPort(), connection.getAddress().getHostAddress(), connection.getLastPingTime(), registerPeer.shortName, registerPeer.description, message.getConnectionID());
                peers.add(peer);
            } else if (SerializationUtils.getInstance().deserialize(message.getData()) instanceof RetrievePeerList) {
                Connection connection = masterServer.getConnectionFromUUID(message.getConnectionID());
                connection.addToSendQue(SerializationUtils.getInstance().serialize(new Peers(peers)), NetworkSendType.PEER_DATA);
            } else if (SerializationUtils.getInstance().deserialize(message.getData()) instanceof ConnectToPeer) {
                ConnectToPeer connectToPeer = SerializationUtils.getInstance().deserialize(message.getData());
                Connection connection = masterServer.getConnectionFromUUID(connectToPeer.connectionID);
                Connection askingPeer = masterServer.getConnectionFromUUID(message.getConnectionID());
                connection.addToSendQue(SerializationUtils.getInstance().serialize(new ConnectToPeer(askingPeer.getPort(), askingPeer.getAddress().getHostAddress())), NetworkSendType.PEER_DATA);
            }
        }
    }

    @Override
    public void disconnected(UUID connectionId) {
        for (Peer peer : peers) {
            if (peer.connectionID.equals(connectionId)) {
                peers.remove(peer);
                break;
            }
        }
    }
}
