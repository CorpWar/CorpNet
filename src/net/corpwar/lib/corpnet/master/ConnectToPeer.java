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

public class ConnectToPeer implements Serializable {

    public ConnectToPeer() {
    }

    public ConnectToPeer(UUID connectionID) {
        this.connectionID = connectionID;
    }

    public ConnectToPeer(int externalPort, String externalIp) {
        this.externalPort = externalPort;
        this.externalIp = externalIp;
    }

    // External port to peer
    public int externalPort;

    // External ip to peer
    public String externalIp;

    // UUID that should be connected to
    public UUID connectionID;
}
