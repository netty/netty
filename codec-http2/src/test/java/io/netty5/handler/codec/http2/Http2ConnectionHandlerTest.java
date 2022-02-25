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

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.DefaultChannelConfig;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http2.Http2Exception.ShutdownHint;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty5.buffer.Unpooled.copiedBuffer;
import static io.netty5.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;
import static io.netty5.handler.codec.http2.Http2Error.CANCEL;
import static io.netty5.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty5.handler.codec.http2.Http2Error.STREAM_CLOSED;
import static io.netty5.handler.codec.http2.Http2Stream.State.CLOSED;
import static io.netty5.handler.codec.http2.Http2Stream.State.IDLE;
import static io.netty5.util.CharsetUtil.US_ASCII;
import static io.netty5.util.CharsetUtil.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link Http2ConnectionHandler}
 */
@SuppressWarnings("unchecked")
public class Http2ConnectionHandlerTest {
    private static final int STREAM_ID = 1;
    private static final int NON_EXISTANT_STREAM_ID = 13;

    private Http2ConnectionHandler handler;
    private Promise<Void> promise;

    @Mock
    private Http2Connection connection;

    @Mock
    private Http2RemoteFlowController remoteFlow;

    @Mock
    private Http2LocalFlowController localFlow;

    @Mock
    private Http2Connection.Endpoint<Http2RemoteFlowController> remote;

    @Mock
    private Http2RemoteFlowController remoteFlowController;

    @Mock
    private Http2Connection.Endpoint<Http2LocalFlowController> local;

    @Mock
    private Http2LocalFlowController localFlowController;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private EventExecutor executor;

    @Mock
    private Channel channel;

    @Mock
    private ChannelPipeline pipeline;

    @Mock
    private Future<Void> future;

    @Mock
    private Http2Stream stream;

    @Mock
    private Http2ConnectionDecoder decoder;

    @Mock
    private Http2ConnectionEncoder encoder;

    @Mock
    private Http2FrameWriter frameWriter;

    private String goAwayDebugCap;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        promise = ImmediateEventExecutor.INSTANCE.newPromise();

        when(channel.metadata()).thenReturn(new ChannelMetadata(false));
        DefaultChannelConfig config = new DefaultChannelConfig(channel);
        when(channel.config()).thenReturn(config);

