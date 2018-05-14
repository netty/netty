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
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2CodecUtil.SimpleChannelPromiseAggregator;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.STREAM_CLOSED;
import static io.netty.handler.codec.http2.Http2Stream.State.CLOSED;
import static io.netty.handler.codec.http2.Http2Stream.State.IDLE;
import static io.netty.handler.codec.http2.Http2TestUtil.newVoidPromise;
import static io.netty.util.CharsetUtil.US_ASCII;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
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
import static org.mockito.Mockito.when;

/**
 * Tests for {@link Http2ConnectionHandler}
 */
public class Http2ConnectionHandlerTest {
    private static final int STREAM_ID = 1;
    private static final int NON_EXISTANT_STREAM_ID = 13;

    private Http2ConnectionHandler handler;
    private ChannelPromise promise;
    private ChannelPromise voidPromise;

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
    private ChannelFuture future;

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
    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        promise = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        voidPromise = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        Throwable fakeException = new RuntimeException("Fake exception");
        when(encoder.connection()).thenReturn(connection);
        when(decoder.connection()).thenReturn(connection);
        when(encoder.frameWriter()).thenReturn(frameWriter);
        when(encoder.flowController()).thenReturn(remoteFlow);
        when(decoder.flowController()).thenReturn(localFlow);
        doAnswer(new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocation) throws Throwable {
                ByteBuf buf = invocation.getArgument(3);
                goAwayDebugCap = buf.toString(UTF_8);
                buf.release();
                return future;
            }
        }).when(frameWriter).writeGoAway(
                any(ChannelHandlerContext.class), anyInt(), anyLong(), any(ByteBuf.class), any(ChannelPromise.class));
        doAnswer(new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocation) throws Throwable {
                Object o = invocation.getArguments()[0];
                if (o instanceof ChannelFutureListener) {
                    ((ChannelFutureListener) o).operationComplete(future);
                }
                return future;
            }
        }).when(future).addListener(any(GenericFutureListener.class));
        when(future.cause()).thenReturn(fakeException);
        when(future.channel()).thenReturn(channel);
        when(channel.isActive()).thenReturn(true);
        when(channel.pipeline()).thenReturn(pipeline);
        when(connection.remote()).thenReturn(remote);
        when(remote.flowController()).thenReturn(remoteFlowController);
        when(connection.local()).thenReturn(local);
        when(local.flowController()).thenReturn(localFlowController);
        doAnswer(new Answer<Http2Stream>() {
            @Override
            public Http2Stream answer(InvocationOnMock in) throws Throwable {
                Http2StreamVisitor visitor = in.getArgument(0);
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
        when(ctx.voidPromise()).thenReturn(voidPromise);
        when(ctx.write(any())).thenReturn(future);
        when(ctx.executor()).thenReturn(executor);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock in) throws Throwable {
                Object msg = in.getArgument(0);
                ReferenceCountUtil.release(msg);
                return null;
            }
        }).when(ctx).fireChannelRead(any());
    }

    private Http2ConnectionHandler newHandler() throws Exception {
        Http2ConnectionHandler handler = new Http2ConnectionHandlerBuilder().codec(decoder, encoder).build();
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
    public void onHttpServerUpgradeWithoutHandlerAdded() throws Exception {
        handler = new Http2ConnectionHandlerBuilder().frameListener(new Http2FrameAdapter()).server(true).build();
        try {
            handler.onHttpServerUpgrade(new Http2Settings());
            fail();
        } catch (Http2Exception e) {
            assertEquals(Http2Error.INTERNAL_ERROR, e.error());
        }
    }

    @Test
    public void onHttpClientUpgradeWithoutHandlerAdded() throws Exception {
        handler = new Http2ConnectionHandlerBuilder().frameListener(new Http2FrameAdapter()).server(false).build();
        try {
            handler.onHttpClientUpgrade();
            fail();
        } catch (Http2Exception e) {
            assertEquals(Http2Error.INTERNAL_ERROR, e.error());
        }
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
        final Answer verifier = new Answer() {
            @Override
            public Object answer(final InvocationOnMock in) throws Throwable {
                assertTrue(in.getArgument(0).equals(evt));  // sanity check...
                verify(ctx).write(eq(connectionPrefaceBuf()));
                verify(encoder).writeSettings(eq(ctx), any(Http2Settings.class), any(ChannelPromise.class));
                verified.set(true);
                return null;
            }
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
        verify(frameWriter).writeGoAway(eq(ctx), eq(0), eq(PROTOCOL_ERROR.code()),
                captor.capture(), eq(promise));
        assertEquals(0, captor.getValue().refCnt());
    }

    @Test
    public void serverReceivingHttp1ClientPrefaceStringShouldIncludePreface() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        handler.channelRead(ctx, copiedBuffer("GET /path HTTP/1.1", US_ASCII));
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeGoAway(eq(ctx), eq(0), eq(PROTOCOL_ERROR.code()),
            captor.capture(), eq(promise));
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
        verify(frameWriter, atLeastOnce()).writeGoAway(eq(ctx), eq(0), eq(PROTOCOL_ERROR.code()),
                captor.capture(), eq(promise));
        assertEquals(0, captor.getValue().refCnt());
    }

    @Test
    public void serverReceivingValidClientPrefaceStringShouldContinueReadingFrames() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        ByteBuf prefacePlusSome = addSettingsHeader(Unpooled.buffer().writeBytes(connectionPrefaceBuf()));
        handler.channelRead(ctx, prefacePlusSome);
        verify(decoder, atLeastOnce()).decodeFrame(any(ChannelHandlerContext.class),
                any(ByteBuf.class), ArgumentMatchers.<List<Object>>any());
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
                any(ByteBuf.class), ArgumentMatchers.<List<Object>>any());

        // Now remove and add the handler...this is setting up the test condition.
        handler.handlerRemoved(ctx);
        handler.handlerAdded(ctx);

        // Now verify we can continue as normal, reading connection preface plus more.
        ByteBuf prefacePlusSome = addSettingsHeader(Unpooled.buffer().writeBytes(connectionPrefaceBuf()));
        handler.channelRead(ctx, prefacePlusSome);
        verify(decoder, atLeastOnce()).decodeFrame(eq(ctx), any(ByteBuf.class), ArgumentMatchers.<List<Object>>any());
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
        when(remote.lastStreamCreated()).thenReturn(STREAM_ID);
        handler.exceptionCaught(ctx, e);
        ArgumentCaptor<ByteBuf> captor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(PROTOCOL_ERROR.code()),
                captor.capture(), eq(promise));
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
                eq(PROTOCOL_ERROR.code()), eq(promise))).thenReturn(future);

        handler.exceptionCaught(ctx, e);

        ArgumentCaptor<Http2Headers> captor = ArgumentCaptor.forClass(Http2Headers.class);
        verify(encoder).writeHeaders(eq(ctx), eq(STREAM_ID),
                captor.capture(), eq(padding), eq(true), eq(promise));
        Http2Headers headers = captor.getValue();
        assertEquals(HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE.codeAsText(), headers.status());
        verify(frameWriter).writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code(), promise);
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
            eq(PROTOCOL_ERROR.code()), eq(promise))).thenReturn(future);

        handler.exceptionCaught(ctx, e);

        verify(encoder, never()).writeHeaders(eq(ctx), eq(STREAM_ID),
            any(Http2Headers.class), eq(padding), eq(true), eq(promise));
        verify(frameWriter).writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code(), promise);
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
                eq(PROTOCOL_ERROR.code()), eq(promise))).thenReturn(future);

        handler.exceptionCaught(ctx, e);

        verify(encoder, never()).writeHeaders(eq(ctx), eq(STREAM_ID),
                any(Http2Headers.class), eq(padding), eq(true), eq(promise));
        verify(frameWriter).writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code(), promise);
    }

    @Test
    public void prefaceUserEventProcessed() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        handler = new Http2ConnectionHandler(decoder, encoder, new Http2Settings()) {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
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
            eq(PROTOCOL_ERROR.code()), eq(promise))).thenReturn(future);
        handler.exceptionCaught(ctx, e);

        verify(encoder, never()).writeHeaders(eq(ctx), eq(STREAM_ID),
            any(Http2Headers.class), eq(padding), eq(true), eq(promise));

        verify(frameWriter).writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code(), promise);
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
            eq(Http2Error.PROTOCOL_ERROR.code()), eq(promise))).thenReturn(future);
        handler.exceptionCaught(ctx, e);

        verify(remote).createStream(STREAM_ID, true);
        verify(encoder).writeHeaders(eq(ctx), eq(STREAM_ID),
            any(Http2Headers.class), eq(padding), eq(true), eq(promise));

        verify(frameWriter).writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code(), promise);
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
                                        eq(STREAM_CLOSED.code()), eq(promise))).thenReturn(future);
        handler.resetStream(ctx, NON_EXISTANT_STREAM_ID, STREAM_CLOSED.code(), promise);
        verify(frameWriter).writeRstStream(eq(ctx), eq(NON_EXISTANT_STREAM_ID), eq(STREAM_CLOSED.code()), eq(promise));
    }

    @Test
    public void writeRstOnClosedStreamShouldSucceed() throws Exception {
        handler = newHandler();
        when(stream.id()).thenReturn(STREAM_ID);
        when(frameWriter.writeRstStream(eq(ctx), eq(STREAM_ID),
                anyLong(), any(ChannelPromise.class))).thenReturn(future);
        when(stream.state()).thenReturn(CLOSED);
        when(stream.isHeadersSent()).thenReturn(true);
        // The stream is "closed" but is still known about by the connection (connection().stream(..)
        // will return the stream). We should still write a RST_STREAM frame in this scenario.
        handler.resetStream(ctx, STREAM_ID, STREAM_CLOSED.code(), promise);
        verify(frameWriter).writeRstStream(eq(ctx), eq(STREAM_ID), anyLong(), any(ChannelPromise.class));
    }

    @Test
    public void writeRstOnIdleStreamShouldNotWriteButStillSucceed() throws Exception {
        handler = newHandler();
        when(stream.state()).thenReturn(IDLE);
        handler.resetStream(ctx, STREAM_ID, STREAM_CLOSED.code(), promise);
        verify(frameWriter, never()).writeRstStream(eq(ctx), eq(STREAM_ID), anyLong(), any(ChannelPromise.class));
        verify(stream).close();
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
        if (future.isDone()) {
            when(connection.numActiveStreams()).thenReturn(0);
        }
        handler.closeStream(stream, future);
        // Simulate another stream close call being made after the context should already be closed.
        handler.closeStream(stream, future);
        verify(ctx, times(1)).close(any(ChannelPromise.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void canSendGoAwayFrame() throws Exception {
        ByteBuf data = dummyData();
        long errorCode = Http2Error.INTERNAL_ERROR.code();
        when(future.isDone()).thenReturn(true);
        when(future.isSuccess()).thenReturn(true);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ((GenericFutureListener) invocation.getArgument(0)).operationComplete(future);
                return null;
            }
        }).when(future).addListener(any(GenericFutureListener.class));
        handler = newHandler();
        handler.goAway(ctx, STREAM_ID, errorCode, data, promise);

        verify(connection).goAwaySent(eq(STREAM_ID), eq(errorCode), eq(data));
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(errorCode), eq(data),
                eq(promise));
        verify(ctx).close();
        assertEquals(0, data.refCnt());
    }

    @Test
    public void canSendGoAwayFramesWithDecreasingLastStreamIds() throws Exception {
        handler = newHandler();
        ByteBuf data = dummyData();
        long errorCode = Http2Error.INTERNAL_ERROR.code();

        handler.goAway(ctx, STREAM_ID + 2, errorCode, data.retain(), promise);
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID + 2), eq(errorCode), eq(data),
                eq(promise));
        verify(connection).goAwaySent(eq(STREAM_ID + 2), eq(errorCode), eq(data));
        promise = new DefaultChannelPromise(channel);
        handler.goAway(ctx, STREAM_ID, errorCode, data, promise);
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(errorCode), eq(data), eq(promise));
        verify(connection).goAwaySent(eq(STREAM_ID), eq(errorCode), eq(data));
        assertEquals(0, data.refCnt());
    }

    @Test
    public void cannotSendGoAwayFrameWithIncreasingLastStreamIds() throws Exception {
        handler = newHandler();
        ByteBuf data = dummyData();
        long errorCode = Http2Error.INTERNAL_ERROR.code();

        handler.goAway(ctx, STREAM_ID, errorCode, data.retain(), promise);
        verify(connection).goAwaySent(eq(STREAM_ID), eq(errorCode), eq(data));
        verify(frameWriter).writeGoAway(eq(ctx), eq(STREAM_ID), eq(errorCode), eq(data), eq(promise));
        // The frameWriter is only mocked, so it should not have interacted with the promise.
        assertFalse(promise.isDone());

        when(connection.goAwaySent()).thenReturn(true);
        when(remote.lastStreamKnownByPeer()).thenReturn(STREAM_ID);
        handler.goAway(ctx, STREAM_ID + 2, errorCode, data, promise);
        assertTrue(promise.isDone());
        assertFalse(promise.isSuccess());
        assertEquals(0, data.refCnt());
        verifyNoMoreInteractions(frameWriter);
    }

    @Test
    public void canSendGoAwayUsingVoidPromise() throws Exception {
        handler = newHandler();
        ByteBuf data = dummyData();
        long errorCode = Http2Error.INTERNAL_ERROR.code();
        handler = newHandler();
        final Throwable cause = new RuntimeException("fake exception");
        doAnswer(new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocation) throws Throwable {
                ChannelPromise promise = invocation.getArgument(4);
                assertFalse(promise.isVoid());
                // This is what DefaultHttp2FrameWriter does... I hate mocking :-(.
                SimpleChannelPromiseAggregator aggregatedPromise =
                        new SimpleChannelPromiseAggregator(promise, channel, ImmediateEventExecutor.INSTANCE);
                aggregatedPromise.newPromise();
                aggregatedPromise.doneAllocatingPromises();
                return aggregatedPromise.setFailure(cause);
            }
        }).when(frameWriter).writeGoAway(
                any(ChannelHandlerContext.class), anyInt(), anyLong(), any(ByteBuf.class), any(ChannelPromise.class));
        handler.goAway(ctx, STREAM_ID, errorCode, data, newVoidPromise(channel));
        verify(pipeline).fireExceptionCaught(cause);
    }

    @Test
    public void channelReadCompleteTriggersFlush() throws Exception {
        handler = newHandler();
        handler.channelReadComplete(ctx);
        verify(ctx, times(1)).flush();
    }

    @Test
    public void channelClosedDoesNotThrowPrefaceException() throws Exception {
        when(connection.isServer()).thenReturn(true);
        handler = newHandler();
        when(channel.isActive()).thenReturn(false);
        handler.channelInactive(ctx);
        verify(frameWriter, never()).writeGoAway(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                                                 any(ByteBuf.class), any(ChannelPromise.class));
        verify(frameWriter, never()).writeRstStream(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                                                    any(ChannelPromise.class));
    }

    @Test
    public void writeRstStreamForUnknownStreamUsingVoidPromise() throws Exception {
        writeRstStreamUsingVoidPromise(NON_EXISTANT_STREAM_ID);
    }

    @Test
    public void writeRstStreamForKnownStreamUsingVoidPromise() throws Exception {
        writeRstStreamUsingVoidPromise(STREAM_ID);
    }

    @Test
    public void gracefulShutdownTimeoutTest() throws Exception {
        handler = newHandler();
        final long expectedMillis = 1234;
        handler.gracefulShutdownTimeoutMillis(expectedMillis);
        handler.close(ctx, promise);
        verify(executor).schedule(any(Runnable.class), eq(expectedMillis), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void gracefulShutdownIndefiniteTimeoutTest() throws Exception {
        handler = newHandler();
        handler.gracefulShutdownTimeoutMillis(-1);
        handler.close(ctx, promise);
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    private void writeRstStreamUsingVoidPromise(int streamId) throws Exception {
        handler = newHandler();
        final Throwable cause = new RuntimeException("fake exception");
        when(stream.id()).thenReturn(STREAM_ID);
        when(frameWriter.writeRstStream(eq(ctx), eq(streamId), anyLong(), any(ChannelPromise.class)))
                .then(new Answer<ChannelFuture>() {
                    @Override
                    public ChannelFuture answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ChannelPromise promise = invocationOnMock.getArgument(3);
                        assertFalse(promise.isVoid());
                        return promise.setFailure(cause);
                    }
                });
        handler.resetStream(ctx, streamId, STREAM_CLOSED.code(), newVoidPromise(channel));
        verify(frameWriter).writeRstStream(eq(ctx), eq(streamId), anyLong(), any(ChannelPromise.class));
        verify(pipeline).fireExceptionCaught(cause);
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
