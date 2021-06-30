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

package io.netty.handler.codec.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.http2.Http2TestUtil.FrameCountDown;
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.AsciiString;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECTION_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2TestUtil.randomString;
import static io.netty.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.lang.Integer.MAX_VALUE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests the full HTTP/2 framing stack including the connection and preface handlers.
 */
public class Http2ConnectionRoundtripTest {

    private static final long DEFAULT_AWAIT_TIMEOUT_SECONDS = 15;

    @Mock
    private Http2FrameListener clientListener;

    @Mock
    private Http2FrameListener serverListener;

    private Http2ConnectionHandler http2Client;
    private Http2ConnectionHandler http2Server;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private volatile Channel serverConnectedChannel;
    private Channel clientChannel;
    private FrameCountDown serverFrameCountDown;
    private CountDownLatch requestLatch;
    private CountDownLatch serverSettingsAckLatch;
    private CountDownLatch dataLatch;
    private CountDownLatch trailersLatch;
    private CountDownLatch goAwayLatch;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mockFlowControl(clientListener);
        mockFlowControl(serverListener);
    }

    @AfterEach
    public void teardown() throws Exception {
        if (clientChannel != null) {
            clientChannel.close().syncUninterruptibly();
            clientChannel = null;
        }
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        final Channel serverConnectedChannel = this.serverConnectedChannel;
        if (serverConnectedChannel != null) {
            serverConnectedChannel.close().syncUninterruptibly();
            this.serverConnectedChannel = null;
        }
        Future<?> serverGroup = sb.config().group().shutdownGracefully(0, 5, SECONDS);
        Future<?> serverChildGroup = sb.config().childGroup().shutdownGracefully(0, 5, SECONDS);
        Future<?> clientGroup = cb.config().group().shutdownGracefully(0, 5, SECONDS);
        serverGroup.syncUninterruptibly();
        serverChildGroup.syncUninterruptibly();
        clientGroup.syncUninterruptibly();
    }

    @Test
    public void inflightFrameAfterStreamResetShouldNotMakeConnectionUnusable() throws Exception {
        bootstrapEnv(1, 1, 2, 1);
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                ChannelHandlerContext ctx = invocationOnMock.getArgument(0);
                http2Server.encoder().writeHeaders(ctx,
                        (Integer) invocationOnMock.getArgument(1),
                        (Http2Headers) invocationOnMock.getArgument(2),
                        0,
                        false,
                        ctx.newPromise());
                http2Server.flush(ctx);
                return null;
            }
        }).when(serverListener).onHeadersRead(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class),
                anyInt(), anyShort(), anyBoolean(), anyInt(), anyBoolean());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                latch.countDown();
                return null;
            }
        }).when(clientListener).onHeadersRead(any(ChannelHandlerContext.class), eq(5), any(Http2Headers.class),
                anyInt(), anyShort(), anyBoolean(), anyInt(), anyBoolean());

        // Create a single stream by sending a HEADERS frame to the server.
        final short weight = 16;
        final Http2Headers headers = dummyHeaders();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, weight, false, 0, false, newPromise());
                http2Client.flush(ctx());
                http2Client.encoder().writeRstStream(ctx(), 3, Http2Error.INTERNAL_ERROR.code(), newPromise());
                http2Client.flush(ctx());
            }
        });

        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 5, headers, 0, weight, false, 0, false, newPromise());
                http2Client.flush(ctx());
            }
        });

        assertTrue(latch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
    }

    @Test
    public void headersWithEndStreamShouldNotSendError() throws Exception {
        bootstrapEnv(1, 1, 2, 1);

        // Create a single stream by sending a HEADERS frame to the server.
        final short weight = 16;
        final Http2Headers headers = dummyHeaders();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, weight, false, 0, true,
                        newPromise());
                http2Client.flush(ctx());
            }
        });

        assertTrue(requestLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(headers),
                eq(0), eq(weight), eq(false), eq(0), eq(true));
        // Wait for some time to see if a go_away or reset frame will be received.
        Thread.sleep(1000);

        // Verify that no errors have been received.
        verify(serverListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(),
                anyLong(), any(ByteBuf.class));
        verify(serverListener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(),
                anyLong());

        // The server will not respond, and so don't wait for graceful shutdown
        setClientGracefulShutdownTime(0);
    }

    @Test
    public void encodeViolatesMaxHeaderListSizeCanStillUseConnection() throws Exception {
        bootstrapEnv(1, 2, 1, 0, 0);

        final CountDownLatch serverSettingsAckLatch1 = new CountDownLatch(2);
        final CountDownLatch serverSettingsAckLatch2 = new CountDownLatch(3);
        final CountDownLatch clientSettingsLatch1 = new CountDownLatch(3);
        final CountDownLatch serverRevHeadersLatch = new CountDownLatch(1);
        final CountDownLatch clientHeadersLatch = new CountDownLatch(1);
        final CountDownLatch clientDataWrite = new CountDownLatch(1);
        final AtomicReference<Throwable> clientHeadersWriteException = new AtomicReference<Throwable>();
        final AtomicReference<Throwable> clientHeadersWriteException2 = new AtomicReference<Throwable>();
        final AtomicReference<Throwable> clientDataWriteException = new AtomicReference<Throwable>();

        final Http2Headers headers = dummyHeaders();

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                serverSettingsAckLatch1.countDown();
                serverSettingsAckLatch2.countDown();
                return null;
            }
        }).when(serverListener).onSettingsAckRead(any(ChannelHandlerContext.class));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                clientSettingsLatch1.countDown();
                return null;
            }
        }).when(clientListener).onSettingsRead(any(ChannelHandlerContext.class), any(Http2Settings.class));

        // Manually add a listener for when we receive the expected headers on the server.
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                serverRevHeadersLatch.countDown();
                return null;
            }
        }).when(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(5), eq(headers),
                anyInt(), anyShort(), anyBoolean(), eq(0), eq(true));

        // Set the maxHeaderListSize to 100 so we may be able to write some headers, but not all. We want to verify
        // that we don't corrupt state if some can be written but not all.
        runInChannel(serverConnectedChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Server.encoder().writeSettings(serverCtx(),
                        new Http2Settings().copyFrom(http2Server.decoder().localSettings())
                                .maxHeaderListSize(100),
                        serverNewPromise());
                http2Server.flush(serverCtx());
            }
        });

        assertTrue(serverSettingsAckLatch1.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, false, newPromise())
                        .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        clientHeadersWriteException.set(future.cause());
                    }
                });
                // It is expected that this write should fail locally and the remote peer will never see this.
                http2Client.encoder().writeData(ctx(), 3, Unpooled.buffer(), 0, true, newPromise())
                    .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            clientDataWriteException.set(future.cause());
                            clientDataWrite.countDown();
                        }
                });
                http2Client.flush(ctx());
            }
        });

        assertTrue(clientDataWrite.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertNotNull(clientHeadersWriteException.get(), "Header encode should have exceeded maxHeaderListSize!");
        assertNotNull(clientDataWriteException.get(), "Data on closed stream should fail!");

        // Set the maxHeaderListSize to the max value so we can send the headers.
        runInChannel(serverConnectedChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Server.encoder().writeSettings(serverCtx(),
                        new Http2Settings().copyFrom(http2Server.decoder().localSettings())
                                .maxHeaderListSize(Http2CodecUtil.MAX_HEADER_LIST_SIZE),
                        serverNewPromise());
                http2Server.flush(serverCtx());
            }
        });

        assertTrue(clientSettingsLatch1.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertTrue(serverSettingsAckLatch2.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 5, headers, 0, true,
                        newPromise()).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        clientHeadersWriteException2.set(future.cause());
                        clientHeadersLatch.countDown();
                    }
                });
                http2Client.flush(ctx());
            }
        });

        assertTrue(clientHeadersLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertNull(clientHeadersWriteException2.get(),
                "Client write of headers should succeed with increased header list size!");
        assertTrue(serverRevHeadersLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        verify(serverListener, never()).onDataRead(any(ChannelHandlerContext.class), anyInt(), any(ByteBuf.class),
                anyInt(), anyBoolean());

        // Verify that no errors have been received.
        verify(serverListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));
        verify(serverListener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(), anyLong());
        verify(clientListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));
        verify(clientListener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(), anyLong());
    }

    @Test
    public void testSettingsAckIsSentBeforeUsingFlowControl() throws Exception {
        bootstrapEnv(1, 1, 1, 1);

        final CountDownLatch serverSettingsAckLatch1 = new CountDownLatch(1);
        final CountDownLatch serverSettingsAckLatch2 = new CountDownLatch(2);
        final CountDownLatch serverDataLatch = new CountDownLatch(1);
        final CountDownLatch clientWriteDataLatch = new CountDownLatch(1);
        final byte[] data = new byte[] {1, 2, 3, 4, 5};
        final ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                serverSettingsAckLatch1.countDown();
                serverSettingsAckLatch2.countDown();
                return null;
            }
        }).when(serverListener).onSettingsAckRead(any(ChannelHandlerContext.class));
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock in) throws Throwable {
                ByteBuf buf = (ByteBuf) in.getArguments()[2];
                int padding = (Integer) in.getArguments()[3];
                int processedBytes = buf.readableBytes() + padding;

                buf.readBytes(out, buf.readableBytes());
                serverDataLatch.countDown();
                return processedBytes;
            }
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3),
                any(ByteBuf.class), eq(0), anyBoolean());

        final Http2Headers headers = dummyHeaders();

        // The server initially reduces the connection flow control window to 0.
        runInChannel(serverConnectedChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Server.encoder().writeSettings(serverCtx(),
                        new Http2Settings().copyFrom(http2Server.decoder().localSettings())
                                .initialWindowSize(0),
                        serverNewPromise());
                http2Server.flush(serverCtx());
            }
        });

        assertTrue(serverSettingsAckLatch1.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        // The client should now attempt to send data, but the window size is 0 so it will be queued in the flow
        // controller.
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0, false,
                        newPromise());
                http2Client.encoder().writeData(ctx(), 3, Unpooled.wrappedBuffer(data), 0, true, newPromise());
                http2Client.flush(ctx());
                clientWriteDataLatch.countDown();
            }
        });

        assertTrue(clientWriteDataLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        // Now the server opens up the connection window to allow the client to send the pending data.
        runInChannel(serverConnectedChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Server.encoder().writeSettings(serverCtx(),
                        new Http2Settings().copyFrom(http2Server.decoder().localSettings())
                                .initialWindowSize(data.length),
                        serverNewPromise());
                http2Server.flush(serverCtx());
            }
        });

        assertTrue(serverSettingsAckLatch2.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertTrue(serverDataLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertArrayEquals(data, out.toByteArray());

        // Verify that no errors have been received.
        verify(serverListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));
        verify(serverListener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(), anyLong());
        verify(clientListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));
        verify(clientListener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(), anyLong());
    }

    @Test
    public void priorityUsingHigherValuedStreamIdDoesNotPreventUsingLowerStreamId() throws Exception {
        bootstrapEnv(1, 1, 2, 0);

        final Http2Headers headers = dummyHeaders();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writePriority(ctx(), 5, 3, (short) 14, false, newPromise());
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0, false,
                        newPromise());
                http2Client.flush(ctx());
            }
        });

        assertTrue(serverSettingsAckLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertTrue(requestLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        verify(serverListener).onPriorityRead(any(ChannelHandlerContext.class), eq(5), eq(3), eq((short) 14),
                eq(false));
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(headers), eq(0),
                eq((short) 16), eq(false), eq(0), eq(false));

        // Verify that no errors have been received.
        verify(serverListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));
        verify(serverListener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(), anyLong());
        verify(clientListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));
        verify(clientListener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(), anyLong());
    }

    @Test
    public void headersUsingHigherValuedStreamIdPreventsUsingLowerStreamId() throws Exception {
        bootstrapEnv(1, 1, 1, 0);

        final Http2Headers headers = dummyHeaders();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 5, headers, 0, (short) 16, false, 0, false,
                        newPromise());
                http2Client.encoder().frameWriter().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0, false,
                        newPromise());
                http2Client.flush(ctx());
            }
        });

        assertTrue(serverSettingsAckLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertTrue(requestLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(5), eq(headers), eq(0),
                eq((short) 16), eq(false), eq(0), eq(false));
        verify(serverListener, never()).onHeadersRead(any(ChannelHandlerContext.class), eq(3), any(Http2Headers.class),
                anyInt(), anyShort(), anyBoolean(), anyInt(), anyBoolean());

        // Client should receive a RST_STREAM for stream 3, but there is not Http2Stream object so the listener is never
        // notified.
        verify(serverListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));
        verify(serverListener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(), anyLong());
        verify(clientListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));
        verify(clientListener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(), anyLong());
    }

    @Test
    public void headersWriteForPeerStreamWhichWasResetShouldNotGoAway() throws Exception {
        bootstrapEnv(1, 1, 1, 0);

        final CountDownLatch serverGotRstLatch = new CountDownLatch(1);
        final CountDownLatch serverWriteHeadersLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> serverWriteHeadersCauseRef = new AtomicReference<Throwable>();

        final int streamId = 3;
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                if (streamId == (Integer) invocationOnMock.getArgument(1)) {
                    serverGotRstLatch.countDown();
                }
                return null;
            }
        }).when(serverListener).onRstStreamRead(any(ChannelHandlerContext.class), eq(streamId), anyLong());

        final Http2Headers headers = dummyHeaders();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), streamId, headers, CONNECTION_STREAM_ID,
                        DEFAULT_PRIORITY_WEIGHT, false, 0, false, newPromise());
                http2Client.encoder().writeRstStream(ctx(), streamId, Http2Error.CANCEL.code(), newPromise());
                http2Client.flush(ctx());
            }
        });

        assertTrue(serverSettingsAckLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertTrue(serverGotRstLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(streamId), eq(headers), anyInt(),
                anyShort(), anyBoolean(), anyInt(), eq(false));

        // Now have the server attempt to send a headers frame simulating some asynchronous work.
        runInChannel(serverConnectedChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Server.encoder().writeHeaders(serverCtx(), streamId, headers, 0, true, serverNewPromise())
                        .addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                serverWriteHeadersCauseRef.set(future.cause());
                                serverWriteHeadersLatch.countDown();
                            }
                        });
                http2Server.flush(serverCtx());
            }
        });

        assertTrue(serverWriteHeadersLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        Throwable serverWriteHeadersCause = serverWriteHeadersCauseRef.get();
        assertNotNull(serverWriteHeadersCause);
        assertThat(serverWriteHeadersCauseRef.get(), not(instanceOf(Http2Exception.class)));

        // Server should receive a RST_STREAM for stream 3.
        verify(serverListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));
        verify(clientListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));
        verify(clientListener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(), anyLong());
    }

    @Test
    public void http2ExceptionInPipelineShouldCloseConnection() throws Exception {
        bootstrapEnv(1, 1, 2, 1);

        // Create a latch to track when the close occurs.
        final CountDownLatch closeLatch = new CountDownLatch(1);
        clientChannel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                closeLatch.countDown();
            }
        });

        // Create a single stream by sending a HEADERS frame to the server.
        final Http2Headers headers = dummyHeaders();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0, false,
                        newPromise());
                http2Client.flush(ctx());
            }
        });

        // Wait for the server to create the stream.
        assertTrue(serverSettingsAckLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertTrue(requestLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        // Add a handler that will immediately throw an exception.
        clientChannel.pipeline().addFirst(new ChannelHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                throw Http2Exception.connectionError(PROTOCOL_ERROR, "Fake Exception");
            }
        });

        // Wait for the close to occur.
        assertTrue(closeLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertFalse(clientChannel.isOpen());
    }

    @Test
    public void listenerExceptionShouldCloseConnection() throws Exception {
        final Http2Headers headers = dummyHeaders();
        doThrow(new RuntimeException("Fake Exception")).when(serverListener).onHeadersRead(
                any(ChannelHandlerContext.class), eq(3), eq(headers), eq(0), eq((short) 16),
                eq(false), eq(0), eq(false));

        bootstrapEnv(1, 0, 1, 1);

        // Create a latch to track when the close occurs.
        final CountDownLatch closeLatch = new CountDownLatch(1);
        clientChannel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                closeLatch.countDown();
            }
        });

        // Create a single stream by sending a HEADERS frame to the server.
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0, false,
                        newPromise());
                http2Client.flush(ctx());
            }
        });

        // Wait for the server to create the stream.
        assertTrue(serverSettingsAckLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertTrue(requestLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        // Wait for the close to occur.
        assertTrue(closeLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertFalse(clientChannel.isOpen());
    }

    private enum WriteEmptyBufferMode {
        SINGLE_END_OF_STREAM,
        SECOND_END_OF_STREAM,
        SINGLE_WITH_TRAILERS,
        SECOND_WITH_TRAILERS
    }

    @Test
    public void writeOfEmptyReleasedBufferSingleBufferQueuedInFlowControllerShouldFail() throws Exception {
        writeOfEmptyReleasedBufferQueuedInFlowControllerShouldFail(WriteEmptyBufferMode.SINGLE_END_OF_STREAM);
    }

    @Test
    public void writeOfEmptyReleasedBufferSingleBufferTrailersQueuedInFlowControllerShouldFail() throws Exception {
        writeOfEmptyReleasedBufferQueuedInFlowControllerShouldFail(WriteEmptyBufferMode.SINGLE_WITH_TRAILERS);
    }

    @Test
    public void writeOfEmptyReleasedBufferMultipleBuffersQueuedInFlowControllerShouldFail() throws Exception {
        writeOfEmptyReleasedBufferQueuedInFlowControllerShouldFail(WriteEmptyBufferMode.SECOND_END_OF_STREAM);
    }

    @Test
    public void writeOfEmptyReleasedBufferMultipleBuffersTrailersQueuedInFlowControllerShouldFail() throws Exception {
        writeOfEmptyReleasedBufferQueuedInFlowControllerShouldFail(WriteEmptyBufferMode.SECOND_WITH_TRAILERS);
    }

    private void writeOfEmptyReleasedBufferQueuedInFlowControllerShouldFail(final WriteEmptyBufferMode mode)
            throws Exception {
        bootstrapEnv(1, 1, 2, 1);

        final ChannelPromise emptyDataPromise = newPromise();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, EmptyHttp2Headers.INSTANCE, 0, (short) 16, false, 0, false,
                        newPromise());
                ByteBuf emptyBuf = Unpooled.buffer();
                emptyBuf.release();
                switch (mode) {
                    case SINGLE_END_OF_STREAM:
                        http2Client.encoder().writeData(ctx(), 3, emptyBuf, 0, true, emptyDataPromise);
                        break;
                    case SECOND_END_OF_STREAM:
                        http2Client.encoder().writeData(ctx(), 3, emptyBuf, 0, false, emptyDataPromise);
                        http2Client.encoder().writeData(ctx(), 3, randomBytes(8), 0, true, newPromise());
                        break;
                    case SINGLE_WITH_TRAILERS:
                        http2Client.encoder().writeData(ctx(), 3, emptyBuf, 0, false, emptyDataPromise);
                        http2Client.encoder().writeHeaders(ctx(), 3, EmptyHttp2Headers.INSTANCE, 0,
                                (short) 16, false, 0, true, newPromise());
                        break;
                    case SECOND_WITH_TRAILERS:
                        http2Client.encoder().writeData(ctx(), 3, emptyBuf, 0, false, emptyDataPromise);
                        http2Client.encoder().writeData(ctx(), 3, randomBytes(8), 0, false, newPromise());
                        http2Client.encoder().writeHeaders(ctx(), 3, EmptyHttp2Headers.INSTANCE, 0,
                                (short) 16, false, 0, true, newPromise());
                        break;
                    default:
                        throw new Error();
                }
                http2Client.flush(ctx());
            }
        });

        ExecutionException e = assertThrows(ExecutionException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                emptyDataPromise.get();
            }
        });
        assertThat(e.getCause(), is(instanceOf(IllegalReferenceCountException.class)));
    }

    @Test
    public void writeFailureFlowControllerRemoveFrame()
            throws Exception {
        bootstrapEnv(1, 1, 2, 1);

        final ChannelPromise dataPromise = newPromise();
        final ChannelPromise assertPromise = newPromise();

        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, EmptyHttp2Headers.INSTANCE, 0, (short) 16, false, 0, false,
                        newPromise());
                clientChannel.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        ReferenceCountUtil.release(msg);

                        // Ensure we update the window size so we will try to write the rest of the frame while
                        // processing the flush.
                        http2Client.encoder().flowController().initialWindowSize(8);
                        promise.setFailure(new IllegalStateException());
                    }
                });

                http2Client.encoder().flowController().initialWindowSize(4);
                http2Client.encoder().writeData(ctx(), 3, randomBytes(8), 0, false, dataPromise);
                assertTrue(http2Client.encoder().flowController()
                        .hasFlowControlled(http2Client.connection().stream(3)));

                http2Client.flush(ctx());

                try {
                    // The Frame should have been removed after the write failed.
                    assertFalse(http2Client.encoder().flowController()
                            .hasFlowControlled(http2Client.connection().stream(3)));
                    assertPromise.setSuccess();
                } catch (Throwable error) {
                    assertPromise.setFailure(error);
                }
            }
        });

        ExecutionException e = assertThrows(ExecutionException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                dataPromise.get();
            }
        });
        assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
        assertPromise.sync();
    }

    @Test
    public void nonHttp2ExceptionInPipelineShouldNotCloseConnection() throws Exception {
        bootstrapEnv(1, 1, 2, 1);

        // Create a latch to track when the close occurs.
        final CountDownLatch closeLatch = new CountDownLatch(1);
        clientChannel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                closeLatch.countDown();
            }
        });

        // Create a single stream by sending a HEADERS frame to the server.
        final Http2Headers headers = dummyHeaders();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0, false,
                        newPromise());
                http2Client.flush(ctx());
            }
        });

        // Wait for the server to create the stream.
        assertTrue(serverSettingsAckLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertTrue(requestLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        // Add a handler that will immediately throw an exception.
        clientChannel.pipeline().addFirst(new ChannelHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                throw new RuntimeException("Fake Exception");
            }
        });

        // The close should NOT occur.
        assertFalse(closeLatch.await(2, SECONDS));
        assertTrue(clientChannel.isOpen());

        // Set the timeout very low because we know graceful shutdown won't complete
        setClientGracefulShutdownTime(0);
    }

    @Test
    public void noMoreStreamIdsShouldSendGoAway() throws Exception {
        bootstrapEnv(1, 1, 3, 1, 1);

        // Don't wait for the server to close streams
        setClientGracefulShutdownTime(0);

        // Create a single stream by sending a HEADERS frame to the server.
        final Http2Headers headers = dummyHeaders();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0,
                        true, newPromise());
                http2Client.flush(ctx());
            }
        });

        assertTrue(serverSettingsAckLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), MAX_VALUE + 1, headers, 0, (short) 16, false, 0,
                        true, newPromise());
                http2Client.flush(ctx());
            }
        });

        assertTrue(goAwayLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        verify(serverListener).onGoAwayRead(any(ChannelHandlerContext.class), eq(0),
                eq(PROTOCOL_ERROR.code()), any(ByteBuf.class));
    }

    @Test
    public void createStreamAfterReceiveGoAwayShouldNotSendGoAway() throws Exception {
        bootstrapEnv(1, 1, 2, 1, 1);

        // We want both sides to do graceful shutdown during the test.
        setClientGracefulShutdownTime(10000);
        setServerGracefulShutdownTime(10000);

        final CountDownLatch clientGoAwayLatch = new CountDownLatch(1);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                clientGoAwayLatch.countDown();
                return null;
            }
        }).when(clientListener).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(), any(ByteBuf.class));

        // Create a single stream by sending a HEADERS frame to the server.
        final Http2Headers headers = dummyHeaders();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0,
                        false, newPromise());
                http2Client.flush(ctx());
            }
        });

        assertTrue(serverSettingsAckLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        // Server has received the headers, so the stream is open
        assertTrue(requestLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        runInChannel(serverChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Server.encoder().writeGoAway(serverCtx(), 3, NO_ERROR.code(), EMPTY_BUFFER, serverNewPromise());
                http2Server.flush(serverCtx());
            }
        });

        // wait for the client to receive the GO_AWAY.
        assertTrue(clientGoAwayLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        verify(clientListener).onGoAwayRead(any(ChannelHandlerContext.class), eq(3), eq(NO_ERROR.code()),
                any(ByteBuf.class));

        final AtomicReference<ChannelFuture> clientWriteAfterGoAwayFutureRef = new AtomicReference<ChannelFuture>();
        final CountDownLatch clientWriteAfterGoAwayLatch = new CountDownLatch(1);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                ChannelFuture f = http2Client.encoder().writeHeaders(ctx(), 5, headers, 0, (short) 16, false, 0,
                        true, newPromise());
                clientWriteAfterGoAwayFutureRef.set(f);
                http2Client.flush(ctx());
                f.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        clientWriteAfterGoAwayLatch.countDown();
                    }
                });
            }
        });

        // Wait for the client's write operation to complete.
        assertTrue(clientWriteAfterGoAwayLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        ChannelFuture clientWriteAfterGoAwayFuture = clientWriteAfterGoAwayFutureRef.get();
        assertNotNull(clientWriteAfterGoAwayFuture);
        Throwable clientCause = clientWriteAfterGoAwayFuture.cause();
        assertThat(clientCause, is(instanceOf(Http2Exception.StreamException.class)));
        assertEquals(Http2Error.REFUSED_STREAM.code(), ((Http2Exception.StreamException) clientCause).error().code());

        // Wait for the server to receive a GO_AWAY, but this is expected to timeout!
        assertFalse(goAwayLatch.await(1, SECONDS));
        verify(serverListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
                any(ByteBuf.class));

        // Shutdown shouldn't wait for the server to close streams
        setClientGracefulShutdownTime(0);
        setServerGracefulShutdownTime(0);
    }

    @Test
    public void listenerIsNotifiedOfGoawayBeforeStreamsAreRemovedFromTheConnection() throws Exception {
        bootstrapEnv(1, 1, 2, 1, 1);

        // We want both sides to do graceful shutdown during the test.
        setClientGracefulShutdownTime(10000);
        setServerGracefulShutdownTime(10000);

        final AtomicReference<Http2Stream.State> clientStream3State = new AtomicReference<Http2Stream.State>();
        final CountDownLatch clientGoAwayLatch = new CountDownLatch(1);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                clientStream3State.set(http2Client.connection().stream(3).state());
                clientGoAwayLatch.countDown();
                return null;
            }
        }).when(clientListener).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(), any(ByteBuf.class));

        // Create a single stream by sending a HEADERS frame to the server.
        final Http2Headers headers = dummyHeaders();
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Client.encoder().writeHeaders(ctx(), 1, headers, 0, (short) 16, false, 0,
                    false, newPromise());
                http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0,
                    false, newPromise());
                http2Client.flush(ctx());
            }
        });

        assertTrue(serverSettingsAckLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        // Server has received the headers, so the stream is open
        assertTrue(requestLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

        runInChannel(serverChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                http2Server.encoder().writeGoAway(serverCtx(), 1, NO_ERROR.code(), EMPTY_BUFFER, serverNewPromise());
                http2Server.flush(serverCtx());
            }
        });

        // wait for the client to receive the GO_AWAY.
        assertTrue(clientGoAwayLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        verify(clientListener).onGoAwayRead(any(ChannelHandlerContext.class), eq(1), eq(NO_ERROR.code()),
            any(ByteBuf.class));
        assertEquals(Http2Stream.State.OPEN, clientStream3State.get());

        // Make sure that stream 3 has been closed which is true if it's gone.
        final CountDownLatch probeStreamCount = new CountDownLatch(1);
        final AtomicBoolean stream3Exists = new AtomicBoolean();
        final AtomicInteger streamCount = new AtomicInteger();
        runInChannel(this.clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                stream3Exists.set(http2Client.connection().stream(3) != null);
                streamCount.set(http2Client.connection().numActiveStreams());
                probeStreamCount.countDown();
            }
        });
        // The stream should be closed right after
        assertTrue(probeStreamCount.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        assertEquals(1, streamCount.get());
        assertFalse(stream3Exists.get());

        // Wait for the server to receive a GO_AWAY, but this is expected to timeout!
        assertFalse(goAwayLatch.await(1, SECONDS));
        verify(serverListener, never()).onGoAwayRead(any(ChannelHandlerContext.class), anyInt(), anyLong(),
            any(ByteBuf.class));

        // Shutdown shouldn't wait for the server to close streams
        setClientGracefulShutdownTime(0);
        setServerGracefulShutdownTime(0);
    }

    @Test
    public void flowControlProperlyChunksLargeMessage() throws Exception {
        final Http2Headers headers = dummyHeaders();

        // Create a large message to send.
        final int length = 10485760; // 10MB

        // Create a buffer filled with random bytes.
        final ByteBuf data = randomBytes(length);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock in) throws Throwable {
                ByteBuf buf = (ByteBuf) in.getArguments()[2];
                int padding = (Integer) in.getArguments()[3];
                int processedBytes = buf.readableBytes() + padding;

                buf.readBytes(out, buf.readableBytes());
                return processedBytes;
            }
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3),
                any(ByteBuf.class), eq(0), anyBoolean());
        try {
            // Initialize the data latch based on the number of bytes expected.
            bootstrapEnv(length, 1, 2, 1);

            // Create the stream and send all of the data at once.
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0,
                            false, newPromise());
                    http2Client.encoder().writeData(ctx(), 3, data.retainedDuplicate(), 0, false, newPromise());

                    // Write trailers.
                    http2Client.encoder().writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0,
                            true, newPromise());
                    http2Client.flush(ctx());
                }
            });

            // Wait for the trailers to be received.
            assertTrue(serverSettingsAckLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
            assertTrue(trailersLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));

            // Verify that headers and trailers were received.
            verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(headers), eq(0),
                    eq((short) 16), eq(false), eq(0), eq(false));
            verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(headers), eq(0),
                    eq((short) 16), eq(false), eq(0), eq(true));

            // Verify we received all the bytes.
            assertEquals(0, dataLatch.getCount());
            out.flush();
            byte[] received = out.toByteArray();
            assertArrayEquals(data.array(), received);
        } finally {
            // Don't wait for server to close streams
            setClientGracefulShutdownTime(0);
            data.release();
            out.close();
        }
    }

    @Test
    public void stressTest() throws Exception {
        final Http2Headers headers = dummyHeaders();
        int length = 10;
        final ByteBuf data = randomBytes(length);
        final String dataAsHex = ByteBufUtil.hexDump(data);
        final long pingData = 8;
        final int numStreams = 2000;

        // Collect all the ping buffers as we receive them at the server.
        final long[] receivedPings = new long[numStreams];
        doAnswer(new Answer<Void>() {
            int nextIndex;

            @Override
            public Void answer(InvocationOnMock in) throws Throwable {
                receivedPings[nextIndex++] = (Long) in.getArguments()[1];
                return null;
            }
        }).when(serverListener).onPingRead(any(ChannelHandlerContext.class), any(Long.class));

        // Collect all the data buffers as we receive them at the server.
        final StringBuilder[] receivedData = new StringBuilder[numStreams];
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock in) throws Throwable {
                int streamId = (Integer) in.getArguments()[1];
                ByteBuf buf = (ByteBuf) in.getArguments()[2];
                int padding = (Integer) in.getArguments()[3];
                int processedBytes = buf.readableBytes() + padding;

                int streamIndex = (streamId - 3) / 2;
                StringBuilder builder = receivedData[streamIndex];
                if (builder == null) {
                    builder = new StringBuilder(dataAsHex.length());
                    receivedData[streamIndex] = builder;
                }
                builder.append(ByteBufUtil.hexDump(buf));
                return processedBytes;
            }
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), anyInt(),
                any(ByteBuf.class), anyInt(), anyBoolean());
        try {
            bootstrapEnv(numStreams * length, 1, numStreams * 4, numStreams);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    int upperLimit = 3 + 2 * numStreams;
                    for (int streamId = 3; streamId < upperLimit; streamId += 2) {
                        // Send a bunch of data on each stream.
                        http2Client.encoder().writeHeaders(ctx(), streamId, headers, 0, (short) 16,
                                false, 0, false, newPromise());
                        http2Client.encoder().writePing(ctx(), false, pingData,
                                newPromise());
                        http2Client.encoder().writeData(ctx(), streamId, data.retainedSlice(), 0,
                                                        false, newPromise());
                        // Write trailers.
                        http2Client.encoder().writeHeaders(ctx(), streamId, headers, 0, (short) 16,
                                false, 0, true, newPromise());
                        http2Client.flush(ctx());
                    }
                }
            });
            // Wait for all frames to be received.
            assertTrue(serverSettingsAckLatch.await(60, SECONDS));
            assertTrue(trailersLatch.await(60, SECONDS));
            verify(serverListener, times(numStreams)).onHeadersRead(any(ChannelHandlerContext.class), anyInt(),
                    eq(headers), eq(0), eq((short) 16), eq(false), eq(0), eq(false));
            verify(serverListener, times(numStreams)).onHeadersRead(any(ChannelHandlerContext.class), anyInt(),
                    eq(headers), eq(0), eq((short) 16), eq(false), eq(0), eq(true));
            verify(serverListener, times(numStreams)).onPingRead(any(ChannelHandlerContext.class),
                    any(long.class));
            verify(serverListener, never()).onDataRead(any(ChannelHandlerContext.class),
                    anyInt(), any(ByteBuf.class), eq(0), eq(true));
            for (StringBuilder builder : receivedData) {
                assertEquals(dataAsHex, builder.toString());
            }
            for (long receivedPing : receivedPings) {
                assertEquals(pingData, receivedPing);
            }
        } finally {
            // Don't wait for server to close streams
            setClientGracefulShutdownTime(0);
            data.release();
        }
    }

    private void bootstrapEnv(int dataCountDown, int settingsAckCount,
            int requestCountDown, int trailersCountDown) throws Exception {
        bootstrapEnv(dataCountDown, settingsAckCount, requestCountDown, trailersCountDown, -1);
    }

    private void bootstrapEnv(int dataCountDown, int settingsAckCount,
            int requestCountDown, int trailersCountDown, int goAwayCountDown) throws Exception {
        final CountDownLatch prefaceWrittenLatch = new CountDownLatch(1);
        requestLatch = new CountDownLatch(requestCountDown);
        serverSettingsAckLatch = new CountDownLatch(settingsAckCount);
        dataLatch = new CountDownLatch(dataCountDown);
        trailersLatch = new CountDownLatch(trailersCountDown);
        goAwayLatch = goAwayCountDown > 0 ? new CountDownLatch(goAwayCountDown) : requestLatch;
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        final AtomicReference<Http2ConnectionHandler> serverHandlerRef = new AtomicReference<Http2ConnectionHandler>();
        final CountDownLatch serverInitLatch = new CountDownLatch(1);
        sb.group(new DefaultEventLoopGroup());
        sb.channel(LocalServerChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                serverConnectedChannel = ch;
                ChannelPipeline p = ch.pipeline();
                serverFrameCountDown =
                        new FrameCountDown(serverListener, serverSettingsAckLatch,
                                requestLatch, dataLatch, trailersLatch, goAwayLatch);
                serverHandlerRef.set(new Http2ConnectionHandlerBuilder()
                        .server(true)
                        .frameListener(serverFrameCountDown)
                        .validateHeaders(false)
                        .build());
                p.addLast(serverHandlerRef.get());
                serverInitLatch.countDown();
            }
        });

        cb.group(new DefaultEventLoopGroup());
        cb.channel(LocalChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new Http2ConnectionHandlerBuilder()
                        .server(false)
                        .frameListener(clientListener)
                        .validateHeaders(false)
                        .gracefulShutdownTimeoutMillis(0)
                        .build());
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == Http2ConnectionPrefaceAndSettingsFrameWrittenEvent.INSTANCE) {
                            prefaceWrittenLatch.countDown();
                            ctx.pipeline().remove(this);
                        }
                    }
                });
            }
        });

        serverChannel = sb.bind(new LocalAddress("Http2ConnectionRoundtripTest")).sync().channel();

        ChannelFuture ccf = cb.connect(serverChannel.localAddress());
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
        assertTrue(prefaceWrittenLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        http2Client = clientChannel.pipeline().get(Http2ConnectionHandler.class);
        assertTrue(serverInitLatch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
        http2Server = serverHandlerRef.get();
    }

    private ChannelHandlerContext ctx() {
        return clientChannel.pipeline().firstContext();
    }

    private ChannelHandlerContext serverCtx() {
        return serverConnectedChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromise() {
        return ctx().newPromise();
    }

    private ChannelPromise serverNewPromise() {
        return serverCtx().newPromise();
    }

    private static Http2Headers dummyHeaders() {
        return new DefaultHttp2Headers(false).method(new AsciiString("GET")).scheme(new AsciiString("https"))
        .authority(new AsciiString("example.org")).path(new AsciiString("/some/path/resource2"))
        .add(randomString(), randomString());
    }

    private static void mockFlowControl(Http2FrameListener listener) throws Http2Exception {
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                ByteBuf buf = (ByteBuf) invocation.getArguments()[2];
                int padding = (Integer) invocation.getArguments()[3];
                int processedBytes = buf.readableBytes() + padding;
                return processedBytes;
            }

        }).when(listener).onDataRead(any(ChannelHandlerContext.class), anyInt(),
                any(ByteBuf.class), anyInt(), anyBoolean());
    }

    private void setClientGracefulShutdownTime(final long millis) throws InterruptedException {
        setGracefulShutdownTime(clientChannel, http2Client, millis);
    }

    private void setServerGracefulShutdownTime(final long millis) throws InterruptedException {
        setGracefulShutdownTime(serverChannel, http2Server, millis);
    }

    private static void setGracefulShutdownTime(Channel channel, final Http2ConnectionHandler handler,
                                                final long millis) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        runInChannel(channel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                handler.gracefulShutdownTimeoutMillis(millis);
                latch.countDown();
            }
        });

        assertTrue(latch.await(DEFAULT_AWAIT_TIMEOUT_SECONDS, SECONDS));
    }

    /**
     * Creates a {@link ByteBuf} of the given length, filled with random bytes.
     */
    private static ByteBuf randomBytes(int length) {
        final byte[] bytes = new byte[length];
        new Random().nextBytes(bytes);
        return Unpooled.wrappedBuffer(bytes);
    }
}
