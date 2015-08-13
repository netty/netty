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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AsciiString;
import io.netty.util.ByteString;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.EventExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_INT;
import static io.netty.handler.codec.http2.Http2TestUtil.randomString;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link DefaultHttp2FrameReader} and {@link DefaultHttp2FrameWriter}.
 */
public class DefaultHttp2FrameIOTest {

    private DefaultHttp2FrameReader reader;
    private DefaultHttp2FrameWriter writer;
    private ByteBufAllocator alloc;
    private ByteBuf buffer;
    private ByteBuf data;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Http2FrameListener listener;

    @Mock
    private ChannelPromise promise;

    @Mock
    private ChannelPromise aggregatePromise;

    @Mock
    private Channel channel;

    @Mock
    private EventExecutor executor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        alloc = UnpooledByteBufAllocator.DEFAULT;
        buffer = alloc.buffer();
        data = dummyData();

        when(executor.inEventLoop()).thenReturn(true);
        when(ctx.alloc()).thenReturn(alloc);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.executor()).thenReturn(executor);
        when(ctx.newPromise()).thenReturn(promise);
        when(promise.isDone()).thenReturn(true);
        when(promise.isSuccess()).thenReturn(true);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock in) throws Throwable {
                ChannelFutureListener l = (ChannelFutureListener) in.getArguments()[0];
                l.operationComplete(promise);
                return null;
            }
        }).when(promise).addListener(any(ChannelFutureListener.class));

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

    @After
    public void tearDown() {
        buffer.release();
        data.release();
    }

    @Test
    public void emptyDataShouldRoundtrip() throws Exception {
        final ByteBuf data = Unpooled.EMPTY_BUFFER;
        writer.writeData(ctx, 1000, data, 0, false, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onDataRead(eq(ctx), eq(1000), eq(data), eq(0), eq(false));
    }

    @Test
    public void dataShouldRoundtrip() throws Exception {
        writer.writeData(ctx, 1000, data.retain().duplicate(), 0, false, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onDataRead(eq(ctx), eq(1000), eq(data), eq(0), eq(false));
    }

    @Test
    public void dataWithPaddingShouldRoundtrip() throws Exception {
        writer.writeData(ctx, 1, data.retain().duplicate(), 0xFF, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onDataRead(eq(ctx), eq(1), eq(data), eq(0xFF), eq(true));
    }

    @Test
    public void priorityShouldRoundtrip() throws Exception {
        writer.writePriority(ctx, 1, 2, (short) 255, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onPriorityRead(eq(ctx), eq(1), eq(2), eq((short) 255), eq(true));
    }

    @Test
    public void rstStreamShouldRoundtrip() throws Exception {
        writer.writeRstStream(ctx, 1, MAX_UNSIGNED_INT, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onRstStreamRead(eq(ctx), eq(1), eq(MAX_UNSIGNED_INT));
    }

    @Test
    public void emptySettingsShouldRoundtrip() throws Exception {
        writer.writeSettings(ctx, new Http2Settings(), promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onSettingsRead(eq(ctx), eq(new Http2Settings()));
    }

    @Test
    public void settingsShouldStripShouldRoundtrip() throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.pushEnabled(true);
        settings.headerTableSize(4096);
        settings.initialWindowSize(123);
        settings.maxConcurrentStreams(456);

        writer.writeSettings(ctx, settings, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onSettingsRead(eq(ctx), eq(settings));
    }

    @Test
    public void settingsAckShouldRoundtrip() throws Exception {
        writer.writeSettingsAck(ctx, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onSettingsAckRead(eq(ctx));
    }

    @Test
    public void pingShouldRoundtrip() throws Exception {
        writer.writePing(ctx, false, data.retain().duplicate(), promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onPingRead(eq(ctx), eq(data));
    }

    @Test
    public void pingAckShouldRoundtrip() throws Exception {
        writer.writePing(ctx, true, data.retain().duplicate(), promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onPingAckRead(eq(ctx), eq(data));
    }

    @Test
    public void goAwayShouldRoundtrip() throws Exception {
        writer.writeGoAway(ctx, 1, MAX_UNSIGNED_INT, data.retain().duplicate(), promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onGoAwayRead(eq(ctx), eq(1), eq(MAX_UNSIGNED_INT), eq(data));
    }

    @Test
    public void windowUpdateShouldRoundtrip() throws Exception {
        writer.writeWindowUpdate(ctx, 1, Integer.MAX_VALUE, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onWindowUpdateRead(eq(ctx), eq(1), eq(Integer.MAX_VALUE));
    }

    @Test
    public void emptyHeadersShouldRoundtrip() throws Exception {
        Http2Headers headers = EmptyHttp2Headers.INSTANCE;
        writer.writeHeaders(ctx, 1, headers, 0, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0), eq(true));
    }

    @Test
    public void emptyHeadersWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = EmptyHttp2Headers.INSTANCE;
        writer.writeHeaders(ctx, 1, headers, 0xFF, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0xFF), eq(true));
    }

    @Test
    public void binaryHeadersWithoutPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyBinaryHeaders();
        writer.writeHeaders(ctx, 1, headers, 0, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0), eq(true));
    }

    @Test
    public void headersWithoutPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, 1, headers, 0, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0), eq(true));
    }

    @Test
    public void headersWithPaddingWithoutPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, 1, headers, 0xFF, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(0xFF), eq(true));
    }

    @Test
    public void headersWithPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, 1, headers, 2, (short) 3, true, 0, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener)
                .onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0), eq(true));
    }

    @Test
    public void headersWithPaddingWithPriorityShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writeHeaders(ctx, 1, headers, 2, (short) 3, true, 0xFF, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0xFF),
                eq(true));
    }

    @Test
    public void continuedHeadersShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, 1, headers, 2, (short) 3, true, 0, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener)
                .onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0), eq(true));
    }

    @Test
    public void continuedHeadersWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writeHeaders(ctx, 1, headers, 2, (short) 3, true, 0xFF, true, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onHeadersRead(eq(ctx), eq(1), eq(headers), eq(2), eq((short) 3), eq(true), eq(0xFF),
                eq(true));
    }

    @Test
    public void headersThatAreTooBigShouldFail() throws Exception {
        Http2Headers headers = headersOfSize(DEFAULT_MAX_HEADER_SIZE + 1);
        writer.writeHeaders(ctx, 1, headers, 2, (short) 3, true, 0xFF, true, promise);
        try {
            reader.readFrame(ctx, buffer, listener);
            fail();
        } catch (Http2Exception e) {
            verify(listener, never()).onHeadersRead(any(ChannelHandlerContext.class), anyInt(),
                    any(Http2Headers.class), anyInt(), anyShort(), anyBoolean(), anyInt(),
                    anyBoolean());
        }
    }

    @Test
    public void emptypushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = EmptyHttp2Headers.INSTANCE;
        writer.writePushPromise(ctx, 1, 2, headers, 0, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0));
    }

    @Test
    public void pushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writePushPromise(ctx, 1, 2, headers, 0, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0));
    }

    @Test
    public void pushPromiseWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = dummyHeaders();
        writer.writePushPromise(ctx, 1, 2, headers, 0xFF, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0xFF));
    }

    @Test
    public void continuedPushPromiseShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, 1, 2, headers, 0, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0));
    }

    @Test
    public void continuedPushPromiseWithPaddingShouldRoundtrip() throws Exception {
        Http2Headers headers = largeHeaders();
        writer.writePushPromise(ctx, 1, 2, headers, 0xFF, promise);
        reader.readFrame(ctx, buffer, listener);
        verify(listener).onPushPromiseRead(eq(ctx), eq(1), eq(2), eq(headers), eq(0xFF));
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
        return new DefaultHttp2Headers().method(new AsciiString("GET")).scheme(new AsciiString("https"))
                .authority(new AsciiString("example.org")).path(new AsciiString("/some/path"))
                .add(new AsciiString("accept"), new AsciiString("*/*"));
    }

    private static Http2Headers largeHeaders() {
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        for (int i = 0; i < 100; ++i) {
            String key = "this-is-a-test-header-key-" + i;
            String value = "this-is-a-test-header-value-" + i;
            headers.add(new AsciiString(key), new AsciiString(value));
        }
        return headers;
    }

    private Http2Headers headersOfSize(final int minSize) {
        final ByteString singleByte = new ByteString(new byte[]{0});
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        for (int size = 0; size < minSize; size += 2) {
            headers.add(singleByte, singleByte);
        }
        return headers;
    }
}
