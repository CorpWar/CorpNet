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

import java.io.Serializable;
import java.util.UUID;

public class Peer implements Serializable{

    // External port to peer
    public int externalPort;

    // External ip to peer
    public String externalIp;

    // Ping time to peer
    public long ping;

    // A short description or name of the peer
    public String shortName;

    // Longer description of peer
    public String description;

    // Handle the connection
    public UUID connectionID;

    public Peer(int externalPort, String externalIp) {
        this.externalPort = externalPort;
        this.externalIp = externalIp;
    }

    public Peer(int externalPort, String externalIp, long ping, String shortName, String description, UUID connectionID) {
        this.externalPort = externalPort;
        this.externalIp = externalIp;
        this.ping = ping;
        this.shortName = shortName;
        this.description = description;
        this.connectionID = connectionID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Peer peer = (Peer) o;

        return connectionID != null ? connectionID.equals(peer.connectionID) : peer.connectionID == null;

    }

    @Override
    public int hashCode() {
        return connectionID != null ? connectionID.hashCode() : 0;
    }
}
