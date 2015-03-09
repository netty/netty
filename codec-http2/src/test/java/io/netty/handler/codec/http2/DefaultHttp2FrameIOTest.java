/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_INT;
import static io.netty.handler.codec.http2.Http2TestUtil.as;
import static io.netty.handler.codec.http2.Http2TestUtil.randomString;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.EventExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Integration tests for {@link DefaultHttp2FrameReader} and {@link DefaultHttp2FrameWriter}.
 */
public class DefaultHttp2FrameIOTest {

    private DefaultHttp2FrameReader reader;
    private DefaultHttp2FrameWriter writer;
    private ByteBufAllocator alloc;
    private CountDownLatch latch;
    private ByteBuf buffer;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Http2FrameListener listener;

    @Mock
    private ChannelPromise promise;

    @Mock
    private Channel channel;

    @Mock
    private EventExecutor executor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        alloc = UnpooledByteBufAllocator.DEFAULT;
        buffer = alloc.buffer();
        latch = new CountDownLatch(1);

        when(executor.inEventLoop()).thenReturn(true);
        when(ctx.alloc()).thenReturn(alloc);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.executor()).thenReturn(executor);
        doAnswer(new Answer<ChannelPromise>() {
            @Override
            public ChannelPromise answer(InvocationOnMock invocation) throws Throwable {
                return new DefaultChannelPromise(channel, executor);
            }
        }).when(ctx).newPromise();

        doAnswer(new Answer<ChannelPromise>() {
            @Override
            public ChannelPromise answer(InvocationOnMock in) throws Throwable {
                latch.countDown();
                return promise;
            }
        }).when(promise).setSuccess();

        doAnswer(new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock in) throws Throwable {
                if (in.getArguments()[0] instanceof ByteBuf) {
                    ByteBuf tmp = (ByteBuf) in.getArguments()[0];
                    try {
                        buffer.writeBytes(tmp);
                    } finally {
                        tmp.release();
                    }
                }
                if (in.getArguments()[1] instanceof ChannelPromise) {
                    return ((ChannelPromise) in.getArguments()[1]).setSuccess();
                }
                return null;
            }
        }).when(ctx).write(any(), any(ChannelPromise.class));

        reader = new DefaultHttp2FrameReader();
        writer = new DefaultHttp2FrameWriter();
    }

    @Test
    public void emptyDataShouldRoundtrip() throws Exception {
        final ByteBuf data = Unpooled.EMPTY_BUFFER;
        writer.writeData(ctx, 1000, data, 0, false, promise);

        ByteBuf frame = null;
        try {
            frame = captureWrite();
            reader.readFrame(ctx, frame, listener);
            verify(listener).onDataRead(eq(ctx), eq(1000), eq(data), eq(0), eq(false));
        } finally {
            if (frame != null) {
                frame.release();
            }
            data.release();
        }
    }

    @Test
    public void dataShouldRoundtrip() throws Exception {
        final ByteBuf data = dummyData();
        writer.writeData(ctx, 1000, data.retain().duplicate(), 0, false, promise);

        ByteBuf frame = null;
        try {
            frame = captureWrite();
            reader.readFrame(ctx, frame, listener);
            verify(listener).onDataRead(eq(ctx), eq(1000), eq(data), eq(0), eq(false));
        } finally {
            if (frame != null) {
                frame.release();
            }
            data.release();
        }
    }

    @Test
    public void dataWithPaddingShouldRoundtrip() throws Exception {
        final ByteBuf data = dummyData();
        writer.writeData(ctx, 1, data.retain().duplicate(), 0xFF, true, promise);

        ByteBuf frame = null;
        try {
            frame = captureWrite();
            reader.readFrame(ctx, frame, listener);
            verify(listener).onDataRead(eq(ctx), eq(1), eq(data), eq(0xFF), eq(true));
        } finally {
            if (frame != null) {
                frame.release();
            }
            data.release();
        }
    }

    @Test
    public void priorityShouldRoundtrip() throws Exception {
        writer.writePriority(ctx, 1, 2, (short) 255, true, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onPriorityRead(eq(ctx), eq(1), eq(2), eq((short) 255), eq(true));
        } finally {
            frame.release();
        }
    }

    @Test
    public void rstStreamShouldRoundtrip() throws Exception {
        writer.writeRstStream(ctx, 1, MAX_UNSIGNED_INT, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onRstStreamRead(eq(ctx), eq(1), eq(MAX_UNSIGNED_INT));
        } finally {
            frame.release();
        }
    }

    @Test
    public void emptySettingsShouldRoundtrip() throws Exception {
        writer.writeSettings(ctx, new Http2Settings(), promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onSettingsRead(eq(ctx), eq(new Http2Settings()));
        } finally {
            frame.release();
        }
    }

    @Test
    public void settingsShouldStripShouldRoundtrip() throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.pushEnabled(true);
        settings.headerTableSize(4096);
        settings.initialWindowSize(123);
        settings.maxConcurrentStreams(456);

        writer.writeSettings(ctx, settings, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onSettingsRead(eq(ctx), eq(settings));
        } finally {
            frame.release();
        }
    }

    @Test
    public void settingsAckShouldRoundtrip() throws Exception {
        writer.writeSettingsAck(ctx, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onSettingsAckRead(eq(ctx));
        } finally {
            frame.release();
        }
    }

    @Test
    public void pingShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writePing(ctx, false, data.retain().duplicate(), promise);

        ByteBuf frame = null;
        try {
            frame = captureWrite();
            reader.readFrame(ctx, frame, listener);
            verify(listener).onPingRead(eq(ctx), eq(data));
        } finally {
            if (frame != null) {
                frame.release();
            }
            data.release();
        }
    }

    @Test
    public void pingAckShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writePing(ctx, true, data.retain().duplicate(), promise);

        ByteBuf frame = null;
        try {
            frame = captureWrite();
            reader.readFrame(ctx, frame, listener);
            verify(listener).onPingAckRead(eq(ctx), eq(data));
        } finally {
            if (frame != null) {
                frame.release();
            }
            data.release();
        }
    }

    @Test
    public void goAwayShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writeGoAway(ctx, 1, MAX_UNSIGNED_INT, data.retain().duplicate(), promise);

        ByteBuf frame = null;
        try {
            frame = captureWrite();
            reader.readFrame(ctx, frame, listener);
            verify(listener).onGoAwayRead(eq(ctx), eq(1), eq(MAX_UNSIGNED_INT), eq(data));
        } finally {
            if (frame != null) {
                frame.release();
            }
            data.release();
        }
    }

    @Test
    public void windowUpdateShouldRoundtrip() throws Exception {
        writer.writeWindowUpdate(ctx, 1, Integer.MAX_VALUE, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onWindowUpdateRead(eq(ctx), eq(1), eq(Integer.MAX_VALUE));
        } finally {
            frame.release();
        }
    }

    @Test
    public void emptyHeadersShouldRoundtrip() throws Exception {
        Http2Headers headers = EmptyHttp2Headers.INSTANCE;
        writer.writeHeaders(ctx, 1, headers, 0, true, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0), eq(true));
        } finally {
            frame.release();
        }
    }

    @Test
    public void emptyHeadersWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = EmptyHttp2Headers.INSTANCE;
        writer.writeHeaders(ctx, 1, headers, 0xFF, true, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0xFF), eq(true));
        } finally {
            frame.release();
        }
    }

    @Test
    public void binaryHeadersWithoutPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyBinaryHeaders();
        writer.writeHeaders(ctx, 1, headers, 0, true, promise);
        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0), eq(true));
        } finally {
            frame.release();
        }
    }

    @Test
    public void headersWithoutPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, 1, headers, 0, true, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0), eq(true));
        } finally {
            frame.release();
        }
    }

    @Test
    public void headersWithPaddingWithoutPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, 1, headers, 0xFF, true, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0xFF), eq(true));
        } finally {
            frame.release();
        }
    }

    @Test
    public void headersWithPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, 1, headers, 2, (short) 3, true, 0, true, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener)
                    .onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0), eq(true));
        } finally {
            frame.release();
        }
    }

    @Test
    public void headersWithPaddingWithPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, 1, headers, 2, (short) 3, true, 0xFF, true, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0xFF),
                    eq(true));
        } finally {
            frame.release();
        }
    }

    @Test
    public void continuedHeadersShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, 1, headers, 2, (short) 3, true, 0, true, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener)
                    .onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0), eq(true));
        } finally {
            frame.release();
        }
    }

    @Test
    public void continuedHeadersWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, 1, headers, 2, (short) 3, true, 0xFF, true, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0xFF),
                    eq(true));
        } finally {
            frame.release();
        }
    }

    @Test
    public void emptypushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = EmptyHttp2Headers.INSTANCE;
        writer.writePushPromise(ctx, 1, 2, headers, 0, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0));
        } finally {
            frame.release();
        }
    }

    @Test
    public void pushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writePushPromise(ctx, 1, 2, headers, 0, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0));
        } finally {
            frame.release();
        }
    }

    @Test
    public void pushPromiseWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writePushPromise(ctx, 1, 2, headers, 0xFF, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0xFF));
        } finally {
            frame.release();
        }
    }

    @Test
    public void continuedPushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, 1, 2, headers, 0, promise);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, listener);
        verify(listener).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0));
        frame.release();
    }

    @Test
    public void continuedPushPromiseWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, 1, 2, headers, 0xFF, promise);

        ByteBuf frame = captureWrite();
        try {
            reader.readFrame(ctx, frame, listener);
            verify(listener).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0xFF));
        } finally {
            frame.release();
        }
    }

    private ByteBuf captureWrite() throws InterruptedException {
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        return buffer;
    }

    private ByteBuf dummyData() {
        return alloc.buffer().writeBytes("abcdefgh".getBytes(CharsetUtil.UTF_8));
    }

    private static Http2Headers dummyBinaryHeaders() {
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        for (int ix = 0; ix < 10; ++ix) {
            headers.add(randomString(), randomString());
        }
        return headers;
    }

    private static Http2Headers dummyHeaders() {
        return new DefaultHttp2Headers().method(as("GET")).scheme(as("https")).authority(as("example.org"))
                .path(as("/some/path")).add(as("accept"), as("*/*"));
    }

    private static Http2Headers largeHeaders() {
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        for (int i = 0; i < 100; ++i) {
            String key = "this-is-a-test-header-key-" + i;
            String value = "this-is-a-test-header-value-" + i;
            headers.add(as(key), as(value));
        }
        return headers;
    }
}
