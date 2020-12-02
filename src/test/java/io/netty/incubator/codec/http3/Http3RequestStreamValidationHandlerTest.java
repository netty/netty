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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Http3RequestStreamValidationHandlerTest extends Http3FrameTypeValidationHandlerTest {
    @Override
    protected Http3FrameTypeValidationHandler<Http3RequestStreamFrame> newValidationHandler() {
        return new Http3RequestStreamValidationHandler();
    }

    @Test
    public void testInvalidFrameSequenceStartInbound() {
        QuicChannel parent = mock(QuicChannel.class);
        ArgumentCaptor<ByteBuf> argumentCaptor = ArgumentCaptor.forClass(ByteBuf.class);
        when(parent.close(anyBoolean(), anyInt(),
                any(ByteBuf.class))).thenReturn(new DefaultChannelPromise(parent));
        when(parent.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

        EmbeddedChannel channel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(), true, false,
                newValidationHandler());
        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        assertFalse(channel.writeInbound(dataFrame));
        assertFalse(channel.finish());
        verify(parent).close(eq(true), eq(Http3ErrorCode.H3_FRAME_UNEXPECTED.code), argumentCaptor.capture());
        argumentCaptor.getValue().release();
        assertEquals(0, dataFrame.refCnt());
    }

    @Test
    public void testInvalidFrameSequenceEndInbound() {
        QuicChannel parent = mock(QuicChannel.class);
        ArgumentCaptor<ByteBuf> argumentCaptor = ArgumentCaptor.forClass(ByteBuf.class);
        when(parent.close(anyBoolean(), anyInt(),
                any(ByteBuf.class))).thenReturn(new DefaultChannelPromise(parent));
        when(parent.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

        EmbeddedChannel channel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(), true, false,
                newValidationHandler());
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dataFrame2 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dat3Frame3 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3HeadersFrame trailersFrame = new DefaultHttp3HeadersFrame();

        assertTrue(channel.writeInbound(headersFrame));
        assertTrue(channel.writeInbound(dataFrame.retainedDuplicate()));
        assertTrue(channel.writeInbound(dataFrame2.retainedDuplicate()));
        assertTrue(channel.writeInbound(trailersFrame));
        assertTrue(channel.writeInbound(dat3Frame3));

        assertTrue(channel.finish());
        verify(parent).close(eq(true), eq(Http3ErrorCode.H3_FRAME_UNEXPECTED.code), argumentCaptor.capture());
        argumentCaptor.getValue().release();
        assertEquals(0, dat3Frame3.refCnt());

        assertFrame(headersFrame, channel.readInbound());
        assertFrame(dataFrame, channel.readInbound());
        assertFrame(dataFrame2, channel.readInbound());
        assertFrame(trailersFrame, channel.readInbound());
        assertNull(channel.readInbound());
    }

    @Test
    public void testInvalidFrameSequenceStartOutbound() {
        EmbeddedChannel channel = new EmbeddedChannel(newValidationHandler());

        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        try {
            channel.writeOutbound(dataFrame);
        } catch (Exception e) {
            assertException(e);
        }
        assertFalse(channel.finish());
        assertEquals(0, dataFrame.refCnt());
    }

    @Test
    public void testInvalidFrameSequenceEndOutbound() {
        EmbeddedChannel channel = new EmbeddedChannel(newValidationHandler());

        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dataFrame2 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dat3Frame3 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3HeadersFrame trailersFrame = new DefaultHttp3HeadersFrame();
        assertTrue(channel.writeOutbound(headersFrame));
        assertTrue(channel.writeOutbound(dataFrame.retainedDuplicate()));
        assertTrue(channel.writeOutbound(dataFrame2.retainedDuplicate()));
        assertTrue(channel.writeOutbound(trailersFrame));

        try {
            channel.writeOutbound(dat3Frame3);
        } catch (Exception e) {
            assertException(e);
        }
        assertTrue(channel.finish());
        assertEquals(0, dat3Frame3.refCnt());

        assertFrame(headersFrame, channel.readOutbound());
        assertFrame(dataFrame, channel.readOutbound());
        assertFrame(dataFrame2, channel.readOutbound());
        assertFrame(trailersFrame, channel.readOutbound());
        assertNull(channel.readOutbound());
    }

    private static void assertFrame(Http3Frame expected, Http3Frame actual) {
        try {
            assertEquals(expected, actual);
        } finally {
            ReferenceCountUtil.release(expected);
            ReferenceCountUtil.release(actual);
        }
    }
}
