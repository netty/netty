/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilterProvider;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.handler.codec.http2.Http2WebSocketHandshaker.ControlFrameHandling;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.http.HttpHeaderNames.ORIGIN;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL;
import static io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker.DEFAULT_MEM_LEVEL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(30)
class Http2WebSocketTransportTest {
    private static final int MAX_FRAME_SIZE = 65536;
    private static final int STREAM_WINDOW = 1024;
    private static final int TIMEOUT_SECONDS = 10;

    private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
    private final CompletableFuture<Http2Settings> settings = new CompletableFuture<Http2Settings>();
    private final CompletableFuture<Http2Headers> serverRequest = new CompletableFuture<Http2Headers>();
    private final CompletableFuture<Http2StreamChannel> serverStream = new CompletableFuture<Http2StreamChannel>();
    private final CompletableFuture<Short> serverPing = new CompletableFuture<Short>();
    private final CompletableFuture<Short> serverPong = new CompletableFuture<Short>();
    private final CompletableFuture<Short> clientPing = new CompletableFuture<Short>();
    private final CompletableFuture<Short> clientPong = new CompletableFuture<Short>();
    private final CompletableFuture<Integer> serverCloseCode = new CompletableFuture<Integer>();
    private final CompletableFuture<String> clientCloseReason = new CompletableFuture<String>();
    private final CompletableFuture<byte[]> binaryEcho = new CompletableFuture<byte[]>();
    private final BlockingQueue<String> textEchoes = new LinkedBlockingQueue<String>();
    private final Wire serverWire = new Wire();
    private final Wire clientWire = new Wire();
    private Channel listener;
    private Channel client;
    private Http2StreamChannel webSocket;
    private String scheme;
    private Http2Headers response;

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (listener != null) {
            listener.close();
        }
        group.shutdownGracefully(0, 0, SECONDS).get(TIMEOUT_SECONDS, SECONDS);
    }

    @ParameterizedTest
    @CsvSource({ "false, false, false", "false, true, true", "false, true, false", "false, false, true",
                 "true, false, false", "true, true, true" })
    void handshakeEchoAndClose(boolean tls, boolean clientCompression, boolean serverCompression) throws Exception {
        start(tls, serverCompression, false);
        connectWebSocket(clientCompression);
        boolean compressed = clientCompression && serverCompression;
        assertThat(response.status().toString()).isEqualTo("200");
        assertThat(response.get(SEC_WEBSOCKET_PROTOCOL).toString()).isEqualTo("chat");
        assertThat(response.contains(SEC_WEBSOCKET_EXTENSIONS)).isEqualTo(compressed);
        Http2Headers request = serverRequest.get(TIMEOUT_SECONDS, SECONDS);
        assertThat(request.get(ORIGIN).toString()).isEqualTo("https://example.org");
        assertThat(request.contains(SEC_WEBSOCKET_EXTENSIONS)).isEqualTo(clientCompression);
        if (compressed) {
            assertThat(response.get(SEC_WEBSOCKET_EXTENSIONS).toString()).startsWith("permessage-deflate");
        }
        if (tls) {
            assertThat(client.pipeline().get(SslHandler.class).applicationProtocol())
                    .isEqualTo(ApplicationProtocolNames.HTTP_2);
            Channel server = serverStream.get(TIMEOUT_SECONDS, SECONDS).parent();
            assertThat(server.pipeline().get(SslHandler.class).applicationProtocol())
                    .isEqualTo(ApplicationProtocolNames.HTTP_2);
        }

        webSocket.writeAndFlush(new TextWebSocketFrame(false, 0, "Hello ")).get(TIMEOUT_SECONDS, SECONDS);
        webSocket.writeAndFlush(new ContinuationWebSocketFrame(true, 0, "world")).get(TIMEOUT_SECONDS, SECONDS);
        assertThat(textEchoes.poll(TIMEOUT_SECONDS, SECONDS)).isEqualTo("Hello ");
        assertThat(textEchoes.poll(TIMEOUT_SECONDS, SECONDS)).isEqualTo("world");
        // FIN=0, opcode=TEXT, optional RSV1, then the mask bit in the second octet.
        int flags = compressed ? 0x4100 : 0x0100;
        assertThat(serverWire.firstHeader.get(TIMEOUT_SECONDS, SECONDS) & 0xff80).isEqualTo(flags | 0x80);
        assertThat(clientWire.firstHeader.get(TIMEOUT_SECONDS, SECONDS) & 0xff80).isEqualTo(flags);
        getHealth();

        webSocket.writeAndFlush(new CloseWebSocketFrame(1000, "finished")).get(TIMEOUT_SECONDS, SECONDS);
        assertGracefulClose();
        assertThat(clientCloseReason.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo("finished");
    }

    @Test
    void closingTheServerChannelCompletesTheWebSocketClosingHandshake() throws Exception {
        start(false, false, false);
        connectWebSocket(false);
        serverStream.get(TIMEOUT_SECONDS, SECONDS).close().get(TIMEOUT_SECONDS, SECONDS);
        assertGracefulClose();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void serverApplicationOwnsPingAndADeferredCloseReply(boolean autoRead) throws Exception {
        start(false, false, !autoRead, ControlFrameHandling.APPLICATION);
        connectWebSocket(false);
        Http2StreamChannel server = serverStream.get(TIMEOUT_SECONDS, SECONDS);
        server.read();

        webSocket.writeAndFlush(new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[] { 31 })))
                .get(TIMEOUT_SECONDS, SECONDS);
        assertThat(serverPing.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo((short) 31);
        assertThat(clientPong.isDone()).isFalse();
        server.writeAndFlush(new PongWebSocketFrame(Unpooled.wrappedBuffer(new byte[] { 31 })))
                .get(TIMEOUT_SECONDS, SECONDS);
        assertThat(clientPong.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo((short) 31);

        server.read();
        webSocket.writeAndFlush(new CloseWebSocketFrame(1000, "client close")).get(TIMEOUT_SECONDS, SECONDS);
        assertThat(serverCloseCode.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo(1000);
        assertThat(clientCloseReason.isDone()).isFalse();
        assertThat(server.isActive()).isTrue();
        getHealth();

        server.writeAndFlush(new CloseWebSocketFrame(1000, "server application reply"))
                .get(TIMEOUT_SECONDS, SECONDS);
        assertGracefulClose();
        assertThat(clientCloseReason.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo("server application reply");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void applicationHandlesPingBetweenCompressedFragments(boolean tls) throws Exception {
        WebSocketExtensionFilterProvider filters = new WebSocketExtensionFilterProvider() {
            @Override
            public WebSocketExtensionFilter encoderFilter() {
                return frame -> frame instanceof PingWebSocketFrame || frame instanceof PongWebSocketFrame;
            }

            @Override
            public WebSocketExtensionFilter decoderFilter() {
                return encoderFilter();
            }
        };
        PerMessageDeflateClientExtensionHandshaker clientCompression = new PerMessageDeflateClientExtensionHandshaker(
                1, false, 15, true, true, filters, 8192);
        PerMessageDeflateServerExtensionHandshaker serverCompression = new PerMessageDeflateServerExtensionHandshaker(
                9, false, 15, true, true, 15, DEFAULT_MEM_LEVEL, filters, 16384);
        start(tls, false, false, ControlFrameHandling.APPLICATION, serverCompression);
        connectWebSocket(false, ControlFrameHandling.APPLICATION, clientCompression);
        Http2StreamChannel server = serverStream.get(TIMEOUT_SECONDS, SECONDS);
        assertThat(response.get(SEC_WEBSOCKET_EXTENSIONS).toString()).startsWith("permessage-deflate")
                .contains("client_no_context_takeover", "server_no_context_takeover");

        webSocket.writeAndFlush(new TextWebSocketFrame(false, 0, "Hello ")).get(TIMEOUT_SECONDS, SECONDS);
        assertThat(textEchoes.poll(TIMEOUT_SECONDS, SECONDS)).isEqualTo("Hello ");
        assertThat(serverWire.firstHeader.get(TIMEOUT_SECONDS, SECONDS) & 0xff80).isEqualTo(0x4180);
        assertThat(clientWire.firstHeader.get(TIMEOUT_SECONDS, SECONDS) & 0xff80).isEqualTo(0x4100);

        webSocket.writeAndFlush(new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[] { 42 })))
                .get(TIMEOUT_SECONDS, SECONDS);
        assertThat(serverPing.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo((short) 42);
        server.writeAndFlush(new PongWebSocketFrame(Unpooled.wrappedBuffer(new byte[] { 42 })))
                .get(TIMEOUT_SECONDS, SECONDS);
        assertThat(clientPong.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo((short) 42);
        webSocket.writeAndFlush(new ContinuationWebSocketFrame(true, 0, "world")).get(TIMEOUT_SECONDS, SECONDS);
        assertThat(textEchoes.poll(TIMEOUT_SECONDS, SECONDS)).isEqualTo("world");

        webSocket.writeAndFlush(new CloseWebSocketFrame(1000, "done")).get(TIMEOUT_SECONDS, SECONDS);
        assertThat(serverCloseCode.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo(1000);
        server.writeAndFlush(new CloseWebSocketFrame(1000, "application reply")).get(TIMEOUT_SECONDS, SECONDS);
        assertGracefulClose();
        assertThat(clientCloseReason.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo("application reply");
    }

    @Test
    void clientApplicationOwnsPingAndADeferredCloseReply() throws Exception {
        start(false, false, false);
        connectWebSocket(false, ControlFrameHandling.APPLICATION);
        Http2StreamChannel server = serverStream.get(TIMEOUT_SECONDS, SECONDS);

        server.writeAndFlush(new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[] { 37 })))
                .get(TIMEOUT_SECONDS, SECONDS);
        assertThat(clientPing.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo((short) 37);
        assertThat(serverPong.isDone()).isFalse();
        webSocket.writeAndFlush(new PongWebSocketFrame(Unpooled.wrappedBuffer(new byte[] { 37 })))
                .get(TIMEOUT_SECONDS, SECONDS);
        assertThat(serverPong.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo((short) 37);

        server.writeAndFlush(new CloseWebSocketFrame(1000, "server close")).get(TIMEOUT_SECONDS, SECONDS);
        assertThat(clientCloseReason.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo("server close");
        assertThat(serverCloseCode.isDone()).isFalse();
        assertThat(webSocket.isActive()).isTrue();
        getHealth();

        webSocket.writeAndFlush(new CloseWebSocketFrame(1000, "client application reply"))
                .addListener(ChannelFutureListener.CLOSE)
                .get(TIMEOUT_SECONDS, SECONDS);
        assertGracefulClose();
    }

    @Test
    void closingTheApplicationManagedChannelBeforeWritingCloseOnlyAbortsThatStream() throws Exception {
        start(false, false, false, ControlFrameHandling.APPLICATION);
        connectWebSocket(false);
        Http2StreamChannel server = serverStream.get(TIMEOUT_SECONDS, SECONDS);

        server.close().get(TIMEOUT_SECONDS, SECONDS);
        webSocket.closeFuture().get(TIMEOUT_SECONDS, SECONDS);
        assertThat(clientCloseReason.isDone()).isFalse();
        assertThat(clientWire.reset.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo(Http2Error.CANCEL.code());
        getHealth();
    }

    private void assertGracefulClose() throws Exception {
        webSocket.closeFuture().get(TIMEOUT_SECONDS, SECONDS);
        Http2StreamChannel server = serverStream.get(TIMEOUT_SECONDS, SECONDS);
        server.closeFuture().get(TIMEOUT_SECONDS, SECONDS);
        assertThat(serverCloseCode.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo(1000);
        assertThat(clientWire.endStream.isDone()).isTrue();
        assertThat(serverWire.endStream.isDone()).isTrue();
        assertThat(clientWire.reset.isDone()).isFalse();
        assertThat(serverWire.reset.isDone()).isFalse();
        getHealth();
    }

    @Test
    void webSocketFramesCanCrossAndShareDataFrames() throws Exception {
        start(false, false, false);
        connectWebSocket(false);
        ChannelHandlerContext wire = webSocket.pipeline().context(clientWire);
        // A single octet of the first frame, then its remaining bytes and a complete second frame.
        wire.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[] { (byte) 0x81 })))
                .get(TIMEOUT_SECONDS, SECONDS);
        byte[] remaining = { (byte) 0x81, 1, 2, 3, 4, 'a' ^ 1,
                (byte) 0x81, (byte) 0x81, 9, 8, 7, 6, 'b' ^ 9 };
        wire.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(remaining))).get(TIMEOUT_SECONDS, SECONDS);
        assertThat(textEchoes.poll(TIMEOUT_SECONDS, SECONDS)).isEqualTo("a");
        assertThat(textEchoes.poll(TIMEOUT_SECONDS, SECONDS)).isEqualTo("b");
        assertThat(serverWire.dataFrames.get()).isEqualTo(2);
    }

    @Test
    void slowReaderStallsOnlyItsOwnStream() throws Exception {
        start(false, false, true);
        connectWebSocket(false);
        byte[] payload = new byte[32 * STREAM_WINDOW];
        new Random(1).nextBytes(payload);
        ChannelFuture write = webSocket.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)));
        // The parent still reads the network while the receiving stream has auto-read disabled.
        serverWire.firstHeader.get(TIMEOUT_SECONDS, SECONDS);
        getHealth();
        client.eventLoop().submit(() -> {
            Http2Connection connection = client.pipeline().get(Http2FrameCodec.class).connection();
            Http2Stream stream = connection.stream(webSocket.stream().id());
            Http2RemoteFlowController flow = connection.remote().flowController();
            assertThat(flow.windowSize(stream)).isZero();
            assertThat(flow.hasFlowControlled(stream)).isTrue();
            assertThat(write.isDone()).isFalse();
        }).get(TIMEOUT_SECONDS, SECONDS);

        Http2StreamChannel slowReader = serverStream.get(TIMEOUT_SECONDS, SECONDS);
        slowReader.eventLoop().submit(() -> slowReader.config().setAutoRead(true)).get(TIMEOUT_SECONDS, SECONDS);
        write.get(TIMEOUT_SECONDS, SECONDS);
        assertThat(binaryEcho.get(TIMEOUT_SECONDS, SECONDS)).containsExactly(payload);
        assertThat(serverWire.dataFrames.get()).isGreaterThan(1);
    }

    @ParameterizedTest
    @EnumSource(ControlFrameHandling.class)
    void resetOnlyClosesTheWebSocketStream(ControlFrameHandling controlFrameHandling) throws Exception {
        start(false, false, false);
        connectWebSocket(false, controlFrameHandling);
        Http2StreamChannel peer = serverStream.get(TIMEOUT_SECONDS, SECONDS);
        peer.parent().writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(peer.stream()))
                .get(TIMEOUT_SECONDS, SECONDS);
        webSocket.closeFuture().get(TIMEOUT_SECONDS, SECONDS);
        assertThat(clientWire.reset.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo(Http2Error.CANCEL.code());
        getHealth();
    }

    private void start(boolean tls, boolean compression, boolean pauseReads) throws Exception {
        start(tls, compression, pauseReads, ControlFrameHandling.AUTOMATIC);
    }

    private void start(boolean tls, boolean compression, boolean pauseReads, ControlFrameHandling controlFrameHandling)
            throws Exception {
        start(tls, compression, pauseReads, controlFrameHandling, null);
    }

    private void start(boolean tls, boolean compression, boolean pauseReads, ControlFrameHandling controlFrameHandling,
                       PerMessageDeflateServerExtensionHandshaker customCompression) throws Exception {
        scheme = tls ? "https" : "http";
        final SslContext serverSsl;
        final SslContext clientSsl;
        if (tls) {
            assumeTrue(SslProvider.isAlpnSupported(SslProvider.JDK));
            X509Bundle cert = new CertificateBuilder().subject("cn=localhost")
                    .setIsCertificateAuthority(true).buildSelfSigned();
            ApplicationProtocolConfig alpn = new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, ApplicationProtocolNames.HTTP_2);
            serverSsl = SslContextBuilder.forServer(cert.getKeyPair().getPrivate(), cert.getCertificatePath())
                    .sslProvider(SslProvider.JDK).applicationProtocolConfig(alpn).build();
            clientSsl = SslContextBuilder.forClient().trustManager(cert.getCertificatePath())
                    .sslProvider(SslProvider.JDK).applicationProtocolConfig(alpn).build();
        } else {
            clientSsl = null;
            serverSsl = null;
        }
        Http2Settings initial = new Http2Settings().connectProtocolEnabled(true).initialWindowSize(STREAM_WINDOW);
        Http2WebSocketHandshaker handshaker = new Http2WebSocketHandshaker(MAX_FRAME_SIZE, compression, 10000,
                controlFrameHandling);
        ChannelFuture bound = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        if (serverSsl != null) {
                            ch.pipeline().addLast(serverSsl.newHandler(ch.alloc()));
                        }
                        ch.pipeline().addLast(Http2FrameCodecBuilder.forServer().initialSettings(initial).build(),
                                serverWire, new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                                    @Override
                                    protected void initChannel(Channel stream) {
                                        stream.pipeline().addLast(
                                                new EchoServer(handshaker, pauseReads, customCompression));
                                    }
                                }));
                    }
                }).bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0));
        listener = bound.channel();
        bound.get(TIMEOUT_SECONDS, SECONDS);

        Bootstrap bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class);
        ChannelFuture connected = bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                if (clientSsl != null) {
                    int port = ((InetSocketAddress) listener.localAddress()).getPort();
                    ch.pipeline().addLast(clientSsl.newHandler(ch.alloc(), "localhost", port));
                }
                ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(),
                        new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(Channel stream) {
                                stream.close();
                            }
                        }), new SimpleChannelInboundHandler<Http2SettingsFrame>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Http2SettingsFrame msg) {
                                settings.complete(msg.settings());
                            }
                        });
            }
        }).connect(listener.localAddress());
        client = connected.channel();
        connected.get(TIMEOUT_SECONDS, SECONDS);
        assertThat(settings.get(TIMEOUT_SECONDS, SECONDS).connectProtocolEnabled()).isTrue();
    }

    private void connectWebSocket(boolean compression) throws Exception {
        connectWebSocket(compression, ControlFrameHandling.AUTOMATIC);
    }

    private void connectWebSocket(boolean compression, ControlFrameHandling controlFrameHandling) throws Exception {
        connectWebSocket(compression, controlFrameHandling, null);
    }

    private void connectWebSocket(boolean compression, ControlFrameHandling controlFrameHandling,
                                  PerMessageDeflateClientExtensionHandshaker customCompression) throws Exception {
        Http2WebSocketHandshaker handshaker = new Http2WebSocketHandshaker(MAX_FRAME_SIZE, compression, 10000,
                controlFrameHandling);
        SimpleChannelInboundHandler<WebSocketFrame> echoes = new SimpleChannelInboundHandler<WebSocketFrame>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) {
                if (msg instanceof TextWebSocketFrame || msg instanceof ContinuationWebSocketFrame) {
                    textEchoes.add(msg.content().toString(CharsetUtil.UTF_8));
                } else if (msg instanceof BinaryWebSocketFrame) {
                    binaryEcho.complete(ByteBufUtil.getBytes(msg.content()));
                } else if (msg instanceof CloseWebSocketFrame) {
                    clientCloseReason.complete(((CloseWebSocketFrame) msg).reasonText());
                } else if (msg instanceof PingWebSocketFrame) {
                    clientPing.complete(msg.content().getUnsignedByte(0));
                } else if (msg instanceof PongWebSocketFrame) {
                    clientPong.complete(msg.content().getUnsignedByte(0));
                }
            }
        };
        webSocket = new Http2StreamChannelBootstrap(client).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel stream) {
                stream.pipeline().addLast(clientWire, echoes);
            }
        }).open().get(TIMEOUT_SECONDS, SECONDS);
        Http2Headers headers = new DefaultHttp2Headers().scheme(scheme).authority("localhost").path("/chat")
                .set(SEC_WEBSOCKET_PROTOCOL, "chat, superchat").set(ORIGIN, "https://example.org");
        Future<Http2Headers> handshake = webSocket.eventLoop().submit(() -> {
            ChannelHandlerContext ctx = webSocket.pipeline().context(echoes);
            return customCompression == null ? handshaker.connect(ctx, headers) :
                    handshaker.connect(ctx, headers, customCompression);
        }).get(TIMEOUT_SECONDS, SECONDS);
        response = handshake.get(TIMEOUT_SECONDS, SECONDS);
    }

    private void getHealth() throws Exception {
        CompletableFuture<String> status = new CompletableFuture<String>();
        CompletableFuture<String> body = new CompletableFuture<String>();
        Http2StreamChannel stream = new Http2StreamChannelBootstrap(client)
                .handler(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {
                        if (msg instanceof Http2HeadersFrame) {
                            status.complete(((Http2HeadersFrame) msg).headers().status().toString());
                        } else if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream()) {
                            body.complete(((Http2DataFrame) msg).content().toString(CharsetUtil.US_ASCII));
                        }
                    }
                }).open().get(TIMEOUT_SECONDS, SECONDS);
        Http2Headers request = new DefaultHttp2Headers().method("GET").scheme(scheme)
                .authority("localhost").path("/health");
        stream.writeAndFlush(new DefaultHttp2HeadersFrame(request, true)).get(TIMEOUT_SECONDS, SECONDS);
        assertThat(status.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo("200");
        assertThat(body.get(TIMEOUT_SECONDS, SECONDS)).isEqualTo("ok");
        assertThat(stream.parent()).isSameAs(webSocket.parent());
        assertThat(stream.stream().id()).isNotEqualTo(webSocket.stream().id());
    }

    private final class EchoServer extends SimpleChannelInboundHandler<Object> {
        private final Http2WebSocketHandshaker handshaker;
        private final boolean pauseReads;
        private final PerMessageDeflateServerExtensionHandshaker customCompression;

        EchoServer(Http2WebSocketHandshaker handshaker, boolean pauseReads,
                   PerMessageDeflateServerExtensionHandshaker customCompression) {
            this.handshaker = handshaker;
            this.pauseReads = pauseReads;
            this.customCompression = customCompression;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2HeadersFrame) {
                Http2HeadersFrame request = (Http2HeadersFrame) msg;
                if (Http2WebSocketHandshaker.isWebSocketRequest(request.headers())) {
                    if (pauseReads) {
                        ctx.channel().config().setAutoRead(false);
                    }
                    serverRequest.complete(request.headers());
                    Http2Headers headers = new DefaultHttp2Headers().set(SEC_WEBSOCKET_PROTOCOL, "chat");
                    ChannelFuture accepted = customCompression == null ? handshaker.accept(ctx, request, headers) :
                            handshaker.accept(ctx, request, headers, customCompression);
                    accepted.addListener(f -> {
                        if (f.isSuccess()) {
                            serverStream.complete((Http2StreamChannel) ctx.channel());
                        } else {
                            serverStream.completeExceptionally(f.cause());
                        }
                    });
                } else if ("GET".contentEquals(request.headers().method()) &&
                        "/health".contentEquals(request.headers().path())) {
                    ctx.write(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().status("200")));
                    ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("ok", CharsetUtil.US_ASCII),
                            true));
                }
            } else if (msg instanceof CloseWebSocketFrame) {
                serverCloseCode.complete(((CloseWebSocketFrame) msg).statusCode());
            } else if (msg instanceof PingWebSocketFrame) {
                serverPing.complete(((PingWebSocketFrame) msg).content().getUnsignedByte(0));
            } else if (msg instanceof PongWebSocketFrame) {
                serverPong.complete(((PongWebSocketFrame) msg).content().getUnsignedByte(0));
            } else if (msg instanceof WebSocketFrame) {
                ctx.writeAndFlush(((WebSocketFrame) msg).retain());
            }
        }
    }

    private static final class Wire extends ChannelInboundHandlerAdapter {
        final CompletableFuture<Integer> firstHeader = new CompletableFuture<Integer>();
        final CompletableFuture<Void> endStream = new CompletableFuture<Void>();
        final CompletableFuture<Long> reset = new CompletableFuture<Long>();
        final AtomicInteger dataFrames = new AtomicInteger();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2DataFrame) {
                Http2DataFrame data = (Http2DataFrame) msg;
                dataFrames.incrementAndGet();
                ByteBuf bytes = data.content();
                if (bytes.readableBytes() >= 2) {
                    firstHeader.complete(bytes.getUnsignedShort(bytes.readerIndex()));
                }
                if (data.isEndStream()) {
                    endStream.complete(null);
                }
            } else if (msg instanceof Http2ResetFrame) {
                reset.complete(((Http2ResetFrame) msg).errorCode());
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof Http2ResetFrame) {
                reset.complete(((Http2ResetFrame) evt).errorCode());
            }
            ctx.fireUserEventTriggered(evt);
        }
    }
}
