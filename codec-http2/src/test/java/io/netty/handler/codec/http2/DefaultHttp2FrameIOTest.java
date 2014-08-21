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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Integration tests for {@link DefaultHttp2FrameReader} and {@link DefaultHttp2FrameWriter}.
 */
public class DefaultHttp2FrameIOTest {

    private DefaultHttp2FrameReader reader;
    private DefaultHttp2FrameWriter writer;
    private ByteBufAllocator alloc;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Http2FrameObserver observer;

    @Mock
    private ChannelPromise promise;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        alloc = UnpooledByteBufAllocator.DEFAULT;

        when(ctx.alloc()).thenReturn(alloc);

        reader = new DefaultHttp2FrameReader();
        writer = new DefaultHttp2FrameWriter();
    }

    @Test
    public void emptyDataShouldRoundtrip() throws Exception {
        ByteBuf data = Unpooled.EMPTY_BUFFER;
        writer.writeData(ctx, promise, 1000, data, 0, false);

        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onDataRead(eq(ctx), eq(1000), eq(data), eq(0), eq(false));
        frame.release();
    }

    @Test
    public void dataShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writeData(ctx, promise, 1000, data.retain().duplicate(), 0, false);

        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onDataRead(eq(ctx), eq(1000), eq(data), eq(0), eq(false));
        frame.release();
    }

    @Test
    public void dataWithPaddingShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writeData(ctx, promise, 1, data.retain().duplicate(), 0xFF, true);

        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onDataRead(eq(ctx), eq(1), eq(data), eq(0xFF), eq(true));
        frame.release();
    }

    @Test
    public void priorityShouldRoundtrip() throws Exception {
        writer.writePriority(ctx, promise, 1, 2, (short) 255, true);

        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onPriorityRead(eq(ctx), eq(1), eq(2), eq((short) 255), eq(true));
        frame.release();
    }

    @Test
    public void rstStreamShouldRoundtrip() throws Exception {
        writer.writeRstStream(ctx, promise, 1, MAX_UNSIGNED_INT);

        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onRstStreamRead(eq(ctx), eq(1), eq(MAX_UNSIGNED_INT));
        frame.release();
    }

    @Test
    public void emptySettingsShouldRoundtrip() throws Exception {
        writer.writeSettings(ctx, promise, new Http2Settings());

        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onSettingsRead(eq(ctx), eq(new Http2Settings()));
        frame.release();
    }

    @Test
    public void settingsShouldStripShouldRoundtrip() throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.pushEnabled(true);
        settings.headerTableSize(4096);
        settings.initialWindowSize(123);
        settings.maxConcurrentStreams(456);

        writer.writeSettings(ctx, promise, settings);

        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onSettingsRead(eq(ctx), eq(settings));
        frame.release();
    }

    @Test
    public void settingsAckShouldRoundtrip() throws Exception {
        writer.writeSettingsAck(ctx, promise);

        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onSettingsAckRead(eq(ctx));
        frame.release();
    }

    @Test
    public void pingShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writePing(ctx, promise, false, data.retain().duplicate());

        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onPingRead(eq(ctx), eq(data));
        frame.release();
    }

    @Test
    public void pingAckShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writePing(ctx, promise, true, data.retain().duplicate());

        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onPingAckRead(eq(ctx), eq(data));
        frame.release();
    }

    @Test
    public void goAwayShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writeGoAway(ctx, promise, 1, MAX_UNSIGNED_INT, data.retain().duplicate());
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onGoAwayRead(eq(ctx), eq(1), eq(MAX_UNSIGNED_INT), eq(data));
        frame.release();
    }

    @Test
    public void windowUpdateShouldRoundtrip() throws Exception {
        writer.writeWindowUpdate(ctx, promise, 1, Integer.MAX_VALUE);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onWindowUpdateRead(eq(ctx), eq(1), eq(Integer.MAX_VALUE));
        frame.release();
    }

    @Test
    public void emptyHeadersShouldRoundtrip() throws Exception {
        Http2Headers headers = Http2Headers.EMPTY_HEADERS;
        writer.writeHeaders(ctx, promise, 1, headers, 0, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0), eq(true));
        frame.release();
    }

    @Test
    public void emptyHeadersWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = Http2Headers.EMPTY_HEADERS;
        writer.writeHeaders(ctx, promise, 1, headers, 0xFF, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0xFF), eq(true));
        frame.release();
    }

    @Test
    public void headersWithoutPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 0, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0), eq(true));
        frame.release();
    }

    @Test
    public void headersWithPaddingWithoutPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 0xFF, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0xFF), eq(true));
        frame.release();
    }

    @Test
    public void headersWithPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 2, (short) 3, true, 0, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0),
                eq(true));
        frame.release();
    }

    @Test
    public void headersWithPaddingWithPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 2, (short) 3, true, 0xFF, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0xFF),
                eq(true));
        frame.release();
    }

    @Test
    public void continuedHeadersShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 2, (short) 3, true, 0, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0),
                eq(true));
        frame.release();
    }

    @Test
    public void continuedHeadersWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 2, (short) 3, true, 0xFF, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0xFF),
                eq(true));
        frame.release();
    }

    @Test
    public void emptypushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = Http2Headers.EMPTY_HEADERS;
        writer.writePushPromise(ctx, promise, 1, 2, headers, 0);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0));
        frame.release();
    }

    @Test
    public void pushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writePushPromise(ctx, promise, 1, 2, headers, 0);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0));
        frame.release();
    }

    @Test
    public void pushPromiseWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writePushPromise(ctx, promise, 1, 2, headers, 0xFF);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0xFF));
        frame.release();
    }

    @Test
    public void continuedPushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, promise, 1, 2, headers, 0);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0));
        frame.release();
    }

    @Test
    public void continuedPushPromiseWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, promise, 1, 2, headers, 0xFF);
        ByteBuf frame = captureWrite();
        reader.readFrame(ctx, frame, observer);
        verify(observer).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0xFF));
        frame.release();
    }

    private ByteBuf captureWrite() {
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(ctx).write(captor.capture(), eq(promise));
        return captor.getValue();
    }

    private ByteBuf dummyData() {
        return ReferenceCountUtil.releaseLater(alloc.buffer().writeBytes("abcdefgh".getBytes(CharsetUtil.UTF_8)));
    }

    private static Http2Headers dummyHeaders() {
        return DefaultHttp2Headers.newBuilder().method("GET").scheme("https")
                .authority("example.org").path("/some/path").add("accept", "*/*").build();
    }

    private static Http2Headers largeHeaders() {
        DefaultHttp2Headers.Builder builder = DefaultHttp2Headers.newBuilder();
        for (int i = 0; i < 100; ++i) {
            String key = "this-is-a-test-header-key-" + i;
            String value = "this-is-a-test-header-value-" + i;
            builder.add(key, value);
        }
        return builder.build();
    }
}
