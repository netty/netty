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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Http3FrameTypeValidationHandlerTest {

    protected Http3FrameTypeValidationHandler<Http3RequestStreamFrame> newValidationHandler() {
        return new Http3FrameTypeValidationHandler<>(Http3RequestStreamFrame.class);
    }

    @Test
    public void testValidTypeInbound() {
        EmbeddedChannel channel = new EmbeddedChannel(newValidationHandler());

        Http3RequestStreamFrame requestStreamFrame = new DefaultHttp3HeadersFrame();
        assertTrue(channel.writeInbound(requestStreamFrame));
        Http3RequestStreamFrame read = channel.readInbound();
        assertEquals(requestStreamFrame, read);
        assertFalse(channel.finish());
    }

    @Test
    public void testValidTypeOutput() {
        EmbeddedChannel channel = new EmbeddedChannel(newValidationHandler());

        Http3RequestStreamFrame requestStreamFrame = new DefaultHttp3HeadersFrame();
        assertTrue(channel.writeOutbound(requestStreamFrame));
        Http3RequestStreamFrame read = channel.readOutbound();
        assertEquals(requestStreamFrame, read);
        assertFalse(channel.finish());
    }

    @Test
    public void testInvalidTypeInbound() {
        QuicChannel parent = mock(QuicChannel.class);
        ArgumentCaptor<ByteBuf> argumentCaptor = ArgumentCaptor.forClass(ByteBuf.class);
        when(parent.close(anyBoolean(), anyInt(),
                any(ByteBuf.class))).thenReturn(new DefaultChannelPromise(parent));
        when(parent.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

        EmbeddedChannel channel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(), true, false,
                newValidationHandler());
        Http3ControlStreamFrame requestStreamFrame = new Http3ControlStreamFrame() { };
        assertFalse(channel.writeInbound(requestStreamFrame));
        assertFalse(channel.finish());
        verify(parent).close(eq(true), eq(Http3ErrorCode.H3_FRAME_UNEXPECTED.code), argumentCaptor.capture());
        argumentCaptor.getValue().release();
    }

    @Test
    public void testInvalidTypeOutput() {
        EmbeddedChannel channel = new EmbeddedChannel(newValidationHandler());

        Http3ControlStreamFrame requestStreamFrame = new Http3ControlStreamFrame() { };
        try {
            channel.writeOutbound(requestStreamFrame);
        } catch (Exception e) {
            assertException(e);
        }
        assertFalse(channel.finish());
    }

    protected static void assertException(Exception e) {
        MatcherAssert.assertThat(e, CoreMatchers.instanceOf(Http3Exception.class));
        Http3Exception exception = (Http3Exception) e;
        assertEquals(Http3ErrorCode.H3_FRAME_UNEXPECTED, exception.errorCode());
    }
}
