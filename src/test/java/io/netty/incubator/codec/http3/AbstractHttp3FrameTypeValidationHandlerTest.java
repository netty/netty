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
import io.netty.util.ReferenceCounted;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

public abstract class AbstractHttp3FrameTypeValidationHandlerTest<T extends Http3Frame> {

    protected abstract Http3FrameTypeValidationHandler<T> newHandler();

    protected abstract List<T> newValidFrames();

    protected abstract List<Http3Frame> newInvalidFrames();

    @Test
    public void testValidTypeInbound() {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler());
        List<T> validFrames = newValidFrames();
        for (T valid : validFrames) {
            assertTrue(channel.writeInbound(valid));
            T read = channel.readInbound();
            assertSame(valid, read);
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testValidTypeOutput() {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler());
        List<T> validFrames = newValidFrames();
        for (T valid : validFrames) {
            assertTrue(channel.writeOutbound(valid));
            T read = channel.readOutbound();
            assertSame(valid, read);
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testInvalidTypeInbound() {
        QuicChannel parent = mockParent();
        EmbeddedChannel channel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(), true, false,
                newHandler());

        List<Http3Frame> invalidFrames = newInvalidFrames();
        for (Http3Frame invalid : invalidFrames) {
            try {
                channel.writeInbound(invalid);
                fail();
            } catch (Exception e) {
                assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
            }
            if (invalid instanceof ReferenceCounted) {
                assertEquals(0, ((ReferenceCounted) invalid).refCnt());
            }
        }
        verifyClose(invalidFrames.size(), Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        assertFalse(channel.finish());
    }

    @Test
    public void testInvalidTypeOutput() {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler());

        List<Http3Frame> invalidFrames = newInvalidFrames();
        for (Http3Frame invalid : invalidFrames) {
            try {
                channel.writeOutbound(invalid);
                fail();
            } catch (Exception e) {
                assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
            }
            if (invalid instanceof ReferenceCounted) {
                assertEquals(0, ((ReferenceCounted) invalid).refCnt());
            }
        }
        assertFalse(channel.finish());
    }

    protected static void assertException(Http3ErrorCode code, Exception e) {
        MatcherAssert.assertThat(e, CoreMatchers.instanceOf(Http3Exception.class));
        Http3Exception exception = (Http3Exception) e;
        assertEquals(code, exception.errorCode());
    }

    protected void verifyClose(Http3ErrorCode expectedCode, QuicChannel parent) {
        verifyClose(1, expectedCode, parent);
    }

    protected void verifyClose(int times, Http3ErrorCode expectedCode, QuicChannel parent) {
        ArgumentCaptor<ByteBuf> argumentCaptor = ArgumentCaptor.forClass(ByteBuf.class);
        try {
            verify(parent, times(times)).close(eq(true),
                    eq(expectedCode.code), argumentCaptor.capture());
        } finally {
            for (ByteBuf buffer : argumentCaptor.getAllValues()) {
                buffer.release();
            }
        }
    }

    protected static QuicChannel mockParent() {
        QuicChannel parent = mock(QuicChannel.class);
        when(parent.close(anyBoolean(), anyInt(),
                any(ByteBuf.class))).thenReturn(new DefaultChannelPromise(parent));
        when(parent.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        return parent;
    }
}
