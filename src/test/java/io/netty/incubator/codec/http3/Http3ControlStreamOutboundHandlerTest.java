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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static io.netty.incubator.codec.http3.Http3TestUtils.assertException;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Http3ControlStreamOutboundHandlerTest extends
        AbstractHttp3FrameTypeValidationHandlerTest<Http3ControlStreamFrame> {
    private final Http3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();

    public Http3ControlStreamOutboundHandlerTest() {
        super(QuicStreamType.UNIDIRECTIONAL, true, true);
    }

    @Override
    protected Http3FrameTypeDuplexValidationHandler<Http3ControlStreamFrame> newHandler() {
        return new Http3ControlStreamOutboundHandler(false, settingsFrame, new ChannelInboundHandlerAdapter());
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

    @Test
    public void testStreamClosedWhileParentStillActive() throws Exception {
        EmbeddedChannel channel = newStream(newHandler());
        assertFalse(channel.finish());
        verifyClose(1, Http3ErrorCode.H3_CLOSED_CRITICAL_STREAM, parent);
    }

    @Test
    public void testGoAwayIdDecreaseWorks() throws Exception {
        parent.close().get();
        // Let's mark the parent as inactive before we close as otherwise we will send a close frame.
        EmbeddedChannel channel = newStream(new Http3ControlStreamOutboundHandler(
                true, settingsFrame, new ChannelInboundHandlerAdapter()));
        assertTrue(channel.writeOutbound(new DefaultHttp3GoAwayFrame(8)));
        ReferenceCountUtil.release(channel.readOutbound());
        assertTrue(channel.writeOutbound(new DefaultHttp3GoAwayFrame(4)));
        ReferenceCountUtil.release(channel.readOutbound());

        assertFalse(channel.finish());
    }

    @Test
    public void testGoAwayIdIncreaseFails() throws Exception {
        // Let's mark the parent as inactive before we close as otherwise we will send a close frame.
        parent.close().get();
        EmbeddedChannel channel = newStream(new Http3ControlStreamOutboundHandler(
                true, settingsFrame, new ChannelInboundHandlerAdapter()));
        assertTrue(channel.writeOutbound(new DefaultHttp3GoAwayFrame(4)));
        ReferenceCountUtil.release(channel.readOutbound());

        try {
            channel.writeOutbound(new DefaultHttp3GoAwayFrame(8));
            fail();
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_ID_ERROR, e);
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testGoAwayIdUseInvalidId() throws Exception {
        parent.close().get();
        // Let's mark the parent as inactive before we close as otherwise we will send a close frame.
        EmbeddedChannel channel = newStream(new Http3ControlStreamOutboundHandler(
                true, settingsFrame, new ChannelInboundHandlerAdapter()));
        try {
            channel.writeOutbound(new DefaultHttp3GoAwayFrame(2));
            fail();
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_ID_ERROR, e);
        }
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
}
