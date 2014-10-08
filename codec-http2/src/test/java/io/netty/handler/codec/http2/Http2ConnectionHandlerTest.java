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

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
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
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link Http2ConnectionHandler}
 */
public class Http2ConnectionHandlerTest {
    private static final int STREAM_ID = 1;

    private Http2ConnectionHandler handler;

    @Mock
    private Http2Connection connection;

    @Mock
    private Http2Connection.Endpoint remote;

    @Mock
    private Http2Connection.Endpoint local;

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
    private Http2ConnectionDecoder.Builder decoderBuilder;

    @Mock
    private Http2ConnectionEncoder.Builder encoderBuilder;

    @Mock
    private Http2ConnectionDecoder decoder;

    @Mock
    private Http2ConnectionEncoder encoder;

    @Mock
    private Http2FrameWriter frameWriter;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        promise = new DefaultChannelPromise(channel);

        when(encoderBuilder.build()).thenReturn(encoder);
        when(decoderBuilder.build()).thenReturn(decoder);
        when(encoder.connection()).thenReturn(connection);
        when(decoder.connection()).thenReturn(connection);
        when(encoder.frameWriter()).thenReturn(frameWriter);
        when(frameWriter.writeGoAway(eq(ctx), anyInt(), anyInt(), any(ByteBuf.class), eq(promise))).thenReturn(future);
        when(channel.isActive()).thenReturn(true);
        when(connection.remote()).thenReturn(remote);
        when(connection.local()).thenReturn(local);
        when(connection.activeStreams()).thenReturn(Collections.singletonList(stream));
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
        when(encoder.writeSettings(eq(ctx), any(Http2Settings.class), eq(promise))).thenReturn(future);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.newSucceededFuture()).thenReturn(future);
        when(ctx.newPromise()).thenReturn(promise);
        when(ctx.write(any())).thenReturn(future);

        handler = newHandler();
    }

    private Http2ConnectionHandler newHandler() {
        return new Http2ConnectionHandler(decoderBuilder, encoderBuilder);
    }

    @After
    public void tearDown() throws Exception {
        handler.handlerRemoved(ctx);
    }

    @Test
    public void clientShouldSendClientPrefaceStringWhenActive() throws Exception {
        when(connection.isServer()).thenReturn(false);
        handler.channelActive(ctx);
        verify(ctx).write(eq(connectionPrefaceBuf()));
    }

    @Test
    public void serverShouldNotSendClientPrefaceStringWhenActive() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler.channelActive(ctx);
        verify(ctx, never()).write(eq(connectionPrefaceBuf()));
    }

    @Test
    public void serverReceivingInvalidClientPrefaceStringShouldHandleException() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        handler.channelRead(ctx, copiedBuffer("BAD_PREFACE", UTF_8));
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeGoAway(eq(ctx), eq(0), eq((long) PROTOCOL_ERROR.code()),
                captor.capture(), eq(promise));
        captor.getValue().release();
    }

    @Test
    public void serverReceivingValidClientPrefaceStringShouldContinueReadingFrames() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler.channelRead(ctx, connectionPrefaceBuf());
        verify(decoder).decodeFrame(eq(ctx), any(ByteBuf.class), Matchers.<List<Object>>any());
    }

    @Test
    public void channelInactiveShouldCloseStreams() throws Exception {
        handler.channelInactive(ctx);
        verify(stream).close();
    }

    @Test
    public void connectionErrorShouldStartShutdown() throws Exception {
        Http2Exception e = new Http2Exception(PROTOCOL_ERROR);
        when(remote.lastStreamCreated()).thenReturn(STREAM_ID);
        handler.exceptionCaught(ctx, e);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq((long) PROTOCOL_ERROR.code()),
                captor.capture(), eq(promise));
        captor.getValue().release();
    }
}
