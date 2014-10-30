CorpNet
=======

Java UDP game network librariy for client server solution.

If you want a fast UDP network library to your multiplayer game then this is the library for you.
There have been some things added to normal UDP.

- You can send reliable request and know that this package have been received.
- You need the correct id to be able to recevie packages.
- Packages have sequence number to idenify them and what order are correct if that are desirable.
 
There are a few things that are not handled. Or should be implemented.

- There are no flow control so if you send to many packages you might flood the connection.
- Packages can come in another order then you send them, if this is a problem you have to deal with it your self.

Default max package size are set to 4096 bytes. If you send data that are larger then this buffer and it need to be split in many packages then you need to make sure the data are received in the correct order.

All data will be sent in byte[] format. This is to give the developer full freedom how things should be sent, and that optimization can be done. To help out with transforming between objects and byte[] there will be utility classes instead.

 
## Starting server

This code start a server on port 55433 with ip 127.0.0.1. If you have different network cards you can tell what IP it should listen to.

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
 client.startClient();
 client.sendReliableData("Send a reliable message to server".getBytes());
 client.sendUnreliableData("Send an unreliable message that maybe get to the server".getBytes());
```

Just tell where the server are and start the thread on the client that handle all the connection to the server.
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

## Convert objects to byte[] and back again
If you want to transform objects to byte[] and back again there are a utility class that can help you out. The object you transform need to be marked with Serializable. You can check out SerializationUtilsTest.java in the repository to find out more how to use this. The code below are just a snipet how to use it. This is work in progress and can be change.

```Java
byte[] testByte = SerializationUtils.getInstance().serialize(testSerialization);
...
TestSerialization returnObj = SerializationUtils.getInstance().deserialize(testByte);
```

## Other great network libraries
If you looking for a good TCP and UDP library I suggest [KryoNet](https://github.com/EsotericSoftware/kryonet).
