CorpNet
=======

Java UDP game network librariy

If you want a fast UDP network library to your multiplayer game then this one can work for you.
There have been some things added to normal UDP.

- You can send reliable request and know that this package have been received.
- All messages have a protocal signature so not any package can get thrue, but only correct once.
- Packages have sequence number to idenify them.
 
There are a few things that are not handled.

- There are no flow controll so if you send to many packages you might flood the connection.
- Packages can come in another order then you send them so you need to handle this in some way.
 
## Staring server

This code start a server on port 55433 on 127.0.0.1. If you have different network cards you can tell what IP it should listen on.

```Java
  Server server = new Server();
  server.setPortAndIp(55433, "127.0.0.1");
  server.startServer();
```

The startServer method will start a new thread and handle all incoming traffic in that thread.
Default it will listen on port 7854 and 127.0.0.1 if you don't set port and ip.

This code adds a listener to handle receiving objects.
```Java
  server.registerServerListerner(new DataReceivedListener() {
      public void recivedMessage(Message message) {
        byte[] data = message.getData();
        ...
      }
  });
```

