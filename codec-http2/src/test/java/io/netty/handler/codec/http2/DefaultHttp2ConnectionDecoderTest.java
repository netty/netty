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

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.emptyPingBuf;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_REMOTE;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
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
 * Tests for {@link DefaultHttp2ConnectionDecoder}.
 */
public class DefaultHttp2ConnectionDecoderTest {
    private static final int STREAM_ID = 1;
    private static final int PUSH_STREAM_ID = 2;

    private Http2ConnectionDecoder decoder;

    @Mock
    private Http2Connection connection;

    @Mock
    private Http2Connection.Endpoint remote;

    @Mock
    private Http2Connection.Endpoint local;

    @Mock
    private Http2InboundFlowController inboundFlow;

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
    private Http2FrameReader reader;

    @Mock
    private Http2ConnectionEncoder encoder;

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
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.newSucceededFuture()).thenReturn(future);
        when(ctx.newPromise()).thenReturn(promise);
        when(ctx.write(any())).thenReturn(future);

        decoder = DefaultHttp2ConnectionDecoder.newBuilder().connection(connection)
                        .frameReader(reader).inboundFlow(inboundFlow).encoder(encoder)
                        .listener(listener).lifecycleManager(lifecycleManager).build();

        // Simulate receiving the initial settings from the remote endpoint.
        decode().onSettingsRead(ctx, new Http2Settings());
        verify(listener).onSettingsRead(eq(ctx), eq(new Http2Settings()));
        assertTrue(decoder.prefaceReceived());
        verify(encoder).writeSettingsAck(eq(ctx), eq(promise));

        // Simulate receiving the SETTINGS ACK for the initial settings.
        decode().onSettingsAckRead(ctx);
    }

    @Test
    public void dataReadAfterGoAwayShouldApplyFlowControl() throws Exception {
        when(remote.isGoAwayReceived()).thenReturn(true);
        final ByteBuf data = dummyData();
        try {
            decode().onDataRead(ctx, STREAM_ID, data, 10, true);
            verify(inboundFlow).onDataRead(eq(ctx), eq(STREAM_ID), eq(data), eq(10), eq(true));

            // Verify that the event was absorbed and not propagated to the oberver.
            verify(listener, never()).onDataRead(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean());
        } finally {
            data.release();
        }
    }

    @Test
    public void dataReadWithEndOfStreamShouldCloseRemoteSide() throws Exception {
        final ByteBuf data = dummyData();
        try {
            decode().onDataRead(ctx, STREAM_ID, data, 10, true);
            verify(inboundFlow).onDataRead(eq(ctx), eq(STREAM_ID), eq(data), eq(10), eq(true));
            verify(lifecycleManager).closeRemoteSide(eq(stream), eq(future));
            verify(listener).onDataRead(eq(ctx), eq(STREAM_ID), eq(data), eq(10), eq(true));
        } finally {
            data.release();
        }
    }

    @Test
    public void headersReadAfterGoAwayShouldBeIgnored() throws Exception {
        when(remote.isGoAwayReceived()).thenReturn(true);
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(remote, never()).createStream(eq(STREAM_ID), eq(false));

        // Verify that the event was absorbed and not propagated to the oberver.
        verify(listener, never()).onHeadersRead(eq(ctx), anyInt(), any(Http2Headers.class), anyInt(), anyBoolean());
        verify(remote, never()).createStream(anyInt(), anyBoolean());
    }

    @Test
    public void headersReadForUnknownStreamShouldCreateStream() throws Exception {
        when(remote.createStream(eq(5), eq(false))).thenReturn(stream);
        decode().onHeadersRead(ctx, 5, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(remote).createStream(eq(5), eq(false));
        verify(listener).onHeadersRead(eq(ctx), eq(5), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false));
    }

    @Test
    public void headersReadForUnknownStreamShouldCreateHalfClosedStream() throws Exception {
        when(remote.createStream(eq(5), eq(true))).thenReturn(stream);
        decode().onHeadersRead(ctx, 5, EmptyHttp2Headers.INSTANCE, 0, true);
        verify(remote).createStream(eq(5), eq(true));
        verify(listener).onHeadersRead(eq(ctx), eq(5), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true));
    }

    @Test
    public void headersReadForPromisedStreamShouldHalfOpenStream() throws Exception {
        when(stream.state()).thenReturn(RESERVED_REMOTE);
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(stream).openForPush();
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false));
    }

    @Test
    public void headersReadForPromisedStreamShouldCloseStream() throws Exception {
        when(stream.state()).thenReturn(RESERVED_REMOTE);
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true);
        verify(stream).openForPush();
        verify(lifecycleManager).closeRemoteSide(eq(stream), eq(future));
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true));
    }

    @Test
    public void pushPromiseReadAfterGoAwayShouldBeIgnored() throws Exception {
        when(remote.isGoAwayReceived()).thenReturn(true);
        decode().onPushPromiseRead(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0);
        verify(remote, never()).reservePushStream(anyInt(), any(Http2Stream.class));
        verify(listener, never()).onPushPromiseRead(eq(ctx), anyInt(), anyInt(), any(Http2Headers.class), anyInt());
    }

    @Test
    public void pushPromiseReadShouldSucceed() throws Exception {
        decode().onPushPromiseRead(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0);
        verify(remote).reservePushStream(eq(PUSH_STREAM_ID), eq(stream));
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(PUSH_STREAM_ID),
                eq(EmptyHttp2Headers.INSTANCE), eq(0));
    }

    @Test
    public void priorityReadAfterGoAwayShouldBeIgnored() throws Exception {
        when(remote.isGoAwayReceived()).thenReturn(true);
        decode().onPriorityRead(ctx, STREAM_ID, 0, (short) 255, true);
        verify(stream, never()).setPriority(anyInt(), anyShort(), anyBoolean());
        verify(listener, never()).onPriorityRead(eq(ctx), anyInt(), anyInt(), anyShort(), anyBoolean());
    }

    @Test
    public void priorityReadShouldSucceed() throws Exception {
        decode().onPriorityRead(ctx, STREAM_ID, 0, (short) 255, true);
        verify(stream).setPriority(eq(0), eq((short) 255), eq(true));
        verify(listener).onPriorityRead(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 255), eq(true));
    }

    @Test
    public void windowUpdateReadAfterGoAwayShouldBeIgnored() throws Exception {
        when(remote.isGoAwayReceived()).thenReturn(true);
        decode().onWindowUpdateRead(ctx, STREAM_ID, 10);
        verify(encoder, never()).updateOutboundWindowSize(anyInt(), anyInt());
        verify(listener, never()).onWindowUpdateRead(eq(ctx), anyInt(), anyInt());
    }

    @Test(expected = Http2Exception.class)
    public void windowUpdateReadForUnknownStreamShouldThrow() throws Exception {
        when(connection.requireStream(5)).thenThrow(protocolError(""));
        decode().onWindowUpdateRead(ctx, 5, 10);
    }

    @Test
    public void windowUpdateReadShouldSucceed() throws Exception {
        decode().onWindowUpdateRead(ctx, STREAM_ID, 10);
        verify(encoder).updateOutboundWindowSize(eq(STREAM_ID), eq(10));
        verify(listener).onWindowUpdateRead(eq(ctx), eq(STREAM_ID), eq(10));
    }

    @Test
    public void rstStreamReadAfterGoAwayShouldSucceed() throws Exception {
        when(remote.isGoAwayReceived()).thenReturn(true);
        decode().onRstStreamRead(ctx, STREAM_ID, PROTOCOL_ERROR.code());
        verify(lifecycleManager).closeStream(eq(stream), eq(future));
        verify(listener).onRstStreamRead(eq(ctx), anyInt(), anyLong());
    }

    @Test(expected = Http2Exception.class)
    public void rstStreamReadForUnknownStreamShouldThrow() throws Exception {
        when(connection.requireStream(5)).thenThrow(protocolError(""));
        decode().onRstStreamRead(ctx, 5, PROTOCOL_ERROR.code());
    }

    @Test
    public void rstStreamReadShouldCloseStream() throws Exception {
        decode().onRstStreamRead(ctx, STREAM_ID, PROTOCOL_ERROR.code());
        verify(lifecycleManager).closeStream(eq(stream), eq(future));
        verify(listener).onRstStreamRead(eq(ctx), eq(STREAM_ID), eq((long) PROTOCOL_ERROR.code()));
    }

    @Test
    public void pingReadWithAckShouldNotifylistener() throws Exception {
        decode().onPingAckRead(ctx, emptyPingBuf());
        verify(listener).onPingAckRead(eq(ctx), eq(emptyPingBuf()));
    }

    @Test
    public void pingReadShouldReplyWithAck() throws Exception {
        decode().onPingRead(ctx, emptyPingBuf());
        verify(encoder).writePing(eq(ctx), eq(true), eq(emptyPingBuf()), eq(promise));
        verify(listener, never()).onPingAckRead(eq(ctx), any(ByteBuf.class));
    }

    @Test
    public void settingsReadWithAckShouldNotifylistener() throws Exception {
        decode().onSettingsAckRead(ctx);
        // Take into account the time this was called during setup().
        verify(listener, times(2)).onSettingsAckRead(eq(ctx));
    }

    @Test
    public void settingsReadShouldSetValues() throws Exception {
        when(connection.isServer()).thenReturn(true);
        Http2Settings settings = new Http2Settings();
        settings.pushEnabled(true);
        settings.initialWindowSize(123);
        settings.maxConcurrentStreams(456);
        settings.headerTableSize(789);
        decode().onSettingsRead(ctx, settings);
        verify(encoder).remoteSettings(settings);
        verify(listener).onSettingsRead(eq(ctx), eq(settings));
    }

    @Test
    public void goAwayShouldReadShouldUpdateConnectionState() throws Exception {
        decode().onGoAwayRead(ctx, 1, 2L, EMPTY_BUFFER);
        verify(local).goAwayReceived(1);
        verify(listener).onGoAwayRead(eq(ctx), eq(1), eq(2L), eq(EMPTY_BUFFER));
    }

    private static ByteBuf dummyData() {
        // The buffer is purposely 8 bytes so it will even work for a ping frame.
        return wrappedBuffer("abcdefgh".getBytes(UTF_8));
    }

    /**
     * Calls the decode method on the handler and gets back the captured internal listener
     */
    private Http2FrameListener decode() throws Exception {
        ArgumentCaptor<Http2FrameListener> internallistener = ArgumentCaptor.forClass(Http2FrameListener.class);
        doNothing().when(reader).readFrame(eq(ctx), any(ByteBuf.class), internallistener.capture());
        decoder.decodeFrame(ctx, EMPTY_BUFFER, Collections.emptyList());
        return internallistener.getValue();
    }
}

