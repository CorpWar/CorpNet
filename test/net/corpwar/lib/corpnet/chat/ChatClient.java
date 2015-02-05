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

import net.corpwar.lib.corpnet.Client;
import net.corpwar.lib.corpnet.DataReceivedListener;
import net.corpwar.lib.corpnet.Message;
import net.corpwar.lib.corpnet.util.SerializationUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Random;
import java.util.UUID;

/**
 * corpnet
 * Created by Ghost on 2014-12-13.
 */
public class ChatClient {


    private ChatFrame chatFrame;
    private Client client;

    public ChatClient() {
        client = new Client();
        client.startClient();

        client.registerClientListerner(new DataReceivedListener() {
            @Override
            public void recivedMessage(Message message) {
                Object obj = SerializationUtils.getInstance().deserialize(message.getData());
                if (obj instanceof Classes.SendMessage) {
                    chatFrame.addMessage(((Classes.SendMessage) obj).message);
                }
            }

            @Override
            public void disconnected(UUID connectionId) {

            }
        });



        chatFrame = new ChatFrame();

        chatFrame.setSendListener(new Runnable() {
            public void run () {
                Classes.SendMessage sendMessage = new Classes.SendMessage();
                sendMessage.message = chatFrame.getSendText();
                client.sendUnreliableData(SerializationUtils.getInstance().serialize(sendMessage));
            }
        });
        chatFrame.setCloseListener(new Runnable() {
            public void run () {
                client.killConnection();
            }
        });
        chatFrame.setVisible(true);

        Classes.RegisterNick nick = new Classes.RegisterNick();
        Random rand = new Random();
        nick.nickname = "test-" + rand.nextInt(10000);
        client.sendReliableDataObject(nick);

        SwingWorker worker = new SwingWorker() {
            @Override
            protected Object doInBackground() throws Exception {
                int i = 0;
                Classes.SendMessage sendMessage = new Classes.SendMessage();
                sendMessage.message = " nu ska vi skicka jätte mycket data som man bara ska skicka ut tilla massor av clienter bara för att testa om det går att skicka massa data med en sträng. Vem vet vad som kan hända om man gör på detta sättet. Det kanska går jättebra. Vem vet!!";
                while (true) {
                    Thread.sleep(50);
                    client.sendUnreliableDataObject(sendMessage);
                    if (i > 100000000) {
                        break;
                    }
                    i++;
                }
                return null;
            }
        };
        worker.execute();
    }

    static private class ChatFrame extends JFrame {
        CardLayout cardLayout;
        JList messageList;
        JTextField sendText;
        JButton sendButton;
        JList nameList;

        public ChatFrame () {
            super("Chat Client");
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setSize(640, 200);
            setLocationRelativeTo(null);

            Container contentPane = getContentPane();
            cardLayout = new CardLayout();
            contentPane.setLayout(cardLayout);
            {
                JPanel panel = new JPanel(new BorderLayout());
                contentPane.add(panel, "chat");
                {
                    JPanel topPanel = new JPanel(new GridLayout(1, 2));
                    panel.add(topPanel);
                    {
                        topPanel.add(new JScrollPane(messageList = new JList()));
                        messageList.setModel(new DefaultListModel());
                    }
                    {
                        topPanel.add(new JScrollPane(nameList = new JList()));
                        nameList.setModel(new DefaultListModel());
                    }
                    DefaultListSelectionModel disableSelections = new DefaultListSelectionModel() {
                        public void setSelectionInterval (int index0, int index1) {
                        }
                    };
                    messageList.setSelectionModel(disableSelections);
                    nameList.setSelectionModel(disableSelections);
                }
                {
                    JPanel bottomPanel = new JPanel(new GridBagLayout());
                    panel.add(bottomPanel, BorderLayout.SOUTH);
                    bottomPanel.add(sendText = new JTextField(), new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER,
                            GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
                    bottomPanel.add(sendButton = new JButton("Send"), new GridBagConstraints(1, 0, 1, 1, 0, 0,
                            GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));
                }
            }

            sendText.addActionListener(new ActionListener() {
                public void actionPerformed (ActionEvent e) {
                    sendButton.doClick();
                }
            });
        }

        public void setSendListener (final Runnable listener) {
            sendButton.addActionListener(new ActionListener() {
                public void actionPerformed (ActionEvent evt) {
                    if (getSendText().length() == 0) return;
                    listener.run();
                    sendText.setText("");
                    sendText.requestFocus();
                }
            });
        }

        public void setCloseListener (final Runnable listener) {
            addWindowListener(new WindowAdapter() {
                public void windowClosed (WindowEvent evt) {
                    listener.run();
                }

                public void windowActivated (WindowEvent evt) {
                    sendText.requestFocus();
                }
            });
        }

        public String getSendText () {
            return sendText.getText().trim();
        }

        public void setNames (final String[] names) {
            EventQueue.invokeLater(new Runnable() {
                public void run () {
                    cardLayout.show(getContentPane(), "chat");
                    DefaultListModel model = (DefaultListModel)nameList.getModel();
                    model.removeAllElements();
                    for (String name : names)
                        model.addElement(name);
                }
            });
        }

        public void addMessage (final String message) {
            EventQueue.invokeLater(new Runnable() {
                public void run () {
                    DefaultListModel model = (DefaultListModel)messageList.getModel();
                    model.addElement(message);
                    messageList.ensureIndexIsVisible(model.size() - 1);
                }
            });
        }
    }

    public static void main (String[] args) {

        new ChatClient();
    }
}
