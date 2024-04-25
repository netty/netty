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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.util.AsciiString;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import static io.netty5.buffer.DefaultBufferAllocators.onHeapAllocator;
import static io.netty5.handler.codec.http2.Http2CodecUtil.MAX_PADDING;
import static io.netty5.handler.codec.http2.Http2HeadersEncoder.NEVER_SENSITIVE;
import static io.netty5.handler.codec.http2.Http2TestUtil.bb;
import static io.netty5.handler.codec.http2.Http2TestUtil.empty;
import static io.netty5.handler.codec.http2.Http2TestUtil.newTestDecoder;
import static io.netty5.handler.codec.http2.Http2TestUtil.newTestEncoder;
import static io.netty5.handler.codec.http2.Http2TestUtil.randomString;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
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
    @AutoClose
    private Http2FrameWriter writer;
    private Http2FrameReader reader;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(ctx.bufferAllocator()).thenReturn(onHeapAllocator());
        when(ctx.executor()).thenReturn(executor);
        when(ctx.channel()).thenReturn(channel);
        doAnswer((Answer<Future<Void>>) in ->
                ImmediateEventExecutor.INSTANCE.newSucceededFuture(null)).when(ctx).write(any());
        doAnswer((Answer<Promise<Void>>) invocation ->
                GlobalEventExecutor.INSTANCE.newPromise()).when(ctx).newPromise();

        writer = new DefaultHttp2FrameWriter(new DefaultHttp2HeadersEncoder(NEVER_SENSITIVE, newTestEncoder()));
        reader = new DefaultHttp2FrameReader(new DefaultHttp2HeadersDecoder(false, false, newTestDecoder()));
    }

    @Test
    public void emptyDataShouldMatch() throws Exception {
        Buffer data = empty();
        writer.writeData(ctx, STREAM_ID, data, 0, false);
        doAnswer(invocation -> {
            assertEquals(ctx, invocation.getArgument(0));
            assertEquals(STREAM_ID, (int) invocation.getArgument(1));
            assertEquals(data, (Buffer) invocation.getArgument(2));
            assertEquals(0, (int) invocation.getArgument(3));
            assertFalse((boolean) invocation.getArgument(4));
            return 0;
        }).when(listener)
                .onDataRead(any(ChannelHandlerContext.class), anyInt(), any(Buffer.class), anyInt(), anyBoolean());

        readFrames();
    }

    @Test
    public void dataShouldMatch() throws Exception {
        try (Buffer data = data(10)) {
            writer.writeData(ctx, STREAM_ID, data.copy(), 1, false);

            doAnswer(invocation -> {
                assertEquals(ctx, invocation.getArgument(0));
                assertEquals(STREAM_ID, (int) invocation.getArgument(1));
                assertEquals(data, (Buffer) invocation.getArgument(2));
                assertEquals(1, (int) invocation.getArgument(3));
                assertFalse((boolean) invocation.getArgument(4));
                return 0;
            }).when(listener)
                    .onDataRead(any(ChannelHandlerContext.class), anyInt(), any(Buffer.class), anyInt(), anyBoolean());

            readFrames();
        }
    }

    @Test
    public void dataWithPaddingShouldMatch() throws Exception {
        try (Buffer data = data(10)) {
            writer.writeData(ctx, STREAM_ID, data.copy(), MAX_PADDING, true);

            doAnswer(invocation -> {
                assertEquals(ctx, invocation.getArgument(0));
                assertEquals(STREAM_ID, (int) invocation.getArgument(1));
                assertEquals(data, (Buffer) invocation.getArgument(2));
                assertEquals(MAX_PADDING, (int) invocation.getArgument(3));
                assertTrue((boolean) invocation.getArgument(4));
                return 0;
            }).when(listener)
                    .onDataRead(any(ChannelHandlerContext.class), anyInt(), any(Buffer.class), anyInt(), anyBoolean());

            readFrames();
        }
    }

    @Test
    public void largeDataFrameShouldMatch() throws Exception {
        // Create a large message to force chunking.
        try (Buffer originalData = data(1024 * 1024)) {
            final int originalPadding = 100;
            final boolean endOfStream = true;

            writer.writeData(ctx, STREAM_ID, originalData.copy(), originalPadding,
                             endOfStream);

            List<Buffer> datas = new ArrayList<>();
            doAnswer(invocation -> {
                datas.add(((Buffer) invocation.getArgument(2)).copy());
                return 0;
            }).when(listener)
                    .onDataRead(any(ChannelHandlerContext.class), anyInt(), any(Buffer.class), anyInt(), anyBoolean());

            readFrames();

            // Verify that at least one frame was sent with eos=false and exactly one with eos=true.
            verify(listener, atLeastOnce()).onDataRead(eq(ctx), eq(STREAM_ID), any(Buffer.class),
                                                       anyInt(), eq(false));
            verify(listener).onDataRead(eq(ctx), eq(STREAM_ID), any(Buffer.class),
                                        anyInt(), eq(true));

            // Capture the read data and padding.
            ArgumentCaptor<Integer> paddingCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(listener, atLeastOnce()).onDataRead(eq(ctx), eq(STREAM_ID), any(Buffer.class),
                                                       paddingCaptor.capture(), anyBoolean());

            // Make sure the data matches the original.
            for (Buffer chunk : datas) {
                try (Buffer originalChunk = originalData.readSplit(chunk.readableBytes());
                     chunk) {
                    assertEquals(originalChunk, chunk);
                }
            }
            assertEquals(0, originalData.readableBytes());

            // Make sure the padding matches the original.
            int totalReadPadding = 0;
            for (int framePadding : paddingCaptor.getAllValues()) {
                totalReadPadding += framePadding;
            }
            assertEquals(originalPadding, totalReadPadding);
        }
    }

    @Test
    public void emptyHeadersShouldMatch() throws Exception {
        final Http2Headers headers = Http2Headers.emptyHeaders();
        writer.writeHeaders(ctx, STREAM_ID, headers, 0, true);
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(0), eq(true));
    }

    @Test
    public void emptyHeadersWithPaddingShouldMatch() throws Exception {
        final Http2Headers headers = Http2Headers.emptyHeaders();
        writer.writeHeaders(ctx, STREAM_ID, headers, MAX_PADDING, true);
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(MAX_PADDING), eq(true));
    }

    @Test
    public void binaryHeadersWithoutPriorityShouldMatch() throws Exception {
        final Http2Headers headers = binaryHeaders();
        writer.writeHeaders(ctx, STREAM_ID, headers, 0, true);
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(0), eq(true));
    }

    @Test
    public void headersFrameWithoutPriorityShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writeHeaders(ctx, STREAM_ID, headers, 0, true);
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(0), eq(true));
    }

    @Test
    public void headersFrameWithPriorityShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writeHeaders(ctx, STREAM_ID, headers, 4, (short) 255, true, 0, true);
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(4), eq((short) 255),
                eq(true), eq(0), eq(true));
    }

    @Test
    public void headersWithPaddingWithoutPriorityShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writeHeaders(ctx, STREAM_ID, headers, MAX_PADDING, true);
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(MAX_PADDING), eq(true));
    }

    @Test
    public void headersWithPaddingWithPriorityShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writeHeaders(ctx, STREAM_ID, headers, 2, (short) 3, true, 1, true);
        readFrames();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(2), eq((short) 3), eq(true),
                eq(1), eq(true));
    }

    @Test
    public void continuedHeadersShouldMatch() throws Exception {
        final Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, STREAM_ID, headers, 2, (short) 3, true, 0, true);
        readFrames();
        verify(listener)
                .onHeadersRead(eq(ctx), eq(STREAM_ID), eq(headers), eq(2), eq((short) 3), eq(true), eq(0), eq(true));
    }

    @Test
    public void continuedHeadersWithPaddingShouldMatch() throws Exception {
        final Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, STREAM_ID, headers, 2, (short) 3, true, MAX_PADDING, true);
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
        writer.writeHeaders(ctx, STREAM_ID, headers, 2, (short) 3, true, MAX_PADDING, true);
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                readFrames();
            }
        });
        verify(listener, never()).onHeadersRead(any(ChannelHandlerContext.class), anyInt(),
                any(Http2Headers.class), anyInt(), anyShort(), anyBoolean(), anyInt(),
                anyBoolean());
    }

    @Test
    public void emptyPushPromiseShouldMatch() throws Exception {
        final Http2Headers headers = Http2Headers.emptyHeaders();
        writer.writePushPromise(ctx, STREAM_ID, 2, headers, 0);
        readFrames();
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(2), eq(headers), eq(0));
    }

    @Test
    public void pushPromiseFrameShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writePushPromise(ctx, STREAM_ID, 1, headers, 5);
        readFrames();
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(1), eq(headers), eq(5));
    }

    @Test
    public void pushPromiseWithPaddingShouldMatch() throws Exception {
        final Http2Headers headers = headers();
        writer.writePushPromise(ctx, STREAM_ID, 2, headers, MAX_PADDING);
        readFrames();
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(2), eq(headers), eq(MAX_PADDING));
    }

    @Test
    public void continuedPushPromiseShouldMatch() throws Exception {
        final Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, STREAM_ID, 2, headers, 0);
        readFrames();
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(2), eq(headers), eq(0));
    }

    @Test
    public void continuedPushPromiseWithPaddingShouldMatch() throws Exception {
        final Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, STREAM_ID, 2, headers, 0xFF);
        readFrames();
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(2), eq(headers), eq(0xFF));
    }

    @Test
    public void goAwayFrameShouldMatch() throws Exception {
        final String text = "test";
        try (Buffer data = bb(text.getBytes())) {
            doAnswer(invocation -> {
                assertEquals(ctx, invocation.getArgument(0));
                assertEquals(STREAM_ID, (int) invocation.getArgument(1));
                assertEquals(ERROR_CODE, (long) invocation.getArgument(2));
                assertEquals(data, (Buffer) invocation.getArgument(3));
                return null;
            }).when(listener)
                    .onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(), any(Buffer.class));

            writer.writeGoAway(ctx, STREAM_ID, ERROR_CODE, data.copy());
            readFrames();
        }
    }

    @Test
    public void pingFrameShouldMatch() throws Exception {
        writer.writePing(ctx, false, 1234567);
        readFrames();

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(long.class);
        verify(listener).onPingRead(eq(ctx), captor.capture());
        assertEquals(1234567, (long) captor.getValue());
    }

    @Test
    public void pingAckFrameShouldMatch() throws Exception {
        writer.writePing(ctx, true, 1234567);
        readFrames();

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(long.class);
        verify(listener).onPingAckRead(eq(ctx), captor.capture());
        assertEquals(1234567, (long) captor.getValue());
    }

    @Test
    public void priorityFrameShouldMatch() throws Exception {
        writer.writePriority(ctx, STREAM_ID, 1, (short) 1, true);
        readFrames();
        verify(listener).onPriorityRead(eq(ctx), eq(STREAM_ID), eq(1), eq((short) 1), eq(true));
    }

    @Test
    public void rstStreamFrameShouldMatch() throws Exception {
        writer.writeRstStream(ctx, STREAM_ID, ERROR_CODE);
        readFrames();
        verify(listener).onRstStreamRead(eq(ctx), eq(STREAM_ID), eq(ERROR_CODE));
    }

    @Test
    public void emptySettingsFrameShouldMatch() throws Exception {
        final Http2Settings settings = new Http2Settings();
        writer.writeSettings(ctx, settings);
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

        writer.writeSettings(ctx, settings);
        readFrames();
        verify(listener).onSettingsRead(eq(ctx), eq(settings));
    }

    @Test
    public void settingsAckShouldMatch() throws Exception {
        writer.writeSettingsAck(ctx);
        readFrames();
        verify(listener).onSettingsAckRead(eq(ctx));
    }

    @Test
    public void windowUpdateFrameShouldMatch() throws Exception {
        writer.writeWindowUpdate(ctx, STREAM_ID, WINDOW_UPDATE);
        readFrames();
        verify(listener).onWindowUpdateRead(eq(ctx), eq(STREAM_ID), eq(WINDOW_UPDATE));
    }

    private void readFrames() throws Http2Exception {
        // Now read all of the written frames.
        try (Buffer write = captureWrites()) {
            reader.readFrame(ctx, write, listener);
        }
    }

    private static Buffer data(int size) {
        byte[] data = new byte[size];
        for (int ix = 0; ix < data.length;) {
            int length = min(MESSAGE.length, data.length - ix);
            System.arraycopy(MESSAGE, 0, data, ix, length);
            ix += length;
        }
        return bb(data);
    }

    private Buffer captureWrites() {
        ArgumentCaptor<Buffer> captor = ArgumentCaptor.forClass(Buffer.class);
        verify(ctx, atLeastOnce()).write(captor.capture());
        List<Buffer> allWrites = captor.getAllValues();
        int bytesWritten = 0;
        for (Buffer buffer : allWrites) {
            bytesWritten += buffer.readableBytes();
        }
        Buffer combined = onHeapAllocator().allocate(bytesWritten);
        for (Buffer buffer : allWrites) {
            try (buffer) {
                combined.writeBytes(buffer);
            }
        }
        return combined;
    }

    private static Http2Headers headers() {
        return Http2Headers.newHeaders(false).method(AsciiString.of("GET")).scheme(AsciiString.of("https"))
                .authority(AsciiString.of("example.org")).path(AsciiString.of("/some/path/resource2"))
                .add(randomString(), randomString());
    }

    private static Http2Headers largeHeaders() {
        Http2Headers headers = Http2Headers.newHeaders(false);
        for (int i = 0; i < 100; ++i) {
            String key = "this-is-a-test-header-key-" + i;
            String value = "this-is-a-test-header-value-" + i;
            headers.add(AsciiString.of(key), AsciiString.of(value));
        }
        return headers;
    }

    private static Http2Headers headersOfSize(final int minSize) {
        final AsciiString singleByte = new AsciiString(new byte[]{0}, false);
        Http2Headers headers = Http2Headers.newHeaders(false);
        for (int size = 0; size < minSize; size += 2) {
            headers.add(singleByte, singleByte);
        }
        return headers;
    }

    private static Http2Headers binaryHeaders() {
        Http2Headers headers = Http2Headers.newHeaders(false);
        for (int ix = 0; ix < 10; ++ix) {
            headers.add(randomString(), randomString());
        }
        return headers;
    }
}
