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
import static io.netty.handler.codec.http2.Http2Error.STREAM_CLOSED;
import static io.netty.handler.codec.http2.Http2Stream.State.CLOSED;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.concurrent.GenericFutureListener;

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
    private static final int NON_EXISTANT_STREAM_ID = 13;

    private Http2ConnectionHandler handler;
    private ChannelPromise promise;

    @Mock
    private Http2Connection connection;

    @Mock
    private Http2Connection.Endpoint<Http2RemoteFlowController> remote;

    @Mock
    private Http2Connection.Endpoint<Http2LocalFlowController> local;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private ChannelFuture future;

    @Mock
    private Http2Stream stream;

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

        when(encoder.connection()).thenReturn(connection);
        when(decoder.connection()).thenReturn(connection);
        when(encoder.frameWriter()).thenReturn(frameWriter);
        when(frameWriter.writeGoAway(eq(ctx), anyInt(), anyInt(), any(ByteBuf.class), eq(promise))).thenReturn(future);
        when(channel.isActive()).thenReturn(true);
        when(connection.remote()).thenReturn(remote);
        when(connection.local()).thenReturn(local);
        doAnswer(new Answer<Http2Stream>() {
            @Override
            public Http2Stream answer(InvocationOnMock in) throws Throwable {
                Http2StreamVisitor visitor = in.getArgumentAt(0, Http2StreamVisitor.class);
                if (!visitor.visit(stream)) {
                    return stream;
                }
                return null;
            }
        }).when(connection).forEachActiveStream(any(Http2StreamVisitor.class));
        when(connection.stream(NON_EXISTANT_STREAM_ID)).thenReturn(null);
        when(connection.numActiveStreams()).thenReturn(1);
        when(connection.stream(STREAM_ID)).thenReturn(stream);
        when(stream.open(anyBoolean())).thenReturn(stream);
        when(encoder.writeSettings(eq(ctx), any(Http2Settings.class), eq(promise))).thenReturn(future);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.newSucceededFuture()).thenReturn(future);
        when(ctx.newPromise()).thenReturn(promise);
        when(ctx.write(any())).thenReturn(future);
    }

    private Http2ConnectionHandler newHandler() throws Exception {
        Http2ConnectionHandler handler = new Http2ConnectionHandler(decoder, encoder);
        handler.handlerAdded(ctx);
        return handler;
    }

    @After
    public void tearDown() throws Exception {
        if (handler != null) {
            handler.handlerRemoved(ctx);
        }
    }

    @Test
    public void clientShouldSendClientPrefaceStringWhenActive() throws Exception {
        when(connection.isServer()).thenReturn(false);
        when(channel.isActive()).thenReturn(false);
        handler = newHandler();
        when(channel.isActive()).thenReturn(true);
        handler.channelActive(ctx);
        verify(ctx).write(eq(connectionPrefaceBuf()));
    }

    @Test
    public void serverShouldNotSendClientPrefaceStringWhenActive() throws Exception {
        when(connection.isServer()).thenReturn(true);
        when(channel.isActive()).thenReturn(false);
        handler = newHandler();
        when(channel.isActive()).thenReturn(true);
        handler.channelActive(ctx);
        verify(ctx, never()).write(eq(connectionPrefaceBuf()));
    }

    @Test
    public void serverReceivingInvalidClientPrefaceStringShouldHandleException() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        handler.channelRead(ctx, copiedBuffer("BAD_PREFACE", UTF_8));
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeGoAway(eq(ctx), eq(0), eq(PROTOCOL_ERROR.code()),
                captor.capture(), eq(promise));
        captor.getValue().release();
    }

    @Test
    public void serverReceivingValidClientPrefaceStringShouldContinueReadingFrames() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        ByteBuf preface = connectionPrefaceBuf();
        ByteBuf prefacePlusSome = Unpooled.wrappedBuffer(new byte[preface.readableBytes() + 1]);
        prefacePlusSome.resetWriterIndex().writeBytes(preface).writeByte(0);
        handler.channelRead(ctx, prefacePlusSome);
        verify(decoder, times(2)).decodeFrame(eq(ctx), any(ByteBuf.class), Matchers.<List<Object>>any());
    }

    @Test
    public void serverReceivingValidClientPrefaceStringShouldOnlyReadWholeFrame() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        handler.channelRead(ctx, connectionPrefaceBuf());
        verify(decoder).decodeFrame(any(ChannelHandlerContext.class),
                any(ByteBuf.class), Matchers.<List<Object>>any());
    }

    @Test
    public void verifyChannelHandlerCanBeReusedInPipeline() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        // Only read the connection preface...after preface is read internal state of Http2ConnectionHandler
        // is expected to change relative to the pipeline.
        ByteBuf preface = connectionPrefaceBuf();
        verify(decoder, never()).decodeFrame(any(ChannelHandlerContext.class),
                any(ByteBuf.class), Matchers.<List<Object>>any());

        // Now remove and add the handler...this is setting up the test condition.
        handler.handlerRemoved(ctx);
        handler.handlerAdded(ctx);

        // Now verify we can continue as normal, reading connection preface plus more.
        ByteBuf prefacePlusSome = Unpooled.wrappedBuffer(new byte[preface.readableBytes() + 1]);
        prefacePlusSome.resetWriterIndex().writeBytes(preface).writeByte(0);
        handler.channelRead(ctx, prefacePlusSome);
        verify(decoder, times(2)).decodeFrame(eq(ctx), any(ByteBuf.class), Matchers.<List<Object>>any());
    }

    @Test
    public void channelInactiveShouldCloseStreams() throws Exception {
        handler = newHandler();
        handler.channelInactive(ctx);
        verify(stream).close();
    }

    @Test
    public void connectionErrorShouldStartShutdown() throws Exception {
        handler = newHandler();
        Http2Exception e = new Http2Exception(PROTOCOL_ERROR);
        when(remote.lastStreamCreated()).thenReturn(STREAM_ID);
        handler.exceptionCaught(ctx, e);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(PROTOCOL_ERROR.code()),
                captor.capture(), eq(promise));
        captor.getValue().release();
    }

    @Test
    public void encoderAndDecoderAreClosedOnChannelInactive() throws Exception {
        handler = newHandler();
        handler.channelActive(ctx);
        when(channel.isActive()).thenReturn(false);
        handler.channelInactive(ctx);
        verify(encoder).close();
        verify(decoder).close();
    }

    @Test
    public void writeRstOnNonExistantStreamShouldSucceed() throws Exception {
        handler = newHandler();
        handler.resetStream(ctx, NON_EXISTANT_STREAM_ID, STREAM_CLOSED.code(), promise);
        verify(frameWriter, never())
            .writeRstStream(any(ChannelHandlerContext.class), anyInt(), anyLong(), any(ChannelPromise.class));
        assertTrue(promise.isDone());
        assertTrue(promise.isSuccess());
        assertNull(promise.cause());
    }

    @Test
    public void writeRstOnClosedStreamShouldSucceed() throws Exception {
        handler = newHandler();
        when(frameWriter.writeRstStream(eq(ctx), eq(STREAM_ID),
                anyLong(), any(ChannelPromise.class))).thenReturn(future);
        when(stream.state()).thenReturn(CLOSED);
        // The stream is "closed" but is still known about by the connection (connection().stream(..)
        // will return the stream). We should still write a RST_STREAM frame in this scenario.
        handler.resetStream(ctx, STREAM_ID, STREAM_CLOSED.code(), promise);
        verify(frameWriter).writeRstStream(eq(ctx), eq(STREAM_ID), anyLong(), any(ChannelPromise.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void closeListenerShouldBeNotifiedOnlyOneTime() throws Exception {
        handler = newHandler();
        when(future.isDone()).thenReturn(true);
        when(future.isSuccess()).thenReturn(true);
        doAnswer(new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                GenericFutureListener<ChannelFuture> listener = (GenericFutureListener<ChannelFuture>) args[0];
                // Simulate that all streams have become inactive by the time the future completes.
                doAnswer(new Answer<Http2Stream>() {
                    @Override
                    public Http2Stream answer(InvocationOnMock in) throws Throwable {
                        return null;
                    }
                }).when(connection).forEachActiveStream(any(Http2StreamVisitor.class));
                when(connection.numActiveStreams()).thenReturn(0);
                // Simulate the future being completed.
                listener.operationComplete(future);
                return future;
            }
        }).when(future).addListener(any(GenericFutureListener.class));
        handler.close(ctx, promise);
        handler.closeStream(stream, future);
        // Simulate another stream close call being made after the context should already be closed.
        handler.closeStream(stream, future);
        verify(ctx, times(1)).close(any(ChannelPromise.class));
    }

    public void canSendGoAwayFrame() throws Exception {
        handler = newHandler();
        ByteBuf data = mock(ByteBuf.class);
        long errorCode = Http2Error.INTERNAL_ERROR.code();
        handler.goAway(ctx, STREAM_ID, errorCode, data, promise);

        verify(connection).goAwaySent(eq(STREAM_ID), eq(errorCode), eq(data));
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(errorCode), eq(data), eq(promise));
    }

    @Test
    public void canSendGoAwayFramesWithDecreasingLastStreamIds() throws Exception {
        handler = newHandler();
        ByteBuf data = mock(ByteBuf.class);
        long errorCode = Http2Error.INTERNAL_ERROR.code();

        handler.goAway(ctx, STREAM_ID + 2, errorCode, data, promise);
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID + 2), eq(errorCode), eq(data), eq(promise));
        verify(connection).goAwaySent(eq(STREAM_ID + 2), eq(errorCode), eq(data));
        handler.goAway(ctx, STREAM_ID, errorCode, data, promise);
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(errorCode), eq(data), eq(promise));
        verify(connection).goAwaySent(eq(STREAM_ID), eq(errorCode), eq(data));
    }

    @Test
    public void cannotSendGoAwayFrameWithIncreasingLastStreamIds() throws Exception {
        handler = newHandler();
        ByteBuf data = mock(ByteBuf.class);
        long errorCode = Http2Error.INTERNAL_ERROR.code();

        handler.goAway(ctx, STREAM_ID, errorCode, data, promise);
        verify(connection).goAwaySent(eq(STREAM_ID), eq(errorCode), eq(data));
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(errorCode), eq(data), eq(promise));
        // The frameWriter is only mocked, so it should not have interacted with the promise.
        assertFalse(promise.isDone());

        when(connection.goAwaySent()).thenReturn(true);
        when(remote.lastKnownStream()).thenReturn(STREAM_ID);
        handler.goAway(ctx, STREAM_ID + 2, errorCode, data, promise);
        assertTrue(promise.isDone());
        assertFalse(promise.isSuccess());
        verify(data).release();
        verifyNoMoreInteractions(frameWriter);
    }
}
