/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.LinkedList;
import java.util.List;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_PADDING;
import static io.netty.handler.codec.http2.Http2HeadersEncoder.NEVER_SENSITIVE;
import static io.netty.handler.codec.http2.Http2TestUtil.newTestDecoder;
import static io.netty.handler.codec.http2.Http2TestUtil.newTestEncoder;
import static io.netty.handler.codec.http2.Http2TestUtil.randomString;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.lang.Math.min;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests encoding/decoding each HTTP2 frame type.
 */
public class Http2FrameRoundtripTest {
    private static final byte[] MESSAGE = "hello world".getBytes(UTF_8);
    private static final int STREAM_ID = 0x7FFFFFFF;
    private static final int WINDOW_UPDATE = 0x7FFFFFFF;
    private static final long ERROR_CODE = 0xFFFFFFFFL;

    @Mock
    private Http2FrameListener listener;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private EventExecutor executor;

    @Mock
    private Channel channel;

    @Mock
    private ByteBufAllocator alloc;

    private Http2FrameWriter writer;
    private Http2FrameReader reader;
    private final List<ByteBuf> needReleasing = new LinkedList<ByteBuf>();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(ctx.alloc()).thenReturn(alloc);
        when(ctx.executor()).thenReturn(executor);
        when(ctx.channel()).thenReturn(channel);
        doAnswer(new Answer<ByteBuf>() {
            @Override
            public ByteBuf answer(InvocationOnMock in) throws Throwable {
                return Unpooled.buffer();
            }
        }).when(alloc).buffer();
        doAnswer(new Answer<ByteBuf>() {
            @Override
            public ByteBuf answer(InvocationOnMock in) throws Throwable {
                return Unpooled.buffer((Integer) in.getArguments()[0]);
            }
        }).when(alloc).buffer(anyInt());
        doAnswer(new Answer<ChannelPromise>() {
            @Override
            public ChannelPromise answer(InvocationOnMock invocation) throws Throwable {
                return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE);
            }
        }).when(ctx).newPromise();

        writer = new DefaultHttp2FrameWriter(new DefaultHttp2HeadersEncoder(NEVER_SENSITIVE, newTestEncoder()));
        reader = new DefaultHttp2FrameReader(new DefaultHttp2HeadersDecoder(false, newTestDecoder()));
    }

    @After
    public void teardown() {
        try {
            // Release all of the buffers.
            for (ByteBuf buf : needReleasing) {
                buf.release();
            }
            // Now verify that all of the reference counts are zero.
            for (ByteBuf buf : needReleasing) {
                int expectedFinalRefCount = 0;
                if (buf.isReadOnly() || buf instanceof EmptyByteBuf) {
                    // Special case for when we're writing slices of the padding buffer.
                    expectedFinalRefCount = 1;
                }
                assertEquals(expectedFinalRefCount, buf.refCnt());
            }
        } finally {
            needReleasing.clear();
        }
    }

    @Test
    public void emptyDataShouldMatch() throws Exception {
        final ByteBuf data = EMPTY_BUFFER;
        writer.writeData(ctx, STREAM_ID, data.slice(), 0, false, ctx.newPromise());
        readFrames();
        verify(listener).onDataRead(eq(ctx), eq(STREAM_ID), eq(data), eq(0), eq(false));
    }

    @Test
    public void dataShouldMatch() throws Exception {
        final ByteBuf data = data(10);
        writer.writeData(ctx, STREAM_ID, data.slice(), 1, false, ctx.newPromise());
        readFrames();
        verify(listener).onDataRead(eq(ctx), eq(STREAM_ID), eq(data), eq(1), eq(false));
    }

    @Test
    public void dataWithPaddingShouldMatch() throws Exception {
        final ByteBuf data = data(10);
        writer.writeData(ctx, STREAM_ID, data.slice(), MAX_PADDING, true, ctx.newPromise());
        readFrames();
        verify(listener).onDataRead(eq(ctx), eq(STREAM_ID), eq(data), eq(MAX_PADDING), eq(true));
    }

    @Test
    public void largeDataFrameShouldMatch() throws Exception {
        // Create a large message to force chunking.
        final ByteBuf originalData = data(1024 * 1024);
        final int originalPadding = 100;
        final boolean endOfStream = true;

        writer.writeData(ctx, STREAM_ID, originalData.slice(), originalPadding,
                endOfStream, ctx.newPromise());
        readFrames();

        // Verify that at least one frame was sent with eos=false and exactly one with eos=true.
        verify(listener, atLeastOnce()).onDataRead(eq(ctx), eq(STREAM_ID), any(ByteBuf.class),
                anyInt(), eq(false));
        verify(listener).onDataRead(eq(ctx), eq(STREAM_ID), any(ByteBuf.class),
                anyInt(), eq(true));

        // Capture the read data and padding.
        ArgumentCaptor<ByteBuf> dataCaptor = ArgumentCaptor.forClass(ByteBuf.class);
        ArgumentCaptor<Integer> paddingCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(listener, atLeastOnce()).onDataRead(eq(ctx), eq(STREAM_ID), dataCaptor.capture(),
                paddingCaptor.capture(), anyBoolean());

        // Make sure the data matches the original.
        for (ByteBuf chunk : dataCaptor.getAllValues()) {
            ByteBuf originalChunk = originalData.readSlice(chunk.readableBytes());
            assertEquals(originalChunk, chunk);
        }
        assertFalse(originalData.isReadable());

        // Make sure the padding matches the original.
        int totalReadPadding = 0;
        for (int framePadding : paddingCaptor.getAllValues()) {
            totalReadPadding += framePadding;
        }
        assertEquals(originalPadding, totalReadPadding);
    }

    @Test
    public void emptyHeadersShouldMatch() throws Exception {
        final Http2Headers headers = EmptyHttp2Headers.INSTANCE;
        writer.writeHeaders(ctx, STREAM_ID, headers, 0, true, ctx.newPromise());
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(0), eq(true));
    }

    @Test
    public void emptyHeadersWithPaddingShouldMatch() throws Exception {
        final Http2Headers headers = EmptyHttp2Headers.INSTANCE;
        writer.writeHeaders(ctx, STREAM_ID, headers, MAX_PADDING, true, ctx.newPromise());
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(MAX_PADDING), eq(true));
    }

    @Test
    public void binaryHeadersWithoutPriorityShouldMatch() throws Exception {
        final Http2Headers headers = binaryHeaders();
        writer.writeHeaders(ctx, STREAM_ID, headers, 0, true, ctx.newPromise());
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(0), eq(true));
    }

    @Test
    public void headersFrameWithoutPriorityShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writeHeaders(ctx, STREAM_ID, headers, 0, true, ctx.newPromise());
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(0), eq(true));
    }

    @Test
    public void headersFrameWithPriorityShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writeHeaders(ctx, STREAM_ID, headers, 4, (short) 255, true, 0, true, ctx.newPromise());
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(4), eq((short) 255),
                eq(true), eq(0), eq(true));
    }

    @Test
    public void headersWithPaddingWithoutPriorityShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writeHeaders(ctx, STREAM_ID, headers, MAX_PADDING, true, ctx.newPromise());
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(MAX_PADDING), eq(true));
    }

    @Test
    public void headersWithPaddingWithPriorityShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writeHeaders(ctx, STREAM_ID, headers, 2, (short) 3, true, 1, true, ctx.newPromise());
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(2), eq((short) 3), eq(true),
                eq(1), eq(true));
    }

    @Test
    public void continuedHeadersShouldMatch() throws Exception {
        final Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, STREAM_ID, headers, 2, (short) 3, true, 0, true, ctx.newPromise());
        readFrames();
        verify(listener)
                .onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(2), eq((short) 3), eq(true), eq(0), eq(true));
    }

    @Test
    public void continuedHeadersWithPaddingShouldMatch() throws Exception {
        final Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, STREAM_ID, headers, 2, (short) 3, true, MAX_PADDING, true, ctx.newPromise());
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(2), eq((short) 3), eq(true),
                eq(MAX_PADDING), eq(true));
    }

    @Test
    public void headersThatAreTooBigShouldFail() throws Exception {
        reader = new DefaultHttp2FrameReader(false);
        final int maxListSize = 100;
        reader.configuration().headersConfiguration().maxHeaderListSize(maxListSize, maxListSize);
        final Http2Headers headers = headersOfSize(maxListSize + 1);
        writer.writeHeaders(ctx, STREAM_ID, headers, 2, (short) 3, true, MAX_PADDING, true, ctx.newPromise());
        try {
            readFrames();
            fail();
        } catch (Http2Exception e) {
            verify(listener, never()).onHeadersRead(any(ChannelHandlerContext.class), anyInt(),
                    any(Http2Headers.class), anyInt(), anyShort(), anyBoolean(), anyInt(),
                    anyBoolean());
        }
    }

    @Test
    public void emptyPushPromiseShouldMatch() throws Exception {
        final Http2Headers headers = EmptyHttp2Headers.INSTANCE;
        writer.writePushPromise(ctx, STREAM_ID, 2, headers, 0, ctx.newPromise());
        readFrames();
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(2), eq(headers), eq(0));
    }

    @Test
    public void pushPromiseFrameShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writePushPromise(ctx, STREAM_ID, 1, headers, 5, ctx.newPromise());
        readFrames();
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(1), eq(headers), eq(5));
    }

    @Test
    public void pushPromiseWithPaddingShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writePushPromise(ctx, STREAM_ID, 2, headers, MAX_PADDING, ctx.newPromise());
        readFrames();
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(2), eq(headers), eq(MAX_PADDING));
    }

    @Test
    public void continuedPushPromiseShouldMatch() throws Exception {
        final Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, STREAM_ID, 2, headers, 0, ctx.newPromise());
        readFrames();
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(2), eq(headers), eq(0));
    }

    @Test
    public void continuedPushPromiseWithPaddingShouldMatch() throws Exception {
        final Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, STREAM_ID, 2, headers, 0xFF, ctx.newPromise());
        readFrames();
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(2), eq(headers), eq(0xFF));
    }

    @Test
    public void goAwayFrameShouldMatch() throws Exception {
        final String text = "test";
        final ByteBuf data = buf(text.getBytes());

        writer.writeGoAway(ctx, STREAM_ID, ERROR_CODE, data.slice(), ctx.newPromise());
        readFrames();

        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(listener).onGoAwayRead(eq(ctx), eq(STREAM_ID), eq(ERROR_CODE), captor.capture());
        assertEquals(data, captor.getValue());
    }

    @Test
    public void pingFrameShouldMatch() throws Exception {
        writer.writePing(ctx, false, 1234567, ctx.newPromise());
        readFrames();

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(long.class);
        verify(listener).onPingRead(eq(ctx), captor.capture());
        assertEquals(1234567, (long) captor.getValue());
    }

    @Test
    public void pingAckFrameShouldMatch() throws Exception {
        writer.writePing(ctx, true, 1234567, ctx.newPromise());
        readFrames();

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(long.class);
        verify(listener).onPingAckRead(eq(ctx), captor.capture());
        assertEquals(1234567, (long) captor.getValue());
    }

    @Test
    public void priorityFrameShouldMatch() throws Exception {
        writer.writePriority(ctx, STREAM_ID, 1, (short) 1, true, ctx.newPromise());
        readFrames();
        verify(listener).onPriorityRead(eq(ctx), eq(STREAM_ID), eq(1), eq((short) 1), eq(true));
    }

    @Test
    public void rstStreamFrameShouldMatch() throws Exception {
        writer.writeRstStream(ctx, STREAM_ID, ERROR_CODE, ctx.newPromise());
        readFrames();
        verify(listener).onRstStreamRead(eq(ctx), eq(STREAM_ID), eq(ERROR_CODE));
    }

    @Test
    public void emptySettingsFrameShouldMatch() throws Exception {
        final Http2Settings settings = new Http2Settings();
        writer.writeSettings(ctx, settings, ctx.newPromise());
        readFrames();
        verify(listener).onSettingsRead(eq(ctx), eq(settings));
    }

    @Test
    public void settingsShouldStripShouldMatch() throws Exception {
        final Http2Settings settings = new Http2Settings();
        settings.pushEnabled(true);
        settings.headerTableSize(4096);
        settings.initialWindowSize(123);
        settings.maxConcurrentStreams(456);

        writer.writeSettings(ctx, settings, ctx.newPromise());
        readFrames();
        verify(listener).onSettingsRead(eq(ctx), eq(settings));
    }

    @Test
    public void settingsAckShouldMatch() throws Exception {
        writer.writeSettingsAck(ctx, ctx.newPromise());
        readFrames();
        verify(listener).onSettingsAckRead(eq(ctx));
    }

    @Test
    public void windowUpdateFrameShouldMatch() throws Exception {
        writer.writeWindowUpdate(ctx, STREAM_ID, WINDOW_UPDATE, ctx.newPromise());
        readFrames();
        verify(listener).onWindowUpdateRead(eq(ctx), eq(STREAM_ID), eq(WINDOW_UPDATE));
    }

    private void readFrames() throws Http2Exception {
        // Now read all of the written frames.
        ByteBuf write = captureWrites();
        reader.readFrame(ctx, write, listener);
    }

    private static ByteBuf data(int size) {
        byte[] data = new byte[size];
        for (int ix = 0; ix < data.length;) {
            int length = min(MESSAGE.length, data.length - ix);
            System.arraycopy(MESSAGE, 0, data, ix, length);
            ix += length;
        }
        return buf(data);
    }

    private static ByteBuf buf(byte[] bytes) {
        return Unpooled.wrappedBuffer(bytes);
    }

    private <T extends ByteBuf> T releaseLater(T buf) {
        needReleasing.add(buf);
        return buf;
    }

    private ByteBuf captureWrites() {
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(ctx, atLeastOnce()).write(captor.capture(), isA(ChannelPromise.class));
        CompositeByteBuf composite = releaseLater(Unpooled.compositeBuffer());
        for (ByteBuf buf : captor.getAllValues()) {
            buf = releaseLater(buf.retain());
            composite.addComponent(true, buf);
        }
        return composite;
    }

    private static Http2Headers headers() {
        return new DefaultHttp2Headers(false).method(AsciiString.of("GET")).scheme(AsciiString.of("https"))
                .authority(AsciiString.of("example.org")).path(AsciiString.of("/some/path/resource2"))
                .add(randomString(), randomString());
    }

    private static Http2Headers largeHeaders() {
        DefaultHttp2Headers headers = new DefaultHttp2Headers(false);
        for (int i = 0; i < 100; ++i) {
            String key = "this-is-a-test-header-key-" + i;
            String value = "this-is-a-test-header-value-" + i;
            headers.add(AsciiString.of(key), AsciiString.of(value));
        }
        return headers;
    }

    private static Http2Headers headersOfSize(final int minSize) {
        final AsciiString singleByte = new AsciiString(new byte[]{0}, false);
        DefaultHttp2Headers headers = new DefaultHttp2Headers(false);
        for (int size = 0; size < minSize; size += 2) {
            headers.add(singleByte, singleByte);
        }
        return headers;
    }

    private static Http2Headers binaryHeaders() {
        DefaultHttp2Headers headers = new DefaultHttp2Headers(false);
        for (int ix = 0; ix < 10; ++ix) {
            headers.add(randomString(), randomString());
        }
        return headers;
    }
}
