/*
 * Copyright 2016 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.handler.codec.http2.LastInboundHandler.Consumer;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.util.AsciiString;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty.handler.codec.http2.Http2TestUtil.anyChannelPromise;
import static io.netty.handler.codec.http2.Http2TestUtil.anyHttp2Settings;
import static io.netty.handler.codec.http2.Http2TestUtil.assertEqualsAndRelease;
import static io.netty.handler.codec.http2.Http2TestUtil.bb;
import static io.netty.util.ReferenceCountUtil.release;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class Http2MultiplexTest<C extends Http2FrameCodec> {
    private final Http2Headers request = new DefaultHttp2Headers()
            .method(HttpMethod.GET.asciiName()).scheme(HttpScheme.HTTPS.name())
            .authority(new AsciiString("example.org")).path(new AsciiString("/foo"));

    private EmbeddedChannel parentChannel;
    private Http2FrameWriter frameWriter;
    private Http2FrameInboundWriter frameInboundWriter;
    private TestChannelInitializer childChannelInitializer;
    private C codec;

    private static final int initialRemoteStreamWindow = 1024;

    protected abstract C newCodec(TestChannelInitializer childChannelInitializer,  Http2FrameWriter frameWriter);
    protected abstract ChannelHandler newMultiplexer(TestChannelInitializer childChannelInitializer);

    @BeforeEach
    public void setUp() {
        childChannelInitializer = new TestChannelInitializer();
        parentChannel = new EmbeddedChannel();
        frameInboundWriter = new Http2FrameInboundWriter(parentChannel);
        parentChannel.connect(new InetSocketAddress(0));
        frameWriter = Http2TestUtil.mockedFrameWriter();
        codec = newCodec(childChannelInitializer, frameWriter);
        parentChannel.pipeline().addLast(codec);
        ChannelHandler multiplexer = newMultiplexer(childChannelInitializer);
        if (multiplexer != null) {
            parentChannel.pipeline().addLast(multiplexer);
        }

        parentChannel.runPendingTasks();
        parentChannel.pipeline().fireChannelActive();

        parentChannel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());

        Http2Settings settings = new Http2Settings().initialWindowSize(initialRemoteStreamWindow);
        frameInboundWriter.writeInboundSettings(settings);

        verify(frameWriter).writeSettingsAck(eqCodecCtx(), anyChannelPromise());

        frameInboundWriter.writeInboundSettingsAck();

        Http2SettingsFrame settingsFrame = parentChannel.readInbound();
        assertNotNull(settingsFrame);
        Http2SettingsAckFrame settingsAckFrame = parentChannel.readInbound();
        assertNotNull(settingsAckFrame);

        // Handshake
        verify(frameWriter).writeSettings(eqCodecCtx(),
                anyHttp2Settings(), anyChannelPromise());
    }

    private ChannelHandlerContext eqCodecCtx() {
        return eq(codec.ctx);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (childChannelInitializer.handler instanceof LastInboundHandler) {
            ((LastInboundHandler) childChannelInitializer.handler).finishAndReleaseAll();
        }
        parentChannel.finishAndReleaseAll();
        codec = null;
    }

    // TODO(buchgr): Flush from child channel
    // TODO(buchgr): ChildChannel.childReadComplete()
    // TODO(buchgr): GOAWAY Logic
    // TODO(buchgr): Test ChannelConfig.setMaxMessagesPerRead

    @Test
    public void writeUnknownFrame() {
        Http2StreamChannel childChannel = newOutboundStream(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
                ctx.writeAndFlush(new DefaultHttp2UnknownFrame((byte) 99, new Http2Flags()));
                ctx.fireChannelActive();
            }
        });
        assertTrue(childChannel.isActive());

        parentChannel.runPendingTasks();

        verify(frameWriter).writeFrame(eq(codec.ctx), eq((byte) 99), eqStreamId(childChannel), any(Http2Flags.class),
                any(ByteBuf.class), any(ChannelPromise.class));
    }

    Http2StreamChannel newInboundStream(int streamId, boolean endStream, final ChannelHandler childHandler) {
        return newInboundStream(streamId, endStream, null, childHandler);
    }

    private Http2StreamChannel newInboundStream(int streamId, boolean endStream,
                                                AtomicInteger maxReads, final ChannelHandler childHandler) {
        final AtomicReference<Http2StreamChannel> streamChannelRef = new AtomicReference<Http2StreamChannel>();
        childChannelInitializer.maxReads = maxReads;
        childChannelInitializer.handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                assertNull(streamChannelRef.get());
                streamChannelRef.set((Http2StreamChannel) ctx.channel());
                ctx.pipeline().addLast(childHandler);
                ctx.fireChannelRegistered();
            }
        };

        frameInboundWriter.writeInboundHeaders(streamId, request, 0, endStream);
        parentChannel.runPendingTasks();
        Http2StreamChannel channel = streamChannelRef.get();
        assertEquals(streamId, channel.stream().id());
        return channel;
    }

    @Test
    public void readUnkownFrame() {
        LastInboundHandler handler = new LastInboundHandler();

        Http2StreamChannel channel = newInboundStream(3, true, handler);
        frameInboundWriter.writeInboundFrame((byte) 99, channel.stream().id(), new Http2Flags(), Unpooled.EMPTY_BUFFER);

        // header frame and unknown frame
        verifyFramesMultiplexedToCorrectChannel(channel, handler, 2);

        Channel childChannel = newOutboundStream(new ChannelInboundHandlerAdapter());
        assertTrue(childChannel.isActive());
    }

    @Test
    public void readPriorityFrame() {
        LastInboundHandler handler = new LastInboundHandler();

        Http2StreamChannel channel = newInboundStream(3, true, handler);
        frameInboundWriter.writeInboundPriority(channel.stream().id(), 0, (short) 2, false);

        // header frame should be multiplexed via fireChannelRead(...)
        int numFrames = useUserEventForPriorityFrame() ? 1 : 2;
        verifyFramesMultiplexedToCorrectChannel(channel, handler, numFrames);

        if (numFrames == 1) {
            Http2PriorityFrame priorityFrame = handler.readUserEvent();
            assertEquals(channel.stream(), priorityFrame.stream());
            assertEquals(0, priorityFrame.streamDependency());
            assertEquals(2, priorityFrame.weight());
            assertFalse(priorityFrame.exclusive());
        }
    }

    @Test
    public void headerAndDataFramesShouldBeDelivered() {
        LastInboundHandler inboundHandler = new LastInboundHandler();

        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(request).stream(channel.stream());
        Http2DataFrame dataFrame1 = new DefaultHttp2DataFrame(bb("hello")).stream(channel.stream());
        Http2DataFrame dataFrame2 = new DefaultHttp2DataFrame(bb("world")).stream(channel.stream());

        assertTrue(inboundHandler.isChannelActive());
        frameInboundWriter.writeInboundData(channel.stream().id(), bb("hello"), 0, false);
        frameInboundWriter.writeInboundData(channel.stream().id(), bb("world"), 0, false);

        assertEquals(headersFrame, inboundHandler.readInbound());

        assertEqualsAndRelease(dataFrame1, inboundHandler.<Http2Frame>readInbound());
        assertEqualsAndRelease(dataFrame2, inboundHandler.<Http2Frame>readInbound());

        assertNull(inboundHandler.readInbound());
    }

     enum RstFrameTestMode {
        HEADERS_END_STREAM,
        DATA_END_STREAM,
        TRAILERS_END_STREAM;
    }
    @ParameterizedTest
    @EnumSource(RstFrameTestMode.class)
    void noRstFrameSentOnCloseViaListener(final RstFrameTestMode mode) throws Exception {
        LastInboundHandler inboundHandler = new LastInboundHandler() {
            private boolean headersReceived;
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                try {
                    final boolean endStream;
                    if (msg instanceof Http2HeadersFrame) {
                        endStream = ((Http2HeadersFrame) msg).isEndStream();
                        switch (mode) {
                            case HEADERS_END_STREAM:
                                assertFalse(headersReceived);
                                assertTrue(endStream);
                                break;
                            case TRAILERS_END_STREAM:
                                if (headersReceived) {
                                    assertTrue(endStream);
                                } else {
                                    assertFalse(endStream);
                                }
                                break;
                            case DATA_END_STREAM:
                                assertFalse(endStream);
                                break;
                            default:
                                fail();
                        }
                        headersReceived = true;
                    } else if (msg instanceof Http2DataFrame) {
                        endStream = ((Http2DataFrame) msg).isEndStream();
                        switch (mode) {
                            case HEADERS_END_STREAM:
                                fail();
                                break;
                            case TRAILERS_END_STREAM:
                                assertFalse(endStream);
                                break;
                            case DATA_END_STREAM:
                                assertTrue(endStream);
                                break;
                            default:
                                fail();
                        }
                    } else {
                        throw new UnsupportedMessageTypeException(msg);
                    }
                    if (endStream) {
                        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(), true, 0))
                                .addListener(ChannelFutureListener.CLOSE);
                    }
                } finally {
                    ReferenceCountUtil.release(msg);
                }
            }
        };

        Http2StreamChannel channel = newInboundStream(3, mode == RstFrameTestMode.HEADERS_END_STREAM, inboundHandler);
        if (mode != RstFrameTestMode.HEADERS_END_STREAM) {
            frameInboundWriter.writeInboundData(
                    channel.stream().id(), bb("something"), 0, mode == RstFrameTestMode.DATA_END_STREAM);
            if (mode != RstFrameTestMode.DATA_END_STREAM) {
                frameInboundWriter.writeInboundHeaders(channel.stream().id(), new DefaultHttp2Headers(), 0, true);
            }
        }
        channel.closeFuture().syncUninterruptibly();

        // We should never produce a RST frame in this case as we received the endOfStream before we write a frame
        // with the endOfStream flag.
        verify(frameWriter, never()).writeRstStream(eqCodecCtx(),
                eqStreamId(channel), anyLong(), anyChannelPromise());
        inboundHandler.checkException();
    }

    @Test
    public void headerMultipleContentLengthValidationShouldPropagate() {
        headerMultipleContentLengthValidationShouldPropagate(false);
    }

    @Test
    public void headerMultipleContentLengthValidationShouldPropagateWithEndStream() {
        headerMultipleContentLengthValidationShouldPropagate(true);
    }

    private void headerMultipleContentLengthValidationShouldPropagate(boolean endStream) {
        final LastInboundHandler inboundHandler = new LastInboundHandler();
        request.addLong(HttpHeaderNames.CONTENT_LENGTH, 0);
        request.addLong(HttpHeaderNames.CONTENT_LENGTH, 1);
        Http2StreamChannel channel = newInboundStream(3, endStream, inboundHandler);

        assertThrows(StreamException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                inboundHandler.checkException();
            }
        });
        assertNull(inboundHandler.readInbound());
        assertFalse(channel.isActive());
    }

    @Test
    public void headerPlusSignContentLengthValidationShouldPropagate() {
        headerSignContentLengthValidationShouldPropagateWithEndStream(false, false);
    }

    @Test
    public void headerPlusSignContentLengthValidationShouldPropagateWithEndStream() {
        headerSignContentLengthValidationShouldPropagateWithEndStream(false, true);
    }

    @Test
    public void headerMinusSignContentLengthValidationShouldPropagate() {
        headerSignContentLengthValidationShouldPropagateWithEndStream(true, false);
    }

    @Test
    public void headerMinusSignContentLengthValidationShouldPropagateWithEndStream() {
        headerSignContentLengthValidationShouldPropagateWithEndStream(true, true);
    }

    private void headerSignContentLengthValidationShouldPropagateWithEndStream(boolean minus, boolean endStream) {
        final LastInboundHandler inboundHandler = new LastInboundHandler();
        request.add(HttpHeaderNames.CONTENT_LENGTH, (minus ? "-" : "+") + 1);
        Http2StreamChannel channel = newInboundStream(3, endStream, inboundHandler);
        assertThrows(StreamException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                inboundHandler.checkException();
            }
        });

        assertNull(inboundHandler.readInbound());
        assertFalse(channel.isActive());
    }

    @Test
    public void headerContentLengthNotMatchValidationShouldPropagate() {
        headerContentLengthNotMatchValidationShouldPropagate(false, false, false);
    }

    @Test
    public void headerContentLengthNotMatchValidationShouldPropagateWithEndStream() {
        headerContentLengthNotMatchValidationShouldPropagate(false, true, false);
    }

    @Test
    public void headerContentLengthNotMatchValidationShouldPropagateCloseLocal() {
        headerContentLengthNotMatchValidationShouldPropagate(true, false, false);
    }

    @Test
    public void headerContentLengthNotMatchValidationShouldPropagateWithEndStreamCloseLocal() {
        headerContentLengthNotMatchValidationShouldPropagate(true, true, false);
    }

    @Test
    public void headerContentLengthNotMatchValidationShouldPropagateTrailers() {
        headerContentLengthNotMatchValidationShouldPropagate(false, false, true);
    }

    @Test
    public void headerContentLengthNotMatchValidationShouldPropagateWithEndStreamTrailers() {
        headerContentLengthNotMatchValidationShouldPropagate(false, true, true);
    }

    @Test
    public void headerContentLengthNotMatchValidationShouldPropagateCloseLocalTrailers() {
        headerContentLengthNotMatchValidationShouldPropagate(true, false, true);
    }

    @Test
    public void headerContentLengthNotMatchValidationShouldPropagateWithEndStreamCloseLocalTrailers() {
        headerContentLengthNotMatchValidationShouldPropagate(true, true, true);
    }

    private void headerContentLengthNotMatchValidationShouldPropagate(
            boolean closeLocal, boolean endStream, boolean trailer) {
        final LastInboundHandler inboundHandler = new LastInboundHandler();
        request.addLong(HttpHeaderNames.CONTENT_LENGTH, 1);
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        assertTrue(channel.isActive());

        if (closeLocal) {
            channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(), true))
                    .syncUninterruptibly();
            assertEquals(Http2Stream.State.HALF_CLOSED_LOCAL, channel.stream().state());
        } else {
            assertEquals(Http2Stream.State.OPEN, channel.stream().state());
        }

        if (trailer) {
            frameInboundWriter.writeInboundHeaders(channel.stream().id(), new DefaultHttp2Headers(), 0, endStream);
        } else {
            frameInboundWriter.writeInboundData(channel.stream().id(), bb("foo"), 0, endStream);
        }

        assertThrows(StreamException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                inboundHandler.checkException();
            }
        });

        Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(request).stream(channel.stream());
        assertEquals(headersFrame, inboundHandler.readInbound());
        assertNull(inboundHandler.readInbound());
        assertFalse(channel.isActive());
    }

    @Test
    public void streamExceptionCauseRstStreamWithProtocolError() {
        request.addLong(HttpHeaderNames.CONTENT_LENGTH, 10);
        Http2StreamChannel channel = newInboundStream(3, false, new ChannelInboundHandlerAdapter());
        channel.pipeline().fireExceptionCaught(new Http2FrameStreamException(channel.stream(),
                Http2Error.PROTOCOL_ERROR, new IllegalArgumentException()));
        assertFalse(channel.isActive());
        verify(frameWriter).writeRstStream(eqCodecCtx(), eq(3),
                eq(Http2Error.PROTOCOL_ERROR.code()), anyChannelPromise());
    }

    @Test
    public void contentLengthNotMatchRstStreamWithProtocolError() {
        final LastInboundHandler inboundHandler = new LastInboundHandler();
        request.addLong(HttpHeaderNames.CONTENT_LENGTH, 10);
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        frameInboundWriter.writeInboundData(3, bb(8), 0, true);
        assertThrows(StreamException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                inboundHandler.checkException();
            }
        });
        assertNotNull(inboundHandler.readInbound());
        assertFalse(channel.isActive());
        verify(frameWriter).writeRstStream(eqCodecCtx(), eq(3),
                eq(Http2Error.PROTOCOL_ERROR.code()), anyChannelPromise());
    }

    @Test
    public void framesShouldBeMultiplexed() {
        LastInboundHandler handler1 = new LastInboundHandler();
        Http2StreamChannel channel1 = newInboundStream(3, false, handler1);
        LastInboundHandler handler2 = new LastInboundHandler();
        Http2StreamChannel channel2 = newInboundStream(5, false, handler2);
        LastInboundHandler handler3 = new LastInboundHandler();
        Http2StreamChannel channel3 = newInboundStream(11, false, handler3);

        verifyFramesMultiplexedToCorrectChannel(channel1, handler1, 1);
        verifyFramesMultiplexedToCorrectChannel(channel2, handler2, 1);
        verifyFramesMultiplexedToCorrectChannel(channel3, handler3, 1);

        frameInboundWriter.writeInboundData(channel2.stream().id(), bb("hello"), 0, false);
        frameInboundWriter.writeInboundData(channel1.stream().id(), bb("foo"), 0, true);
        frameInboundWriter.writeInboundData(channel2.stream().id(), bb("world"), 0, true);
        frameInboundWriter.writeInboundData(channel3.stream().id(), bb("bar"), 0, true);

        verifyFramesMultiplexedToCorrectChannel(channel1, handler1, 1);
        verifyFramesMultiplexedToCorrectChannel(channel2, handler2, 2);
        verifyFramesMultiplexedToCorrectChannel(channel3, handler3, 1);
    }

    @Test
    public void inboundDataFrameShouldUpdateLocalFlowController() throws Http2Exception {
        Http2LocalFlowController flowController = Mockito.mock(Http2LocalFlowController.class);
        codec.connection().local().flowController(flowController);

        LastInboundHandler handler = new LastInboundHandler();
        final Http2StreamChannel channel = newInboundStream(3, false, handler);

        ByteBuf tenBytes = bb("0123456789");

        frameInboundWriter.writeInboundData(channel.stream().id(), tenBytes, 0, true);

        // Verify we marked the bytes as consumed
        verify(flowController).consumeBytes(argThat(new ArgumentMatcher<Http2Stream>() {
            @Override
            public boolean matches(Http2Stream http2Stream) {
                return http2Stream.id() == channel.stream().id();
            }
        }), eq(10));

        // headers and data frame
        verifyFramesMultiplexedToCorrectChannel(channel, handler, 2);
    }

    @Test
    public void unhandledHttp2FramesShouldBePropagated() {
        Http2PingFrame pingFrame = new DefaultHttp2PingFrame(0);
        frameInboundWriter.writeInboundPing(false, 0);
        assertEquals(parentChannel.readInbound(), pingFrame);

        DefaultHttp2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(1,
                parentChannel.alloc().buffer().writeLong(8));
        frameInboundWriter.writeInboundGoAway(0, goAwayFrame.errorCode(), goAwayFrame.content().retainedDuplicate());

        Http2GoAwayFrame frame = parentChannel.readInbound();
        assertEqualsAndRelease(frame, goAwayFrame);
    }

    @Test
    public void channelReadShouldRespectAutoRead() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);
        assertTrue(childChannel.config().isAutoRead());
        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        childChannel.config().setAutoRead(false);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("hello world"), 0, false);
        Http2DataFrame dataFrame0 = inboundHandler.readInbound();
        assertNotNull(dataFrame0);
        release(dataFrame0);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("foo"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar"), 0, false);

        assertNull(inboundHandler.readInbound());

        childChannel.config().setAutoRead(true);
        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 2);
    }

    @Test
    public void noAutoReadWithReentrantReadDoesNotSOOE() {
        final AtomicBoolean shouldRead = new AtomicBoolean();
        Consumer<ChannelHandlerContext> ctxConsumer = new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext obj) {
                if (shouldRead.get()) {
                    obj.read();
                }
            }
        };
        LastInboundHandler inboundHandler = new LastInboundHandler(ctxConsumer);
        AtomicInteger maxReads = new AtomicInteger(1);
        Http2StreamChannel childChannel = newInboundStream(3, false, maxReads, inboundHandler);
        assertTrue(childChannel.config().isAutoRead());
        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        childChannel.config().setAutoRead(false);

        final int maxWrites = 10000; // enough writes to generated SOOE.
        for (int i = 0; i < maxWrites; ++i) {
            frameInboundWriter.writeInboundData(childChannel.stream().id(), bb(String.valueOf(i)), 0, false);
        }
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb(String.valueOf(maxWrites)), 0, true);
        shouldRead.set(true);
        childChannel.read();

        for (int i = 0; i < maxWrites; ++i) {
            Http2DataFrame dataFrame0 = inboundHandler.readInbound();
            assertNotNull(dataFrame0);
            release(dataFrame0);
        }
        Http2DataFrame dataFrame0 = inboundHandler.readInbound();
        assertTrue(dataFrame0.isEndStream());
        release(dataFrame0);

        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 0);
    }

    @Test
    public void readNotRequiredToEndStream() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        AtomicInteger maxReads = new AtomicInteger(1);
        Http2StreamChannel childChannel = newInboundStream(3, false, maxReads, inboundHandler);
        assertTrue(childChannel.config().isAutoRead());

        childChannel.config().setAutoRead(false);

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        assertNull(inboundHandler.readInbound());

        frameInboundWriter.writeInboundRstStream(childChannel.stream().id(), NO_ERROR.code());

        assertFalse(inboundHandler.isChannelActive());
        childChannel.closeFuture().syncUninterruptibly();

        Http2ResetFrame resetFrame = useUserEventForResetFrame() ? inboundHandler.<Http2ResetFrame>readUserEvent() :
                inboundHandler.<Http2ResetFrame>readInbound();

        assertEquals(childChannel.stream(), resetFrame.stream());
        assertEquals(NO_ERROR.code(), resetFrame.errorCode());

        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 0);
    }

    @Test
    public void channelReadShouldRespectAutoReadAndNotProduceNPE() throws Exception {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);
        assertTrue(childChannel.config().isAutoRead());
        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        childChannel.config().setAutoRead(false);
        childChannel.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
            private int count;
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ctx.fireChannelRead(msg);
                // Close channel after 2 reads so there is still something in the inboundBuffer when the close happens.
                if (++count == 2) {
                    ctx.close();
                }
            }
        });
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("hello world"), 0, false);
        Http2DataFrame dataFrame0 = inboundHandler.readInbound();
        assertNotNull(dataFrame0);
        release(dataFrame0);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("foo"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar"), 0, false);

        assertNull(inboundHandler.readInbound());

        childChannel.config().setAutoRead(true);
        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 3);
        inboundHandler.checkException();
    }

    @Test
    public void readInChannelReadWithoutAutoRead() {
        useReadWithoutAutoRead(false);
    }

    @Test
    public void readInChannelReadCompleteWithoutAutoRead() {
        useReadWithoutAutoRead(true);
    }

    private void useReadWithoutAutoRead(final boolean readComplete) {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);
        assertTrue(childChannel.config().isAutoRead());
        childChannel.config().setAutoRead(false);
        assertFalse(childChannel.config().isAutoRead());

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        // Add a handler which will request reads.
        childChannel.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ctx.fireChannelRead(msg);
                if (!readComplete) {
                    ctx.read();
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                ctx.fireChannelReadComplete();
                if (readComplete) {
                    ctx.read();
                }
            }
        });

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("hello world"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("foo"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("hello world"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("foo"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar"), 0, true);

        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 6);
    }

    @Test
    public void allQueuedFramesDeliveredAfterParentIsClosed() throws Exception {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, new AtomicInteger(1), inboundHandler);
        assertTrue(childChannel.config().isAutoRead());
        childChannel.config().setAutoRead(false);
        assertFalse(childChannel.config().isAutoRead());

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("foo"), 0, false);
        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 1);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("baz"), 0, true);
        assertNull(inboundHandler.readInbound());

        parentChannel.close();
        assertTrue(childChannel.isActive());
        childChannel.read();
        inboundHandler.checkException();
        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 2);
        assertFalse(childChannel.isActive());
    }

    private Http2StreamChannel newOutboundStream(ChannelHandler handler) {
        return new Http2StreamChannelBootstrap(parentChannel).handler(handler)
                .open().syncUninterruptibly().getNow();
    }

    /**
     * A child channel for an HTTP/2 stream in IDLE state (that is no headers sent or received),
     * should not emit a RST_STREAM frame on close, as this is a connection error of type protocol error.
     */
    @Test
    public void idleOutboundStreamShouldNotWriteResetFrameOnClose() {
        LastInboundHandler handler = new LastInboundHandler();

        Channel childChannel = newOutboundStream(handler);
        assertTrue(childChannel.isActive());

        childChannel.close();
        parentChannel.runPendingTasks();

        assertFalse(childChannel.isOpen());
        assertFalse(childChannel.isActive());
        assertNull(parentChannel.readOutbound());
    }

    @Test
    public void outboundStreamShouldWriteResetFrameOnClose_headersSent() {
        ChannelHandler handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
                ctx.fireChannelActive();
            }
        };

        Http2StreamChannel childChannel = newOutboundStream(handler);
        assertTrue(childChannel.isActive());

        childChannel.close();
        verify(frameWriter).writeRstStream(eqCodecCtx(),
                eqStreamId(childChannel), eq(Http2Error.CANCEL.code()), anyChannelPromise());
    }

    @Test
    public void outboundStreamShouldNotWriteResetFrameOnClose_IfStreamDidntExist() {
        when(frameWriter.writeHeaders(eqCodecCtx(), anyInt(),
                any(Http2Headers.class), anyInt(), anyBoolean(),
                any(ChannelPromise.class))).thenAnswer(new Answer<ChannelFuture>() {

            private boolean headersWritten;
            @Override
            public ChannelFuture answer(InvocationOnMock invocationOnMock) {
                // We want to fail to write the first headers frame. This is what happens if the connection
                // refuses to allocate a new stream due to having received a GOAWAY.
                if (!headersWritten) {
                    headersWritten = true;
                    return ((ChannelPromise) invocationOnMock.getArgument(5)).setFailure(new Exception("boom"));
                }
                return ((ChannelPromise) invocationOnMock.getArgument(5)).setSuccess();
            }
        });

        Http2StreamChannel childChannel = newOutboundStream(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
                ctx.fireChannelActive();
            }
        });

        assertFalse(childChannel.isActive());

        childChannel.close();
        parentChannel.runPendingTasks();
        // The channel was never active so we should not generate a RST frame.
        verify(frameWriter, never()).writeRstStream(eqCodecCtx(), eqStreamId(childChannel), anyLong(),
                anyChannelPromise());

        assertTrue(parentChannel.outboundMessages().isEmpty());
    }

    @Test
    public void inboundRstStreamFireChannelInactive() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        assertTrue(inboundHandler.isChannelActive());
        frameInboundWriter.writeInboundRstStream(channel.stream().id(), Http2Error.INTERNAL_ERROR.code());

        assertFalse(inboundHandler.isChannelActive());

        // A RST_STREAM frame should NOT be emitted, as we received a RST_STREAM.
        verify(frameWriter, never()).writeRstStream(eqCodecCtx(), eqStreamId(channel),
                anyLong(), anyChannelPromise());
    }

    @Test
    public void streamExceptionTriggersChildChannelExceptionAndClose() throws Exception {
        final LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        assertTrue(channel.isActive());
        StreamException cause = new StreamException(channel.stream().id(), Http2Error.PROTOCOL_ERROR, "baaam!");
        parentChannel.pipeline().fireExceptionCaught(cause);

        assertFalse(channel.isActive());

        assertThrows(StreamException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                inboundHandler.checkException();
            }
        });
    }

    @Test
    public void streamClosedErrorTranslatedToClosedChannelExceptionOnWrites() throws Exception {
        LastInboundHandler inboundHandler = new LastInboundHandler();

        final Http2StreamChannel childChannel = newOutboundStream(inboundHandler);
        assertTrue(childChannel.isActive());

        Http2Headers headers = new DefaultHttp2Headers();
        when(frameWriter.writeHeaders(eqCodecCtx(), anyInt(),
                eq(headers), anyInt(), anyBoolean(),
                any(ChannelPromise.class))).thenAnswer(new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocationOnMock) {
                return ((ChannelPromise) invocationOnMock.getArgument(5)).setFailure(
                        new StreamException(childChannel.stream().id(), Http2Error.STREAM_CLOSED, "Stream Closed"));
            }
        });
        final ChannelFuture future = childChannel.writeAndFlush(
                new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));

        parentChannel.flush();

        assertFalse(childChannel.isActive());
        assertFalse(childChannel.isOpen());

        inboundHandler.checkException();

        assertThrows(ClosedChannelException.class, new Executable() {
            @Override
            public void execute() {
                future.syncUninterruptibly();
            }
        });
    }

    @Test
    public void creatingWritingReadingAndClosingOutboundStreamShouldWork() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newOutboundStream(inboundHandler);
        assertTrue(childChannel.isActive());
        assertTrue(inboundHandler.isChannelActive());

        // Write to the child channel
        Http2Headers headers = new DefaultHttp2Headers().scheme("https").method("GET").path("/foo.txt");
        childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(headers));

        // Read from the child channel
        frameInboundWriter.writeInboundHeaders(childChannel.stream().id(), headers, 0, false);

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);
        assertEquals(headers, headersFrame.headers());

        // Close the child channel.
        childChannel.close();

        parentChannel.runPendingTasks();
        // An active outbound stream should emit a RST_STREAM frame.
        verify(frameWriter).writeRstStream(eqCodecCtx(), eqStreamId(childChannel),
                anyLong(), anyChannelPromise());

        assertFalse(childChannel.isOpen());
        assertFalse(childChannel.isActive());
        assertFalse(inboundHandler.isChannelActive());
    }

    // Test failing the promise of the first headers frame of an outbound stream. In practice this error case would most
    // likely happen due to the max concurrent streams limit being hit or the channel running out of stream identifiers.
    //
    @Test
    public void failedOutboundStreamCreationThrowsAndClosesChannel() throws Exception {
        LastInboundHandler handler = new LastInboundHandler();
        Http2StreamChannel childChannel = newOutboundStream(handler);
        assertTrue(childChannel.isActive());

        Http2Headers headers = new DefaultHttp2Headers();
        when(frameWriter.writeHeaders(eqCodecCtx(), anyInt(),
               eq(headers), anyInt(), anyBoolean(),
               any(ChannelPromise.class))).thenAnswer(new Answer<ChannelFuture>() {
           @Override
           public ChannelFuture answer(InvocationOnMock invocationOnMock) {
               return ((ChannelPromise) invocationOnMock.getArgument(5)).setFailure(
                       new Http2NoMoreStreamIdsException());
            }
        });

        final ChannelFuture future = childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(headers));
        parentChannel.flush();

        assertFalse(childChannel.isActive());
        assertFalse(childChannel.isOpen());

        handler.checkException();

        assertThrows(Http2NoMoreStreamIdsException.class, new Executable() {
            @Override
            public void execute() {
                future.syncUninterruptibly();
            }
        });
    }

    @Test
    public void channelClosedWhenCloseListenerCompletes() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);

        assertTrue(childChannel.isOpen());
        assertTrue(childChannel.isActive());

        final AtomicBoolean channelOpen = new AtomicBoolean(true);
        final AtomicBoolean channelActive = new AtomicBoolean(true);

        // Create a promise before actually doing the close, because otherwise we would be adding a listener to a future
        // that is already completed because we are using EmbeddedChannel which executes code in the JUnit thread.
        ChannelPromise p = childChannel.newPromise();
        p.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                channelOpen.set(future.channel().isOpen());
                channelActive.set(future.channel().isActive());
            }
        });
        childChannel.close(p).syncUninterruptibly();

        assertFalse(channelOpen.get());
        assertFalse(channelActive.get());
        assertFalse(childChannel.isActive());
    }

    @Test
    public void channelClosedWhenChannelClosePromiseCompletes() {
         LastInboundHandler inboundHandler = new LastInboundHandler();
         Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);

         assertTrue(childChannel.isOpen());
         assertTrue(childChannel.isActive());

         final AtomicBoolean channelOpen = new AtomicBoolean(true);
         final AtomicBoolean channelActive = new AtomicBoolean(true);

         childChannel.closeFuture().addListener(new ChannelFutureListener() {
             @Override
             public void operationComplete(ChannelFuture future) {
                 channelOpen.set(future.channel().isOpen());
                 channelActive.set(future.channel().isActive());
             }
         });
         childChannel.close().syncUninterruptibly();

         assertFalse(channelOpen.get());
         assertFalse(channelActive.get());
         assertFalse(childChannel.isActive());
    }

    @Test
    public void channelClosedWhenWriteFutureFails() {
        final Queue<ChannelPromise> writePromises = new ArrayDeque<ChannelPromise>();

        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);

        assertTrue(childChannel.isOpen());
        assertTrue(childChannel.isActive());

        final AtomicBoolean channelOpen = new AtomicBoolean(true);
        final AtomicBoolean channelActive = new AtomicBoolean(true);

        Http2Headers headers = new DefaultHttp2Headers();
        when(frameWriter.writeHeaders(eqCodecCtx(), anyInt(),
                eq(headers), anyInt(), anyBoolean(),
                any(ChannelPromise.class))).thenAnswer(new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocationOnMock) {
                ChannelPromise promise = invocationOnMock.getArgument(5);
                writePromises.offer(promise);
                return promise;
            }
        });

        ChannelFuture f = childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(headers));
        assertFalse(f.isDone());
        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                channelOpen.set(future.channel().isOpen());
                channelActive.set(future.channel().isActive());
            }
        });

        ChannelPromise first = writePromises.poll();
        first.setFailure(new ClosedChannelException());
        f.awaitUninterruptibly();

        assertFalse(channelOpen.get());
        assertFalse(channelActive.get());
        assertFalse(childChannel.isActive());
    }

    @Test
    public void channelClosedTwiceMarksPromiseAsSuccessful() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);

        assertTrue(childChannel.isOpen());
        assertTrue(childChannel.isActive());
        childChannel.close().syncUninterruptibly();
        childChannel.close().syncUninterruptibly();

        assertFalse(childChannel.isOpen());
        assertFalse(childChannel.isActive());
    }

    @Test
    public void settingChannelOptsAndAttrs() {
        AttributeKey<String> key = AttributeKey.newInstance(UUID.randomUUID().toString());

        Channel childChannel = newOutboundStream(new ChannelInboundHandlerAdapter());
        childChannel.config().setAutoRead(false).setWriteSpinCount(1000);
        childChannel.attr(key).set("bar");
        assertFalse(childChannel.config().isAutoRead());
        assertEquals(1000, childChannel.config().getWriteSpinCount());
        assertEquals("bar", childChannel.attr(key).get());
    }

    @Test
    public void outboundFlowControlWritability() {
        Http2StreamChannel childChannel = newOutboundStream(new ChannelInboundHandlerAdapter());
        assertTrue(childChannel.isActive());

        assertTrue(childChannel.isWritable());
        childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
        parentChannel.flush();

        // Test for initial window size
        assertTrue(initialRemoteStreamWindow < childChannel.config().getWriteBufferHighWaterMark());

        assertTrue(childChannel.isWritable());
        childChannel.write(new DefaultHttp2DataFrame(Unpooled.buffer().writeZero(16 * 1024 * 1024)));
        assertEquals(0, childChannel.bytesBeforeUnwritable());
        assertFalse(childChannel.isWritable());
    }

    @Test
    public void writabilityOfParentIsRespected() {
        Http2StreamChannel childChannel = newOutboundStream(new ChannelInboundHandlerAdapter());
        childChannel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(2048, 4096));
        parentChannel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(256, 512));
        assertTrue(childChannel.isWritable());
        assertTrue(parentChannel.isActive());

        childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
        parentChannel.flush();

        assertTrue(childChannel.isWritable());
        childChannel.write(new DefaultHttp2DataFrame(Unpooled.buffer().writeZero(256)));
        assertTrue(childChannel.isWritable());
        childChannel.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.buffer().writeZero(512)));

        long bytesBeforeUnwritable = childChannel.bytesBeforeUnwritable();
        assertNotEquals(0, bytesBeforeUnwritable);
        // Add something to the ChannelOutboundBuffer of the parent to simulate queuing in the parents channel buffer
        // and verify that this only affect the writability of the parent channel while the child stays writable
        // until it used all of its credits.
        parentChannel.unsafe().outboundBuffer().addMessage(
                Unpooled.buffer().writeZero(800), 800, parentChannel.voidPromise());
        assertFalse(parentChannel.isWritable());

        assertTrue(childChannel.isWritable());
        assertEquals(4097, childChannel.bytesBeforeUnwritable());

        // Flush everything which simulate writing everything to the socket.
        parentChannel.flush();
        assertTrue(parentChannel.isWritable());
        assertTrue(childChannel.isWritable());
        assertEquals(bytesBeforeUnwritable, childChannel.bytesBeforeUnwritable());

        ChannelFuture future = childChannel.writeAndFlush(new DefaultHttp2DataFrame(
                Unpooled.buffer().writeZero((int) bytesBeforeUnwritable)));
        assertFalse(childChannel.isWritable());
        assertTrue(parentChannel.isWritable());

        parentChannel.flush();
        assertFalse(future.isDone());
        assertTrue(parentChannel.isWritable());
        assertFalse(childChannel.isWritable());

        // Now write an window update frame for the stream which then should ensure we will flush the bytes that were
        // queued in the RemoteFlowController before for the stream.
        frameInboundWriter.writeInboundWindowUpdate(childChannel.stream().id(), (int) bytesBeforeUnwritable);
        assertTrue(childChannel.isWritable());
        assertTrue(future.isDone());
    }

    @Test
    public void channelClosedWhenInactiveFired() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);

        final AtomicBoolean channelOpen = new AtomicBoolean(false);
        final AtomicBoolean channelActive = new AtomicBoolean(false);
        assertTrue(childChannel.isOpen());
        assertTrue(childChannel.isActive());

        childChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                channelOpen.set(ctx.channel().isOpen());
                channelActive.set(ctx.channel().isActive());

                super.channelInactive(ctx);
            }
        });

        childChannel.close().syncUninterruptibly();
        assertFalse(channelOpen.get());
        assertFalse(channelActive.get());
    }

    @Test
    public void channelInactiveHappensAfterExceptionCaughtEvents() throws Exception {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicInteger exceptionCaught = new AtomicInteger(-1);
        final AtomicInteger channelInactive = new AtomicInteger(-1);
        final AtomicInteger channelUnregistered = new AtomicInteger(-1);
        Http2StreamChannel childChannel = newOutboundStream(new ChannelInboundHandlerAdapter() {

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                ctx.close();
                throw new Exception("exception");
            }
        });

        childChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                channelInactive.set(count.getAndIncrement());
                super.channelInactive(ctx);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                exceptionCaught.set(count.getAndIncrement());
                super.exceptionCaught(ctx, cause);
            }

            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                channelUnregistered.set(count.getAndIncrement());
                super.channelUnregistered(ctx);
            }
        });

        childChannel.pipeline().fireUserEventTriggered(new Object());
        parentChannel.runPendingTasks();

        // The events should have happened in this order because the inactive and deregistration events
        // get deferred as they do in the AbstractChannel.
        assertEquals(0, exceptionCaught.get());
        assertEquals(1, channelInactive.get());
        assertEquals(2, channelUnregistered.get());
    }

    @Test
    public void callUnsafeCloseMultipleTimes() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);
        childChannel.unsafe().close(childChannel.voidPromise());

        ChannelPromise promise = childChannel.newPromise();
        childChannel.unsafe().close(promise);
        promise.syncUninterruptibly();
        childChannel.closeFuture().syncUninterruptibly();
    }

    @Test
    public void endOfStreamDoesNotDiscardData() {
        AtomicInteger numReads = new AtomicInteger(1);
        final AtomicBoolean shouldDisableAutoRead = new AtomicBoolean();
        Consumer<ChannelHandlerContext> ctxConsumer = new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext obj) {
                if (shouldDisableAutoRead.get()) {
                    obj.channel().config().setAutoRead(false);
                }
            }
        };
        LastInboundHandler inboundHandler = new LastInboundHandler(ctxConsumer);
        Http2StreamChannel childChannel = newInboundStream(3, false, numReads, inboundHandler);
        childChannel.config().setAutoRead(false);

        Http2DataFrame dataFrame1 = new DefaultHttp2DataFrame(bb("1")).stream(childChannel.stream());
        Http2DataFrame dataFrame2 = new DefaultHttp2DataFrame(bb("2")).stream(childChannel.stream());
        Http2DataFrame dataFrame3 = new DefaultHttp2DataFrame(bb("3")).stream(childChannel.stream());
        Http2DataFrame dataFrame4 = new DefaultHttp2DataFrame(bb("4")).stream(childChannel.stream());

        assertEquals(new DefaultHttp2HeadersFrame(request).stream(childChannel.stream()), inboundHandler.readInbound());

        ChannelHandler readCompleteSupressHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                // We want to simulate the parent channel calling channelRead and delay calling channelReadComplete.
            }
        };

        parentChannel.pipeline().addFirst(readCompleteSupressHandler);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("1"), 0, false);

        assertEqualsAndRelease(dataFrame1, inboundHandler.<Http2DataFrame>readInbound());

        // Deliver frames, and then a stream closed while read is inactive.
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("2"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("3"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("4"), 0, false);

        shouldDisableAutoRead.set(true);
        childChannel.config().setAutoRead(true);
        numReads.set(1);

        frameInboundWriter.writeInboundRstStream(childChannel.stream().id(), NO_ERROR.code());

        // Detecting EOS should flush all pending data regardless of read calls.
        assertEqualsAndRelease(dataFrame2, inboundHandler.<Http2DataFrame>readInbound());
        assertNull(inboundHandler.readInbound());

        // As we limited the number to 1 we also need to call read() again.
        childChannel.read();

        assertEqualsAndRelease(dataFrame3, inboundHandler.<Http2DataFrame>readInbound());
        assertEqualsAndRelease(dataFrame4, inboundHandler.<Http2DataFrame>readInbound());

        Http2ResetFrame resetFrame = useUserEventForResetFrame() ? inboundHandler.<Http2ResetFrame>readUserEvent() :
                inboundHandler.<Http2ResetFrame>readInbound();

        assertEquals(childChannel.stream(), resetFrame.stream());
        assertEquals(NO_ERROR.code(), resetFrame.errorCode());

        assertNull(inboundHandler.readInbound());

        // Now we want to call channelReadComplete and simulate the end of the read loop.
        parentChannel.pipeline().remove(readCompleteSupressHandler);
        parentChannel.flushInbound();

        childChannel.closeFuture().syncUninterruptibly();
    }

    protected abstract boolean useUserEventForPriorityFrame();

    protected abstract boolean useUserEventForResetFrame();

    protected abstract boolean ignoreWindowUpdateFrames();

    @Test
    public void windowUpdateFrames() {
        AtomicInteger numReads = new AtomicInteger(1);
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, numReads, inboundHandler);

        assertEquals(new DefaultHttp2HeadersFrame(request).stream(childChannel.stream()), inboundHandler.readInbound());

        frameInboundWriter.writeInboundWindowUpdate(childChannel.stream().id(), 4);

        Http2WindowUpdateFrame updateFrame = inboundHandler.readInbound();
        if (ignoreWindowUpdateFrames()) {
            assertNull(updateFrame);
        } else {
            assertEquals(new DefaultHttp2WindowUpdateFrame(4).stream(childChannel.stream()), updateFrame);
        }

        frameInboundWriter.writeInboundWindowUpdate(Http2CodecUtil.CONNECTION_STREAM_ID, 6);

        assertNull(parentChannel.readInbound());
        childChannel.close().syncUninterruptibly();
    }

    @Test
    public void childQueueIsDrainedAndNewDataIsDispatchedInParentReadLoopAutoRead() {
        AtomicInteger numReads = new AtomicInteger(1);
        final AtomicInteger channelReadCompleteCount = new AtomicInteger(0);
        final AtomicBoolean shouldDisableAutoRead = new AtomicBoolean();
        Consumer<ChannelHandlerContext> ctxConsumer = new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext obj) {
                channelReadCompleteCount.incrementAndGet();
                if (shouldDisableAutoRead.get()) {
                    obj.channel().config().setAutoRead(false);
                }
            }
        };
        LastInboundHandler inboundHandler = new LastInboundHandler(ctxConsumer);
        Http2StreamChannel childChannel = newInboundStream(3, false, numReads, inboundHandler);
        childChannel.config().setAutoRead(false);

        Http2DataFrame dataFrame1 = new DefaultHttp2DataFrame(bb("1")).stream(childChannel.stream());
        Http2DataFrame dataFrame2 = new DefaultHttp2DataFrame(bb("2")).stream(childChannel.stream());
        Http2DataFrame dataFrame3 = new DefaultHttp2DataFrame(bb("3")).stream(childChannel.stream());
        Http2DataFrame dataFrame4 = new DefaultHttp2DataFrame(bb("4")).stream(childChannel.stream());

        assertEquals(new DefaultHttp2HeadersFrame(request).stream(childChannel.stream()), inboundHandler.readInbound());

        ChannelHandler readCompleteSupressHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                // We want to simulate the parent channel calling channelRead and delay calling channelReadComplete.
            }
        };
        parentChannel.pipeline().addFirst(readCompleteSupressHandler);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("1"), 0, false);

        assertEqualsAndRelease(dataFrame1, inboundHandler.<Http2DataFrame>readInbound());

        // We want one item to be in the queue, and allow the numReads to be larger than 1. This will ensure that
        // when beginRead() is called the child channel is added to the readPending queue of the parent channel.
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("2"), 0, false);

        numReads.set(10);
        shouldDisableAutoRead.set(true);
        childChannel.config().setAutoRead(true);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("3"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("4"), 0, false);

        // Detecting EOS should flush all pending data regardless of read calls.
        assertEqualsAndRelease(dataFrame2, inboundHandler.<Http2DataFrame>readInbound());
        assertEqualsAndRelease(dataFrame3, inboundHandler.<Http2DataFrame>readInbound());
        assertEqualsAndRelease(dataFrame4, inboundHandler.<Http2DataFrame>readInbound());

        assertNull(inboundHandler.readInbound());

        // Now we want to call channelReadComplete and simulate the end of the read loop.
        parentChannel.pipeline().remove(readCompleteSupressHandler);
        parentChannel.flushInbound();

        // 3 = 1 for initialization + 1 for read when auto read was off + 1 for when auto read was back on
        assertEquals(3, channelReadCompleteCount.get());
    }

    @Test
    public void childQueueIsDrainedAndNewDataIsDispatchedInParentReadLoopNoAutoRead() {
        final AtomicInteger numReads = new AtomicInteger(1);
        final AtomicInteger channelReadCompleteCount = new AtomicInteger(0);
        final AtomicBoolean shouldDisableAutoRead = new AtomicBoolean();
        Consumer<ChannelHandlerContext> ctxConsumer = new Consumer<ChannelHandlerContext>() {
            @Override
            public void accept(ChannelHandlerContext obj) {
                channelReadCompleteCount.incrementAndGet();
                if (shouldDisableAutoRead.get()) {
                    obj.channel().config().setAutoRead(false);
                }
            }
        };
        final LastInboundHandler inboundHandler = new LastInboundHandler(ctxConsumer);
        Http2StreamChannel childChannel = newInboundStream(3, false, numReads, inboundHandler);
        childChannel.config().setAutoRead(false);

        Http2DataFrame dataFrame1 = new DefaultHttp2DataFrame(bb("1")).stream(childChannel.stream());
        Http2DataFrame dataFrame2 = new DefaultHttp2DataFrame(bb("2")).stream(childChannel.stream());
        Http2DataFrame dataFrame3 = new DefaultHttp2DataFrame(bb("3")).stream(childChannel.stream());
        Http2DataFrame dataFrame4 = new DefaultHttp2DataFrame(bb("4")).stream(childChannel.stream());

        assertEquals(new DefaultHttp2HeadersFrame(request).stream(childChannel.stream()), inboundHandler.readInbound());

        ChannelHandler readCompleteSupressHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                // We want to simulate the parent channel calling channelRead and delay calling channelReadComplete.
            }
        };
        parentChannel.pipeline().addFirst(readCompleteSupressHandler);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("1"), 0, false);

        assertEqualsAndRelease(dataFrame1, inboundHandler.<Http2Frame>readInbound());

        // We want one item to be in the queue, and allow the numReads to be larger than 1. This will ensure that
        // when beginRead() is called the child channel is added to the readPending queue of the parent channel.
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("2"), 0, false);

        numReads.set(2);
        childChannel.read();

        assertEqualsAndRelease(dataFrame2, inboundHandler.<Http2Frame>readInbound());

        assertNull(inboundHandler.readInbound());

        // This is the second item that was read, this should be the last until we call read() again. This should also
        // notify of readComplete().
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("3"), 0, false);

        assertEqualsAndRelease(dataFrame3, inboundHandler.<Http2Frame>readInbound());

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("4"), 0, false);
        assertNull(inboundHandler.readInbound());

        childChannel.read();

        assertEqualsAndRelease(dataFrame4, inboundHandler.<Http2Frame>readInbound());

        assertNull(inboundHandler.readInbound());

        // Now we want to call channelReadComplete and simulate the end of the read loop.
        parentChannel.pipeline().remove(readCompleteSupressHandler);
        parentChannel.flushInbound();

        // 3 = 1 for initialization + 1 for first read of 2 items + 1 for second read of 2 items +
        // 1 for parent channel readComplete
        assertEquals(4, channelReadCompleteCount.get());
    }

    @Test
    public void useReadWithoutAutoReadInRead() {
        useReadWithoutAutoReadBuffered(false);
    }

    @Test
    public void useReadWithoutAutoReadInReadComplete() {
        useReadWithoutAutoReadBuffered(true);
    }

    private void useReadWithoutAutoReadBuffered(final boolean triggerOnReadComplete) {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);
        assertTrue(childChannel.config().isAutoRead());
        childChannel.config().setAutoRead(false);
        assertFalse(childChannel.config().isAutoRead());

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        // Write some bytes to get the channel into the idle state with buffered data and also verify we
        // do not dispatch it until we receive a read() call.
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("hello world"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("foo"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar"), 0, false);

        // Add a handler which will request reads.
        childChannel.pipeline().addFirst(new ChannelInboundHandlerAdapter() {

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                super.channelReadComplete(ctx);
                if (triggerOnReadComplete) {
                    ctx.read();
                    ctx.read();
                }
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ctx.fireChannelRead(msg);
                if (!triggerOnReadComplete) {
                    ctx.read();
                    ctx.read();
                }
            }
        });

        inboundHandler.channel().read();

        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 3);

        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("hello world2"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("foo2"), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb("bar2"), 0, true);

        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 3);
    }

    private static final class FlushSniffer extends ChannelOutboundHandlerAdapter {

        private boolean didFlush;

        public boolean checkFlush() {
            boolean r = didFlush;
            didFlush = false;
            return r;
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            didFlush = true;
            super.flush(ctx);
        }
    }

    @Test
    public void windowUpdatesAreFlushed() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        FlushSniffer flushSniffer = new FlushSniffer();
        parentChannel.pipeline().addFirst(flushSniffer);

        Http2StreamChannel childChannel = newInboundStream(3, false, inboundHandler);
        assertTrue(childChannel.config().isAutoRead());
        childChannel.config().setAutoRead(false);
        assertFalse(childChannel.config().isAutoRead());

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        assertTrue(flushSniffer.checkFlush());

        // Write some bytes to get the channel into the idle state with buffered data and also verify we
        // do not dispatch it until we receive a read() call.
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb(16 * 1024), 0, false);
        frameInboundWriter.writeInboundData(childChannel.stream().id(), bb(16 * 1024), 0, false);
        assertTrue(flushSniffer.checkFlush());

        verify(frameWriter, never()).writeWindowUpdate(eqCodecCtx(), anyInt(), anyInt(), anyChannelPromise());
        // only the first one was read because it was legacy auto-read behavior.
        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 1);
        assertFalse(flushSniffer.checkFlush());

        // Trigger a read of the second frame.
        childChannel.read();
        verifyFramesMultiplexedToCorrectChannel(childChannel, inboundHandler, 1);
        // We expect a flush here because the StreamChannel will flush the smaller increment but the
        // connection will collect the bytes and decide not to send a wire level frame until more are consumed.
        assertTrue(flushSniffer.checkFlush());
        verify(frameWriter, never()).writeWindowUpdate(eqCodecCtx(), anyInt(), anyInt(), anyChannelPromise());

        // Call read one more time which should trigger the writing of the flow control update.
        childChannel.read();
        verify(frameWriter).writeWindowUpdate(eqCodecCtx(), eq(0), eq(32 * 1024), anyChannelPromise());
        verify(frameWriter).writeWindowUpdate(
            eqCodecCtx(), eq(childChannel.stream().id()), eq(32 * 1024), anyChannelPromise());
        assertTrue(flushSniffer.checkFlush());
    }

    @ParameterizedTest(name = "{displayName} [{index}] value={0}")
    @MethodSource("userEvents")
    public void userEventsThatPropagatedToChildChannels(Object userEvent) {
        final LastInboundHandler inboundParentHandler = new LastInboundHandler();
        final LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        assertTrue(channel.isActive());
        parentChannel.pipeline().addLast(inboundParentHandler);
        parentChannel.pipeline().fireUserEventTriggered(userEvent);
        assertEquals(userEvent, inboundHandler.readUserEvent());
        assertEquals(userEvent, inboundParentHandler.readUserEvent());
        assertNull(inboundHandler.readUserEvent());
        assertNull(inboundParentHandler.readUserEvent());
    }

    private static Collection<Object> userEvents() {
        return Arrays.asList(ChannelInputShutdownReadComplete.INSTANCE,
                ChannelOutputShutdownEvent.INSTANCE, SslCloseCompletionEvent.SUCCESS);
    }

    private static void verifyFramesMultiplexedToCorrectChannel(Http2StreamChannel streamChannel,
                                                                LastInboundHandler inboundHandler,
                                                                int numFrames) {
        for (int i = 0; i < numFrames; i++) {
            Http2StreamFrame frame = inboundHandler.readInbound();
            assertNotNull(frame, i + " out of " + numFrames + " received");
            assertEquals(streamChannel.stream(), frame.stream());
            release(frame);
        }
        assertNull(inboundHandler.readInbound());
    }

    private static int eqStreamId(Http2StreamChannel channel) {
        return eq(channel.stream().id());
    }
}
