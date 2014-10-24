CorpNet
=======

Java UDP game network librariy

If you want a fast UDP network library to your multiplayer game then this one can work for you.
There have been some things added to normal UDP.

- You can send reliable request and know that this package have been received.
- All messages have a protocal signature so not any package can get thrue, but only correct once.
- Packages have sequence number to idenify them and what order are correct if that are desirable.
 
There are a few things that are not handled. Or should be implemented.

- There are no flow controll so if you send to many packages you might flood the connection.
- Packages can come in another order then you send them so you need to handle this in some way.
- There are no timeout on client. If server disconnect the client don't tell.
 
## Staring server

This code start a server on port 55433 with ip 127.0.0.1. If you have different network cards you can tell what IP it should listen on.

```Java
  Server server = new Server();
  server.setPortAndIp(55433, "127.0.0.1");
  server.startServer();
```

The startServer method will start a new thread and handle all incoming traffic in that thread.
Default it will listen on port 7854 and 127.0.0.1 if you don't set port and ip.

This code adds a listener to handle receiving data from clients
```Java
  server.registerServerListerner(new DataReceivedListener() {
      public void recivedMessage(Message message) {
        byte[] data = message.getData();
        ...
      }
  });
```

## Connect client to server

This code start a client and connect it to the server on port 55433 with ip 127.0.0.1.
Then it first send a reliable message and then an unreliable message to the server.
```Java
 Client client = new Client();
 client.setPortAndIp(4567, "127.0.0.1");
 client.sendReliableData("Send a reliable message to server".getBytes());
 client.sendUnreliableData("Send a unreliable message to server".getBytes());
```

You need to make a call to setPortAndIp because in this method the thread to the client are started.
After this is setup you can easily send messages to the server.

This code adds a listener to handle receiving data from the server
```Java
 client.registerClientListerner(new DataReceivedListener() {
      public void recivedMessage(Message message) {
       byte[] data = message.getData();
        ...
      }
  });
```

## Other great network libraries
If you looking for a good TCP and UDP library I strongly suggest [KryoNet](https://github.com/EsotericSoftware/kryonet). You can easy transfur objects over the net with this library.
