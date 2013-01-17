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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.embedded.EmbeddedMessageChannel;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
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
        assertEquals(spdyDataFrame.streamId(), streamID);
        assertEquals(spdyDataFrame.isLast(), last);
    }

    private static void assertSynReply(Object msg, int streamID, boolean last, SpdyHeaderBlock headers) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdySynReplyFrame);
        SpdySynReplyFrame spdySynReplyFrame = (SpdySynReplyFrame) msg;
        assertEquals(spdySynReplyFrame.streamId(), streamID);
        assertEquals(spdySynReplyFrame.isLast(), last);
        assertHeaderBlock(spdySynReplyFrame, headers);
    }

    private static void assertRstStream(Object msg, int streamID, SpdyStreamStatus status) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyRstStreamFrame);
        SpdyRstStreamFrame spdyRstStreamFrame = (SpdyRstStreamFrame) msg;
        assertEquals(spdyRstStreamFrame.streamId(), streamID);
        assertEquals(spdyRstStreamFrame.status(), status);
    }

    private static void assertPing(Object msg, int id) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyPingFrame);
        SpdyPingFrame spdyPingFrame = (SpdyPingFrame) msg;
        assertEquals(spdyPingFrame.id(), id);
    }

    private static void assertGoAway(Object msg, int lastGoodStreamID) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyGoAwayFrame);
        SpdyGoAwayFrame spdyGoAwayFrame = (SpdyGoAwayFrame) msg;
        assertEquals(spdyGoAwayFrame.lastGoodStreamId(), lastGoodStreamID);
    }

    private static void assertHeaders(Object msg, int streamID, SpdyHeaderBlock headers) {
        assertNotNull(msg);
        assertTrue(msg instanceof SpdyHeadersFrame);
        SpdyHeadersFrame spdyHeadersFrame = (SpdyHeadersFrame) msg;
        assertEquals(spdyHeadersFrame.streamId(), streamID);
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

        SpdySynStreamFrame spdySynStreamFrame =
                new DefaultSpdySynStreamFrame(localStreamID, 0, (byte) 0);
        spdySynStreamFrame.headers().set("Compression", "test");
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
        spdySynStreamFrame = new DefaultSpdySynStreamFrame(localStreamID, 0, (byte) 0, true, true);
        spdySynStreamFrame.headers().set("Compression", "test");
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID, SpdyStreamStatus.REFUSED_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler drops active streams if it receives
        // a RST_STREAM frame for that Stream-ID
        sessionHandler.writeInbound(new DefaultSpdyRstStreamFrame(remoteStreamID, 3));
        assertNull(sessionHandler.readOutbound());
        remoteStreamID += 2;

        // Check if session handler honors UNIDIRECTIONAL streams
        spdySynStreamFrame = new DefaultSpdySynStreamFrame(localStreamID, 0, (byte) 0, true, false);
        spdySynStreamFrame.headers().set("Compression", "test");

        sessionHandler.writeInbound(spdySynStreamFrame);
        assertNull(sessionHandler.readOutbound());
        spdySynStreamFrame = new DefaultSpdySynStreamFrame(localStreamID, 0, (byte) 0, false, false);
        spdySynStreamFrame.headers().set("Compression", "test");

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // multiple SYN_STREAM frames for the same active Stream-ID
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());
        localStreamID += 2;

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // a SYN_STREAM frame with an invalid Stream-ID
        spdySynStreamFrame = new DefaultSpdySynStreamFrame(localStreamID -1, 0, (byte) 0, false, false);
        spdySynStreamFrame.headers().set("Compression", "test");

        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID - 1, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());

        spdySynStreamFrame = new DefaultSpdySynStreamFrame(localStreamID, 0, (byte) 0, false, false);
        spdySynStreamFrame.headers().set("Compression", "test");

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


        SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(localStreamID, true, Unpooled.EMPTY_BUFFER);
        // Check if session handler rejects HEADERS for closed streams
        int testStreamID = spdyDataFrame.streamId();
        sessionHandler.writeInbound(spdyDataFrame);
        assertDataFrame(sessionHandler.readOutbound(), testStreamID, spdyDataFrame.isLast());
        assertNull(sessionHandler.readOutbound());

        spdyHeadersFrame = new DefaultSpdyHeadersFrame(testStreamID);
        spdyHeadersFrame.headers().set("HEADER","test1");
        spdyHeadersFrame.headers().set("HEADER","test2");

        sessionHandler.writeInbound(spdyHeadersFrame);
        assertRstStream(sessionHandler.readOutbound(), testStreamID, SpdyStreamStatus.INVALID_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler returns PROTOCOL_ERROR if it receives
        // an invalid HEADERS frame

        spdyHeadersFrame = new DefaultSpdyHeadersFrame(localStreamID);
        spdyHeadersFrame.headers().set("HEADER","test1");
        spdyHeadersFrame.headers().set("HEADER","test2");
        spdyHeadersFrame.setInvalid();
        sessionHandler.writeInbound(spdyHeadersFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID, SpdyStreamStatus.PROTOCOL_ERROR);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler returns identical local PINGs
        sessionHandler.writeInbound(localPingFrame);
        assertPing(sessionHandler.readOutbound(), localPingFrame.id());
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
        spdySynStreamFrame = new DefaultSpdySynStreamFrame(localStreamID, 0, (byte) 0, false, false);
        sessionHandler.writeInbound(spdySynStreamFrame);
        assertRstStream(sessionHandler.readOutbound(), localStreamID, SpdyStreamStatus.REFUSED_STREAM);
        assertNull(sessionHandler.readOutbound());

        // Check if session handler ignores Data frames after sending
        // a GOAWAY frame
        sessionHandler.writeInbound(new DefaultSpdyDataFrame(localStreamID));
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
            ctx.write(new DefaultSpdySynStreamFrame(streamID, 0, (byte) 0, false, true));
            ctx.write(new DefaultSpdySynStreamFrame(streamID + 2, 0, (byte) 0, false, true));
            ctx.write(new DefaultSpdySynStreamFrame(streamID + 4, 0, (byte) 0, false, true));
            ctx.write(new DefaultSpdySynStreamFrame(streamID + 6, 0, (byte) 0, false, true));

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
                    int streamID = spdySynStreamFrame.streamId();
                    SpdySynReplyFrame spdySynReplyFrame = new DefaultSpdySynReplyFrame(streamID,
                            spdySynStreamFrame.isLast());
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
