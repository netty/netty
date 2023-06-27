/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpdySessionHandlerTest {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SpdySessionHandlerTest.class);

    private static final int closeSignal = SpdyCodecUtil.SPDY_SETTINGS_MAX_ID;
    private static final SpdySettingsFrame closeMessage = new DefaultSpdySettingsFrame();

    static {
        closeMessage.setValue(closeSignal, 0);
    }

    private static void assertDataFrame(Object msg, int streamId, boolean last) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyDataFrame);
        SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
        assertEquals(streamId, spdyDataFrame.streamId());
        assertEquals(last, spdyDataFrame.isLast());
    }

    private static void assertSynReply(Object msg, int streamId, boolean last, SpdyHeaders headers) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdySynReplyFrame);
        assertHeaders(msg, streamId, last, headers);
    }

    private static void assertRstStream(Object msg, int streamId, SpdyStreamStatus status) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyRstStreamFrame);
        SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
        assertEquals(streamId, spdyRstStreamFrame.streamId());
        assertEquals(status, spdyRstStreamFrame.status());
    }

    private static void assertPing(Object msg, int id) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyPingFrame);
        SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;
        assertEquals(id, spdyPingFrame.id());
    }

    private static void assertGoAway(Object msg, int lastGoodStreamId) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyGoAwayFrame);
        SpdyGoAwayFrame spdyGoAwayFrame = (SpdyGoAwayFrame) msg;
        assertEquals(lastGoodStreamId, spdyGoAwayFrame.lastGoodStreamId());
    }

    private static void assertHeaders(Object msg, int streamId, boolean last, SpdyHeaders headers) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyHeadersFrame);
        SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
        assertEquals(streamId, spdyHeadersFrame.streamId());
        assertEquals(last, spdyHeadersFrame.isLast());
        for (CharSequence name: headers.names()) {
            List<CharSequence> expectedValues = headers.getAll(name);
            List<CharSequence> receivedValues = spdyHeadersFrame.headers().getAll(name);
            assertTrue(receivedValues.containsAll(expectedValues));
            receivedValues.removeAll(expectedValues);
            assertTrue(receivedValues.isEmpty());
            spdyHeadersFrame.headers().remove(name);
        }
        assertTrue(spdyHeadersFrame.headers().isEmpty());
    }

    private static void testSpdySessionHandler(SpdyVersion version, boolean server) {
        EmbeddedChannel sessionHandler = new EmbeddedChannel(
                new SpdySessionHandler(version, server), new EchoHandler(closeSignal, server));

        while (sessionHandler.readOutbound() != null) {
            continue;
        }

        int localStreamId = server ? 1 : 2;
        int remoteStreamId = server ? 2 : 1;

        SpdySynStreamFrame spdySynStreamFrame =
                new DefaultSpdySynStreamFrame(localStreamId, 0, (byte) 0);
        spdySynStreamFrame.headers().set("compression", "test");

        SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(localStreamId);
        spdyDataFrame.setLast(true);

        // Check if session handler returns INVALID_STREAM if it receives
        // a data frame for a Stream-ID that is not open
        sessionHandler.writeInbound(new DefaultSpdyDataFrame(localStreamId));
        assertRstStream(sessionHandler.readOutbound(), localStreamId, SpdyStreamStatus.INVALID_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // a data frame for a Stream-ID before receiving a SYN_REPLY frame
        sessionHandler.writeInbound(new DefaultSpdyDataFrame(remoteStreamId));
        assertRstStream(sessionHandler.readOutbound(), remoteStreamId, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());
        remoteStreamId += 2;

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // multiple SYN_REPLY frames for the same active Stream-ID
        sessionHandler.writeInbound(new DefaultSpdySynReplyFrame(remoteStreamId));
        assertNull(sessionHandler.readOutbound());
        sessionHandler.writeInbound(new DefaultSpdySynReplyFrame(remoteStreamId));
        assertRstStream(sessionHandler.readOutbound(), remoteStreamId, SpdyStreamStatus.STREAM_IN_USE);
        assertNull(sessionHandler.readOutbound());
        remoteStreamId += 2;

        // Check if frame codec correctly compresses/uncompresses headers
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertSynReply(sessionHandler.readOutbound(), localStreamId, false, spdySynStreamFrame.headers());
        assertNull(sessionHandler.readOutbound());
        SpdyHeadersFrame spdyHeadersFrame = new DefaultSpdyHeadersFrame(localStreamId);

        spdyHeadersFrame.headers().add("header", "test1");
        spdyHeadersFrame.headers().add("header", "test2");

        sessionHandler.writeInbound(spdyHeadersFrame);
        assertHeaders(sessionHandler.readOutbound(), localStreamId, false, spdyHeadersFrame.headers());
        assertNull(sessionHandler.readOutbound());
        localStreamId += 2;

        // Check if session handler closed the streams using the number
        // of concurrent streams and that it returns REFUSED_STREAM
        // if it receives a SYN_STREAM frame it does not wish to accept
        spdySynStreamFrame.setStreamId(localStreamId);
        spdySynStreamFrame.setLast(true);
        spdySynStreamFrame.setUnidirectional(true);

        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamId, SpdyStreamStatus.REFUSED_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler rejects HEADERS for closed streams
        int testStreamId = spdyDataFrame.streamId();
        sessionHandler.writeInbound(spdyDataFrame);
        assertDataFrame(sessionHandler.readOutbound(), testStreamId, spdyDataFrame.isLast());
        assertNull(sessionHandler.readOutbound());
        spdyHeadersFrame.setStreamId(testStreamId);

        sessionHandler.writeInbound(spdyHeadersFrame);
        assertRstStream(sessionHandler.readOutbound(), testStreamId, SpdyStreamStatus.INVALID_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler drops active streams if it receives
        // a RST_STREAM frame for that Stream-ID
        sessionHandler.writeInbound(new DefaultSpdyRstStreamFrame(remoteStreamId, 3));
        assertNull(sessionHandler.readOutbound());
        //remoteStreamId += 2;

        // Check if session handler honors UNIDIRECTIONAL streams
        spdySynStreamFrame.setLast(false);
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertNull(sessionHandler.readOutbound());
        spdySynStreamFrame.setUnidirectional(false);

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // multiple SYN_STREAM frames for the same active Stream-ID
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamId, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());
        localStreamId += 2;

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // a SYN_STREAM frame with an invalid Stream-ID
        spdySynStreamFrame.setStreamId(localStreamId - 1);
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamId - 1, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());
        spdySynStreamFrame.setStreamId(localStreamId);

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // an invalid HEADERS frame
        spdyHeadersFrame.setStreamId(localStreamId);

        spdyHeadersFrame.setInvalid();
        sessionHandler.writeInbound(spdyHeadersFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamId, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());

        sessionHandler.finish();
    }

    private static void testSpdySessionHandlerPing(SpdyVersion version, boolean server) {
        EmbeddedChannel sessionHandler = new EmbeddedChannel(
                new SpdySessionHandler(version, server), new EchoHandler(closeSignal, server));

        while (sessionHandler.readOutbound() != null) {
            continue;
        }

        int localStreamId = server ? 1 : 2;
        int remoteStreamId = server ? 2 : 1;

        SpdyPingFrame localPingFrame = new DefaultSpdyPingFrame(localStreamId);
        SpdyPingFrame remotePingFrame = new DefaultSpdyPingFrame(remoteStreamId);

        // Check if session handler returns identical local PINGs
        sessionHandler.writeInbound(localPingFrame);
        assertPing(sessionHandler.readOutbound(), localPingFrame.id());
        assertNull(sessionHandler.readOutbound());

        // Check if session handler ignores un-initiated remote PINGs
        sessionHandler.writeInbound(remotePingFrame);
        assertNull(sessionHandler.readOutbound());

        sessionHandler.finish();
    }

    private static void testSpdySessionHandlerGoAway(SpdyVersion version, boolean server) {
        EmbeddedChannel sessionHandler = new EmbeddedChannel(
                new SpdySessionHandler(version, server), new EchoHandler(closeSignal, server));

        while (sessionHandler.readOutbound() != null) {
            continue;
        }

        int localStreamId = server ? 1 : 2;

        SpdySynStreamFrame spdySynStreamFrame =
                new DefaultSpdySynStreamFrame(localStreamId, 0, (byte) 0);
        spdySynStreamFrame.headers().set("compression", "test");

        SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(localStreamId);
        spdyDataFrame.setLast(true);

        // Send an initial request
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertSynReply(sessionHandler.readOutbound(), localStreamId, false, spdySynStreamFrame.headers());
        assertNull(sessionHandler.readOutbound());
        sessionHandler.writeInbound(spdyDataFrame);
        assertDataFrame(sessionHandler.readOutbound(), localStreamId, true);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler sends a GOAWAY frame when closing
        sessionHandler.writeInbound(closeMessage);
        assertGoAway(sessionHandler.readOutbound(), localStreamId);
        assertNull(sessionHandler.readOutbound());
        localStreamId += 2;

        // Check if session handler returns REFUSED_STREAM if it receives
        // SYN_STREAM frames after sending a GOAWAY frame
        spdySynStreamFrame.setStreamId(localStreamId);
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamId, SpdyStreamStatus.REFUSED_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler ignores Data frames after sending
        // a GOAWAY frame
        spdyDataFrame.setStreamId(localStreamId);
        sessionHandler.writeInbound(spdyDataFrame);
        assertNull(sessionHandler.readOutbound());

        sessionHandler.finish();
    }

    @Test
    public void testSpdyClientSessionHandler() {
        logger.info("Running: testSpdyClientSessionHandler v3.1");
        testSpdySessionHandler(SpdyVersion.SPDY_3_1, false);
    }

    @Test
    public void testSpdyClientSessionHandlerPing() {
        logger.info("Running: testSpdyClientSessionHandlerPing v3.1");
        testSpdySessionHandlerPing(SpdyVersion.SPDY_3_1, false);
    }

    @Test
    public void testSpdyClientSessionHandlerGoAway() {
        logger.info("Running: testSpdyClientSessionHandlerGoAway v3.1");
        testSpdySessionHandlerGoAway(SpdyVersion.SPDY_3_1, false);
    }

    @Test
    public void testSpdyServerSessionHandler() {
        logger.info("Running: testSpdyServerSessionHandler v3.1");
        testSpdySessionHandler(SpdyVersion.SPDY_3_1, true);
    }

    @Test
    public void testSpdyServerSessionHandlerPing() {
        logger.info("Running: testSpdyServerSessionHandlerPing v3.1");
        testSpdySessionHandlerPing(SpdyVersion.SPDY_3_1, true);
    }

    @Test
    public void testSpdyServerSessionHandlerGoAway() {
        logger.info("Running: testSpdyServerSessionHandlerGoAway v3.1");
        testSpdySessionHandlerGoAway(SpdyVersion.SPDY_3_1, true);
    }

    // Echo Handler opens 4 half-closed streams on session connection
    // and then sets the number of concurrent streams to 1
    private static class EchoHandler extends ChannelInboundHandlerAdapter {
        private final int closeSignal;
        private final boolean server;

        EchoHandler(int closeSignal, boolean server) {
            this.closeSignal = closeSignal;
            this.server = server;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // Initiate 4 new streams
            int streamId = server ? 2 : 1;
            SpdySynStreamFrame spdySynStreamFrame =
                    new DefaultSpdySynStreamFrame(streamId, 0, (byte) 0);
            spdySynStreamFrame.setLast(true);
            ctx.writeAndFlush(spdySynStreamFrame);
            spdySynStreamFrame.setStreamId(spdySynStreamFrame.streamId() + 2);
            ctx.writeAndFlush(spdySynStreamFrame);
            spdySynStreamFrame.setStreamId(spdySynStreamFrame.streamId() + 2);
            ctx.writeAndFlush(spdySynStreamFrame);
            spdySynStreamFrame.setStreamId(spdySynStreamFrame.streamId() + 2);
            ctx.writeAndFlush(spdySynStreamFrame);

            // Limit the number of concurrent streams to 1
            SpdySettingsFrame spdySettingsFrame = new DefaultSpdySettingsFrame();
            spdySettingsFrame.setValue(SpdySettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS, 1);
            ctx.writeAndFlush(spdySettingsFrame);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof SpdySynStreamFrame) {

                SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
                if (!spdySynStreamFrame.isUnidirectional()) {
                    int streamId = spdySynStreamFrame.streamId();
                    SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamId);
                    spdySynReplyFrame.setLast(spdySynStreamFrame.isLast());
                    for (Map.Entry<CharSequence, CharSequence> entry: spdySynStreamFrame.headers()) {
                        spdySynReplyFrame.headers().add(entry.getKey(), entry.getValue());
                    }

                    ctx.writeAndFlush(spdySynReplyFrame);
                }
                return;
            }

            if (msg instanceof SpdySynReplyFrame) {
                return;
            }

            if (msg instanceof SpdyDataFrame ||
                msg instanceof SpdyPingFrame ||
                msg instanceof SpdyHeadersFrame) {

                ctx.writeAndFlush(msg);
                return;
            }

            if (msg instanceof SpdySettingsFrame) {
                SpdySettingsFrame spdySettingsFrame = (SpdySettingsFrame) msg;
                if (spdySettingsFrame.isSet(closeSignal)) {
                    ctx.close();
                }
            }
        }
    }
}
