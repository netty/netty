/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.embedded.EmbeddedMessageChannel;
import io.netty.util.internal.InternalLogger;
import io.netty.util.internal.InternalLoggerFactory;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class SpdySessionHandlerTest {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SpdySessionHandlerTest.class);

    private static final int closeSignal = SpdyCodecUtil.SPDY_SETTINGS_MAX_ID;
    private static final SpdySettingsFrame closeMessage = new DefaultSpdySettingsFrame();

    static {
        closeMessage.setValue(closeSignal, 0);
    }

    private static void assertHeaderBlock(SpdyHeaderBlock received, SpdyHeaderBlock expected) {
        for (String name: expected.headers().names()) {
            List<String> expectedValues = expected.headers().getAll(name);
            List<String> receivedValues = received.headers().getAll(name);
            assertTrue(receivedValues.containsAll(expectedValues));
            receivedValues.removeAll(expectedValues);
            assertTrue(receivedValues.isEmpty());
            received.headers().remove(name);
        }
        assertTrue(received.headers().entries().isEmpty());
    }

    private static void assertDataFrame(Object msg, int streamID, boolean last) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyDataFrame);
        SpdyDataFrame spdyDataFrame = (SpdyDataFrame) msg;
        assertEquals(spdyDataFrame.getStreamId(), streamID);
        assertEquals(spdyDataFrame.isLast(), last);
    }

    private static void assertSynReply(Object msg, int streamID, boolean last, SpdyHeaderBlock headers) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdySynReplyFrame);
        SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
        assertEquals(spdySynReplyFrame.getStreamId(), streamID);
        assertEquals(spdySynReplyFrame.isLast(), last);
        assertHeaderBlock(spdySynReplyFrame, headers);
    }

    private static void assertRstStream(Object msg, int streamID, SpdyStreamStatus status) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyRstStreamFrame);
        SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
        assertEquals(spdyRstStreamFrame.getStreamId(), streamID);
        assertEquals(spdyRstStreamFrame.getStatus(), status);
    }

    private static void assertPing(Object msg, int id) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyPingFrame);
        SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;
        assertEquals(spdyPingFrame.getId(), id);
    }

    private static void assertGoAway(Object msg, int lastGoodStreamID) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyGoAwayFrame);
        SpdyGoAwayFrame spdyGoAwayFrame = (SpdyGoAwayFrame) msg;
        assertEquals(spdyGoAwayFrame.getLastGoodStreamId(), lastGoodStreamID);
    }

    private static void assertHeaders(Object msg, int streamID, SpdyHeaderBlock headers) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyHeadersFrame);
        SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
        assertEquals(spdyHeadersFrame.getStreamId(), streamID);
        assertHeaderBlock(spdyHeadersFrame, headers);
    }

    private static void testSpdySessionHandler(int version, boolean server) {
        EmbeddedMessageChannel sessionHandler = new EmbeddedMessageChannel(
                new SpdySessionHandler(version, server), new EchoHandler(closeSignal, server));

        while (sessionHandler.readOutbound() != null) {
            continue;
        }

        int localStreamID = server ? 1 : 2;
        int remoteStreamID = server ? 2 : 1;

        SpdyPingFrame localPingFrame = new DefaultSpdyPingFrame(localStreamID);
        SpdyPingFrame remotePingFrame = new DefaultSpdyPingFrame(remoteStreamID);

        SpdySynStreamFrame spdySynStreamFrame =
                new DefaultSpdySynStreamFrame(localStreamID, 0, (byte) 0);
        spdySynStreamFrame.headers().set("Compression", "test");

        SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(localStreamID);
        spdyDataFrame.setLast(true);

        // Check if session handler returns INVALID_STREAM if it receives
        // a data frame for a Stream-ID that is not open
        sessionHandler.writeInbound(new DefaultSpdyDataFrame(localStreamID));
        assertRstStream(sessionHandler.readOutbound(), localStreamID, SpdyStreamStatus.INVALID_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // a data frame for a Stream-ID before receiving a SYN_REPLY frame
        sessionHandler.writeInbound(new DefaultSpdyDataFrame(remoteStreamID));
        assertRstStream(sessionHandler.readOutbound(), remoteStreamID, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());
        remoteStreamID += 2;

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // multiple SYN_REPLY frames for the same active Stream-ID
        sessionHandler.writeInbound(new DefaultSpdySynReplyFrame(remoteStreamID));
        assertNull(sessionHandler.readOutbound());
        sessionHandler.writeInbound(new DefaultSpdySynReplyFrame(remoteStreamID));
        assertRstStream(sessionHandler.readOutbound(), remoteStreamID, SpdyStreamStatus.STREAM_IN_USE);
        assertNull(sessionHandler.readOutbound());
        remoteStreamID += 2;

        // Check if frame codec correctly compresses/uncompresses headers
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertSynReply(sessionHandler.readOutbound(), localStreamID, false, spdySynStreamFrame);
        assertNull(sessionHandler.readOutbound());
        SpdyHeadersFrame spdyHeadersFrame = new DefaultSpdyHeadersFrame(localStreamID);

        spdyHeadersFrame.headers().add("HEADER","test1");
        spdyHeadersFrame.headers().add("HEADER","test2");

        sessionHandler.writeInbound(spdyHeadersFrame);
        assertHeaders(sessionHandler.readOutbound(), localStreamID, spdyHeadersFrame);
        assertNull(sessionHandler.readOutbound());
        localStreamID += 2;

        // Check if session handler closed the streams using the number
        // of concurrent streams and that it returns REFUSED_STREAM
        // if it receives a SYN_STREAM frame it does not wish to accept
        spdySynStreamFrame.setStreamId(localStreamID);
        spdySynStreamFrame.setLast(true);
        spdySynStreamFrame.setUnidirectional(true);

        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID, SpdyStreamStatus.REFUSED_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler drops active streams if it receives
        // a RST_STREAM frame for that Stream-ID
        sessionHandler.writeInbound(new DefaultSpdyRstStreamFrame(remoteStreamID, 3));
        assertNull(sessionHandler.readOutbound());
        remoteStreamID += 2;

        // Check if session handler honors UNIDIRECTIONAL streams
        spdySynStreamFrame.setLast(false);
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertNull(sessionHandler.readOutbound());
        spdySynStreamFrame.setUnidirectional(false);


        // Check if session handler returns PROTOCOL_ERROR if it receives
        // multiple SYN_STREAM frames for the same active Stream-ID
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());
        localStreamID += 2;

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // a SYN_STREAM frame with an invalid Stream-ID
        spdySynStreamFrame.setStreamId(localStreamID - 1);
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID - 1, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());
        spdySynStreamFrame.setStreamId(localStreamID);


        // Check if session handler correctly limits the number of
        // concurrent streams in the SETTINGS frame
        SpdySettingsFrame spdySettingsFrame = new DefaultSpdySettingsFrame();
        spdySettingsFrame.setValue(SpdySettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS, 2);
        sessionHandler.writeInbound(spdySettingsFrame);
        assertNull(sessionHandler.readOutbound());
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID, SpdyStreamStatus.REFUSED_STREAM);
        assertNull(sessionHandler.readOutbound());
        spdySettingsFrame.setValue(SpdySettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS, 4);
        sessionHandler.writeInbound(spdySettingsFrame);
        assertNull(sessionHandler.readOutbound());
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertSynReply(sessionHandler.readOutbound(), localStreamID, false, spdySynStreamFrame);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler rejects HEADERS for closed streams
        int testStreamID = spdyDataFrame.getStreamId();
        sessionHandler.writeInbound(spdyDataFrame);
        assertDataFrame(sessionHandler.readOutbound(), testStreamID, spdyDataFrame.isLast());
        assertNull(sessionHandler.readOutbound());
        spdyHeadersFrame.setStreamId(testStreamID);

        sessionHandler.writeInbound(spdyHeadersFrame);
        assertRstStream(sessionHandler.readOutbound(), testStreamID, SpdyStreamStatus.INVALID_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // an invalid HEADERS frame
        spdyHeadersFrame.setStreamId(localStreamID);

        spdyHeadersFrame.setInvalid();
        sessionHandler.writeInbound(spdyHeadersFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler returns identical local PINGs
        sessionHandler.writeInbound(localPingFrame);
        assertPing(sessionHandler.readOutbound(), localPingFrame.getId());
        assertNull(sessionHandler.readOutbound());

        // Check if session handler ignores un-initiated remote PINGs
        sessionHandler.writeInbound(remotePingFrame);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler sends a GOAWAY frame when closing
        sessionHandler.writeInbound(closeMessage);
        assertGoAway(sessionHandler.readOutbound(), localStreamID);
        assertNull(sessionHandler.readOutbound());
        localStreamID += 2;

        // Check if session handler returns REFUSED_STREAM if it receives
        // SYN_STREAM frames after sending a GOAWAY frame
        spdySynStreamFrame.setStreamId(localStreamID);
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID, SpdyStreamStatus.REFUSED_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler ignores Data frames after sending
        // a GOAWAY frame
        spdyDataFrame.setStreamId(localStreamID);
        sessionHandler.writeInbound(spdyDataFrame);
        assertNull(sessionHandler.readOutbound());

        sessionHandler.finish();
    }

    @Test
    public void testSpdyClientSessionHandler() {
        for (int version = SpdyConstants.SPDY_MIN_VERSION; version <= SpdyConstants.SPDY_MAX_VERSION; version ++) {
            logger.info("Running: testSpdyClientSessionHandler v" + version);
            testSpdySessionHandler(version, false);
        }
    }

    @Test
    public void testSpdyServerSessionHandler() {
        for (int version = SpdyConstants.SPDY_MIN_VERSION; version <= SpdyConstants.SPDY_MAX_VERSION; version ++) {
            logger.info("Running: testSpdyServerSessionHandler v" + version);
            testSpdySessionHandler(version, true);
        }
    }

    // Echo Handler opens 4 half-closed streams on session connection
    // and then sets the number of concurrent streams to 3
    private static class EchoHandler extends ChannelInboundMessageHandlerAdapter<Object> {
        private final int closeSignal;
        private final boolean server;

        EchoHandler(int closeSignal, boolean server) {
            this.closeSignal = closeSignal;
            this.server = server;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // Initiate 4 new streams
            int streamID = server ? 2 : 1;
            SpdySynStreamFrame spdySynStreamFrame =
                    new DefaultSpdySynStreamFrame(streamID, 0, (byte) 0);
            spdySynStreamFrame.setLast(true);
            ctx.write(spdySynStreamFrame);
            spdySynStreamFrame.setStreamId(spdySynStreamFrame.getStreamId() + 2);
            ctx.write(spdySynStreamFrame);
            spdySynStreamFrame.setStreamId(spdySynStreamFrame.getStreamId() + 2);
            ctx.write(spdySynStreamFrame);
            spdySynStreamFrame.setStreamId(spdySynStreamFrame.getStreamId() + 2);
            ctx.write(spdySynStreamFrame);

            // Limit the number of concurrent streams to 3
            SpdySettingsFrame spdySettingsFrame = new DefaultSpdySettingsFrame();
            spdySettingsFrame.setValue(SpdySettingsFrame.SETTINGS_MAX_CONCURRENT_STREAMS, 3);
            ctx.write(spdySettingsFrame);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof SpdyDataFrame ||
                    msg instanceof SpdyPingFrame ||
                    msg instanceof SpdyHeadersFrame) {

                ctx.write(msg);
                return;
            }

            if (msg instanceof SpdySynStreamFrame) {

                SpdySynStreamFrame spdySynStreamFrame = (SpdySynStreamFrame) msg;
                if (!spdySynStreamFrame.isUnidirectional()) {
                    int streamID = spdySynStreamFrame.getStreamId();
                    SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamID);
                    spdySynReplyFrame.setLast(spdySynStreamFrame.isLast());
                    for (Map.Entry<String, String> entry: spdySynStreamFrame.headers()) {
                        spdySynReplyFrame.headers().add(entry.getKey(), entry.getValue());
                    }

                    ctx.write(spdySynReplyFrame);
                }
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
