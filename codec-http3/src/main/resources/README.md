# WebTransport over HTTP/3 in Netty

This module provides a simplified abstraction layer for WebTransport over HTTP/3 based on Netty's existing HTTP/3 and QUIC capabilities.

## Features

1. **Simplified API**: Provides high-level APIs for Session, Stream, and Datagram operations
2. **Observability Hooks**: Pluggable observer interface for monitoring WebTransport events
3. **Netty Style**: Follows Netty's conventions with ByteBuf, EventLoop, and backpressure support
4. **Default Noop Observer**: Provides a default no-operation observer implementation

## Core Components

### WebTransportSession
Interface defining WebTransport session operations:
- `createBidirectionalStream()`: Create a bidirectional stream
- `createUnidirectionalStream()`: Create a unidirectional stream
- `sendDatagram(ByteBuf data)`: Send a datagram
- `handleDatagram(ByteBuf data)`: Handle incoming datagram
- `close()`: Close the session
- `isActive()`: Check if the session is active

### WebTransportObserver
Interface for observability hooks:
- `onSessionEstablished(WebTransportSession session)`: Called when a session is established
- `onSessionClosed(WebTransportSession session, Throwable cause)`: Called when a session is closed
- `onBidirectionalStreamCreated(QuicStreamChannel stream)`: Called when a bidirectional stream is created
- `onUnidirectionalStreamCreated(QuicStreamChannel stream)`: Called when a unidirectional stream is created
- `onStreamClosed(QuicStreamChannel stream, Throwable cause)`: Called when a stream is closed
- `onDatagramSent(WebTransportSession session, ByteBuf data)`: Called when a datagram is sent
- `onDatagramReceived(WebTransportSession session, ByteBuf data)`: Called when a datagram is received
- `onError(WebTransportSession session, Throwable cause)`: Called when an error occurs

### NoopWebTransportObserver
Default no-operation implementation of WebTransportObserver.

### DefaultWebTransportSession
Default implementation of WebTransportSession using Netty's QuicChannel.

### WebTransportSessionProvider
Interface for creating WebTransport sessions with a default implementation.

### WebTransportChannelInitializer
Channel initializer for configuring WebTransport channels.

### WebTransportHandler
Handler for processing HTTP/3 CONNECT requests and WebTransport handshake.

### WebTransportServer
WebTransport server implementation.

### WebTransportClient
WebTransport client implementation.

## Usage Example

### Server

```java
QuicSslContext sslContext = QuicSslContextBuilder.forServer(
        new File("server.crt"), new File("server.key"))
        .build();

WebTransportObserver observer = new WebTransportObserver() {
    // Implement observer methods
};

WebTransportServer server = new WebTransportServer(4433, sslContext, observer);
ChannelFuture future = server.start();

// Wait for server to close
future.channel().closeFuture().sync();

// Stop server
server.stop();
```

### Client

```java
QuicSslContext sslContext = QuicSslContextBuilder.forClient()
        .trustManager(new File("server.crt"))
        .build();

WebTransportObserver observer = new WebTransportObserver() {
    // Implement observer methods
};

WebTransportClient client = new WebTransportClient("localhost", 4433, sslContext, observer);
ChannelFuture future = client.connect();

// Wait for client to close
future.channel().closeFuture().sync();

// Disconnect
client.disconnect();
```

## Building

```bash
mvn clean install
```

## Running the Example

```bash
java -cp target/netty-codec-http3-*.jar io.netty.handler.codec.http3.WebTransportExample
```

## Dependencies

- Netty 5.0+ (incubator QUIC module)
- Java 11+