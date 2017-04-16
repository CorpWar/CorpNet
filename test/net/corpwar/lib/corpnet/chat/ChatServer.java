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
package net.corpwar.lib.corpnet.chat;

import net.corpwar.lib.corpnet.*;
import net.corpwar.lib.corpnet.util.SerializationUtils;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.UUID;

/**
 * corpnet
 * Created by Ghost on 2014-12-13.
 */
public class ChatServer {

    private Server server;
    private Classes.SendMessage sendMessage = new Classes.SendMessage();

    public ChatServer() {
        server = new Server();
        server.setKeepAlive(true);
        server.startServer();

        server.registerServerListerner(new DataReceivedListener() {
            @Override
            public void connected(Connection connection) {

            }

            @Override
            public void receivedMessage(Message message) {
                Object obj = SerializationUtils.getInstance().deserialize(message.getData());
                if (obj instanceof Classes.RegisterNick) {
                    Classes.RegisterNick regNick = (Classes.RegisterNick) obj;
                    if (regNick.nickname == null || regNick.nickname.length() == 0) {
                        return;
                    }
                    sendMessage.message = regNick.nickname + " joined";
                    server.sendReliableToAllClients(SerializationUtils.getInstance().serialize(sendMessage));
                } else if (obj instanceof Classes.SendMessage) {
                    sendMessage = (Classes.SendMessage) obj;
                    if (message.getNetworkSendType() == NetworkSendType.RELIABLE_GAME_DATA) {
                        server.sendReliableToAllClients(SerializationUtils.getInstance().serialize(sendMessage));
                    } else {
                        server.sendUnreliableToAllClients(SerializationUtils.getInstance().serialize(sendMessage));
                    }

                }
            }

            @Override
            public void disconnected(UUID connectionId) {
                System.out.println("Disconnected: " + connectionId.toString());
            }
        });


        // Open a window to provide an easy way to stop the server.
        JFrame frame = new JFrame("Chat Server");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosed (WindowEvent evt) {
                server.killServer();
            }
        });
        frame.getContentPane().add(new JLabel("Close to stop the chat server."));
        frame.setSize(320, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main (String[] args)  {

        new ChatServer();
    }
}
