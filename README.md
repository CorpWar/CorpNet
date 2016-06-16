![CorpNet](http://www.corpwar.net/wp-content/uploads/2014/10/corpnet.png)
=======

[![Build Status](http://home.corpwar.net:7890/buildStatus/icon?job=CorpNet)](http://home.corpwar.net:7890/job/CorpNet/)

You can find the latest snapshot version [here](http://home.corpwar.net:7890/job/CorpNet/lastBuild/net.corpwar.lib$corpnet/) 

Java R-UDP network library for client <-> server solutions and Peer-to-Peer connections.

If you want a fast UDP network library for your multiplayer game or application that support both reliable and unreliable packages, then this is the library for you.
This library work on both desktop and on Android.

## How to get going

I have added a wiki page that describe how to get going and how to use this library, so just go to wiki for this library to read more. If there are something that is hard to understand just add an issue and I take a look at it.

You can ether grab it from maven central
```
<dependency>
    <groupId>net.corpwar.lib</groupId>
    <artifactId>corpnet</artifactId>
    <version>1.7.0</version>
</dependency>
```
Or you can head over to [release section](https://github.com/CorpWar/CorpNet/releases) and download the latest version of the jar. Add this to your project and you should be good to go. Or just use the source from here to get the latest updates.

## Why use UDP instead of TCP

I did this library mostly to learn more about network but also after I read [this](http://gafferongames.com/networking-for-game-programmers/udp-vs-tcp/) article from Glenn Fiedler.
He describe very good why UDP can be more beneficial in some situation. If you should do games with multiplayer I suggest you read that article before you start.


## What this R-UDP support

There have been some things added to this library that isn't normally in UDP to get it more reliability.

- You can send reliable package and know it will be delivered to the other side.
- You will be informed if someone get disconnected.
- Every package have a unique id number.
- You can get information how long packages take to send.
- If packages are bigger then max package size (default 512) then it will be split. If an unreliable package don't arrive, the entire message will be discarded. Reliable messages will always be delivered.
- Flow control implemented. If to much data are sent unreliable messages are dropped. And sents are slowed down if ping goes up.
 
There are a few things that are not handled.

- Packages can still come in another order then you send them, if this is a problem you have to deal with it your self and can be done via sequence number that is sent with the package.

Default max package size are set to 512 bytes. After that it will be split up in smaller chunks.
If split packages are arrived in the wrong order then the framework will handle it and put it together in the right order.

All data will be sent in byte[] format. This is to give the developer full freedom how things should be sent, and that optimization can be done. To help out with transforming between objects and byte[] there will be methods for that.

If you add this library to Android don't forget this
```Java
    <uses-permission android:name="android.permission.INTERNET" />
 ```
 or it will not work.
 
## Convert objects to byte[] and back again
If you want to transform objects to byte[] and back again there are a utility class that can help you out. The object you transform need to be marked with Serializable. You can check out SerializationUtilsTest.java in the repository to find out more how to use this. The code below are just a snipet how to use it. This is work in progress and can be change.

```Java
byte[] testByte = SerializationUtils.getInstance().serialize(testSerialization);
...
TestSerialization returnObj = SerializationUtils.getInstance().deserialize(testByte);
```

## Other great network libraries
If you looking for a good TCP and UDP library I suggest [KryoNet](https://github.com/EsotericSoftware/kryonet).
