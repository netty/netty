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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.websocketx.CorruptedWebSocketFrameException;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilterProvider;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS;
import static io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker.DEFAULT_MEM_LEVEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Http2WebSocketHandshakerTest {
    private static final long TIMEOUT_MILLIS = 1000;
    private final Http2WebSocketHandshaker handshaker = new Http2WebSocketHandshaker(65536, false, TIMEOUT_MILLIS);
    private EmbeddedChannel client;
    private EmbeddedChannel peer;

    @BeforeEach
    void setUp() {
        client = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(),
                new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
    }

    @AfterEach
    void tearDown() {
        try {
            if (client != null) {
                client.finishAndReleaseAll();
            }
        } finally {
            if (peer != null) {
                peer.finishAndReleaseAll();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void requiresPeerSettingBeforeOpeningTunnel(boolean omitSetting) {
        Http2Settings settings = new Http2Settings();
        if (!omitSetting) {
            settings.connectProtocolEnabled(false);
        }
        initPeer(settings);
        // Before the first SETTINGS there is no opt-in either.
        assertHandshakeUnavailable();
        exchange();
        assertHandshakeUnavailable();
        exchange();
        assertThat(peer.inboundMessages()).isEmpty();

        peer.writeAndFlush(new DefaultHttp2SettingsFrame(new Http2Settings().connectProtocolEnabled(true)));
        peer.writeAndFlush(new DefaultHttp2SettingsFrame(new Http2Settings().maxConcurrentStreams(10)));
        exchange();
        openTunnel(new StreamObserver());
        assertParentUsable();
    }

    @ParameterizedTest
    @CsvSource({ "403,false", "403,true", "101,false", "200,true" })
    void rejectsUnsuccessfulOrEndedResponse(String status, boolean endStream) {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        Future<Http2Headers> result = connect(observer);
        Http2FrameStream stream = readConnect();
        reply(stream, status, endStream);
        assertThat(result.cause()).isInstanceOf(WebSocketHandshakeException.class);
        if (!"200".equals(status)) {
            assertThat(result.cause()).hasMessageContaining(status);
        }
        assertThat(observer.ctx.channel().isOpen()).isFalse();
        assertParentUsable();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void negotiatesWithSuppliedCompressionHandshakers(boolean noContext) {
        initEnabledPeer();
        StreamObserver server = new StreamObserver();
        peer.pipeline().addLast(new Http2MultiplexHandler(server));

        AtomicInteger clientEncoded = new AtomicInteger();
        AtomicInteger serverEncoded = new AtomicInteger();
        PerMessageDeflateClientExtensionHandshaker clientCompression = new PerMessageDeflateClientExtensionHandshaker(
                1, false, 15, noContext, noContext, countingCompressor(clientEncoded), 8192);
        PerMessageDeflateServerExtensionHandshaker serverCompression = new PerMessageDeflateServerExtensionHandshaker(
                9, false, 15, true, noContext, 15, DEFAULT_MEM_LEVEL, countingCompressor(serverEncoded), 16384);

        StreamObserver clientObserver = new StreamObserver();
        newStream(clientObserver);
        Future<Http2Headers> result = handshaker.connect(clientObserver.ctx, request(), clientCompression);
        exchange();
        Http2Headers opening = server.receivedHeaders.headers();
        assertCompression(opening, noContext);
        AtomicInteger wireBytes = new AtomicInteger();
        server.ctx.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof Http2DataFrame) {
                    wireBytes.addAndGet(((Http2DataFrame) msg).content().readableBytes());
                }
                ctx.fireChannelRead(msg);
            }
        });
        ChannelFuture accepted = handshaker.accept(server.ctx, server.receivedHeaders,
                new DefaultHttp2Headers(), serverCompression);
        exchange();
        assertThat(accepted.isSuccess()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        Http2Headers response = result.getNow();
        assertCompression(response, noContext);

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            text.append("A normal message on the same WebSocket. ");
        }
        String payload = text.toString();
        clientObserver.ctx.writeAndFlush(new TextWebSocketFrame(payload)).syncUninterruptibly();
        exchange();
        assertThat(server.text).isEqualTo(payload);
        assertThat(clientEncoded.get()).isPositive();
        int firstBytes = wireBytes.getAndSet(0);
        assertThat(firstBytes).isEqualTo(encodedWireLength(opening, response, clientCompression, payload));
        PerMessageDeflateClientExtensionHandshaker levelSix = new PerMessageDeflateClientExtensionHandshaker(
                6, false, 15, noContext, noContext, 8192);
        assertThat(firstBytes).isNotEqualTo(encodedWireLength(opening, response, levelSix, payload));

        server.text = null;
        clientObserver.ctx.writeAndFlush(new TextWebSocketFrame(payload)).syncUninterruptibly();
        exchange();
        assertThat(server.text).isEqualTo(payload);
        if (noContext) {
            assertThat(wireBytes.get()).isEqualTo(firstBytes);
        } else {
            assertThat(wireBytes.get()).isLessThan(firstBytes);
        }
        server.ctx.writeAndFlush(new TextWebSocketFrame("response")).syncUninterruptibly();
        exchange();
        assertThat(clientObserver.text).isEqualTo("response");
        assertThat(serverEncoded.get()).isPositive();
    }

    private static void assertCompression(Http2Headers headers, boolean noContext) {
        String extension = headers.get(SEC_WEBSOCKET_EXTENSIONS).toString();
        assertThat(extension).startsWith("permessage-deflate").doesNotContain("client_max_window_bits");
        assertThat(extension.contains("client_no_context_takeover")).isEqualTo(noContext);
        assertThat(extension.contains("server_no_context_takeover")).isEqualTo(noContext);
    }

    private static int encodedWireLength(Http2Headers opening, Http2Headers response,
                                         PerMessageDeflateClientExtensionHandshaker compression, String payload) {
        EmbeddedChannel reference = new EmbeddedChannel(new WebSocket13FrameEncoder(true),
                Http2WebSocketHandshakeHeaders.validateResponse(opening, response, compression).newExtensionEncoder());
        try {
            reference.writeOutbound(new TextWebSocketFrame(payload));
            int length = 0;
            ByteBuf frame;
            while ((frame = reference.readOutbound()) != null) {
                length += frame.readableBytes();
                frame.release();
            }
            return length;
        } finally {
            reference.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void existingConnectOverloadKeepsDefaultOffer(boolean compress) {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        newStream(observer);
        Http2WebSocketHandshaker configured = new Http2WebSocketHandshaker(65536, compress, TIMEOUT_MILLIS);
        Future<Http2Headers> result = configured.connect(observer.ctx, request());
        exchange();
        Http2HeadersFrame opening = peer.readInbound();
        assertThat(opening.headers().contains(SEC_WEBSOCKET_EXTENSIONS)).isEqualTo(compress);
        if (compress) {
            assertThat(opening.headers().get(SEC_WEBSOCKET_EXTENSIONS)).hasToString("permessage-deflate");
        }
        reply(opening.stream(), "200", false);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void configuredClientWindowIsDeclinedLocally() {
        assumeTrue(ZlibCodecFactory.isSupportingWindowSizeAndMemLevel());
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        Http2StreamChannel stream = newStream(observer);
        PerMessageDeflateClientExtensionHandshaker compression =
                new PerMessageDeflateClientExtensionHandshaker(6, true, 15, false, false, 65536);
        Future<Http2Headers> result = handshaker.connect(observer.ctx, request(), compression);
        assertThat(result.cause()).isInstanceOf(WebSocketHandshakeException.class)
                .hasMessageContaining("client_max_window_bits");
        assertThat(stream.stream().id()).isNegative();
        exchange();
        assertThat(peer.inboundMessages()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 65537 })
    void rejectsClientCompressionExceedingTheDecompressionBufferLimit(int maxAllocation) {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        Http2StreamChannel stream = newStream(observer);
        PerMessageDeflateClientExtensionHandshaker compression =
                new PerMessageDeflateClientExtensionHandshaker(maxAllocation);

        assertThatThrownBy(() -> handshaker.connect(observer.ctx, request(), compression))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxAllocation");
        assertThat(stream.stream().id()).isNegative();
        exchange();
        assertThat(peer.inboundMessages()).isEmpty();

        Future<Http2Headers> result = handshaker.connect(observer.ctx, request(),
                new PerMessageDeflateClientExtensionHandshaker(65536));
        reply(readConnect(), "200", false);
        assertThat(result.isSuccess()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 65537 })
    void rejectsServerCompressionExceedingTheDecompressionBufferLimit(int maxAllocation) {
        initEnabledPeer();
        StreamObserver server = new StreamObserver();
        peer.pipeline().addLast(new Http2MultiplexHandler(server));
        StreamObserver clientObserver = new StreamObserver();
        newStream(clientObserver);
        Future<Http2Headers> result = handshaker.connect(clientObserver.ctx, request(),
                new PerMessageDeflateClientExtensionHandshaker(65536));
        exchange();

        PerMessageDeflateServerExtensionHandshaker compression =
                new PerMessageDeflateServerExtensionHandshaker(maxAllocation);
        assertThatThrownBy(() -> handshaker.accept(server.ctx, server.receivedHeaders,
                new DefaultHttp2Headers(), compression))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxAllocation");
        exchange();
        assertThat(result.isDone()).isFalse();

        ChannelFuture accepted = handshaker.accept(server.ctx, server.receivedHeaders,
                new DefaultHttp2Headers(), new PerMessageDeflateServerExtensionHandshaker(65536));
        exchange();
        assertThat(accepted.isSuccess()).isTrue();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNow().get(SEC_WEBSOCKET_EXTENSIONS)).hasToString("permessage-deflate");
    }

    private static WebSocketExtensionFilterProvider countingCompressor(AtomicInteger encoded) {
        return new WebSocketExtensionFilterProvider() {
            @Override
            public WebSocketExtensionFilter encoderFilter() {
                return frame -> {
                    encoded.incrementAndGet();
                    return false;
                };
            }

            @Override
            public WebSocketExtensionFilter decoderFilter() {
                return WebSocketExtensionFilter.NEVER_SKIP;
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = { "default", "typed-default", "typed-explicit" })
    void acceptsConformingPeerNoContextResponse(String configuration) {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        newStream(observer);
        Http2WebSocketHandshaker enabled = new Http2WebSocketHandshaker(4096, true, TIMEOUT_MILLIS);
        Future<Http2Headers> result;
        if ("typed-default".equals(configuration)) {
            result = enabled.connect(observer.ctx, request(), new PerMessageDeflateClientExtensionHandshaker(2048));
        } else if ("typed-explicit".equals(configuration)) {
            result = enabled.connect(observer.ctx, request(),
                    new PerMessageDeflateClientExtensionHandshaker(6, false, 15, true, false, 2048));
        } else {
            result = enabled.connect(observer.ctx, request());
        }
        exchange();
        Http2HeadersFrame opening = peer.readInbound();
        String offer = opening.headers().get(SEC_WEBSOCKET_EXTENSIONS).toString();
        assertThat(offer).startsWith("permessage-deflate");
        assertThat(offer.contains("client_no_context_takeover")).isEqualTo("typed-explicit".equals(configuration));
        Http2Headers accepted = new DefaultHttp2Headers().status("200")
                .set(SEC_WEBSOCKET_EXTENSIONS, "permessage-deflate; client_no_context_takeover");
        peer.writeAndFlush(new DefaultHttp2HeadersFrame(accepted).stream(opening.stream()));
        exchange();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void waitsForFinalResponseAndReturnsChosenProtocol() {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        newStream(observer);
        Future<Http2Headers> result = handshaker.connect(observer.ctx,
                request().add("sec-websocket-protocol", "chat, monitor"));
        Http2FrameStream stream = readConnect();
        reply(stream, "103", false);
        assertThat(result.isDone()).isFalse();

        Http2Headers accepted = new DefaultHttp2Headers().status("204").set("sec-websocket-protocol", "monitor");
        peer.writeAndFlush(new DefaultHttp2HeadersFrame(accepted).stream(stream));
        exchange();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNow()).isEqualTo(accepted);
    }

    @ParameterizedTest
    @CsvSource({ "200,0", "204,100", "299,ignored" })
    void successfulWebSocketConnectIgnoresResponseContentLength(String status, String length) {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        Future<Http2Headers> result = connect(observer);
        Http2FrameStream stream = readConnect();
        reply(stream, "103", false);
        Http2Headers response = new DefaultHttp2Headers().status(status).set(CONTENT_LENGTH, length);
        peer.writeAndFlush(new DefaultHttp2HeadersFrame(response).stream(stream));
        peer.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[] { (byte) 0x81, 1, 'a' }))
                .stream(stream));
        exchange();
        assertThat(result.isSuccess()).isTrue();
        assertThat(observer.text).isEqualTo("a");
        assertThat(result.getNow().contains(CONTENT_LENGTH)).isFalse();
        assertParentUsable();
    }

    @ParameterizedTest
    @CsvSource({ "CONNECT,200,true", "CONNECT,407,false", "GET,200,false" })
    void onlySuccessfulConnectSwitchesTheStreamToTunnelData(String method, String status, boolean tunnel) {
        initPeer(new Http2Settings());
        exchange();
        StreamObserver observer = new StreamObserver();
        Http2Headers request = "CONNECT".equals(method) ? new DefaultHttp2Headers().authority("example.com:443") :
                request();
        newStream(observer).writeAndFlush(new DefaultHttp2HeadersFrame(request.method(method), !tunnel));
        exchange();
        Http2FrameStream stream = ((Http2HeadersFrame) peer.readInbound()).stream();
        Http2Headers response = new DefaultHttp2Headers().status(status).setInt(CONTENT_LENGTH, 0);
        peer.writeAndFlush(new DefaultHttp2HeadersFrame(response).stream(stream));
        peer.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("data", CharsetUtil.US_ASCII), true)
                .stream(stream));
        exchange();
        assertThat(observer.receivedHeaders.headers().contains(CONTENT_LENGTH)).isEqualTo(!tunnel);
        if (tunnel) {
            assertThat(observer.text).isEqualTo("data");
            assertThat(observer.error).isNull();
        } else {
            assertThat(observer.text).isNull();
            assertThat(observer.error).isInstanceOf(Http2Exception.StreamException.class);
            assertThat(((Http2Exception) observer.error).error()).isEqualTo(Http2Error.PROTOCOL_ERROR);
        }
        assertParentUsable();
    }

    @ParameterizedTest
    @CsvSource({ "200,true", "407,false", "103,false" })
    void requestContentLengthStopsApplyingOnlyAfterConnectSucceeds(String status, boolean tunnel) {
        initPeer(new Http2Settings());
        exchange();
        StreamObserver received = new StreamObserver();
        peer.pipeline().addLast(new Http2MultiplexHandler(received));
        Http2StreamChannel sender = newStream(new StreamObserver());
        Http2Headers request = new DefaultHttp2Headers().method("CONNECT").authority("example.com:443")
                .setInt(CONTENT_LENGTH, 0);
        sender.writeAndFlush(new DefaultHttp2HeadersFrame(request));
        exchange();
        assertThat(received.receivedHeaders.headers().get(CONTENT_LENGTH)).hasToString("0");
        received.ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().status(status)));
        exchange();
        sender.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("data", CharsetUtil.US_ASCII), true));
        exchange();
        if (tunnel) {
            assertThat(received.text).isEqualTo("data");
            assertThat(received.error).isNull();
        } else {
            assertThat(received.text).isNull();
            assertThat(received.error).isInstanceOf(Http2Exception.StreamException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void deferredAcceptObservesPeerEndStream(boolean authorizeDuringRead) {
        initEnabledPeer();
        Promise<Void> authorized = peer.eventLoop().newPromise();
        Promise<ChannelFuture> accepted = peer.eventLoop().newPromise();
        peer.pipeline().addLast(new Http2MultiplexHandler(new SimpleChannelInboundHandler<Http2StreamFrame>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {
                if (msg instanceof Http2HeadersFrame) {
                    Http2HeadersFrame opening = (Http2HeadersFrame) msg;
                    authorized.addListener(f -> accepted.setSuccess(
                            handshaker.accept(ctx, opening, new DefaultHttp2Headers())));
                } else if (authorizeDuringRead && msg instanceof Http2DataFrame &&
                        ((Http2DataFrame) msg).isEndStream()) {
                    authorized.setSuccess(null);
                }
            }
        }));
        StreamObserver observer = new StreamObserver();
        Http2StreamChannel stream = newStream(observer);
        stream.writeAndFlush(new DefaultHttp2HeadersFrame(Http2WebSocketHandshakeHeaders.newRequest(request(), null)));
        exchange();
        stream.writeAndFlush(new DefaultHttp2DataFrame(true));
        exchange();
        if (!authorizeDuringRead) {
            authorized.setSuccess(null);
            exchange();
        }
        assertThat(accepted.getNow().cause()).isInstanceOf(WebSocketHandshakeException.class);
        assertThat(observer.receivedHeaders.headers().status()).hasToString("400");
        assertThat(observer.receivedHeaders.isEndStream()).isTrue();
        assertThat(stream.isOpen()).isFalse();
        assertThat(peer.isActive()).isTrue();
    }

    @Test
    void unknownFrameBeforeResponseDoesNotConsumeTheOnlyManualRead() {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        newManuallyReadStream(observer);
        Future<Http2Headers> result = handshaker.connect(observer.ctx, request());
        Http2FrameStream stream = readConnect();
        peer.writeAndFlush(new DefaultHttp2UnknownFrame((byte) 99, new Http2Flags(), Unpooled.buffer(1).writeByte(1))
                .stream(stream));
        exchange();
        assertThat(result.isDone()).isFalse();
        reply(stream, "200", false);
        assertThat(result.isSuccess()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "true,12,false", "true,13,true", "false,13,false" })
    void invalidOpeningRequestGetsFinal400(boolean advertise, String version, boolean endStream) {
        initPeer(new Http2Settings().connectProtocolEnabled(advertise));
        AcceptingServer server = new AcceptingServer(handshaker);
        peer.pipeline().addLast(new Http2MultiplexHandler(server));
        exchange();
        StreamObserver observer = new StreamObserver();
        Http2StreamChannel child = newStream(observer);
        Http2Headers invalid = request().method("CONNECT").set(Http2Headers.PseudoHeaderName.PROTOCOL.value(),
                "websocket").set("sec-websocket-version", version);
        child.writeAndFlush(new DefaultHttp2HeadersFrame(invalid, endStream)).syncUninterruptibly();
        exchange();
        assertThat(server.accepted.cause()).isInstanceOf(WebSocketHandshakeException.class);
        assertThat(observer.receivedHeaders.headers().status()).hasToString("400");
        assertThat(observer.receivedHeaders.isEndStream()).isTrue();
        if (!"13".equals(version)) {
            assertThat(observer.receivedHeaders.headers().get("sec-websocket-version")).hasToString("13");
        }
        if (!endStream) {
            child.writeAndFlush(new DefaultHttp2DataFrame(true));
            exchange();
        }
        assertThat(child.isOpen()).isFalse();

        StreamObserver health = new StreamObserver();
        newStream(health).writeAndFlush(new DefaultHttp2HeadersFrame(request().method("GET"), true));
        exchange();
        assertThat(health.receivedHeaders.headers().status()).hasToString("200");
    }

    @Test
    void cannotAcceptAnotherStreamsRequest() {
        initEnabledPeer();
        List<StreamObserver> serverStreams = new ArrayList<>();
        peer.pipeline().addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
            @Override
            protected void initChannel(Http2StreamChannel ch) {
                StreamObserver observer = new StreamObserver();
                serverStreams.add(observer);
                ch.pipeline().addLast(observer);
            }
        }));
        Future<Http2Headers> first = connect(new StreamObserver());
        Future<Http2Headers> second = connect(new StreamObserver());
        exchange();
        assertThat(serverStreams).hasSize(2);
        StreamObserver a = serverStreams.get(0);
        StreamObserver b = serverStreams.get(1);
        ChannelFuture wrongStream = handshaker.accept(b.ctx, a.receivedHeaders, new DefaultHttp2Headers());
        assertThat(wrongStream.cause()).isNotNull();
        exchange();
        assertThat(second.isDone()).isFalse();

        handshaker.accept(a.ctx, a.receivedHeaders, new DefaultHttp2Headers()).syncUninterruptibly();
        handshaker.accept(b.ctx, b.receivedHeaders, new DefaultHttp2Headers()).syncUninterruptibly();
        exchange();
        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isTrue();
    }

    @Test
    void cancelledHandshakeResetsOnlyThatStream() {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        Future<Http2Headers> result = connect(observer);
        Http2FrameStream stream = readConnect();
        assertThat(result.cancel(false)).isTrue();
        exchange();
        assertCancel(stream);
        assertThat(observer.ctx.channel().isOpen()).isFalse();
        assertParentUsable();
    }

    @Test
    void handshakeDeadlineResetsOnlyThatStream() {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        Future<Http2Headers> result = connect(observer);
        Http2FrameStream stream = readConnect();
        expireDeadline();
        assertThat(result.isDone()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertCancel(stream);
        assertThat(observer.ctx.channel().isOpen()).isFalse();
        assertParentUsable();
    }

    @Test
    void resetWhileWaitingForResponseCompletesHandshake() {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        Future<Http2Headers> result = connect(observer);
        Http2FrameStream stream = readConnect();
        peer.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream));
        transfer(peer, client);
        client.runPendingTasks();
        // RFC 9113 section 5.4.2 prohibits answering a reset with another reset.
        assertThat(client.outboundMessages()).isEmpty();
        assertThat(result.isDone()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(observer.ctx.channel().isOpen()).isFalse();
        assertParentUsable();
    }

    @Test
    void endStreamWithoutWebSocketCloseReportsAbnormalClosure() {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        Http2FrameStream stream = openTunnel(observer);
        peer.writeAndFlush(new DefaultHttp2DataFrame(true).stream(stream));
        exchange();
        assertThat(observer.error).isInstanceOf(CorruptedWebSocketFrameException.class);
        assertThat(((CorruptedWebSocketFrameException) observer.error).closeStatus())
                .isEqualTo(WebSocketCloseStatus.ABNORMAL_CLOSURE);
        assertThat(observer.ctx.channel().isOpen()).isFalse();
        assertParentUsable();
    }

    @Test
    void abnormalEndStreamReleasesWritesBlockedByThePeerWindow() {
        initPeer(new Http2Settings().connectProtocolEnabled(true).initialWindowSize(0));
        exchange();
        StreamObserver observer = new StreamObserver();
        Http2FrameStream stream = openTunnel(observer);
        ChannelFuture write = observer.ctx.writeAndFlush(new TextWebSocketFrame("in flight"));
        exchange();
        assertThat(write.isDone()).isFalse();
        peer.writeAndFlush(new DefaultHttp2DataFrame(true).stream(stream));
        exchange();
        assertThat(observer.error).isInstanceOf(CorruptedWebSocketFrameException.class);
        expireDeadline();
        assertThat(observer.ctx.channel().isOpen()).isFalse();
        assertThat(write.isDone()).isTrue();
        assertThat(write.isSuccess()).isFalse();
        assertParentUsable();
    }

    @Test
    void resetEstablishedTunnelLeavesConnectionOpen() {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        Http2FrameStream stream = openTunnel(observer);
        peer.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream));
        exchange();
        assertThat(observer.ctx.channel().isOpen()).isFalse();
        assertParentUsable();
    }

    @Test
    void stalledCloseHandshakeIsBoundedEvenWhenDataCannotBeWritten() {
        initPeer(new Http2Settings().connectProtocolEnabled(true).initialWindowSize(0));
        exchange();
        StreamObserver observer = new StreamObserver();
        Http2FrameStream stream = openTunnel(observer);
        ChannelFuture closed = observer.ctx.channel().close();
        exchange();
        assertThat(closed.isDone()).isFalse();
        assertThat(peer.inboundMessages()).isEmpty();
        expireDeadline();
        assertThat(closed.isDone()).isTrue();
        assertThat(observer.ctx.channel().isOpen()).isFalse();
        assertCancel(stream);
        assertParentUsable();
    }

    @Test
    void closeDrainsInFlightDataAndFinWhenAutoReadIsDisabled() {
        initEnabledPeer();
        StreamObserver observer = new StreamObserver();
        Http2StreamChannel child = newManuallyReadStream(observer);
        Future<Http2Headers> accepted = handshaker.connect(observer.ctx, request());
        Http2FrameStream stream = readConnect();
        reply(stream, "200", false);
        assertThat(accepted.isSuccess()).isTrue();

        ChannelFuture closed = child.close();
        exchange();
        assertThat(closed.isDone()).isFalse();
        peer.releaseInbound();
        // An ordinary message was already in flight while our closing frame was on the wire.
        peer.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[] { (byte) 0x81, 1, 'x' }))
                .stream(stream));
        exchange();
        peer.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[] { (byte) 0x88, 0 }))
                .stream(stream));
        exchange();
        peer.writeAndFlush(new DefaultHttp2DataFrame(true).stream(stream));
        exchange();

        assertThat(closed.isSuccess()).isTrue();
        assertThat(observer.error).isNull();
        assertParentUsable();
    }

    private void initEnabledPeer() {
        initPeer(new Http2Settings().connectProtocolEnabled(true));
        exchange();
    }

    private void initPeer(Http2Settings settings) {
        client.freezeTime();
        peer = new EmbeddedChannel(Http2FrameCodecBuilder.forServer().initialSettings(settings).build(),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof Http2StreamFrame) {
                            ctx.fireChannelRead(msg);
                        } else {
                            ReferenceCountUtil.release(msg);
                        }
                    }
                });
    }

    private static Http2Headers request() {
        return new DefaultHttp2Headers().scheme("https").authority("example.com").path("/chat");
    }

    private void assertHandshakeUnavailable() {
        StreamObserver observer = new StreamObserver();
        Future<Http2Headers> failed = connect(observer);
        assertThat(failed.cause()).isInstanceOf(WebSocketHandshakeException.class);
        assertThat(((Http2StreamChannel) observer.ctx.channel()).stream().id()).isNegative();
    }

    private Http2StreamChannel newStream(StreamObserver observer) {
        return new Http2StreamChannelBootstrap(client).handler(observer).open().syncUninterruptibly().getNow();
    }

    private Http2StreamChannel newManuallyReadStream(StreamObserver observer) {
        return new Http2StreamChannelBootstrap(client).option(ChannelOption.AUTO_READ, false)
                .handler(observer).open().syncUninterruptibly().getNow();
    }

    private Future<Http2Headers> connect(StreamObserver observer) {
        newStream(observer);
        return handshaker.connect(observer.ctx, request());
    }

    private Http2FrameStream readConnect() {
        exchange();
        Http2HeadersFrame frame = peer.readInbound();
        assertThat(frame).isNotNull();
        assertThat(Http2WebSocketHandshaker.isWebSocketRequest(frame.headers())).isTrue();
        assertThat(frame.isEndStream()).isFalse();
        return frame.stream();
    }

    private Http2FrameStream openTunnel(StreamObserver observer) {
        Future<Http2Headers> result = connect(observer);
        Http2FrameStream stream = readConnect();
        reply(stream, "200", false);
        assertThat(result.isSuccess()).isTrue();
        return stream;
    }

    private void reply(Http2FrameStream stream, String status, boolean endStream) {
        Http2Headers headers = new DefaultHttp2Headers().status(status);
        peer.writeAndFlush(new DefaultHttp2HeadersFrame(headers, endStream).stream(stream)).syncUninterruptibly();
        exchange();
    }

    private void assertCancel(Http2FrameStream stream) {
        Http2ResetFrame rst = peer.readInbound();
        assertThat(rst).isNotNull();
        assertThat(rst.stream()).isSameAs(stream);
        assertThat(rst.errorCode()).isEqualTo(Http2Error.CANCEL.code());
    }

    private void assertParentUsable() {
        assertThat(client.isActive()).isTrue();
        // Discard any terminal frames from the previous stream before checking the next one.
        peer.releaseInbound();
        StreamObserver observer = new StreamObserver();
        Http2StreamChannel stream = newStream(observer);
        stream.writeAndFlush(new DefaultHttp2HeadersFrame(request().method("GET").path("/health"), true))
                .syncUninterruptibly();
        exchange();
        Http2HeadersFrame get = peer.readInbound();
        assertThat(get).isNotNull();
        assertThat(get.stream().id()).isEqualTo(stream.stream().id());
        assertThat(get.headers().method().toString()).isEqualTo("GET");
        Http2Headers response = new DefaultHttp2Headers().status("200").setInt(CONTENT_LENGTH, 0);
        peer.writeAndFlush(new DefaultHttp2HeadersFrame(response, true).stream(get.stream()));
        exchange();
        assertThat(observer.receivedHeaders.headers().status()).hasToString("200");
        assertThat(observer.receivedHeaders.headers().get(CONTENT_LENGTH)).hasToString("0");
    }

    private void expireDeadline() {
        client.advanceTimeBy(TIMEOUT_MILLIS + 1, TimeUnit.MILLISECONDS);
        exchange();
    }

    private void exchange() {
        do {
            client.runPendingTasks();
            peer.runPendingTasks();
            transfer(client, peer);
            transfer(peer, client);
        } while (client.hasPendingTasks() || peer.hasPendingTasks() || !client.outboundMessages().isEmpty() ||
                !peer.outboundMessages().isEmpty());
    }

    private static void transfer(EmbeddedChannel from, EmbeddedChannel to) {
        ByteBuf bytes;
        while ((bytes = from.readOutbound()) != null) {
            to.writeInbound(bytes);
        }
    }

    private static final class StreamObserver extends SimpleChannelInboundHandler<Object> {
        ChannelHandlerContext ctx;
        Http2HeadersFrame receivedHeaders;
        Throwable error;
        String text;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2HeadersFrame) {
                receivedHeaders = (Http2HeadersFrame) msg;
            } else if (msg instanceof TextWebSocketFrame) {
                text = ((TextWebSocketFrame) msg).text();
            } else if (msg instanceof Http2DataFrame) {
                text = ((Http2DataFrame) msg).content().toString(CharsetUtil.US_ASCII);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            error = cause;
        }
    }

    @ChannelHandler.Sharable
    private static final class AcceptingServer extends SimpleChannelInboundHandler<Http2HeadersFrame> {
        private final Http2WebSocketHandshaker handshaker;
        private ChannelFuture accepted;

        AcceptingServer(Http2WebSocketHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2HeadersFrame msg) {
            if (Http2WebSocketHandshaker.isWebSocketRequest(msg.headers())) {
                accepted = handshaker.accept(ctx, msg, new DefaultHttp2Headers());
            } else {
                ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().status("200"), true));
            }
        }
    }
}
