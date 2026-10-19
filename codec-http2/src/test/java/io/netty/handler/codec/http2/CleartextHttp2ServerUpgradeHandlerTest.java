/*
 * Copyright 2017 The Netty Project
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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodecFactory;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeEvent;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler.PriorKnowledgeUpgradeEvent;
import io.netty.handler.codec.http2.Http2Stream.State;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link CleartextHttp2ServerUpgradeHandler}
 */
public class CleartextHttp2ServerUpgradeHandlerTest {
    private EmbeddedChannel channel;

    private Http2FrameListener frameListener;

    private Http2ConnectionHandler http2ConnectionHandler;

    private List<Object> userEvents;

    private void setUpServerChannel() {
        frameListener = mock(Http2FrameListener.class);

        http2ConnectionHandler = new Http2ConnectionHandlerBuilder()
                .frameListener(frameListener).build();

        UpgradeCodecFactory upgradeCodecFactory = new UpgradeCodecFactory() {
            @Override
            public UpgradeCodec newUpgradeCodec(CharSequence protocol) {
                return new Http2ServerUpgradeCodec(http2ConnectionHandler);
            }
        };

        userEvents = new ArrayList<Object>();

        HttpServerCodec httpServerCodec = new HttpServerCodec();
        HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(httpServerCodec, upgradeCodecFactory);

        CleartextHttp2ServerUpgradeHandler handler = new CleartextHttp2ServerUpgradeHandler(
                httpServerCodec, upgradeHandler, http2ConnectionHandler);
        channel = new EmbeddedChannel(handler, new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                userEvents.add(evt);
            }
        });
    }

    @AfterEach
    public void tearDown() throws Exception {
        channel.finishAndReleaseAll();
    }

    @Test
    public void priorKnowledge() throws Exception {
        setUpServerChannel();

        channel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());

        ByteBuf settingsFrame = settingsFrameBuf();

        assertFalse(channel.writeInbound(settingsFrame));

        assertEquals(1, userEvents.size());
        assertInstanceOf(PriorKnowledgeUpgradeEvent.class, userEvents.get(0));

        assertEquals(100, http2ConnectionHandler.connection().local().maxActiveStreams());
        assertEquals(65535, http2ConnectionHandler.connection().local().flowController().initialWindowSize());

        verify(frameListener).onSettingsRead(
                any(ChannelHandlerContext.class), eq(expectedSettings()));
    }

    @Test
    public void upgrade() throws Exception {
        String upgradeString = "GET / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: Upgrade, HTTP2-Settings\r\n" +
                "Upgrade: h2c\r\n" +
                "HTTP2-Settings: AAMAAABkAAQAAP__\r\n\r\n";
        validateClearTextUpgrade(upgradeString);
    }

    @Test
    public void upgradeWithMultipleConnectionHeaders() {
        String upgradeString = "GET / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: keep-alive\r\n" +
                "Connection: Upgrade, HTTP2-Settings\r\n" +
                "Upgrade: h2c\r\n" +
                "HTTP2-Settings: AAMAAABkAAQAAP__\r\n\r\n";
        validateClearTextUpgrade(upgradeString);
    }

    @Test
    public void requiredHeadersInSeparateConnectionHeaders() {
        String upgradeString = "GET / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: keep-alive\r\n" +
                "Connection: HTTP2-Settings\r\n" +
                "Connection: Upgrade\r\n" +
                "Upgrade: h2c\r\n" +
                "HTTP2-Settings: AAMAAABkAAQAAP__\r\n\r\n";
        validateClearTextUpgrade(upgradeString);
    }

    @Test
    public void priorKnowledgeInFragments() throws Exception {
        setUpServerChannel();

        ByteBuf connectionPreface = Http2CodecUtil.connectionPrefaceBuf();
        assertFalse(channel.writeInbound(connectionPreface.readBytes(5), connectionPreface));

        ByteBuf settingsFrame = settingsFrameBuf();
        assertFalse(channel.writeInbound(settingsFrame));

        assertEquals(1, userEvents.size());
        assertInstanceOf(PriorKnowledgeUpgradeEvent.class, userEvents.get(0));

        assertEquals(100, http2ConnectionHandler.connection().local().maxActiveStreams());
        assertEquals(65535, http2ConnectionHandler.connection().local().flowController().initialWindowSize());

        verify(frameListener).onSettingsRead(
                any(ChannelHandlerContext.class), eq(expectedSettings()));
    }

    @Test
    public void downgrade() throws Exception {
        setUpServerChannel();

        String requestString = "GET / HTTP/1.1\r\n" +
                         "Host: example.com\r\n\r\n";
        ByteBuf inbound = Unpooled.buffer().writeBytes(requestString.getBytes(CharsetUtil.US_ASCII));

        assertTrue(channel.writeInbound(inbound));

        Object firstInbound = channel.readInbound();
        assertInstanceOf(HttpRequest.class, firstInbound);
        HttpRequest request = (HttpRequest) firstInbound;
        assertEquals(HttpMethod.GET, request.method());
        assertEquals("/", request.uri());
        assertEquals(HttpVersion.HTTP_1_1, request.protocolVersion());
        assertEquals(new DefaultHttpHeaders().add("Host", "example.com"), request.headers());

        ((LastHttpContent) channel.readInbound()).release();

        assertNull(channel.readInbound());
    }

    @Test
    public void usedHttp2MultiplexCodec() throws Exception {
        final Http2MultiplexCodec http2Codec = new Http2MultiplexCodecBuilder(true, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
            }
        }).build();
        UpgradeCodecFactory upgradeCodecFactory = new UpgradeCodecFactory() {
            @Override
            public UpgradeCodec newUpgradeCodec(CharSequence protocol) {
                return new Http2ServerUpgradeCodec(http2Codec);
            }
        };
        http2ConnectionHandler = http2Codec;

        userEvents = new ArrayList<Object>();

        HttpServerCodec httpServerCodec = new HttpServerCodec();
        HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(httpServerCodec, upgradeCodecFactory);

        CleartextHttp2ServerUpgradeHandler handler = new CleartextHttp2ServerUpgradeHandler(
                httpServerCodec, upgradeHandler, http2Codec);
        channel = new EmbeddedChannel(handler, new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                userEvents.add(evt);
            }
        });

        assertFalse(channel.writeInbound(Http2CodecUtil.connectionPrefaceBuf()));

        ByteBuf settingsFrame = settingsFrameBuf();

        assertTrue(channel.writeInbound(settingsFrame));

        assertEquals(1, userEvents.size());
        assertInstanceOf(PriorKnowledgeUpgradeEvent.class, userEvents.get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/a\u007fb", "Upgrade-Insecure-Request://" })
    public void upgradeWithRequestTargetThatIsInvalidForHttp2(String requestTarget) throws Exception {
        final List<Throwable> childExceptions = new ArrayList<Throwable>();
        final Http2FrameCodec http2Codec = new Http2FrameCodecBuilder(true).build();
        final ChannelInitializer<Channel> streamInitializer = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        childExceptions.add(cause);
                    }
                });
            }
        };
        final Http2MultiplexHandler multiplexHandler = new Http2MultiplexHandler(streamInitializer, streamInitializer);
        UpgradeCodecFactory upgradeCodecFactory = new UpgradeCodecFactory() {
            @Override
            public UpgradeCodec newUpgradeCodec(CharSequence protocol) {
                return new Http2ServerUpgradeCodec(http2Codec, multiplexHandler);
            }
        };
        HttpServerCodec httpServerCodec = new HttpServerCodec();
        HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(httpServerCodec, upgradeCodecFactory);
        channel = new EmbeddedChannel(new CleartextHttp2ServerUpgradeHandler(
                httpServerCodec, upgradeHandler, http2Codec));

        String upgradeString = "GET " + requestTarget + " HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: Upgrade, HTTP2-Settings\r\n" +
                "Upgrade: h2c\r\n" +
                "HTTP2-Settings: AAMAAABkAAQAAP__\r\n\r\n";
        assertFalse(channel.writeInbound(Unpooled.copiedBuffer(upgradeString, CharsetUtil.ISO_8859_1)));
        channel.checkException();

        // The request can not be converted to HTTP/2 headers, so the upgraded stream must be reset.
        assertEquals(1, childExceptions.size());
        Http2Exception.StreamException streamException =
                assertInstanceOf(Http2Exception.StreamException.class, childExceptions.get(0));
        assertEquals(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID, streamException.streamId());
        assertSame(Http2Error.PROTOCOL_ERROR, streamException.error());
        assertInstanceOf(IllegalArgumentException.class, streamException.getCause());
        assertEquals(0, http2Codec.connection().numActiveStreams());

        ByteBuf responseBuffer = channel.readOutbound();
        assertTrue(responseBuffer.toString(CharsetUtil.US_ASCII).startsWith("HTTP/1.1 101 Switching Protocols"));
        responseBuffer.release();

        CompositeByteBuf frames = Unpooled.compositeBuffer();
        for (;;) {
            ByteBuf buf = channel.readOutbound();
            if (buf == null) {
                break;
            }
            frames.addComponent(true, buf);
        }
        final List<Long> rstStreams = new ArrayList<Long>();
        try {
            new DefaultHttp2FrameReader().readFrame(null, frames, new Http2FrameAdapter() {
                @Override
                public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
                    assertEquals(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID, streamId);
                    rstStreams.add(errorCode);
                }
            });
        } finally {
            frames.release();
        }
        assertEquals(1, rstStreams.size());
        assertSame(Http2Error.PROTOCOL_ERROR, Http2Error.valueOf(rstStreams.get(0)));
    }

    private static ByteBuf settingsFrameBuf() {
        ByteBuf settingsFrame = Unpooled.buffer();
        settingsFrame.writeMedium(12); // Payload length
        settingsFrame.writeByte(0x4); // Frame type
        settingsFrame.writeByte(0x0); // Flags
        settingsFrame.writeInt(0x0); // StreamId
        settingsFrame.writeShort(0x3);
        settingsFrame.writeInt(100);
        settingsFrame.writeShort(0x4);
        settingsFrame.writeInt(65535);

        return settingsFrame;
    }

    private static Http2Settings expectedSettings() {
        return new Http2Settings().maxConcurrentStreams(100).initialWindowSize(65535);
    }

    @Test
    public void upgradeAfterChannelClosed() {
        final List<Throwable> exceptions = new ArrayList<Throwable>();
        HttpServerCodec httpServerCodec = new HttpServerCodec();
        HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(httpServerCodec,
                new UpgradeCodecFactory() {
                    @Override
                    public UpgradeCodec newUpgradeCodec(CharSequence protocol) {
                        return new Http2ServerUpgradeCodec(Http2FrameCodecBuilder.forServer().build(),
                                new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        exceptions.add(cause);
                                    }
                                });
                    }
                }, 1024);
        channel = new EmbeddedChannel(new CleartextHttp2ServerUpgradeHandler(
                httpServerCodec, upgradeHandler, Http2FrameCodecBuilder.forServer().build()));

        // The first request is too large, so the server responds with 413 and closes the channel. The pipelined
        // upgrade request is still decoded and handled after the channel was closed.
        StringBuilder request = new StringBuilder("POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Upgrade: h2c\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 2000\r\n\r\n");
        for (int i = 0; i < 2000; i++) {
            request.append('x');
        }
        request.append("GET / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: Upgrade, HTTP2-Settings\r\n" +
                "Upgrade: h2c\r\n" +
                "HTTP2-Settings: AAMAAABkAAQAAP__\r\n\r\n");

        channel.writeInbound(Unpooled.copiedBuffer(request, CharsetUtil.US_ASCII));
        assertFalse(channel.isOpen());

        // The upgrade fails because the channel is closed, which must be the only exception.
        assertEquals(1, exceptions.size());
        assertInstanceOf(Http2Exception.class, exceptions.get(0));
    }

    private void validateClearTextUpgrade(String upgradeString) {
        setUpServerChannel();

        ByteBuf upgrade = Unpooled.copiedBuffer(upgradeString, CharsetUtil.US_ASCII);

        assertFalse(channel.writeInbound(upgrade));

        assertEquals(1, userEvents.size());

        Object userEvent = userEvents.get(0);
        assertInstanceOf(UpgradeEvent.class, userEvent);
        assertEquals("h2c", ((UpgradeEvent) userEvent).protocol());
        ReferenceCountUtil.release(userEvent);

        assertEquals(100, http2ConnectionHandler.connection().local().maxActiveStreams());
        assertEquals(65535, http2ConnectionHandler.connection().local().flowController().initialWindowSize());

        assertEquals(1, http2ConnectionHandler.connection().numActiveStreams());
        assertNotNull(http2ConnectionHandler.connection().stream(1));

        Http2Stream stream = http2ConnectionHandler.connection().stream(1);
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());
        assertFalse(stream.isHeadersSent());

        String expectedHttpResponse = "HTTP/1.1 101 Switching Protocols\r\n" +
                "connection: upgrade\r\n" +
                "upgrade: h2c\r\n\r\n";
        ByteBuf responseBuffer = channel.readOutbound();
        assertEquals(expectedHttpResponse, responseBuffer.toString(CharsetUtil.UTF_8));
        responseBuffer.release();

        // Check that the preface was send (a.k.a the settings frame)
        ByteBuf settingsBuffer = channel.readOutbound();
        assertNotNull(settingsBuffer);
        settingsBuffer.release();

        assertNull(channel.readOutbound());
    }
}
