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

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.emptyPingBuf;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_LOCAL;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link DefaultHttp2ConnectionEncoder}
 */
public class DefaultHttp2ConnectionEncoderTest {
    private static final int STREAM_ID = 1;
    private static final int PUSH_STREAM_ID = 2;

    private DefaultHttp2ConnectionEncoder encoder;

    @Mock
    private Http2Connection connection;

    @Mock
    private Http2Connection.Endpoint remote;

    @Mock
    private Http2Connection.Endpoint local;

    @Mock
    private Http2OutboundFlowController outboundFlow;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    private ChannelPromise promise;

    @Mock
    private ChannelFuture future;

    @Mock
    private Http2Stream stream;

    @Mock
    private Http2Stream pushStream;

    @Mock
    private Http2FrameListener listener;

    @Mock
    private Http2FrameWriter writer;

    @Mock
    private Http2LifecycleManager lifecycleManager;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        promise = new DefaultChannelPromise(channel);

        when(channel.isActive()).thenReturn(true);
        when(stream.id()).thenReturn(STREAM_ID);
        when(stream.state()).thenReturn(OPEN);
        when(pushStream.id()).thenReturn(PUSH_STREAM_ID);
        when(connection.activeStreams()).thenReturn(Collections.singletonList(stream));
        when(connection.stream(STREAM_ID)).thenReturn(stream);
        when(connection.requireStream(STREAM_ID)).thenReturn(stream);
        when(connection.local()).thenReturn(local);
        when(connection.remote()).thenReturn(remote);
        doAnswer(new Answer<Http2Stream>() {
            @Override
            public Http2Stream answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return local.createStream((Integer) args[0], (Boolean) args[1]);
            }
        }).when(connection).createLocalStream(anyInt(), anyBoolean());
        doAnswer(new Answer<Http2Stream>() {
            @Override
            public Http2Stream answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return remote.createStream((Integer) args[0], (Boolean) args[1]);
            }
        }).when(connection).createRemoteStream(anyInt(), anyBoolean());
        when(local.createStream(eq(STREAM_ID), anyBoolean())).thenReturn(stream);
        when(local.reservePushStream(eq(PUSH_STREAM_ID), eq(stream))).thenReturn(pushStream);
        when(remote.createStream(eq(STREAM_ID), anyBoolean())).thenReturn(stream);
        when(remote.reservePushStream(eq(PUSH_STREAM_ID), eq(stream))).thenReturn(pushStream);
        when(writer.writeSettings(eq(ctx), any(Http2Settings.class), eq(promise))).thenReturn(future);
        when(writer.writeGoAway(eq(ctx), anyInt(), anyInt(), any(ByteBuf.class), eq(promise))).thenReturn(future);
        when(outboundFlow.writeData(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean(), eq(promise)))
                .thenReturn(future);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.newSucceededFuture()).thenReturn(future);
        when(ctx.newPromise()).thenReturn(promise);
        when(ctx.write(any())).thenReturn(future);

        encoder = new DefaultHttp2ConnectionEncoder(connection, writer, outboundFlow, lifecycleManager);
    }

    @Test
    public void dataWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        final ByteBuf data = dummyData();
        try {
            ChannelFuture future = encoder.writeData(ctx, STREAM_ID, data, 0, false, promise);
            assertTrue(future.awaitUninterruptibly().cause() instanceof Http2Exception);
        } finally {
            while (data.refCnt() > 0) {
                data.release();
            }
        }
    }

    @Test
    public void dataWriteShouldSucceed() throws Exception {
        final ByteBuf data = dummyData();
        try {
            encoder.writeData(ctx, STREAM_ID, data, 0, false, promise);
            verify(outboundFlow).writeData(eq(ctx), eq(STREAM_ID), eq(data), eq(0), eq(false), eq(promise));
        } finally {
            data.release();
        }
    }

    @Test
    public void dataWriteShouldHalfCloseStream() throws Exception {
        reset(future);
        final ByteBuf data = dummyData();
        try {
            encoder.writeData(ctx, STREAM_ID, data, 0, true, promise);
            verify(outboundFlow).writeData(eq(ctx), eq(STREAM_ID), eq(data), eq(0), eq(true), eq(promise));

            // Invoke the listener callback indicating that the write completed successfully.
            ArgumentCaptor<ChannelFutureListener> captor = ArgumentCaptor.forClass(ChannelFutureListener.class);
            verify(future).addListener(captor.capture());
            when(future.isSuccess()).thenReturn(true);
            captor.getValue().operationComplete(future);
            verify(lifecycleManager).closeLocalSide(eq(stream), eq(promise));
        } finally {
            data.release();
        }
    }

    @Test
    public void headersWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        ChannelFuture future = encoder.writeHeaders(
                ctx, 5, EmptyHttp2Headers.INSTANCE, 0, (short) 255, false, 0, false, promise);
        verify(local, never()).createStream(anyInt(), anyBoolean());
        verify(writer, never()).writeHeaders(eq(ctx), anyInt(), any(Http2Headers.class), anyInt(), anyBoolean(),
                eq(promise));
        assertTrue(future.awaitUninterruptibly().cause() instanceof Http2Exception);
    }

    @Test
    public void headersWriteForUnknownStreamShouldCreateStream() throws Exception {
        when(local.createStream(eq(5), eq(false))).thenReturn(stream);
        encoder.writeHeaders(ctx, 5, EmptyHttp2Headers.INSTANCE, 0, false, promise);
        verify(local).createStream(eq(5), eq(false));
        verify(writer).writeHeaders(eq(ctx), eq(5), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false), eq(promise));
    }

    @Test
    public void headersWriteShouldCreateHalfClosedStream() throws Exception {
        when(local.createStream(eq(5), eq(true))).thenReturn(stream);
        encoder.writeHeaders(ctx, 5, EmptyHttp2Headers.INSTANCE, 0, true, promise);
        verify(local).createStream(eq(5), eq(true));
        verify(writer).writeHeaders(eq(ctx), eq(5), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true), eq(promise));
    }

    @Test
    public void headersWriteShouldOpenStreamForPush() throws Exception {
        when(stream.state()).thenReturn(RESERVED_LOCAL);
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false, promise);
        verify(stream).openForPush();
        verify(stream, never()).closeLocalSide();
        verify(writer).writeHeaders(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false), eq(promise));
    }

    @Test
    public void headersWriteShouldClosePushStream() throws Exception {
        when(stream.state()).thenReturn(RESERVED_LOCAL).thenReturn(HALF_CLOSED_LOCAL);
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true, promise);
        verify(stream).openForPush();
        verify(lifecycleManager).closeLocalSide(eq(stream), eq(promise));
        verify(writer).writeHeaders(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true), eq(promise));
    }

    @Test
    public void pushPromiseWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        ChannelFuture future =
                encoder.writePushPromise(ctx, STREAM_ID, PUSH_STREAM_ID,
                        EmptyHttp2Headers.INSTANCE, 0, promise);
        assertTrue(future.awaitUninterruptibly().cause() instanceof Http2Exception);
    }

    @Test
    public void pushPromiseWriteShouldReserveStream() throws Exception {
        encoder.writePushPromise(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, promise);
        verify(local).reservePushStream(eq(PUSH_STREAM_ID), eq(stream));
        verify(writer).writePushPromise(eq(ctx), eq(STREAM_ID), eq(PUSH_STREAM_ID),
                eq(EmptyHttp2Headers.INSTANCE), eq(0), eq(promise));
    }

    @Test
    public void priorityWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        ChannelFuture future = encoder.writePriority(ctx, STREAM_ID, 0, (short) 255, true, promise);
        assertTrue(future.awaitUninterruptibly().cause() instanceof Http2Exception);
    }

    @Test
    public void priorityWriteShouldSetPriorityForStream() throws Exception {
        encoder.writePriority(ctx, STREAM_ID, 0, (short) 255, true, promise);
        verify(stream).setPriority(eq(0), eq((short) 255), eq(true));
        verify(writer).writePriority(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 255), eq(true), eq(promise));
    }

    @Test
    public void rstStreamWriteForUnknownStreamShouldIgnore() throws Exception {
        encoder.writeRstStream(ctx, 5, PROTOCOL_ERROR.code(), promise);
        verify(writer, never()).writeRstStream(eq(ctx), anyInt(), anyLong(), eq(promise));
    }

    @Test
    public void rstStreamWriteShouldCloseStream() throws Exception {
        encoder.writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code(), promise);
        verify(lifecycleManager).closeStream(eq(stream), eq(promise));
        verify(writer).writeRstStream(eq(ctx), eq(STREAM_ID), eq((long) PROTOCOL_ERROR.code()), eq(promise));
    }

    @Test
    public void pingWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        ChannelFuture future = encoder.writePing(ctx, false, emptyPingBuf(), promise);
        assertTrue(future.awaitUninterruptibly().cause() instanceof Http2Exception);
    }

    @Test
    public void pingWriteShouldSucceed() throws Exception {
        encoder.writePing(ctx, false, emptyPingBuf(), promise);
        verify(writer).writePing(eq(ctx), eq(false), eq(emptyPingBuf()), eq(promise));
    }

    @Test
    public void settingsWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        ChannelFuture future = encoder.writeSettings(ctx, new Http2Settings(), promise);
        assertTrue(future.awaitUninterruptibly().cause() instanceof Http2Exception);
    }

    @Test
    public void settingsWriteShouldNotUpdateSettings() throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.initialWindowSize(100);
        settings.pushEnabled(false);
        settings.maxConcurrentStreams(1000);
        settings.headerTableSize(2000);
        encoder.writeSettings(ctx, settings, promise);
        verify(writer).writeSettings(eq(ctx), eq(settings), eq(promise));
    }

    private static ByteBuf dummyData() {
        // The buffer is purposely 8 bytes so it will even work for a ping frame.
        return wrappedBuffer("abcdefgh".getBytes(UTF_8));
    }
}
