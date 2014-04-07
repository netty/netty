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

package io.netty.handler.codec.http2.draft10.connection;

import static io.netty.handler.codec.http2.draft10.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.draft10.connection.Http2Stream.State.RESERVED_LOCAL;
import static io.netty.handler.codec.http2.draft10.connection.Http2Stream.State.RESERVED_REMOTE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.PING_FRAME_PAYLOAD_LENGTH;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.draft10.Http2Error;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.Http2Headers;
import io.netty.handler.codec.http2.draft10.Http2StreamException;
import io.netty.handler.codec.http2.draft10.connection.InboundFlowController.FrameWriter;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2PriorityFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2PushPromiseFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2RstStreamFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2WindowUpdateFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2PingFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2SettingsFrame;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link Http2ConnectionHandler}.
 */
public class Http2ConnectionHandlerTest {
    private static final int STREAM_ID = 1;
    private static final int PUSH_STREAM_ID = 2;

    private Http2ConnectionHandler handler;

    @Mock
    private Http2Connection connection;

    @Mock
    private Http2Connection.Endpoint remote;

    @Mock
    private Http2Connection.Endpoint local;

    @Mock
    private InboundFlowController inboundFlow;

    @Mock
    private OutboundFlowController outboundFlow;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private ChannelPromise promise;

    @Mock
    private ChannelFuture future;

    @Mock
    private Http2Stream stream;

