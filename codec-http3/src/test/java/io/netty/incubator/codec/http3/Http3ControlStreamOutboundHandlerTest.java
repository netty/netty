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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;

import static io.netty.incubator.codec.http3.Http3TestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http3ControlStreamOutboundHandlerTest extends
        AbstractHttp3FrameTypeValidationHandlerTest<Http3ControlStreamFrame> {
    private final Http3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();

    public Http3ControlStreamOutboundHandlerTest() {
        super(QuicStreamType.UNIDIRECTIONAL, false, true);
    }

    @Override
    protected Http3ControlStreamOutboundHandler newHandler(boolean server) {
        return new Http3ControlStreamOutboundHandler(server, settingsFrame, new ChannelInboundHandlerAdapter());
    }

    @Override
    protected List<Http3ControlStreamFrame> newValidFrames() {
        return Arrays.asList(new DefaultHttp3SettingsFrame(), new DefaultHttp3GoAwayFrame(0),
                new DefaultHttp3MaxPushIdFrame(0), new DefaultHttp3CancelPushFrame(0));
    }

    @Override
    protected List<Http3Frame> newInvalidFrames() {
        return Arrays.asList(Http3TestUtils.newHttp3RequestStreamFrame(), Http3TestUtils.newHttp3PushStreamFrame());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testStreamClosedWhileParentStillActive(boolean server) throws Exception {
        setUp(server);
        EmbeddedChannel channel = newStream(newHandler(server));
        assertFalse(channel.finish());
        verifyClose(1, Http3ErrorCode.H3_CLOSED_CRITICAL_STREAM, parent);
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testGoAwayIdDecreaseWorks(boolean server) throws Exception {
        setUp(server);
        parent.close().get();
        // Let's mark the parent as inactive before we close as otherwise we will send a close frame.
        EmbeddedChannel channel = newStream(newHandler(server));

        if (server) {
            writeValidFrame(channel, new DefaultHttp3GoAwayFrame(8));
            writeValidFrame(channel, new DefaultHttp3GoAwayFrame(4));
        } else {
            writeValidFrame(channel, new DefaultHttp3GoAwayFrame(9));
            writeValidFrame(channel, new DefaultHttp3GoAwayFrame(5));
        }

        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testGoAwayIdIncreaseFails(boolean server) throws Exception {
        setUp(server);
        // Let's mark the parent as inactive before we close as otherwise we will send a close frame.
        parent.close().get();
        EmbeddedChannel channel = newStream(newHandler(server));

        if (server) {
            writeValidFrame(channel, new DefaultHttp3GoAwayFrame(4));
            writeInvalidFrame(Http3ErrorCode.H3_ID_ERROR, channel, new DefaultHttp3GoAwayFrame(8));
        } else {
            writeValidFrame(channel, new DefaultHttp3GoAwayFrame(1));
            writeInvalidFrame(Http3ErrorCode.H3_ID_ERROR, channel, new DefaultHttp3GoAwayFrame(3));
        }

        assertFalse(channel.finish());
    }

    @Test
    public void testGoAwayIdUseInvalidId() throws Exception {
        setUp(true);
        parent.close().get();
        // Let's mark the parent as inactive before we close as otherwise we will send a close frame.
        EmbeddedChannel channel = newStream(newHandler(true));

        writeInvalidFrame(Http3ErrorCode.H3_ID_ERROR, channel, new DefaultHttp3GoAwayFrame(2));

        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testPassesUnknownFrame(boolean server) throws Exception {
        setUp(server);
        parent.close().get();
        // Let's mark the parent as inactive before we close as otherwise we will send a close frame.
        EmbeddedChannel channel = newStream(newHandler(server));

        writeValidFrame(channel, new DefaultHttp3UnknownFrame(Http3CodecUtils.MIN_RESERVED_FRAME_TYPE,
                Unpooled.buffer().writeLong(8)));

        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testMaxPushIdFailsWhenReduced(boolean server) throws Exception {
        setUp(server);
        parent.close().get();
        // Let's mark the parent as inactive before we close as otherwise we will send a close frame.
        EmbeddedChannel channel = newStream(newHandler(server));

        writeValidFrame(channel, new DefaultHttp3MaxPushIdFrame(8));
        writeInvalidFrame(Http3ErrorCode.H3_ID_ERROR, channel, new DefaultHttp3MaxPushIdFrame(4));

        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}")
    @MethodSource("data")
    public void testMaxPushIdCanBeIncreased(boolean server) throws Exception {
        setUp(server);
        parent.close().get();
        // Let's mark the parent as inactive before we close as otherwise we will send a close frame.
        Http3ControlStreamOutboundHandler handler = newHandler(server);
        EmbeddedChannel channel = newStream(handler);

        writeValidFrame(channel, new DefaultHttp3MaxPushIdFrame(4));
        writeValidFrame(channel, new DefaultHttp3MaxPushIdFrame(8));
        assertEquals(Long.valueOf(8), handler.sentMaxPushId());

        assertFalse(channel.finish());
    }

    @Override
    protected EmbeddedQuicStreamChannel newStream(QuicStreamType streamType, ChannelHandler handler)
            throws Exception {
        return newStream(handler);
    }

    private EmbeddedQuicStreamChannel newStream(ChannelHandler handler)
            throws Exception {
        EmbeddedQuicStreamChannel channel = super.newStream(QuicStreamType.UNIDIRECTIONAL, handler);
        ByteBuf buffer = channel.readOutbound();
        // Verify that we did write the control stream prefix
        int len = Http3CodecUtils.numBytesForVariableLengthInteger(buffer.getByte(0));
        assertEquals(Http3CodecUtils.HTTP3_CONTROL_STREAM_TYPE, Http3CodecUtils.readVariableLengthInteger(buffer, len));
        assertFalse(buffer.isReadable());
        buffer.release();

        Http3SettingsFrame settings = channel.readOutbound();
        assertEquals(settingsFrame, settings);

        assertNull(channel.readOutbound());
        return channel;
    }

    private void writeInvalidFrame(Http3ErrorCode expectedCode,
                                   EmbeddedChannel channel,
                                   Http3Frame frame) {
        Exception e = assertThrows(Exception.class, () -> channel.writeOutbound(frame));
        assertException(expectedCode, e);
    }

    private void writeValidFrame(EmbeddedChannel channel, Http3Frame frame) {
        try {
            assertTrue(channel.writeOutbound(frame));
        } finally {
            ReferenceCountUtil.release(channel.readOutbound());
        }
    }
}
