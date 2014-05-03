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
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_SHORT;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.CharsetUtil;

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

        reader = new DefaultHttp2FrameReader(false);
        writer = new DefaultHttp2FrameWriter(true);
    }

    @Test
    public void emptyDataShouldRoundtrip() throws Exception {
        ByteBuf data = Unpooled.EMPTY_BUFFER;
        writer.writeData(ctx, promise, 1000, data, 0, false, false, false);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onDataRead(eq(1000), eq(data), eq(0), eq(false), eq(false), eq(false));
    }

    @Test
    public void dataShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writeData(ctx, promise, 1000, data, 0, false, false, false);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onDataRead(eq(1000), eq(data), eq(0), eq(false), eq(false), eq(false));
    }

    @Test
    public void dataWithPaddingShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writeData(ctx, promise, 1, data, 256, true, true, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onDataRead(eq(1), eq(data), eq(256), eq(true), eq(true), eq(true));
    }

    @Test
    public void priorityShouldRoundtrip() throws Exception {
        writer.writePriority(ctx, promise, 1, 2, (short) 255, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onPriorityRead(eq(1), eq(2), eq((short) 255), eq(true));
    }

    @Test
    public void rstStreamShouldRoundtrip() throws Exception {
        writer.writeRstStream(ctx, promise, 1, MAX_UNSIGNED_INT);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onRstStreamRead(eq(1), eq(MAX_UNSIGNED_INT));
    }

    @Test
    public void emptySettingsShouldRoundtrip() throws Exception {
        writer.writeSettings(ctx, promise, new Http2Settings());
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onSettingsRead(eq(new Http2Settings()));
    }

    @Test
    public void settingsShouldRoundtrip() throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.pushEnabled(true);
        settings.maxHeaderTableSize(4096);
        settings.initialWindowSize(123);
        settings.maxConcurrentStreams(456);
        settings.allowCompressedData(false);

        writer.writeSettings(ctx, promise, settings);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onSettingsRead(eq(settings));
    }

    @Test
    public void settingsAckShouldRoundtrip() throws Exception {
        writer.writeSettingsAck(ctx, promise);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onSettingsAckRead();
    }

    @Test
    public void pingShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writePing(ctx, promise, false, data);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onPingRead(eq(data));
    }

    @Test
    public void pingAckShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writePing(ctx, promise, true, data);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onPingAckRead(eq(data));
    }

    @Test
    public void goAwayShouldRoundtrip() throws Exception {
        ByteBuf data = dummyData();
        writer.writeGoAway(ctx, promise, 1, MAX_UNSIGNED_INT, data);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onGoAwayRead(eq(1), eq(MAX_UNSIGNED_INT), eq(data));
    }

    @Test
    public void windowUpdateShouldRoundtrip() throws Exception {
        writer.writeWindowUpdate(ctx, promise, 1, Integer.MAX_VALUE);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onWindowUpdateRead(eq(1), eq(Integer.MAX_VALUE));
    }

    @Test
    public void altSvcShouldRoundtrip() throws Exception {
        writer.writeAltSvc(ctx, promise, 1, MAX_UNSIGNED_INT, MAX_UNSIGNED_SHORT, dummyData(), "host",
                "origin");
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onAltSvcRead(eq(1), eq(MAX_UNSIGNED_INT), eq(MAX_UNSIGNED_SHORT),
                eq(dummyData()), eq("host"), eq("origin"));
    }

    @Test
    public void altSvcWithoutOriginShouldRoundtrip() throws Exception {
        writer.writeAltSvc(ctx, promise, 1, MAX_UNSIGNED_INT, MAX_UNSIGNED_SHORT, dummyData(), "host", null);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onAltSvcRead(eq(1), eq(MAX_UNSIGNED_INT), eq(MAX_UNSIGNED_SHORT),
                eq(dummyData()), eq("host"), isNull(String.class));
    }

    @Test
    public void blockedShouldRoundtrip() throws Exception {
        writer.writeBlocked(ctx, promise, 1);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onBlockedRead(eq(1));
    }

    @Test
    public void emptyHeadersShouldRoundtrip() throws Exception {
        Http2Headers headers = DefaultHttp2Headers.EMPTY_HEADERS;
        writer.writeHeaders(ctx, promise, 1, headers, 0, true, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onHeadersRead(eq(1), eq(headers), eq(0), eq(true), eq(true));
    }

    @Test
    public void emptyHeadersWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = DefaultHttp2Headers.EMPTY_HEADERS;
        writer.writeHeaders(ctx, promise, 1, headers, 256, true, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onHeadersRead(eq(1), eq(headers), eq(256), eq(true), eq(true));
    }

    @Test
    public void headersWithoutPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 0, true, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onHeadersRead(eq(1), eq(headers), eq(0), eq(true), eq(true));
    }

    @Test
    public void headersWithPaddingWithoutPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 256, true, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onHeadersRead(eq(1), eq(headers), eq(256), eq(true), eq(true));
    }

    @Test
    public void headersWithPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 2, (short) 3, true, 0, true, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onHeadersRead(eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0),
                eq(true), eq(true));
    }

    @Test
    public void headersWithPaddingWithPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 2, (short) 3, true, 256, true, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onHeadersRead(eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(256),
                eq(true), eq(true));
    }

    @Test
    public void continuedHeadersShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 2, (short) 3, true, 0, true, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onHeadersRead(eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0),
                eq(true), eq(true));
    }

    @Test
    public void continuedHeadersWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, promise, 1, headers, 2, (short) 3, true, 256, true, true);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onHeadersRead(eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(256),
                eq(true), eq(true));
    }

    @Test
    public void emptypushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = Http2Headers.EMPTY_HEADERS;
        writer.writePushPromise(ctx, promise, 1, 2, headers, 0);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onPushPromiseRead(eq(1), eq(2), eq(headers), eq(0));
    }

    @Test
    public void pushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writePushPromise(ctx, promise, 1, 2, headers, 0);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onPushPromiseRead(eq(1), eq(2), eq(headers), eq(0));
    }

    @Test
    public void pushPromiseWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writePushPromise(ctx, promise, 1, 2, headers, 256);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onPushPromiseRead(eq(1), eq(2), eq(headers), eq(256));
    }

    @Test
    public void continuedPushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, promise, 1, 2, headers, 0);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onPushPromiseRead(eq(1), eq(2), eq(headers), eq(0));
    }

    @Test
    public void continuedPushPromiseWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, promise, 1, 2, headers, 256);
        ByteBuf frame = captureWrite();
        reader.readFrame(alloc, frame, observer);
        verify(observer).onPushPromiseRead(eq(1), eq(2), eq(headers), eq(256));
    }

    private ByteBuf captureWrite() {
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(ctx).writeAndFlush(captor.capture(), eq(promise));
        return captor.getValue();
    }

    private ByteBuf dummyData() {
        return alloc.buffer().writeBytes("abcdefgh".getBytes(CharsetUtil.UTF_8));
    }

    private Http2Headers dummyHeaders() {
        return DefaultHttp2Headers.newBuilder().method("GET").scheme("https")
                .authority("example.org").path("/some/path").add("accept", "*/*").build();
    }

    private Http2Headers largeHeaders() {
        DefaultHttp2Headers.Builder builder = DefaultHttp2Headers.newBuilder();
        for (int i = 0; i < 100; ++i) {
            String key = "this-is-a-test-header-key-" + i;
            String value = "this-is-a-test-header-value-" + i;
            builder.add(key, value);
        }
        return builder.build();
    }
}