    @Mock
    private Http2Stream pushStream;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.newSucceededFuture()).thenReturn(future);
        when(ctx.newPromise()).thenReturn(promise);
        when(channel.isActive()).thenReturn(true);
        when(stream.getId()).thenReturn(STREAM_ID);
        when(pushStream.getId()).thenReturn(PUSH_STREAM_ID);
        when(connection.getActiveStreams()).thenReturn(Arrays.asList(stream));
        when(connection.getStream(STREAM_ID)).thenReturn(stream);
        when(connection.getStreamOrFail(STREAM_ID)).thenReturn(stream);
        when(connection.local()).thenReturn(local);
        when(connection.remote()).thenReturn(remote);
        when(local.createStream(eq(STREAM_ID), anyInt(), anyBoolean())).thenReturn(stream);
        when(local.reservePushStream(eq(PUSH_STREAM_ID), eq(stream))).thenReturn(pushStream);
        when(remote.createStream(eq(STREAM_ID), anyInt(), anyBoolean())).thenReturn(stream);
        when(remote.reservePushStream(eq(PUSH_STREAM_ID), eq(stream))).thenReturn(pushStream);

        handler = new Http2ConnectionHandler(connection, inboundFlow, outboundFlow);
    }

    @Test
    public void closeShouldSendGoAway() throws Exception {
        handler.close(ctx, promise);
        verify(connection).sendGoAway(eq(ctx), eq(promise), isNull(Http2Exception.class));
    }

    @Test
    public void channelInactiveShouldCloseStreams() throws Exception {
        handler.channelInactive(ctx);
        verify(stream).close(eq(ctx), eq(future));
        verify(ctx).fireChannelInactive();
    }

    @Test
    public void streamErrorShouldCloseStream() throws Exception {
        Http2Exception e = new Http2StreamException(STREAM_ID, PROTOCOL_ERROR);
        handler.exceptionCaught(ctx, e);
        verify(stream).close(eq(ctx), eq(promise));
        verify(ctx).writeAndFlush(eq(createRstStreamFrame(STREAM_ID, PROTOCOL_ERROR)), eq(promise));
        verify(ctx).fireExceptionCaught(e);
    }

    @Test
    public void connectionErrorShouldSendGoAway() throws Exception {
        Http2Exception e = new Http2Exception(PROTOCOL_ERROR);
        handler.exceptionCaught(ctx, e);
        verify(connection).sendGoAway(eq(ctx), eq(promise), eq(e));
        verify(ctx).fireExceptionCaught(e);
    }

    @Test
    public void inboundDataAfterGoAwayShouldApplyFlowControl() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        ByteBuf data = Unpooled.copiedBuffer("Hello", UTF_8);
        Http2DataFrame frame =
                new DefaultHttp2DataFrame.Builder().setStreamId(STREAM_ID).setContent(data).build();
        handler.channelRead(ctx, frame);
        assertEquals(0, data.refCnt());
        verify(inboundFlow).applyInboundFlowControl(eq(frame), any(FrameWriter.class));
        verify(ctx, never()).fireChannelRead(frame);
    }

    @Test
    public void inboundDataWithEndOfStreamShouldCloseRemoteSide() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("Hello", UTF_8);
        Http2DataFrame frame =
                new DefaultHttp2DataFrame.Builder().setStreamId(STREAM_ID).setEndOfStream(true)
                        .setContent(data).build();
        handler.channelRead(ctx, frame);
        assertEquals(1, data.refCnt());
        verify(inboundFlow).applyInboundFlowControl(eq(frame), any(FrameWriter.class));
        verify(stream).closeRemoteSide(eq(ctx), eq(future));
        verify(ctx).fireChannelRead(frame);
        data.release();
    }

    @Test
    public void inboundDataShouldSucceed() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("Hello", UTF_8);
        Http2DataFrame frame =
                new DefaultHttp2DataFrame.Builder().setStreamId(STREAM_ID).setContent(data).build();
        handler.channelRead(ctx, frame);
        assertEquals(1, data.refCnt());
        verify(inboundFlow).applyInboundFlowControl(eq(frame), any(FrameWriter.class));
        verify(stream, never()).closeRemoteSide(eq(ctx), eq(future));
        verify(ctx).fireChannelRead(frame);
        data.release();
    }

    @Test
    public void inboundHeadersAfterGoAwayShouldBeIgnored() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        Http2Frame frame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(STREAM_ID).setPriority(1)
                        .setHeaders(Http2Headers.EMPTY_HEADERS).build();
        handler.channelRead(ctx, frame);
        verify(remote, never()).createStream(eq(STREAM_ID), eq(1), eq(false));
        verify(ctx, never()).fireChannelRead(frame);
    }

    @Test
    public void inboundHeadersShouldCreateStream() throws Exception {
        int newStreamId = 5;
        Http2Frame frame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(newStreamId).setPriority(1)
                        .setHeaders(Http2Headers.EMPTY_HEADERS).build();
        handler.channelRead(ctx, frame);
        verify(remote).createStream(eq(newStreamId), eq(1), eq(false));
        verify(ctx).fireChannelRead(frame);
    }

    @Test
    public void inboundHeadersWithEndOfStreamShouldCreateHalfClosedStream() throws Exception {
        int newStreamId = 5;
        Http2Frame frame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(newStreamId).setPriority(1)
                        .setEndOfStream(true).setHeaders(Http2Headers.EMPTY_HEADERS)
                        .build();
        handler.channelRead(ctx, frame);
        verify(remote).createStream(eq(newStreamId), eq(1), eq(true));
        verify(ctx).fireChannelRead(frame);
    }

    @Test
    public void inboundHeadersForPromisedStreamShouldHalfOpenStream() throws Exception {
        when(stream.getState()).thenReturn(RESERVED_REMOTE);
        Http2Frame frame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(STREAM_ID).setPriority(1)
                        .setHeaders(Http2Headers.EMPTY_HEADERS).build();
        handler.channelRead(ctx, frame);
        verify(stream).openForPush();
        verify(ctx).fireChannelRead(frame);
    }

    @Test
    public void inboundHeadersForPromisedStreamShouldCloseStream() throws Exception {
        when(stream.getState()).thenReturn(RESERVED_REMOTE);
        Http2Frame frame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(STREAM_ID).setPriority(1)
                        .setEndOfStream(true).setHeaders(Http2Headers.EMPTY_HEADERS)
                        .build();
        handler.channelRead(ctx, frame);
        verify(stream).openForPush();
        verify(stream).closeRemoteSide(ctx, future);
        verify(ctx).fireChannelRead(frame);
    }

    @Test
    public void inboundPushPromiseAfterGoAwayShouldBeIgnored() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        Http2Frame frame =
                new DefaultHttp2PushPromiseFrame.Builder().setStreamId(STREAM_ID)
                        .setPromisedStreamId(PUSH_STREAM_ID)
                        .setHeaders(Http2Headers.EMPTY_HEADERS).build();
        handler.channelRead(ctx, frame);
        verify(remote, never()).reservePushStream(eq(PUSH_STREAM_ID), eq(stream));
        verify(ctx, never()).fireChannelRead(frame);
    }

    @Test
    public void inboundPushPromiseShouldSucceed() throws Exception {
        Http2Frame frame =
                new DefaultHttp2PushPromiseFrame.Builder().setStreamId(STREAM_ID)
                        .setPromisedStreamId(PUSH_STREAM_ID)
                        .setHeaders(Http2Headers.EMPTY_HEADERS).build();
        handler.channelRead(ctx, frame);
        verify(remote).reservePushStream(eq(PUSH_STREAM_ID), eq(stream));
        verify(ctx).fireChannelRead(frame);
    }

    @Test
    public void inboundPriorityAfterGoAwayShouldBeIgnored() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        Http2Frame frame =
                new DefaultHttp2PriorityFrame.Builder().setStreamId(STREAM_ID).setPriority(100)
                        .build();
        handler.channelRead(ctx, frame);
        verify(stream, never()).setPriority(eq(100));
        verify(ctx, never()).fireChannelRead(frame);
    }

    @Test
    public void inboundPriorityForUnknownStreamShouldBeIgnored() throws Exception {
        Http2Frame frame =
                new DefaultHttp2PriorityFrame.Builder().setStreamId(5).setPriority(100).build();
        handler.channelRead(ctx, frame);
        verify(stream, never()).setPriority(eq(100));
        verify(ctx, never()).fireChannelRead(frame);
    }

    @Test
    public void inboundPriorityShouldSucceed() throws Exception {
        Http2Frame frame =
                new DefaultHttp2PriorityFrame.Builder().setStreamId(STREAM_ID).setPriority(100)
                        .build();
        handler.channelRead(ctx, frame);
        verify(stream).setPriority(eq(100));
        verify(ctx).fireChannelRead(frame);
    }

    @Test
    public void inboundWindowUpdateAfterGoAwayShouldBeIgnored() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        Http2Frame frame =
                new DefaultHttp2WindowUpdateFrame.Builder().setStreamId(STREAM_ID)
                        .setWindowSizeIncrement(10).build();
        handler.channelRead(ctx, frame);
        verify(outboundFlow, never()).updateOutboundWindowSize(eq(STREAM_ID), eq(10));
        verify(ctx, never()).fireChannelRead(frame);
    }

    @Test
    public void inboundWindowUpdateForUnknownStreamShouldBeIgnored() throws Exception {
        Http2Frame frame =
                new DefaultHttp2WindowUpdateFrame.Builder().setStreamId(5)
                        .setWindowSizeIncrement(10).build();
        handler.channelRead(ctx, frame);
        verify(outboundFlow, never()).updateOutboundWindowSize(eq(5), eq(10));
        verify(ctx, never()).fireChannelRead(frame);
    }

    @Test
    public void inboundWindowUpdateShouldSucceed() throws Exception {
        Http2Frame frame =
                new DefaultHttp2WindowUpdateFrame.Builder().setStreamId(STREAM_ID)
                        .setWindowSizeIncrement(10).build();
        handler.channelRead(ctx, frame);
        verify(outboundFlow).updateOutboundWindowSize(eq(STREAM_ID), eq(10));
        verify(ctx).fireChannelRead(frame);
    }

    @Test
    public void inboundRstStreamAfterGoAwayShouldBeIgnored() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        Http2Frame frame =
                new DefaultHttp2RstStreamFrame.Builder().setStreamId(STREAM_ID)
                        .setErrorCode(PROTOCOL_ERROR.getCode()).build();
        handler.channelRead(ctx, frame);
        verify(stream, never()).close(eq(ctx), eq(future));
        verify(ctx, never()).fireChannelRead(frame);
    }

    @Test
    public void inboundRstStreamForUnknownStreamShouldBeIgnored() throws Exception {
        Http2Frame frame =
                new DefaultHttp2RstStreamFrame.Builder().setStreamId(5)
                        .setErrorCode(PROTOCOL_ERROR.getCode()).build();
        handler.channelRead(ctx, frame);
        verify(stream, never()).close(eq(ctx), eq(future));
        verify(ctx, never()).fireChannelRead(frame);
    }

    @Test
    public void inboundRstStreamShouldCloseStream() throws Exception {
        Http2Frame frame =
                new DefaultHttp2RstStreamFrame.Builder().setStreamId(STREAM_ID)
                        .setErrorCode(PROTOCOL_ERROR.getCode()).build();
        handler.channelRead(ctx, frame);
        verify(stream).close(eq(ctx), eq(future));
        verify(ctx).fireChannelRead(frame);
    }

    @Test
    public void inboundPingWithAckShouldFireRead() throws Exception {
        ByteBuf data = Unpooled.wrappedBuffer(new byte[PING_FRAME_PAYLOAD_LENGTH]);
        Http2Frame frame = new DefaultHttp2PingFrame.Builder().setData(data).setAck(true).build();
        handler.channelRead(ctx, frame);
        verify(ctx, never()).writeAndFlush(any(Http2PingFrame.class));
        verify(ctx).fireChannelRead(frame);
    }

    @Test
    public void inboundPingWithoutAckShouldReplyWithAck() throws Exception {
        ByteBuf data = Unpooled.wrappedBuffer(new byte[PING_FRAME_PAYLOAD_LENGTH]);
        Http2Frame frame = new DefaultHttp2PingFrame.Builder().setData(data).build();
        Http2Frame ack = new DefaultHttp2PingFrame.Builder().setData(data).setAck(true).build();
        handler.channelRead(ctx, frame);
        verify(ctx).writeAndFlush(eq(ack));
        verify(ctx, never()).fireChannelRead(frame);
    }

    @Test
    public void inboundSettingsWithAckShouldFireRead() throws Exception {
        Http2Frame frame = new DefaultHttp2SettingsFrame.Builder().setAck(true).build();
        handler.channelRead(ctx, frame);
        verify(remote, never()).setPushToAllowed(anyBoolean());
        verify(remote, never()).setMaxStreams(anyInt());
        verify(outboundFlow, never()).setInitialOutboundWindowSize(anyInt());
        verify(ctx).fireChannelRead(frame);
        verify(ctx, never()).writeAndFlush(any(Http2SettingsFrame.class));
    }

    @Test
    public void inboundSettingsShouldSetValues() throws Exception {
        Http2Frame frame =
                new DefaultHttp2SettingsFrame.Builder().setPushEnabled(true)
                        .setMaxConcurrentStreams(10).setInitialWindowSize(20).build();
        handler.channelRead(ctx, frame);
        verify(remote).setPushToAllowed(true);
        verify(local).setMaxStreams(10);
        verify(outboundFlow).setInitialOutboundWindowSize(20);
        verify(ctx, never()).fireChannelRead(frame);
        verify(ctx).writeAndFlush(eq(new DefaultHttp2SettingsFrame.Builder().setAck(true).build()));
    }

    @Test
    public void inboundGoAwayShouldUpdateConnectionState() throws Exception {
        Http2Frame frame =
                new DefaultHttp2GoAwayFrame.Builder().setLastStreamId(1).setErrorCode(2).build();
        handler.channelRead(ctx, frame);
        verify(connection).goAwayReceived();
        verify(ctx).fireChannelRead(frame);
    }

    @Test
    public void outboundDataAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        ByteBuf data = Unpooled.copiedBuffer("Hello", UTF_8);
        Http2DataFrame frame =
                new DefaultHttp2DataFrame.Builder().setStreamId(STREAM_ID).setContent(data).build();
        handler.write(ctx, frame, promise);
        verify(outboundFlow, never()).sendFlowControlled(eq(frame),
                any(OutboundFlowController.FrameWriter.class));
        verify(promise).setFailure(any(Http2Exception.class));
        assertEquals(0, frame.refCnt());
    }

    @Test
    public void outboundDataShouldApplyFlowControl() throws Exception {
        ByteBuf data = Unpooled.copiedBuffer("Hello", UTF_8);
        Http2DataFrame frame =
                new DefaultHttp2DataFrame.Builder().setStreamId(STREAM_ID).setContent(data).build();
        handler.write(ctx, frame, promise);
        verify(outboundFlow).sendFlowControlled(eq(frame),
                any(OutboundFlowController.FrameWriter.class));
        verify(promise, never()).setFailure(any(Http2Exception.class));
        assertEquals(1, frame.refCnt());
        frame.release();
    }

    @Test
    public void outboundHeadersAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        Http2Frame frame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(STREAM_ID).setPriority(1)
                        .setHeaders(Http2Headers.EMPTY_HEADERS).build();
        handler.write(ctx, frame, promise);
        verify(promise).setFailure(any(Http2Exception.class));
        verify(ctx, never()).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundHeadersShouldCreateOpenStream() throws Exception {
        int newStreamId = 5;
        Http2Frame frame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(newStreamId).setPriority(1)
                        .setHeaders(Http2Headers.EMPTY_HEADERS).build();
        handler.write(ctx, frame, promise);
        verify(local).createStream(eq(newStreamId), eq(1), eq(false));
        verify(promise, never()).setFailure(any(Http2Exception.class));
        verify(ctx).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundHeadersShouldCreateHalfClosedStream() throws Exception {
        int newStreamId = 5;
        Http2Frame frame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(newStreamId).setPriority(1)
                        .setEndOfStream(true).setHeaders(Http2Headers.EMPTY_HEADERS)
                        .build();
        handler.write(ctx, frame, promise);
        verify(local).createStream(eq(newStreamId), eq(1), eq(true));
        verify(promise, never()).setFailure(any(Http2Exception.class));
        verify(ctx).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundHeadersShouldOpenStreamForPush() throws Exception {
        when(stream.getState()).thenReturn(RESERVED_LOCAL);
        Http2Frame frame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(STREAM_ID).setPriority(1)
                        .setHeaders(Http2Headers.EMPTY_HEADERS).build();
        handler.write(ctx, frame, promise);
        verify(stream).openForPush();
        verify(stream, never()).closeLocalSide(eq(ctx), eq(future));
        verify(promise, never()).setFailure(any(Http2Exception.class));
        verify(ctx).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundHeadersShouldClosePushStream() throws Exception {
        when(stream.getState()).thenReturn(RESERVED_LOCAL);
        Http2Frame frame =
                new DefaultHttp2HeadersFrame.Builder().setStreamId(STREAM_ID).setPriority(1)
                        .setEndOfStream(true).setHeaders(Http2Headers.EMPTY_HEADERS)
                        .build();
        handler.write(ctx, frame, promise);
        verify(stream).openForPush();
        verify(stream).closeLocalSide(eq(ctx), eq(promise));
        verify(promise, never()).setFailure(any(Http2Exception.class));
        verify(ctx).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundPushPromiseAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        Http2Frame frame =
                new DefaultHttp2PushPromiseFrame.Builder().setStreamId(STREAM_ID)
                        .setPromisedStreamId(PUSH_STREAM_ID)
                        .setHeaders(Http2Headers.EMPTY_HEADERS).build();
        handler.write(ctx, frame, promise);
        verify(promise).setFailure(any(Http2Exception.class));
        verify(local, never()).reservePushStream(eq(PUSH_STREAM_ID), eq(stream));
        verify(ctx, never()).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundPushPromiseShouldReserveStream() throws Exception {
        Http2Frame frame =
                new DefaultHttp2PushPromiseFrame.Builder().setStreamId(STREAM_ID)
                        .setPromisedStreamId(PUSH_STREAM_ID)
                        .setHeaders(Http2Headers.EMPTY_HEADERS).build();
        handler.write(ctx, frame, promise);
        verify(promise, never()).setFailure(any(Http2Exception.class));
        verify(local).reservePushStream(eq(PUSH_STREAM_ID), eq(stream));
        verify(ctx).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundPriorityAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        Http2Frame frame =
                new DefaultHttp2PriorityFrame.Builder().setStreamId(STREAM_ID).setPriority(10)
                        .build();
        handler.write(ctx, frame, promise);
        verify(promise).setFailure(any(Http2Exception.class));
        verify(stream, never()).setPriority(10);
        verify(ctx, never()).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundPriorityShouldSetPriorityOnStream() throws Exception {
        Http2Frame frame =
                new DefaultHttp2PriorityFrame.Builder().setStreamId(STREAM_ID).setPriority(10)
                        .build();
        handler.write(ctx, frame, promise);
        verify(promise, never()).setFailure(any(Http2Exception.class));
        verify(stream).setPriority(10);
        verify(ctx).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundRstStreamForUnknownStreamShouldIgnore() throws Exception {
        Http2Frame frame =
                new DefaultHttp2RstStreamFrame.Builder().setStreamId(5).setErrorCode(1).build();
        handler.write(ctx, frame, promise);
        verify(promise, never()).setFailure(any(Http2Exception.class));
        verify(promise).setSuccess();
        verify(ctx, never()).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundRstStreamShouldCloseStream() throws Exception {
        Http2Frame frame =
                new DefaultHttp2RstStreamFrame.Builder().setStreamId(STREAM_ID).setErrorCode(1)
                        .build();
        handler.write(ctx, frame, promise);
        verify(promise, never()).setFailure(any(Http2Exception.class));
        verify(promise, never()).setSuccess();
        verify(stream).close(ctx, promise);
        verify(ctx).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundPingAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        ByteBuf data = Unpooled.wrappedBuffer(new byte[PING_FRAME_PAYLOAD_LENGTH]);
        Http2Frame frame = new DefaultHttp2PingFrame.Builder().setData(data).build();
        handler.write(ctx, frame, promise);
        verify(promise).setFailure(any(Http2Exception.class));
        verify(ctx, never()).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundPingWithAckShouldFail() throws Exception {
        ByteBuf data = Unpooled.wrappedBuffer(new byte[PING_FRAME_PAYLOAD_LENGTH]);
        Http2Frame frame = new DefaultHttp2PingFrame.Builder().setAck(true).setData(data).build();
        handler.write(ctx, frame, promise);
        verify(promise).setFailure(any(Http2Exception.class));
        verify(ctx, never()).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundPingWithAckShouldSend() throws Exception {
        ByteBuf data = Unpooled.wrappedBuffer(new byte[PING_FRAME_PAYLOAD_LENGTH]);
        Http2Frame frame = new DefaultHttp2PingFrame.Builder().setData(data).build();
        handler.write(ctx, frame, promise);
        verify(promise, never()).setFailure(any(Http2Exception.class));
        verify(ctx).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundGoAwayShouldFail() throws Exception {
        Http2Frame frame =
                new DefaultHttp2GoAwayFrame.Builder().setLastStreamId(0).setErrorCode(1).build();
        handler.write(ctx, frame, promise);
        verify(promise).setFailure(any(Http2Exception.class));
    }

    @Test
    public void outboundWindowUpdateShouldFail() throws Exception {
        Http2Frame frame =
                new DefaultHttp2WindowUpdateFrame.Builder().setStreamId(STREAM_ID)
                        .setWindowSizeIncrement(1).build();
        handler.write(ctx, frame, promise);
        verify(promise).setFailure(any(Http2Exception.class));
    }

    @Test
    public void outboundSettingsAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        Http2Frame frame =
                new DefaultHttp2SettingsFrame.Builder().setInitialWindowSize(10)
                        .setMaxConcurrentStreams(20).setPushEnabled(true).build();
        handler.write(ctx, frame, promise);
        verify(promise).setFailure(any(Http2Exception.class));
        verify(local, never()).setPushToAllowed(anyBoolean());
        verify(remote, never()).setMaxStreams(anyInt());
        verify(inboundFlow, never()).setInitialInboundWindowSize(anyInt());
        verify(ctx, never()).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundSettingsWithAckShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        Http2Frame frame = new DefaultHttp2SettingsFrame.Builder().setAck(true).build();
        handler.write(ctx, frame, promise);
        verify(promise).setFailure(any(Http2Exception.class));
        verify(local, never()).setPushToAllowed(anyBoolean());
        verify(remote, never()).setMaxStreams(anyInt());
        verify(inboundFlow, never()).setInitialInboundWindowSize(anyInt());
        verify(ctx, never()).writeAndFlush(frame, promise);
    }

    @Test
    public void outboundSettingsShouldUpdateSettings() throws Exception {
        Http2Frame frame =
                new DefaultHttp2SettingsFrame.Builder().setInitialWindowSize(10)
                        .setMaxConcurrentStreams(20).setPushEnabled(true).build();
        handler.write(ctx, frame, promise);
        verify(promise, never()).setFailure(any(Http2Exception.class));
        verify(local).setPushToAllowed(eq(true));
        verify(remote).setMaxStreams(eq(20));
        verify(inboundFlow).setInitialInboundWindowSize(eq(10));
        verify(ctx).writeAndFlush(frame, promise);
    }

    private DefaultHttp2RstStreamFrame createRstStreamFrame(int streamId, Http2Error error) {
        return new DefaultHttp2RstStreamFrame.Builder().setStreamId(streamId)
                .setErrorCode(error.getCode()).build();
    }
}
