# Netty QUIC codec

This is a new experimental QUIC codec for netty which makes use of [quiche](https://github.com/cloudflare/quiche).

## How to include the dependency

To include the dependency you need to ensure you also specify the right classifier. At the moment we only support linux
 x86_64 and macOS / OSX x86_64 but this may change. 
 
As an example this is how you would include the dependency in maven:
```
<dependency>
    <groupId>io.netty.incubator</groupId>
    <artifactId>netty-incubator-codec-quic</artifactId>
    <version>0.0.1.Final-SNAPSHOT</version>
    <classifier>linux-x86_64</classifier>
</dependency>
```

For macOS / OSX:

```
<dependency>
    <groupId>io.netty.incubator</groupId>
    <artifactId>netty-incubator-codec-quic</artifactId>
    <version>0.0.1.Final-SNAPSHOT</version>
    <classifier>osx-x86_64</classifier>
</dependency>
```

## How to use this codec ?

For some examples please check our 
[example package](https://github.com/netty/netty-incubator-codec-quic/tree/main/src/test/java/io/netty/incubator/codec/quic).
This contains a server and a client that can speak some limited HTTP/0.9 with each other.
