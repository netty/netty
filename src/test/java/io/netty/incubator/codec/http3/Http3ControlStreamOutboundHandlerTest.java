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
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static io.netty.incubator.codec.http3.Http3TestUtils.mockParent;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class Http3ControlStreamOutboundHandlerTest extends
        AbstractHttp3FrameTypeValidationHandlerTest<Http3ControlStreamFrame> {
    private final Http3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();

    @Override
    protected Http3FrameTypeValidationHandler<Http3ControlStreamFrame> newHandler() {
        return new Http3ControlStreamOutboundHandler(settingsFrame, new ChannelInboundHandlerAdapter());
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
    public void testStreamClosedWhileParentStillActive() {
        QuicChannel parent = mockParent();
        EmbeddedChannel channel = newChannel(parent, newHandler(), true);
        assertFalse(channel.finish());
        verifyClose(1, Http3ErrorCode.H3_CLOSED_CRITICAL_STREAM, parent);
    }

    @Override
    protected EmbeddedChannel newChannel(Channel parent,
                                         Http3FrameTypeValidationHandler<Http3ControlStreamFrame> handler) {
        if (parent == null) {
            parent = Mockito.mock(QuicChannel.class);
        }
        return newChannel(parent, handler, false);
    }

    private EmbeddedChannel newChannel(Channel parent, Http3FrameTypeValidationHandler<Http3ControlStreamFrame> handler,
                                       boolean parentActive) {
        Mockito.when(parent.isActive()).thenReturn(parentActive);

        EmbeddedChannel channel = super.newChannel(parent, handler);
        ByteBuf buffer = channel.readOutbound();
        // Verify that we did write the control stream prefix
        int len = Http3CodecUtils.numBytesForVariableLengthInteger(buffer.getByte(0));
        assertEquals(Http3CodecUtils.HTTP3_CONTROL_STREAM_TYPE, Http3CodecUtils.readVariableLengthInteger(buffer, len));
        assertFalse(buffer.isReadable());
        buffer.release();

        Http3SettingsFrame settings = channel.readOutbound();
        assertSame(settingsFrame, settings);

        assertNull(channel.readOutbound());
        return channel;
    }
}
