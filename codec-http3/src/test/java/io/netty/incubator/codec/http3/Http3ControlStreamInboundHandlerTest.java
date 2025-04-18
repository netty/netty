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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.netty.incubator.codec.http3.Http3TestUtils.assertException;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameReleased;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameSame;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Http3ControlStreamInboundHandlerTest extends
        AbstractHttp3FrameTypeValidationHandlerTest<Http3ControlStreamFrame> {

    private QpackEncoder qpackEncoder;
    private Http3ControlStreamOutboundHandler remoteControlStreamHandler;

    public Http3ControlStreamInboundHandlerTest() {
        super(QuicStreamType.UNIDIRECTIONAL, false, false);
    }

    static Collection<Object[]> testData() {
        List<Object[]> config = new ArrayList<>();
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                config.add(new Object[] { a == 0, b == 0 });
            }
        }
        return config;
    }

    @Override
    protected void setUp(boolean server) {
        super.setUp(server);
        qpackEncoder = new QpackEncoder();
        remoteControlStreamHandler = new Http3ControlStreamOutboundHandler(server, new DefaultHttp3SettingsFrame(),
                new ChannelInboundHandlerAdapter());
    }

    @Override
    protected void afterSettingsFrameRead(Http3SettingsFrame settingsFrame) {
        if (!qpackAttributes.dynamicTableDisabled()) {
            // settings frame initialize QPACK streams
            readAndReleaseStreamHeader(qPACKEncoderStream());
            readAndReleaseStreamHeader(qPACKDecoderStream());
        }
    }

    @Override
    protected ChannelHandler newHandler(boolean server) {
        return new Http3ControlStreamInboundHandler(server, new ChannelInboundHandlerAdapter(), qpackEncoder,
                remoteControlStreamHandler);
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

    @ParameterizedTest(name = "{index}: server = {0}, forwardControlFrames = {1}")
    @MethodSource("testData")
    public void testInvalidFirstFrameHttp3GoAwayFrame(boolean server, boolean forwardControlFrames) throws Exception {
        setUp(server);
        testInvalidFirstFrame(server, forwardControlFrames, new DefaultHttp3GoAwayFrame(0));
    }

    @ParameterizedTest(name = "{index}: server = {0}, forwardControlFrames = {1}")
    @MethodSource("testData")
    public void testInvalidFirstFrameHttp3MaxPushIdFrame(boolean server, boolean forwardControlFrames)
            throws Exception {
        setUp(server);
        testInvalidFirstFrame(server, forwardControlFrames, new DefaultHttp3MaxPushIdFrame(0));
    }

    @ParameterizedTest(name = "{index}: server = {0}, forwardControlFrames = {1}")
    @MethodSource("testData")
    public void testInvalidFirstFrameHttp3CancelPushFrame(boolean server, boolean forwardControlFrames)
            throws Exception {
        setUp(server);
        testInvalidFirstFrame(server, forwardControlFrames, new DefaultHttp3CancelPushFrame(0));
    }

    @ParameterizedTest(name = "{index}: server = {0}, forwardControlFrames = {1}")
    @MethodSource("testData")
    public void testInvalidFirstFrameNonControlFrame(boolean server, boolean forwardControlFrames) throws Exception {
        setUp(server);
        testInvalidFirstFrame(server, forwardControlFrames, () -> 9999);
    }

    private void testInvalidFirstFrame(boolean server, boolean forwardControlFrames, Http3Frame frame)
            throws Exception {
        final EmbeddedQuicStreamChannel channel = newStream(QuicStreamType.BIDIRECTIONAL,
                        new Http3ControlStreamInboundHandler(server,
                                forwardControlFrames ? new ChannelInboundHandlerAdapter() : null,
                                qpackEncoder, remoteControlStreamHandler));

        writeInvalidFrame(forwardControlFrames, Http3ErrorCode.H3_MISSING_SETTINGS, channel, frame);
        verifyClose(Http3ErrorCode.H3_MISSING_SETTINGS, parent);

        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}, forwardControlFrames = {1}")
    @MethodSource("testData")
    public void testValidGoAwayFrame(boolean server, boolean forwardControlFrames) throws Exception {
        setUp(server);
        EmbeddedChannel channel = newStream(server, forwardControlFrames);
        writeValidFrame(forwardControlFrames, channel, new DefaultHttp3GoAwayFrame(0));
        writeValidFrame(forwardControlFrames, channel, new DefaultHttp3GoAwayFrame(0));
        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}, forwardControlFrames = {1}")
    @MethodSource("testData")
    public void testSecondGoAwayFrameFailsWithHigherId(boolean server, boolean forwardControlFrames) throws Exception {
        setUp(server);
        EmbeddedChannel channel = newStream(server, forwardControlFrames);
        writeValidFrame(forwardControlFrames, channel, new DefaultHttp3GoAwayFrame(0));
        writeInvalidFrame(forwardControlFrames, Http3ErrorCode.H3_ID_ERROR, channel, new DefaultHttp3GoAwayFrame(4));
        verifyClose(Http3ErrorCode.H3_ID_ERROR, parent);
        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}, forwardControlFrames = {1}")
    @MethodSource("testData")
    public void testGoAwayFrameIdNonRequestStream(boolean server, boolean forwardControlFrames) throws Exception {
        setUp(server);
        EmbeddedChannel channel = newStream(server, forwardControlFrames);
        if (server) {
            writeValidFrame(forwardControlFrames, channel, new DefaultHttp3GoAwayFrame(3));
        } else {
            writeInvalidFrame(forwardControlFrames, Http3ErrorCode.H3_FRAME_UNEXPECTED, channel,
                    new DefaultHttp3GoAwayFrame(3));
            verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        }
        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}, forwardControlFrames = {1}")
    @MethodSource("testData")
    public void testHttp3MaxPushIdFrames(boolean server, boolean forwardControlFrames) throws Exception {
        setUp(server);
        EmbeddedChannel channel = newStream(server, forwardControlFrames);
        if (server) {
            writeValidFrame(forwardControlFrames, channel, new DefaultHttp3MaxPushIdFrame(0));
            writeValidFrame(forwardControlFrames, channel, new DefaultHttp3MaxPushIdFrame(4));
        } else {
            writeInvalidFrame(forwardControlFrames, Http3ErrorCode.H3_FRAME_UNEXPECTED, channel,
                    new DefaultHttp3MaxPushIdFrame(4));
            verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        }
        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: forwardControlFrames = {0}")
    @ValueSource(booleans = { true, false })
    public void testSecondHttp3MaxPushIdFrameFailsWithSmallerId(boolean forwardControlFrames)
            throws Exception {
        setUp(true);
        EmbeddedChannel channel = newStream(true, forwardControlFrames);
        writeValidFrame(forwardControlFrames, channel, new DefaultHttp3MaxPushIdFrame(4));
        writeInvalidFrame(forwardControlFrames, Http3ErrorCode.H3_ID_ERROR, channel, new DefaultHttp3MaxPushIdFrame(0));
        verifyClose(Http3ErrorCode.H3_ID_ERROR, parent);
        assertFalse(channel.finish());
    }

    private EmbeddedQuicStreamChannel newStream(boolean server, boolean forwardControlFrames) throws Exception {
        EmbeddedQuicStreamChannel channel = newStream(QuicStreamType.UNIDIRECTIONAL,
                        new Http3ControlStreamInboundHandler(server,
                                forwardControlFrames ? new ChannelInboundHandlerAdapter() : null,
                                qpackEncoder, remoteControlStreamHandler));

        // We always need to start with a settings frame.
        Http3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
        assertEquals(forwardControlFrames, channel.writeInbound(settingsFrame));
        if (forwardControlFrames) {
            assertFrameSame(settingsFrame, channel.readInbound());
        } else {
            assertFrameReleased(settingsFrame);
        }
        Object streamType = qPACKEncoderStream().readOutbound();
        assertNotNull(streamType);
        ReferenceCountUtil.release(streamType);

        streamType = qPACKDecoderStream().readOutbound();
        assertNotNull(streamType);
        ReferenceCountUtil.release(streamType);
        return channel;
    }

    private void writeValidFrame(boolean forwardControlFrames, EmbeddedChannel channel,
                                 Http3ControlStreamFrame controlStreamFrame) {
        assertEquals(forwardControlFrames, channel.writeInbound(controlStreamFrame));
        if (forwardControlFrames) {
            assertFrameSame(controlStreamFrame, channel.readInbound());
        } else {
            assertFrameReleased(controlStreamFrame);
        }
    }

    private void writeInvalidFrame(boolean forwardControlFrames, Http3ErrorCode expectedCode, EmbeddedChannel channel,
                                   Http3Frame frame) {
        if (forwardControlFrames) {
            Exception e = assertThrows(Exception.class, () -> channel.writeInbound(frame));
            assertException(expectedCode, e);
        } else {
            assertFalse(channel.writeInbound(frame));
        }
        assertFrameReleased(frame);
    }

    @ParameterizedTest(name = "{index}: server = {0}, forwardControlFrames = {1}")
    @MethodSource("testData")
    public void testSecondSettingsFrameFails(boolean server, boolean forwardControlFrames) throws Exception {
        setUp(server);
        EmbeddedChannel channel = newStream(server, forwardControlFrames);
        writeInvalidFrame(forwardControlFrames, Http3ErrorCode.H3_FRAME_UNEXPECTED, channel,
                new DefaultHttp3SettingsFrame());
        verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = "{index}: server = {0}, forwardControlFrames = {1}")
    @MethodSource("testData")
    public void testControlStreamClosed(boolean server, boolean forwardControlFrames) throws Exception {
        setUp(server);
        EmbeddedQuicStreamChannel channel = newStream(server, forwardControlFrames);
        channel.writeInboundFin();
        verifyClose(Http3ErrorCode.H3_CLOSED_CRITICAL_STREAM, parent);
        assertFalse(channel.finish());
    }

    @Override
    protected Http3ErrorCode inboundErrorCodeInvalid() {
        return Http3ErrorCode.H3_MISSING_SETTINGS;
    }
}
