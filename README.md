CorpNet
=======

Java UDP game network librariy

If you need a simple network library to send data in your game then you can use this. It can send both reliable and unreliable data.

It use only UDP so you only need to open one port.
So far there are some restriction to it. You can't tell in what order things will come, they might come out of order.
If you need to split reliable data in more packaged you need to manage that they are coming in the correct order.
There are no flow controll so don't send to much data to fast, then you can kill the server or a client.
