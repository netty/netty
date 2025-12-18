# WebTransport over HTTP/3 模块

## 概述

本模块基于 Netty 现有的 HTTP/3 / QUIC 能力，实现了 WebTransport over HTTP/3 的简化抽象层，并提供基础的可观测性钩子。

## 实现目标

1. **封装 HTTP/3 CONNECT WebTransport 细节**：隐藏底层 HTTP/3 和 QUIC 的复杂细节，提供简洁的高层 API。
2. **提供 Session / Stream / Datagram 的高层 API**：支持会话管理、流操作和数据报传输。
3. **提供可插拔的观测接口**：默认实现空观测，用户可以自定义观测逻辑。
4. **保持 Netty 风格**：使用 ByteBuf、EventLoop、backpressure 等 Netty 核心概念。

## 核心类与接口

### 1. WebTransportSession

WebTransport 会话的高层抽象，封装 HTTP/3 CONNECT WebTransport 细节。

**主要方法**：
- `sessionId()`：获取会话的 ID。
- `createBidirectionalStream()`：创建一个新的双向流。
- `createUnidirectionalStream()`：创建一个新的单向流。
- `sendDatagram(ByteBuf datagram)`：发送数据报。
- `close()`：关闭会话。
- `closeFuture()`：监听会话的关闭事件。
- `observer()`：获取会话的观测者。

### 2. WebTransportStream

WebTransport 流的高层抽象，提供流的读写操作。

**主要方法**：
- `streamId()`：获取流的 ID。
- `write(ByteBuf buf)`：写入数据到流。
- `writeAndFlush(ByteBuf buf)`：写入数据到流并立即刷新。
- `close()`：关闭流。
- `closeFuture()`：监听流的关闭事件。

### 3. WebTransportDatagram

WebTransport 数据报的高层抽象。

**主要方法**：
- `content()`：获取数据报的有效负载。
- `channel()`：获取数据报的源通道。

### 4. WebTransportObserver

可插拔的 WebTransport 观测接口，用于收集会话、流、数据报的事件。

**主要方法**：
- `onSessionCreated(WebTransportSession session)`：当新的 WebTransport 会话建立时调用。
- `onSessionClosed(WebTransportSession session, Future<Void> future)`：当 WebTransport 会话关闭时调用。
- `onStreamCreated(WebTransportStream stream)`：当新的 WebTransport 流创建时调用。
- `onStreamClosed(WebTransportStream stream, Future<Void> future)`：当 WebTransport 流关闭时调用。
- `onDatagramReceived(WebTransportSession session, WebTransportDatagram datagram)`：当接收到 WebTransport 数据报时调用。
- `onDatagramSent(WebTransportSession session, WebTransportDatagram datagram)`：当发送 WebTransport 数据报时调用。
- `onError(Channel channel, Throwable cause)`：当发生错误时调用。

### 5. NoopWebTransportObserver

WebTransportObserver 的空实现，所有方法不做任何操作。

### 6. WebTransportHandler

WebTransport 会话的处理器，负责处理 HTTP/3 CONNECT 请求并建立 WebTransport 会话。

### 7. WebTransportSessionProvider

WebTransportSession 的提供者，用于从通道中获取 WebTransportSession。

### 8. WebTransportChannelInitializer

WebTransport 通道初始化器，用于配置 WebTransport 通道的处理器。

### 9. WebTransportServer

WebTransport 服务器，用于启动 WebTransport 服务。

### 10. WebTransportClient

WebTransport 客户端，用于连接 WebTransport 服务器。

### 11. WebTransportExample

WebTransport 使用示例，展示如何建立会话、创建流和发送数据报。

## 使用示例

### 启动服务器

```java
WebTransportServer server = new WebTransportServer(8443, new CustomWebTransportObserver());
ChannelFuture serverFuture = server.start();
```

### 连接服务器

```java
WebTransportClient client = new WebTransportClient("localhost", 8443, new CustomWebTransportObserver());
ChannelFuture clientFuture = client.connect();
```

### 获取会话

```java
WebTransportSession session = client.getSession();
```

### 创建双向流并发送数据

```java
session.createBidirectionalStream().addListener(future -> {
    if (future.isSuccess()) {
        WebTransportStream stream = future.getNow();
        ByteBuf buf = Unpooled.copiedBuffer("Hello, WebTransport!", CharsetUtil.UTF_8);
        stream.writeAndFlush(buf);
    }
});
```

### 发送数据报

```java
ByteBuf datagram = Unpooled.copiedBuffer("Hello, WebTransport Datagram!", CharsetUtil.UTF_8);
session.sendDatagram(datagram);
```

### 自定义观测者

```java
private static class CustomWebTransportObserver extends NoopWebTransportObserver {

    @Override
    public void onSessionCreated(WebTransportSession session) {
        System.out.println("Session created: " + session.sessionId());
    }

    @Override
    public void onSessionClosed(WebTransportSession session, Future<Void> future) {
        System.out.println("Session closed: " + session.sessionId());
    }

    @Override
    public void onStreamCreated(WebTransportStream stream) {
        System.out.println("Stream created: " + stream.streamId());
    }

    @Override
    public void onStreamClosed(WebTransportStream stream, Future<Void> future) {
        System.out.println("Stream closed: " + stream.streamId());
    }

    @Override
    public void onDatagramReceived(WebTransportSession session, WebTransportDatagram datagram) {
        System.out.println("Datagram received: " + datagram.content().toString(CharsetUtil.UTF_8));
    }

    @Override
    public void onDatagramSent(WebTransportSession session, WebTransportDatagram datagram) {
        System.out.println("Datagram sent: " + datagram.content().toString(CharsetUtil.UTF_8));
    }

    @Override
    public void onError(Channel channel, Throwable cause) {
        System.err.println("Error: " + cause.getMessage());
        cause.printStackTrace();
    }
}
```

## 注意事项

1. 本模块依赖 Netty 现有的 HTTP/3 和 QUIC 实现。
2. 需要使用 TLS 1.3 协议。
3. 数据报的大小受限于 QUIC 数据包的最大大小。
4. 流的数量受限于 HTTP/3 连接的最大并发流数。

## 许可证

本模块遵循 Apache License, Version 2.0 许可证。