        Throwable fakeException = new RuntimeException("Fake exception");
        when(encoder.connection()).thenReturn(connection);
        when(decoder.connection()).thenReturn(connection);
        when(encoder.frameWriter()).thenReturn(frameWriter);
        when(encoder.flowController()).thenReturn(remoteFlow);
        when(decoder.flowController()).thenReturn(localFlow);
        doAnswer((Answer<Future<Void>>) invocation -> {
            ByteBuf buf = invocation.getArgument(3);
            goAwayDebugCap = buf.toString(UTF_8);
            buf.release();
            return future;
        }).when(frameWriter).writeGoAway(
                any(ChannelHandlerContext.class), anyInt(), anyLong(), any(ByteBuf.class));
        doAnswer((Answer<Future<Void>>) invocation -> {
            Object o = invocation.getArguments()[0];
            if (o instanceof FutureListener) {
                ((FutureListener<Void>) o).operationComplete(future);
            }
            return future;
        }).when(future).addListener(any(FutureListener.class));
        when(future.cause()).thenReturn(fakeException);
        when(channel.isActive()).thenReturn(true);
        when(future.isFailed()).thenReturn(true);
        when(channel.pipeline()).thenReturn(pipeline);
        when(connection.remote()).thenReturn(remote);
        when(remote.flowController()).thenReturn(remoteFlowController);
        when(connection.local()).thenReturn(local);
        when(local.flowController()).thenReturn(localFlowController);
        doAnswer((Answer<Http2Stream>) in -> {
            Http2StreamVisitor visitor = in.getArgument(0);
            if (!visitor.visit(stream)) {
                return stream;
            }
            return null;
        }).when(connection).forEachActiveStream(any(Http2StreamVisitor.class));
        when(connection.stream(NON_EXISTANT_STREAM_ID)).thenReturn(null);
        when(connection.numActiveStreams()).thenReturn(1);
        when(connection.stream(STREAM_ID)).thenReturn(stream);
        when(connection.goAwaySent(anyInt(), anyLong(), any(ByteBuf.class))).thenReturn(true);
        when(stream.open(anyBoolean())).thenReturn(stream);
        when(encoder.writeSettings(any(ChannelHandlerContext.class),
                any(Http2Settings.class))).thenReturn(future);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.newFailedFuture(any(Throwable.class)))
                .thenAnswer(invocationOnMock ->
                        ImmediateEventExecutor.INSTANCE.newFailedFuture(invocationOnMock.getArgument(0)));
        when(ctx.newSucceededFuture()).thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));
        when(ctx.newPromise()).thenReturn(promise);
        when(ctx.write(any())).thenReturn(future);
        when(ctx.executor()).thenReturn(executor);
        doAnswer(in -> {
            Object msg = in.getArgument(0);
            ReferenceCountUtil.release(msg);
            return null;
        }).when(ctx).fireChannelRead(any());
        doAnswer((Answer<Future<Void>>) in ->
                ImmediateEventExecutor.INSTANCE.newSucceededFuture(null)).when(ctx).write(any());
        doAnswer((Answer<Future<Void>>) in ->
                ImmediateEventExecutor.INSTANCE.newSucceededFuture(null)).when(ctx).close();
    }

    private Http2ConnectionHandler newHandler() throws Exception {
        Http2ConnectionHandler handler = new Http2ConnectionHandlerBuilder().codec(decoder, encoder).build();
        handler.handlerAdded(ctx);
        return handler;
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (handler != null) {
            handler.handlerRemoved(ctx);
        }
    }

    @Test
    public void onHttpServerUpgradeWithoutHandlerAdded() throws Exception {
        handler = new Http2ConnectionHandlerBuilder().frameListener(new Http2FrameAdapter()).server(true).build();
        Http2Exception e = assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                handler.onHttpServerUpgrade(new Http2Settings());
            }
        });
        assertEquals(Http2Error.INTERNAL_ERROR, e.error());
    }

    @Test
    public void onHttpClientUpgradeWithoutHandlerAdded() throws Exception {
        handler = new Http2ConnectionHandlerBuilder().frameListener(new Http2FrameAdapter()).server(false).build();
        Http2Exception e = assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                handler.onHttpClientUpgrade();
            }
        });
        assertEquals(Http2Error.INTERNAL_ERROR, e.error());
    }

    @Test
    public void clientShouldveSentPrefaceAndSettingsFrameWhenUserEventIsTriggered() throws Exception {
        when(connection.isServer()).thenReturn(false);
        when(channel.isActive()).thenReturn(false);
        handler = newHandler();
        when(channel.isActive()).thenReturn(true);

        final Http2ConnectionPrefaceAndSettingsFrameWrittenEvent evt =
                Http2ConnectionPrefaceAndSettingsFrameWrittenEvent.INSTANCE;

        final AtomicBoolean verified = new AtomicBoolean(false);
        final Answer<Object> verifier = in -> {
            assertEquals(in.getArgument(0), evt);  // sanity check...
            verify(ctx).write(eq(connectionPrefaceBuf()));
            verify(encoder).writeSettings(eq(ctx), any(Http2Settings.class));
            verified.set(true);
            return null;
        };

        doAnswer(verifier).when(ctx).fireUserEventTriggered(evt);

        handler.channelActive(ctx);
        assertTrue(verified.get());
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
        verify(frameWriter).writeGoAway(any(ChannelHandlerContext.class),
                eq(Integer.MAX_VALUE), eq(PROTOCOL_ERROR.code()), captor.capture());
        assertEquals(0, captor.getValue().refCnt());
    }

    @Test
    public void serverReceivingHttp1ClientPrefaceStringShouldIncludePreface() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        handler.channelRead(ctx, copiedBuffer("GET /path HTTP/1.1", US_ASCII));
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeGoAway(any(ChannelHandlerContext.class), eq(Integer.MAX_VALUE),
                eq(PROTOCOL_ERROR.code()), captor.capture());
        assertEquals(0, captor.getValue().refCnt());
        assertTrue(goAwayDebugCap.contains("/path"));
    }

    @Test
    public void serverReceivingClientPrefaceStringFollowedByNonSettingsShouldHandleException()
            throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();

        // Create a connection preface followed by a bunch of zeros (i.e. not a settings frame).
        ByteBuf buf = Unpooled.buffer().writeBytes(connectionPrefaceBuf()).writeZero(10);
        handler.channelRead(ctx, buf);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter, atLeastOnce()).writeGoAway(any(ChannelHandlerContext.class),
                eq(Integer.MAX_VALUE), eq(PROTOCOL_ERROR.code()), captor.capture());
        assertEquals(0, captor.getValue().refCnt());
    }

    @Test
    public void serverReceivingValidClientPrefaceStringShouldContinueReadingFrames() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        ByteBuf prefacePlusSome = addSettingsHeader(Unpooled.buffer().writeBytes(connectionPrefaceBuf()));
        handler.channelRead(ctx, prefacePlusSome);
        verify(decoder, atLeastOnce()).decodeFrame(any(ChannelHandlerContext.class),
                any(ByteBuf.class));
    }

    @Test
    public void verifyChannelHandlerCanBeReusedInPipeline() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        // Only read the connection preface...after preface is read internal state of Http2ConnectionHandler
        // is expected to change relative to the pipeline.
        ByteBuf preface = connectionPrefaceBuf();
        handler.channelRead(ctx, preface);
        verify(decoder, never()).decodeFrame(any(ChannelHandlerContext.class),
                any(ByteBuf.class));

        // Now remove and add the handler...this is setting up the test condition.
        handler.handlerRemoved(ctx);
        handler.handlerAdded(ctx);

        // Now verify we can continue as normal, reading connection preface plus more.
        ByteBuf prefacePlusSome = addSettingsHeader(Unpooled.buffer().writeBytes(connectionPrefaceBuf()));
        handler.channelRead(ctx, prefacePlusSome);
        verify(decoder, atLeastOnce()).decodeFrame(any(ChannelHandlerContext.class), any(ByteBuf.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void channelInactiveShouldCloseStreams() throws Exception {
        handler = newHandler();
        handler.channelInactive(ctx);
        verify(connection).close(any(Promise.class));
    }

    @Test
    public void connectionErrorShouldStartShutdown() throws Exception {
        handler = newHandler();
        Http2Exception e = new Http2Exception(PROTOCOL_ERROR);
        // There's no guarantee that lastStreamCreated in correct, as the error could have occurred during header
        // processing before it was updated. Thus, it should _not_ be used for the GOAWAY.
        // https://github.com/netty/netty/issues/10670
        when(remote.lastStreamCreated()).thenReturn(STREAM_ID);
        handler.exceptionCaught(ctx, e);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeGoAway(eq(ctx), eq(Integer.MAX_VALUE), eq(PROTOCOL_ERROR.code()),
                captor.capture());
        captor.getValue().release();
    }

    @Test
    public void serverShouldSend431OnHeaderSizeErrorWhenDecodingInitialHeaders() throws Exception {
        int padding = 0;
        handler = newHandler();
        Http2Exception e = new Http2Exception.HeaderListSizeException(STREAM_ID, PROTOCOL_ERROR,
                "Header size exceeded max allowed size 8196", true);

        when(stream.id()).thenReturn(STREAM_ID);
        when(connection.isServer()).thenReturn(true);
        when(stream.isHeadersSent()).thenReturn(false);
        when(remote.lastStreamCreated()).thenReturn(STREAM_ID);
        when(frameWriter.writeRstStream(eq(ctx), eq(STREAM_ID),
                eq(PROTOCOL_ERROR.code()))).thenReturn(future);

        handler.exceptionCaught(ctx, e);

        ArgumentCaptor<Http2Headers> captor = ArgumentCaptor.forClass(Http2Headers.class);
        verify(encoder).writeHeaders(eq(ctx), eq(STREAM_ID),
                captor.capture(), eq(padding), eq(true));
        Http2Headers headers = captor.getValue();
        assertEquals(HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE.codeAsText(), headers.status());
        verify(frameWriter).writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code());
    }

    @Test
    public void serverShouldNeverSend431HeaderSizeErrorWhenEncoding() throws Exception {
        int padding = 0;
        handler = newHandler();
        Http2Exception e = new Http2Exception.HeaderListSizeException(STREAM_ID, PROTOCOL_ERROR,
            "Header size exceeded max allowed size 8196", false);

        when(stream.id()).thenReturn(STREAM_ID);
        when(connection.isServer()).thenReturn(true);
        when(stream.isHeadersSent()).thenReturn(false);
        when(remote.lastStreamCreated()).thenReturn(STREAM_ID);
        when(frameWriter.writeRstStream(eq(ctx), eq(STREAM_ID),
            eq(PROTOCOL_ERROR.code()))).thenReturn(future);

        handler.exceptionCaught(ctx, e);

        verify(encoder, never()).writeHeaders(eq(ctx), eq(STREAM_ID),
            any(Http2Headers.class), eq(padding), eq(true));
        verify(frameWriter).writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code());
    }

    @Test
    public void clientShouldNeverSend431WhenHeadersAreTooLarge() throws Exception {
        int padding = 0;
        handler = newHandler();
        Http2Exception e = new Http2Exception.HeaderListSizeException(STREAM_ID, PROTOCOL_ERROR,
                "Header size exceeded max allowed size 8196", true);

        when(stream.id()).thenReturn(STREAM_ID);
        when(connection.isServer()).thenReturn(false);
        when(stream.isHeadersSent()).thenReturn(false);
        when(remote.lastStreamCreated()).thenReturn(STREAM_ID);
        when(frameWriter.writeRstStream(eq(ctx), eq(STREAM_ID),
                eq(PROTOCOL_ERROR.code()))).thenReturn(future);

        handler.exceptionCaught(ctx, e);

        verify(encoder, never()).writeHeaders(eq(ctx), eq(STREAM_ID),
                any(Http2Headers.class), eq(padding), eq(true));
        verify(frameWriter).writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code());
    }

    @Test
    public void prefaceUserEventProcessed() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        handler = new Http2ConnectionHandler(decoder, encoder, new Http2Settings()) {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == Http2ConnectionPrefaceAndSettingsFrameWrittenEvent.INSTANCE) {
                    latch.countDown();
                }
            }
        };
        handler.handlerAdded(ctx);
        assertTrue(latch.await(5, SECONDS));
    }

    @Test
    public void serverShouldNeverSend431IfHeadersAlreadySent() throws Exception {
        int padding = 0;
        handler = newHandler();
        Http2Exception e = new Http2Exception.HeaderListSizeException(STREAM_ID, PROTOCOL_ERROR,
            "Header size exceeded max allowed size 8196", true);

        when(stream.id()).thenReturn(STREAM_ID);
        when(connection.isServer()).thenReturn(true);
        when(stream.isHeadersSent()).thenReturn(true);
        when(remote.lastStreamCreated()).thenReturn(STREAM_ID);
        when(frameWriter.writeRstStream(eq(ctx), eq(STREAM_ID),
            eq(PROTOCOL_ERROR.code()))).thenReturn(future);
        handler.exceptionCaught(ctx, e);

        verify(encoder, never()).writeHeaders(eq(ctx), eq(STREAM_ID),
            any(Http2Headers.class), eq(padding), eq(true));

        verify(frameWriter).writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code());
    }

    @Test
    public void serverShouldCreateStreamIfNeededBeforeSending431() throws Exception {
        int padding = 0;
        handler = newHandler();
        Http2Exception e = new Http2Exception.HeaderListSizeException(STREAM_ID, PROTOCOL_ERROR,
            "Header size exceeded max allowed size 8196", true);

        when(connection.stream(STREAM_ID)).thenReturn(null);
        when(remote.createStream(STREAM_ID, true)).thenReturn(stream);
        when(stream.id()).thenReturn(STREAM_ID);

        when(connection.isServer()).thenReturn(true);
        when(stream.isHeadersSent()).thenReturn(false);
        when(remote.lastStreamCreated()).thenReturn(STREAM_ID);
        when(frameWriter.writeRstStream(eq(ctx), eq(STREAM_ID),
            eq(PROTOCOL_ERROR.code()))).thenReturn(future);
        handler.exceptionCaught(ctx, e);

        verify(remote).createStream(STREAM_ID, true);
        verify(encoder).writeHeaders(eq(ctx), eq(STREAM_ID),
            any(Http2Headers.class), eq(padding), eq(true));

        verify(frameWriter).writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code());
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
        when(frameWriter.writeRstStream(eq(ctx), eq(NON_EXISTANT_STREAM_ID),
                                        eq(STREAM_CLOSED.code()))).thenReturn(future);
        handler.resetStream(ctx, NON_EXISTANT_STREAM_ID, STREAM_CLOSED.code());
        verify(frameWriter).writeRstStream(eq(ctx), eq(NON_EXISTANT_STREAM_ID), eq(STREAM_CLOSED.code()));
    }

    @Test
    public void writeRstOnClosedStreamShouldSucceed() throws Exception {
        handler = newHandler();
        when(stream.id()).thenReturn(STREAM_ID);
        when(frameWriter.writeRstStream(eq(ctx), eq(STREAM_ID),
                anyLong())).thenReturn(future);
        when(stream.state()).thenReturn(CLOSED);
        when(stream.isHeadersSent()).thenReturn(true);
        // The stream is "closed" but is still known about by the connection (connection().stream(..)
        // will return the stream). We should still write a RST_STREAM frame in this scenario.
        handler.resetStream(ctx, STREAM_ID, STREAM_CLOSED.code());
        verify(frameWriter).writeRstStream(eq(ctx), eq(STREAM_ID), anyLong());
    }

    @Test
    public void writeRstOnIdleStreamShouldNotWriteButStillSucceed() throws Exception {
        handler = newHandler();
        when(stream.state()).thenReturn(IDLE);
        handler.resetStream(ctx, STREAM_ID, STREAM_CLOSED.code());
        verify(frameWriter, never()).writeRstStream(eq(ctx), eq(STREAM_ID), anyLong());
        verify(stream).close();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void closeListenerShouldBeNotifiedOnlyOneTime() throws Exception {
        handler = newHandler();
        when(future.isDone()).thenReturn(true);
        when(future.isSuccess()).thenReturn(true);
        doAnswer((Answer<Future<Void>>) invocation -> {
            Object[] args = invocation.getArguments();
            FutureListener<Void> listener = (FutureListener<Void>) args[0];
            // Simulate that all streams have become inactive by the time the future completes.
            doAnswer((Answer<Http2Stream>) in -> null).when(connection).forEachActiveStream(
                    any(Http2StreamVisitor.class));
            when(connection.numActiveStreams()).thenReturn(0);
            // Simulate the future being completed.
            listener.operationComplete(future);
            return future;
        }).when(future).addListener(any(FutureListener.class));
        handler.close(ctx);
        if (future.isDone()) {
            when(connection.numActiveStreams()).thenReturn(0);
        }
        handler.closeStream(stream, future);
        // Simulate another stream close call being made after the context should already be closed.
        handler.closeStream(stream, future);
        verify(ctx, times(1)).close();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void canSendGoAwayFrame() throws Exception {
        ByteBuf data = dummyData();
        long errorCode = Http2Error.INTERNAL_ERROR.code();
        when(future.isDone()).thenReturn(true);
        when(future.isSuccess()).thenReturn(true);
        doAnswer((Answer<Void>) invocation -> {
            ((FutureListener<Void>) invocation.getArgument(0)).operationComplete(future);
            return null;
        }).when(future).addListener(any(FutureListener.class));
        handler = newHandler();
        handler.goAway(ctx, STREAM_ID, errorCode, data);

        verify(connection).goAwaySent(eq(STREAM_ID), eq(errorCode), eq(data));
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(errorCode), eq(data));
        verify(ctx).close();
        assertEquals(0, data.refCnt());
    }

    @Test
    public void canSendGoAwayFramesWithDecreasingLastStreamIds() throws Exception {
        handler = newHandler();
        ByteBuf data = dummyData();
        long errorCode = Http2Error.INTERNAL_ERROR.code();

        handler.goAway(ctx, STREAM_ID + 2, errorCode, data.retain());
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID + 2), eq(errorCode), eq(data));
        verify(connection).goAwaySent(eq(STREAM_ID + 2), eq(errorCode), eq(data));
        promise = ImmediateEventExecutor.INSTANCE.newPromise();
        handler.goAway(ctx, STREAM_ID, errorCode, data);
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(errorCode), eq(data));
        verify(connection).goAwaySent(eq(STREAM_ID), eq(errorCode), eq(data));
        assertEquals(0, data.refCnt());
    }

    @Test
    public void cannotSendGoAwayFrameWithIncreasingLastStreamIds() throws Exception {
        handler = newHandler();
        ByteBuf data = dummyData();
        long errorCode = Http2Error.INTERNAL_ERROR.code();

        Future<Void> future = handler.goAway(ctx, STREAM_ID, errorCode, data.retain());
        verify(connection).goAwaySent(eq(STREAM_ID), eq(errorCode), eq(data));
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(errorCode), eq(data));
        // The frameWriter is only mocked, so it should not have interacted with the promise.
        assertFalse(future.isDone());

        when(connection.goAwaySent()).thenReturn(true);
        when(remote.lastStreamKnownByPeer()).thenReturn(STREAM_ID);

        Exception ex = new IllegalStateException();
        doAnswer((Answer<Boolean>) invocationOnMock -> {
            throw ex;
        }).when(connection).goAwaySent(anyInt(), anyLong(), any(ByteBuf.class));
        Future<Void> future2 = handler.goAway(ctx, STREAM_ID + 2, errorCode, data);
        assertTrue(future2.isDone());
        assertFalse(future2.isSuccess());
        assertSame(ex, future2.cause());

        assertEquals(0, data.refCnt());
        verifyNoMoreInteractions(frameWriter);
    }

    @Test
    public void channelReadCompleteTriggersFlush() throws Exception {
        handler = newHandler();
        handler.channelReadComplete(ctx);
        verify(ctx, times(1)).flush();
    }

    @Test
    public void channelReadCompleteCallsReadWhenAutoReadFalse() throws Exception {
        channel.config().setAutoRead(false);
        handler = newHandler();
        handler.channelReadComplete(ctx);
        verify(ctx, times(1)).read();
    }

    @Test
    public void channelClosedDoesNotThrowPrefaceException() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        when(channel.isActive()).thenReturn(false);
        handler.channelInactive(ctx);
        verify(frameWriter, never()).writeGoAway(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                                                 any(ByteBuf.class));
        verify(frameWriter, never()).writeRstStream(any(ChannelHandlerContext.class), anyInt(), anyLong());
    }

    @Test
    public void clientChannelClosedDoesNotSendGoAwayBeforePreface() throws Exception {
        when(connection.isServer()).thenReturn(false);
        when(channel.isActive()).thenReturn(false);
        handler = newHandler();
        when(channel.isActive()).thenReturn(true);
        handler.close(ctx);
        verifyZeroInteractions(frameWriter);
    }

    @Test
    public void gracefulShutdownTimeoutWhenConnectionErrorHardShutdownTest() throws Exception {
        gracefulShutdownTimeoutWhenConnectionErrorTest0(ShutdownHint.HARD_SHUTDOWN);
    }

    @Test
    public void gracefulShutdownTimeoutWhenConnectionErrorGracefulShutdownTest() throws Exception {
        gracefulShutdownTimeoutWhenConnectionErrorTest0(ShutdownHint.GRACEFUL_SHUTDOWN);
    }

    private void gracefulShutdownTimeoutWhenConnectionErrorTest0(ShutdownHint hint) throws Exception {
        handler = newHandler();
        final long expectedMillis = 1234;
        handler.gracefulShutdownTimeoutMillis(expectedMillis);
        Http2Exception exception = new Http2Exception(PROTOCOL_ERROR, "Test error", hint);
        handler.onConnectionError(ctx, false, exception, exception);
        verify(executor, atLeastOnce()).schedule(any(Runnable.class), eq(expectedMillis), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void gracefulShutdownTimeoutTest() throws Exception {
        handler = newHandler();
        final long expectedMillis = 1234;
        handler.gracefulShutdownTimeoutMillis(expectedMillis);
        handler.close(ctx);
        verify(executor, atLeastOnce()).schedule(any(Runnable.class), eq(expectedMillis), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void gracefulShutdownTimeoutNoActiveStreams() throws Exception {
        handler = newHandler();
        when(connection.numActiveStreams()).thenReturn(0);
        final long expectedMillis = 1234;
        handler.gracefulShutdownTimeoutMillis(expectedMillis);
        handler.close(ctx);
        verify(executor, atLeastOnce()).schedule(any(Runnable.class), eq(expectedMillis), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void gracefulShutdownIndefiniteTimeoutTest() throws Exception {
        handler = newHandler();
        handler.gracefulShutdownTimeoutMillis(-1);
        handler.close(ctx);
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    public void writeMultipleRstFramesForSameStream() throws Exception {
        handler = newHandler();
        when(stream.id()).thenReturn(STREAM_ID);

        final AtomicBoolean resetSent = new AtomicBoolean();
        when(stream.resetSent()).then((Answer<Http2Stream>) invocationOnMock -> {
            resetSent.set(true);
            return stream;
        });
        when(stream.isResetSent()).then((Answer<Boolean>) invocationOnMock -> resetSent.get());
        when(frameWriter.writeRstStream(eq(ctx), eq(STREAM_ID), anyLong()))
                .thenReturn(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null));

        Future<Void> f1 = handler.resetStream(ctx, STREAM_ID, STREAM_CLOSED.code());
        Future<Void> f2 = handler.resetStream(ctx, STREAM_ID, CANCEL.code());
        verify(frameWriter).writeRstStream(eq(ctx), eq(STREAM_ID), anyLong());
        assertTrue(f1.isSuccess());
        assertTrue(f2.isSuccess());
    }

    private static ByteBuf dummyData() {
        return Unpooled.buffer().writeBytes("abcdefgh".getBytes(UTF_8));
    }

    private static ByteBuf addSettingsHeader(ByteBuf buf) {
        buf.writeMedium(Http2CodecUtil.SETTING_ENTRY_LENGTH);
        buf.writeByte(Http2FrameTypes.SETTINGS);
        buf.writeByte(0);
        buf.writeInt(0);
        return buf;
    }
}
