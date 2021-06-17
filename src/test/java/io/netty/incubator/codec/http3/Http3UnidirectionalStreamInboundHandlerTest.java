/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.function.LongFunction;

import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CONTROL_STREAM_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_PUSH_STREAM_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_QPACK_DECODER_STREAM_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_QPACK_ENCODER_STREAM_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class Http3UnidirectionalStreamInboundHandlerTest {

    private final boolean server;
    private EmbeddedQuicChannel parent;
    private Http3ControlStreamOutboundHandler remoteControlStreamHandler;
    private Http3ControlStreamInboundHandler localControlStreamHandler;
    private QpackEncoder qpackEncoder;
    private QpackDecoder qpackDecoder;

    @Parameterized.Parameters(name = "{index}: server = {0}")
    public static Object[] parameters() {
        return new Object[] { false, true };
    }

    public Http3UnidirectionalStreamInboundHandlerTest(boolean server) {
        this.server = server;
    }

    @Before
    public void setUp() {
        parent = new EmbeddedQuicChannel(server);
        qpackEncoder = new QpackEncoder();
        qpackDecoder = new QpackDecoder(new DefaultHttp3SettingsFrame());
        localControlStreamHandler = new Http3ControlStreamInboundHandler(server, null, qpackEncoder,
                remoteControlStreamHandler);
        remoteControlStreamHandler = new Http3ControlStreamOutboundHandler(server, new DefaultHttp3SettingsFrame(),
                new CodecHandler());
    }

    @After
    public void tearDown() {
        assertFalse(parent.finish());
    }

    @Test
    public void testUnkownStream() throws Exception {
        EmbeddedChannel channel = newChannel();
        ByteBuf buffer = Unpooled.buffer(8);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 0x06);
        assertFalse(channel.writeInbound(buffer));
        assertEquals(0, buffer.refCnt());
        assertNull(channel.pipeline().context(Http3UnidirectionalStreamInboundHandler.class));
        assertTrue(channel.isActive());

        // Write some buffer to the stream. This should be just released.
        ByteBuf someBuffer = Unpooled.buffer();
        assertFalse(channel.writeInbound(someBuffer));
        assertEquals(0, someBuffer.refCnt());
        assertFalse(channel.finish());
    }

    @Test
    public void testUnknownStreamWithCustomHandler() throws Exception {
        long streamType = 0x06;
        EmbeddedChannel channel = newChannel(v -> {
            assertEquals(streamType, v);
            // Add an handler that will just forward the received bytes.
            return new ChannelInboundHandlerAdapter();
        });
        ByteBuf buffer = Unpooled.buffer(8);
        Http3CodecUtils.writeVariableLengthInteger(buffer, streamType);
        assertFalse(channel.writeInbound(buffer));
        assertEquals(0, buffer.refCnt());
        assertNull(channel.pipeline().context(Http3UnidirectionalStreamInboundHandler.class));
        assertTrue(channel.isActive());

        // Write some buffer to the stream. This should be just released.
        ByteBuf someBuffer = Unpooled.buffer().writeLong(9);
        assertTrue(channel.writeInbound(someBuffer.retainedDuplicate()));
        assertTrue(channel.finish());

        Http3TestUtils.assertBufferEquals(someBuffer, channel.readInbound());
        assertNull(channel.readInbound());
    }

    @Test
    public void testPushStream() throws Exception {
        EmbeddedChannel channel = newChannel();
        ByteBuf buffer = Unpooled.buffer(8);
        Http3CodecUtils.writeVariableLengthInteger(buffer, HTTP3_PUSH_STREAM_TYPE);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 2);
        assertFalse(channel.writeInbound(buffer));
        assertEquals(0, buffer.refCnt());
        if (server) {
            Http3TestUtils.verifyClose(Http3ErrorCode.H3_STREAM_CREATION_ERROR, (EmbeddedQuicChannel) channel.parent());
        } else {
            ByteBuf b = Unpooled.buffer();
            assertFalse(channel.writeInbound(b));
            assertEquals(0, b.refCnt());
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testPushStreamNoMaxPushIdFrameSent() throws Exception {
        testPushStream(-1);
    }

    @Test
    public void testPushStreamMaxPushIdFrameSentWithSmallerId() throws Exception {
        testPushStream(0);
    }

    @Test
    public void testPushStreamMaxPushIdFrameSentWithSameId() throws Exception {
        testPushStream(2);
    }

    private void testPushStream(long maxPushId) throws Exception {
        assertFalse(parent.finish());
        parent = new EmbeddedQuicChannel(server, server ?
                new Http3ServerConnectionHandler(new ChannelInboundHandlerAdapter()) :
                new Http3ClientConnectionHandler());
        final EmbeddedQuicStreamChannel localControlStream =
                (EmbeddedQuicStreamChannel) Http3.getLocalControlStream(parent);
        assertNotNull(localControlStream);
        assertTrue(localControlStream.releaseOutbound());
        EmbeddedQuicStreamChannel outboundControlChannel =
                (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.BIDIRECTIONAL,
                        remoteControlStreamHandler).get();

        // Let's drain everything that was written while channelActive(...) was called.
        for (;;) {
            Object written = outboundControlChannel.readOutbound();
            if (written == null) {
                break;
            }
            ReferenceCountUtil.release(written);
        }

        if (maxPushId >= 0) {
            assertTrue(outboundControlChannel.writeOutbound(new DefaultHttp3MaxPushIdFrame(maxPushId)));
            Object push = outboundControlChannel.readOutbound();
            ReferenceCountUtil.release(push);
        }

        Http3UnidirectionalStreamInboundHandler handler = newUniStreamInboundHandler(null);
        EmbeddedQuicStreamChannel channel =
                (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.UNIDIRECTIONAL, handler).get();

        ByteBuf buffer = Unpooled.buffer(8);
        Http3CodecUtils.writeVariableLengthInteger(buffer, HTTP3_PUSH_STREAM_TYPE);
        final int pushId = 2;
        Http3CodecUtils.writeVariableLengthInteger(buffer, pushId);

        assertFalse(channel.writeInbound(buffer));
        assertEquals(0, buffer.refCnt());
        if (server) {
            Http3TestUtils.verifyClose(Http3ErrorCode.H3_STREAM_CREATION_ERROR, parent);
        } else {
            if (pushId > maxPushId) {
                Http3TestUtils.verifyClose(Http3ErrorCode.H3_ID_ERROR, parent);
            } else {
                assertTrue(parent.isActive());
                assertNotNull(channel.pipeline().context(CodecHandler.class));
            }
        }
        assertFalse(channel.finish());
        assertFalse(outboundControlChannel.finish());
    }

    @Test
    public void testControlStream() throws Exception {
        testStreamSetup(HTTP3_CONTROL_STREAM_TYPE, Http3ControlStreamInboundHandler.class, true);
    }

    @Test
    public void testQpackEncoderStream() throws Exception {
        testStreamSetup(HTTP3_QPACK_ENCODER_STREAM_TYPE, QpackEncoderHandler.class, false);
    }

    @Test
    public void testQpackDecoderStream() throws Exception {
        testStreamSetup(HTTP3_QPACK_DECODER_STREAM_TYPE, QpackDecoderHandler.class, false);
    }

    private void testStreamSetup(long type, Class<? extends ChannelHandler> clazz, boolean hasCodec) throws Exception {
        EmbeddedChannel channel = newChannel();
        ByteBuf buffer = Unpooled.buffer(8);
        Http3CodecUtils.writeVariableLengthInteger(buffer, type);
        assertFalse(channel.writeInbound(buffer));
        assertEquals(0, buffer.refCnt());
        assertNull(channel.pipeline().context(Http3UnidirectionalStreamInboundHandler.class));
        assertNotNull(channel.pipeline().context(clazz));
        if (hasCodec) {
            assertNotNull(channel.pipeline().context(CodecHandler.class));
        } else {
            assertNull(channel.pipeline().context(CodecHandler.class));
        }
        assertFalse(channel.finish());

        channel = new EmbeddedChannel(channel.parent(), DefaultChannelId.newInstance(),
                true, false, newUniStreamInboundHandler(null));

        // Try to create the stream a second time, this should fail
        buffer = Unpooled.buffer(8);
        Http3CodecUtils.writeVariableLengthInteger(buffer, type);
        assertFalse(channel.writeInbound(buffer));
        assertEquals(0, buffer.refCnt());
        Http3TestUtils.verifyClose(Http3ErrorCode.H3_STREAM_CREATION_ERROR, (EmbeddedQuicChannel) channel.parent());
        assertFalse(channel.finish());
    }

    private EmbeddedChannel newChannel() throws Exception {
        return newChannel(null);
    }

    private EmbeddedChannel newChannel(LongFunction<ChannelHandler> unknownStreamHandlerFactory) throws Exception {
        Http3UnidirectionalStreamInboundHandler handler = newUniStreamInboundHandler(unknownStreamHandlerFactory);
        return (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.BIDIRECTIONAL, handler).get();
    }

    private Http3UnidirectionalStreamInboundHandler newUniStreamInboundHandler(
            LongFunction<ChannelHandler> unknownStreamHandlerFactory) {
        return server ?
                new Http3UnidirectionalStreamInboundServerHandler((v, __, ___) -> new CodecHandler(),
                        localControlStreamHandler, remoteControlStreamHandler, unknownStreamHandlerFactory,
                        () -> new QpackEncoderHandler((long) Integer.MAX_VALUE, qpackDecoder),
                        () -> new QpackDecoderHandler(qpackEncoder)) :
                new Http3UnidirectionalStreamInboundClientHandler((v, __, ___) -> new CodecHandler(),
                        localControlStreamHandler, remoteControlStreamHandler,
                        unknownStreamHandlerFactory,
                        pushId -> new Http3PushStreamClientInitializer() {
                            @Override
                            protected void initPushStream(QuicStreamChannel ch) {
                                ch.pipeline().addLast(new CodecHandler());
                            }
                        },
                        () -> new QpackEncoderHandler((long) Integer.MAX_VALUE,
                        qpackDecoder), () -> new QpackDecoderHandler(qpackEncoder));
    }

    private static final class CodecHandler extends ChannelHandlerAdapter {  }
}
