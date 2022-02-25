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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.UnsupportedMessageTypeException;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpScheme;
import io.netty5.handler.codec.http.HttpServerUpgradeHandler;
import io.netty5.handler.codec.http.HttpServerUpgradeHandler.UpgradeEvent;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.handler.codec.http2.Http2Exception.StreamException;
import io.netty5.handler.codec.http2.Http2Stream.State;
import io.netty5.handler.logging.LogLevel;
import io.netty5.util.AbstractReferenceCounted;
import io.netty5.util.AsciiString;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.ReferenceCounted;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.ReflectionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http2.Http2CodecUtil.isStreamIdValid;
import static io.netty5.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty5.handler.codec.http2.Http2TestUtil.anyHttp2Settings;
import static io.netty5.handler.codec.http2.Http2TestUtil.assertEqualsAndRelease;
import static io.netty5.handler.codec.http2.Http2TestUtil.bb;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link Http2FrameCodec}.
 */
public class Http2FrameCodecTest {

    // For verifying outbound frames
    private Http2FrameWriter frameWriter;
    private Http2FrameCodec frameCodec;
    private EmbeddedChannel channel;

    // For injecting inbound frames
    private Http2FrameInboundWriter frameInboundWriter;

    private LastInboundHandler inboundHandler;

    private final Http2Headers request = new DefaultHttp2Headers()
            .method(HttpMethod.GET.asciiName()).scheme(HttpScheme.HTTPS.name())
            .authority(new AsciiString("example.org")).path(new AsciiString("/foo"));
    private final Http2Headers response = new DefaultHttp2Headers()
            .status(HttpResponseStatus.OK.codeAsText());

    @BeforeEach
    public void setUp() throws Exception {
        setUp(Http2FrameCodecBuilder.forServer(), new Http2Settings());
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (inboundHandler != null) {
            inboundHandler.finishAndReleaseAll();
            inboundHandler = null;
        }
        if (channel != null) {
            channel.finishAndReleaseAll();
            channel.close();
            channel = null;
        }
    }

    private void setUp(Http2FrameCodecBuilder frameCodecBuilder, Http2Settings initialRemoteSettings) throws Exception {
        /*
          Some tests call this method twice. Once with JUnit's @Before and once directly to pass special settings.
          This call ensures that in case of two consecutive calls to setUp(), the previous channel is shutdown and
          ByteBufs are released correctly.
         */
        tearDown();

        frameWriter = Http2TestUtil.mockedFrameWriter();

        frameCodec = frameCodecBuilder.frameWriter(frameWriter).frameLogger(new Http2FrameLogger(LogLevel.TRACE))
                .initialSettings(initialRemoteSettings).build();
        inboundHandler = new LastInboundHandler();

        channel = new EmbeddedChannel();
        frameInboundWriter = new Http2FrameInboundWriter(channel);
        channel.connect(new InetSocketAddress(0));
        channel.pipeline().addLast(frameCodec);
        channel.pipeline().addLast(inboundHandler);
        channel.pipeline().fireChannelActive();

        // Handshake
        verify(frameWriter).writeSettings(any(ChannelHandlerContext.class), anyHttp2Settings());
        verifyNoMoreInteractions(frameWriter);
        channel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());

        frameInboundWriter.writeInboundSettings(initialRemoteSettings);

        verify(frameWriter).writeSettingsAck(any(ChannelHandlerContext.class));

        frameInboundWriter.writeInboundSettingsAck();

        Http2SettingsFrame settingsFrame = inboundHandler.readInbound();
        assertNotNull(settingsFrame);
        Http2SettingsAckFrame settingsAckFrame = inboundHandler.readInbound();
        assertNotNull(settingsAckFrame);
    }

    @Test
    public void stateChanges() {
        frameInboundWriter.writeInboundHeaders(1, request, 31, true);

        Http2Stream stream = frameCodec.connection().stream(1);
        assertNotNull(stream);
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());

        Http2FrameStreamEvent event = inboundHandler.readInboundMessageOrUserEvent();
        assertEquals(State.HALF_CLOSED_REMOTE, event.stream().state());

        Http2StreamFrame inboundFrame = inboundHandler.readInbound();
        Http2FrameStream stream2 = inboundFrame.stream();
        assertNotNull(stream2);
        assertEquals(1, stream2.id());
        assertEquals(inboundFrame, new DefaultHttp2HeadersFrame(request, true, 31).stream(stream2));
        assertNull(inboundHandler.readInbound());

        channel.writeOutbound(new DefaultHttp2HeadersFrame(response, true, 27).stream(stream2));
        verify(frameWriter).writeHeaders(
                any(ChannelHandlerContext.class), eq(1), eq(response),
                eq(27), eq(true));
        verify(frameWriter, never()).writeRstStream(
                any(ChannelHandlerContext.class), anyInt(), anyLong());

        assertEquals(State.CLOSED, stream.state());
        event = inboundHandler.readInboundMessageOrUserEvent();
        assertEquals(State.CLOSED, event.stream().state());

        assertTrue(channel.isActive());
    }

    @Test
    public void headerRequestHeaderResponse() {
        frameInboundWriter.writeInboundHeaders(1, request, 31, true);

        Http2Stream stream = frameCodec.connection().stream(1);
        assertNotNull(stream);
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());

        Http2StreamFrame inboundFrame = inboundHandler.readInbound();
        Http2FrameStream stream2 = inboundFrame.stream();
        assertNotNull(stream2);
        assertEquals(1, stream2.id());
        assertEquals(inboundFrame, new DefaultHttp2HeadersFrame(request, true, 31).stream(stream2));
        assertNull(inboundHandler.readInbound());

        channel.writeOutbound(new DefaultHttp2HeadersFrame(response, true, 27).stream(stream2));
        verify(frameWriter).writeHeaders(
                any(ChannelHandlerContext.class), eq(1), eq(response),
                eq(27), eq(true));
        verify(frameWriter, never()).writeRstStream(
                any(ChannelHandlerContext.class), anyInt(), anyLong());

        assertEquals(State.CLOSED, stream.state());
        assertTrue(channel.isActive());
    }

    @Test
    public void flowControlShouldBeResilientToMissingStreams() throws Http2Exception {
        Http2Connection conn = new DefaultHttp2Connection(true);
        Http2ConnectionEncoder enc = new DefaultHttp2ConnectionEncoder(conn, new DefaultHttp2FrameWriter());
        Http2ConnectionDecoder dec = new DefaultHttp2ConnectionDecoder(conn, enc, new DefaultHttp2FrameReader());
        Http2FrameCodec codec = new Http2FrameCodec(enc, dec, new Http2Settings(), false);
        EmbeddedChannel em = new EmbeddedChannel(codec);

        AtomicReference<Http2Exception> errorRef = new AtomicReference<>();
        em.executor().execute(() -> {
            try {
                // We call #consumeBytes on a stream id which has not been seen yet to emulate the case
                // where a stream is deregistered which in reality can happen in response to a RST.
                assertFalse(codec.consumeBytes(1, 1));
            } catch (Http2Exception e) {
                errorRef.set(e);
            }
        });

        assertTrue(em.finishAndReleaseAll());
        Http2Exception exception = errorRef.get();
        if (exception != null) {
            throw exception;
        }
    }

    @Test
    public void entityRequestEntityResponse() throws Exception {
        frameInboundWriter.writeInboundHeaders(1, request, 0, false);

        Http2Stream stream = frameCodec.connection().stream(1);
        assertNotNull(stream);
        assertEquals(State.OPEN, stream.state());

        Http2HeadersFrame inboundHeaders = inboundHandler.readInbound();
        Http2FrameStream stream2 = inboundHeaders.stream();
        assertNotNull(stream2);
        assertEquals(1, stream2.id());
        assertEquals(new DefaultHttp2HeadersFrame(request, false).stream(stream2), inboundHeaders);
        assertNull(inboundHandler.readInbound());

        ByteBuf hello = bb("hello");
        frameInboundWriter.writeInboundData(1, hello, 31, true);
        Http2DataFrame inboundData = inboundHandler.readInbound();
        Http2DataFrame expected = new DefaultHttp2DataFrame(bb("hello"), true, 31).stream(stream2);
        assertEqualsAndRelease(expected, inboundData);

        assertNull(inboundHandler.readInbound());

        inboundHandler.writeOutbound(new DefaultHttp2HeadersFrame(response, false).stream(stream2));
        verify(frameWriter).writeHeaders(any(ChannelHandlerContext.class), eq(1), eq(response), eq(0),
                eq(false));

        channel.writeOutbound(new DefaultHttp2DataFrame(bb("world"), true, 27).stream(stream2));
        ArgumentCaptor<ByteBuf> outboundData = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeData(any(ChannelHandlerContext.class), eq(1), outboundData.capture(), eq(27),
                                      eq(true));

        ByteBuf bb = bb("world");
        assertEquals(bb, outboundData.getValue());
        assertEquals(1, outboundData.getValue().refCnt());
        bb.release();
        outboundData.getValue().release();

        verify(frameWriter, never()).writeRstStream(any(ChannelHandlerContext.class),
                anyInt(), anyLong());
        assertTrue(channel.isActive());
    }

    @Test
    public void sendRstStream() {
        frameInboundWriter.writeInboundHeaders(3, request, 31, true);

        Http2Stream stream = frameCodec.connection().stream(3);
        assertNotNull(stream);
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());

        Http2HeadersFrame inboundHeaders = inboundHandler.readInbound();
        assertNotNull(inboundHeaders);
        assertTrue(inboundHeaders.isEndStream());

        Http2FrameStream stream2 = inboundHeaders.stream();
        assertNotNull(stream2);
        assertEquals(3, stream2.id());

        channel.writeOutbound(new DefaultHttp2ResetFrame(314 /* non-standard error */).stream(stream2));
        verify(frameWriter).writeRstStream(any(ChannelHandlerContext.class), eq(3), eq(314L));
        assertEquals(State.CLOSED, stream.state());
        assertTrue(channel.isActive());
    }

    @Test
    public void receiveRstStream() {
        frameInboundWriter.writeInboundHeaders(3, request, 31, false);

        Http2Stream stream = frameCodec.connection().stream(3);
        assertNotNull(stream);
        assertEquals(State.OPEN, stream.state());

        Http2HeadersFrame expectedHeaders = new DefaultHttp2HeadersFrame(request, false, 31);
        Http2HeadersFrame actualHeaders = inboundHandler.readInbound();
        assertEquals(expectedHeaders.stream(actualHeaders.stream()), actualHeaders);

        frameInboundWriter.writeInboundRstStream(3, NO_ERROR.code());

        Http2ResetFrame expectedRst = new DefaultHttp2ResetFrame(NO_ERROR).stream(actualHeaders.stream());
        Http2ResetFrame actualRst = inboundHandler.readInbound();
        assertEquals(expectedRst, actualRst);

        assertNull(inboundHandler.readInbound());
    }

    @Test
    public void sendGoAway() {
        frameInboundWriter.writeInboundHeaders(3, request, 31, false);
        Http2Stream stream = frameCodec.connection().stream(3);
        assertNotNull(stream);
        assertEquals(State.OPEN, stream.state());

        ByteBuf debugData = bb("debug");
        ByteBuf expected = debugData.copy();

        Http2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(NO_ERROR.code(),
                debugData.retainedDuplicate());
        goAwayFrame.setExtraStreamIds(2);

        channel.writeOutbound(goAwayFrame);
        verify(frameWriter).writeGoAway(any(ChannelHandlerContext.class), eq(7),
                eq(NO_ERROR.code()), eq(expected));
        assertEquals(State.OPEN, stream.state());
        assertTrue(channel.isActive());
        expected.release();
        debugData.release();
    }

    @Test
    public void receiveGoaway() {
        ByteBuf debugData = bb("foo");
        frameInboundWriter.writeInboundGoAway(2, NO_ERROR.code(), debugData);
        Http2GoAwayFrame expectedFrame = new DefaultHttp2GoAwayFrame(2, NO_ERROR.code(), bb("foo"));
        Http2GoAwayFrame actualFrame = inboundHandler.readInbound();

        assertEqualsAndRelease(expectedFrame, actualFrame);

        assertNull(inboundHandler.readInbound());
    }

    @Test
    public void unknownFrameTypeShouldThrowAndBeReleased() throws Exception {
        class UnknownHttp2Frame extends AbstractReferenceCounted implements Http2Frame {
            @Override
            public String name() {
                return "UNKNOWN";
            }

            @Override
            protected void deallocate() {
            }

            @Override
            public ReferenceCounted touch(Object hint) {
                return this;
            }
        }

        UnknownHttp2Frame frame = new UnknownHttp2Frame();
        assertEquals(1, frame.refCnt());

        Future<Void> f = channel.write(frame);
        f.await();
        assertTrue(f.isDone());
        assertFalse(f.isSuccess());
        assertThat(f.cause(), instanceOf(UnsupportedMessageTypeException.class));
        assertEquals(0, frame.refCnt());
    }

    @Test
    public void unknownFrameTypeOnConnectionStream() {
        // handle the case where unknown frames are sent before a stream is created,
        // for example: HTTP/2 GREASE testing
        ByteBuf debugData = bb("debug");
        frameInboundWriter.writeInboundFrame((byte) 0xb, 0, new Http2Flags(), debugData);
        channel.flush();

        assertEquals(0, debugData.refCnt());
        assertTrue(channel.isActive());
    }

    @Test
    public void goAwayLastStreamIdOverflowed() {
        frameInboundWriter.writeInboundHeaders(5, request, 31, false);

        Http2Stream stream = frameCodec.connection().stream(5);
        assertNotNull(stream);
        assertEquals(State.OPEN, stream.state());

        ByteBuf debugData = bb("debug");
        Http2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(NO_ERROR.code(),
                debugData.retainedDuplicate());
        goAwayFrame.setExtraStreamIds(Integer.MAX_VALUE);

        channel.writeOutbound(goAwayFrame);
        // When the last stream id computation overflows, the last stream id should just be set to 2^31 - 1.
        verify(frameWriter).writeGoAway(any(ChannelHandlerContext.class), eq(Integer.MAX_VALUE),
                eq(NO_ERROR.code()), eq(debugData));
        debugData.release();
        assertEquals(State.OPEN, stream.state());
        assertTrue(channel.isActive());
    }

    @Test
    public void streamErrorShouldFireExceptionForInbound() {
        frameInboundWriter.writeInboundHeaders(3, request, 31, false);

        Http2Stream stream = frameCodec.connection().stream(3);
        assertNotNull(stream);

        StreamException streamEx = new StreamException(3, Http2Error.INTERNAL_ERROR, "foo");
        channel.pipeline().fireExceptionCaught(streamEx);

        Http2FrameStreamEvent event = inboundHandler.readInboundMessageOrUserEvent();
        assertEquals(Http2FrameStreamEvent.Type.State, event.type());
        assertEquals(State.OPEN, event.stream().state());
        Http2HeadersFrame headersFrame = inboundHandler.readInboundMessageOrUserEvent();
        assertNotNull(headersFrame);

        Http2FrameStreamException e = assertThrows(Http2FrameStreamException.class,
                () -> inboundHandler.checkException());
        assertEquals(streamEx, e.getCause());

        assertNull(inboundHandler.readInboundMessageOrUserEvent());
    }

    @Test
    public void streamErrorShouldNotFireExceptionForOutbound() throws Exception {
        frameInboundWriter.writeInboundHeaders(3, request, 31, false);

        Http2Stream stream = frameCodec.connection().stream(3);
        assertNotNull(stream);

        StreamException streamEx = new StreamException(3, Http2Error.INTERNAL_ERROR, "foo");
        frameCodec.onError(frameCodec.ctx, true, streamEx);

        Http2FrameStreamEvent event = inboundHandler.readInboundMessageOrUserEvent();
        assertEquals(Http2FrameStreamEvent.Type.State, event.type());
        assertEquals(State.OPEN, event.stream().state());
        Http2HeadersFrame headersFrame = inboundHandler.readInboundMessageOrUserEvent();
        assertNotNull(headersFrame);

        // No exception expected
        inboundHandler.checkException();

        assertNull(inboundHandler.readInboundMessageOrUserEvent());
    }

    @Test
    public void windowUpdateFrameDecrementsConsumedBytes() {
        frameInboundWriter.writeInboundHeaders(3, request, 31, false);

        Http2Connection connection = frameCodec.connection();
        Http2Stream stream = connection.stream(3);
        assertNotNull(stream);

        ByteBuf data = Unpooled.buffer(100).writeZero(100);
        frameInboundWriter.writeInboundData(3, data, 0, false);

        Http2HeadersFrame inboundHeaders = inboundHandler.readInbound();
        assertNotNull(inboundHeaders);
        assertNotNull(inboundHeaders.stream());

        Http2FrameStream stream2 = inboundHeaders.stream();

        int before = connection.local().flowController().unconsumedBytes(stream);
        Future<Void> f = channel.write(new DefaultHttp2WindowUpdateFrame(100).stream(stream2));
        int after = connection.local().flowController().unconsumedBytes(stream);
        assertEquals(100, before - after);
        assertTrue(f.isSuccess());
    }

    @Test
    public void windowUpdateMayFail() {
        frameInboundWriter.writeInboundHeaders(3, request, 31, false);
        Http2Connection connection = frameCodec.connection();
        Http2Stream stream = connection.stream(3);
        assertNotNull(stream);

        Http2HeadersFrame inboundHeaders = inboundHandler.readInbound();
        assertNotNull(inboundHeaders);

        Http2FrameStream stream2 = inboundHeaders.stream();

        // Fails, cause trying to return too many bytes to the flow controller
        Future<Void> f = channel.write(new DefaultHttp2WindowUpdateFrame(100).stream(stream2));
        assertTrue(f.isDone());
        assertFalse(f.isSuccess());
        assertThat(f.cause(), instanceOf(Http2Exception.class));
    }

    @Test
    public void inboundWindowUpdateShouldBeForwarded() {
        frameInboundWriter.writeInboundHeaders(3, request, 31, false);
        frameInboundWriter.writeInboundWindowUpdate(3, 100);
        // Connection-level window update
        frameInboundWriter.writeInboundWindowUpdate(0, 100);

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        Http2WindowUpdateFrame windowUpdateFrame = inboundHandler.readInbound();
        assertNotNull(windowUpdateFrame);
        assertEquals(3, windowUpdateFrame.stream().id());
        assertEquals(100, windowUpdateFrame.windowSizeIncrement());

        // Window update for the connection should not be forwarded.
        assertNull(inboundHandler.readInbound());
    }

    @Test
    public void streamZeroWindowUpdateIncrementsConnectionWindow() throws Http2Exception {
        Http2Connection connection = frameCodec.connection();
        Http2LocalFlowController localFlow = connection.local().flowController();
        int initialWindowSizeBefore = localFlow.initialWindowSize();
        Http2Stream connectionStream = connection.connectionStream();
        int connectionWindowSizeBefore = localFlow.windowSize(connectionStream);

        AtomicReference<Http2Exception> errorRef = new AtomicReference<>();
        channel.executor().execute(() -> {
            try {
                // We only replenish the flow control window after the amount consumed drops below the following
                // threshold. We make the threshold very "high" so that window updates will be sent when the delta is
                // relatively small.
                ((DefaultHttp2LocalFlowController) localFlow).windowUpdateRatio(connectionStream, .999f);
            } catch (Http2Exception e) {
                errorRef.set(e);
            }
        });

        Http2Exception exception = errorRef.get();
        if (exception != null) {
            throw exception;
        }

        int windowUpdate = 1024;

        channel.write(new DefaultHttp2WindowUpdateFrame(windowUpdate));

        // The initial window size is only changed by Http2Settings, so it shouldn't change.
        assertEquals(initialWindowSizeBefore, localFlow.initialWindowSize());
        // The connection window should be increased by the delta amount.
        assertEquals(connectionWindowSizeBefore + windowUpdate, localFlow.windowSize(connectionStream));
    }

    @Test
    public void windowUpdateDoesNotOverflowConnectionWindow() {
        Http2Connection connection = frameCodec.connection();
        Http2LocalFlowController localFlow = connection.local().flowController();
        int initialWindowSizeBefore = localFlow.initialWindowSize();

        channel.write(new DefaultHttp2WindowUpdateFrame(Integer.MAX_VALUE));

        // The initial window size is only changed by Http2Settings, so it shouldn't change.
        assertEquals(initialWindowSizeBefore, localFlow.initialWindowSize());
        // The connection window should be increased by the delta amount.
        assertEquals(Integer.MAX_VALUE, localFlow.windowSize(connection.connectionStream()));
    }

    @Test
    public void writeUnknownFrame() {
        final Http2FrameStream stream = frameCodec.newStream();

        ByteBuf buffer = Unpooled.buffer().writeByte(1);
        DefaultHttp2UnknownFrame unknownFrame = new DefaultHttp2UnknownFrame(
                (byte) 20, new Http2Flags().ack(true), buffer);
        unknownFrame.stream(stream);
        channel.write(unknownFrame);

        verify(frameWriter).writeFrame(any(ChannelHandlerContext.class), eq(unknownFrame.frameType()),
                eq(unknownFrame.stream().id()), eq(unknownFrame.flags()), eq(buffer));
    }

    @Test
    public void sendSettingsFrame() {
        Http2Settings settings = new Http2Settings();
        channel.write(new DefaultHttp2SettingsFrame(settings));

        verify(frameWriter).writeSettings(any(ChannelHandlerContext.class), same(settings));
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void newOutboundStream() {
        final Http2FrameStream stream = frameCodec.newStream();

        assertNotNull(stream);
        assertFalse(isStreamIdValid(stream.id()));

        final Promise<Void> listenerExecuted = GlobalEventExecutor.INSTANCE.newPromise();

        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(), false).stream(stream))
               .addListener(future -> {
                   assertTrue(future.isSuccess());
                   assertTrue(isStreamIdValid(stream.id()));
                   listenerExecuted.setSuccess(null);
               }
               );
        ByteBuf data = Unpooled.buffer().writeZero(100);
        Future<Void> f = channel.writeAndFlush(new DefaultHttp2DataFrame(data).stream(stream));
        assertTrue(f.isSuccess());

        listenerExecuted.asFuture().syncUninterruptibly();
        assertTrue(listenerExecuted.isSuccess());
    }

    @Test
    public void newOutboundStreamsShouldBeBuffered() throws Exception {
        setUp(Http2FrameCodecBuilder.forServer().encoderEnforceMaxConcurrentStreams(true),
              new Http2Settings().maxConcurrentStreams(1));

        Http2FrameStream stream1 = frameCodec.newStream();
        Http2FrameStream stream2 = frameCodec.newStream();

        Future<Void> future1 = channel.writeAndFlush(
                new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream1));
        Future<Void> future2 = channel.writeAndFlush(
                new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream2));

        assertTrue(isStreamIdValid(stream1.id()));
        channel.runPendingTasks();
        assertTrue(isStreamIdValid(stream2.id()));

        assertTrue(future1.syncUninterruptibly().isSuccess());
        assertFalse(future2.isDone());

        // Increase concurrent streams limit to 2
        frameInboundWriter.writeInboundSettings(new Http2Settings().maxConcurrentStreams(2));

        channel.flush();

        assertTrue(future2.syncUninterruptibly().isSuccess());
    }

    @Test
    public void multipleNewOutboundStreamsShouldBeBuffered() throws Exception {
        // We use a limit of 1 and then increase it step by step.
        setUp(Http2FrameCodecBuilder.forServer().encoderEnforceMaxConcurrentStreams(true),
                new Http2Settings().maxConcurrentStreams(1));

        Http2FrameStream stream1 = frameCodec.newStream();
        Http2FrameStream stream2 = frameCodec.newStream();
        Http2FrameStream stream3 = frameCodec.newStream();

        Future<Void> future1 = channel.writeAndFlush(
                new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream1));
        Future<Void> future2 = channel.writeAndFlush(
                new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream2));
        Future<Void> future3 = channel.writeAndFlush(
                new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream3));

        assertTrue(isStreamIdValid(stream1.id()));
        channel.runPendingTasks();
        assertTrue(isStreamIdValid(stream2.id()));

        assertTrue(future1.syncUninterruptibly().isSuccess());
        assertFalse(future2.isDone());
        assertFalse(future3.isDone());

        // Increase concurrent streams limit to 2
        frameInboundWriter.writeInboundSettings(new Http2Settings().maxConcurrentStreams(2));
        channel.flush();

        // As we increased the limit to 2 we should have also succeed the second frame.
        assertTrue(future2.syncUninterruptibly().isSuccess());
        assertFalse(future3.isDone());

        frameInboundWriter.writeInboundSettings(new Http2Settings().maxConcurrentStreams(3));
        channel.flush();

        // With the max streams of 3 all streams should be succeed now.
        assertTrue(future3.syncUninterruptibly().isSuccess());

        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void doNotLeakOnFailedInitializationForChannels() throws Exception {
        setUp(Http2FrameCodecBuilder.forServer(), new Http2Settings().maxConcurrentStreams(2));
        Http2FrameStream stream1 = frameCodec.newStream();
        Http2FrameStream stream2 = frameCodec.newStream();
        Future<Void> stream1HeaderFuture = channel.writeAndFlush(
                new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream1));
        channel.runPendingTasks();

        frameInboundWriter.writeInboundGoAway(stream1.id(), 0L, Unpooled.EMPTY_BUFFER);

        Future<Void> stream2HeaderFuture = channel.writeAndFlush(
                new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream2));
        channel.runPendingTasks();

        assertTrue(stream1HeaderFuture.syncUninterruptibly().isSuccess());
        assertTrue(stream2HeaderFuture.isDone());

        assertEquals(0, frameCodec.numInitializingStreams());
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void streamIdentifiersExhausted() throws Exception {
        int maxServerStreamId = Integer.MAX_VALUE - 1;

        channel.executor().submit(() -> {
            assertNotNull(frameCodec.connection().local().createStream(maxServerStreamId, false));
            return null;
        }).sync();

        Http2FrameStream stream = frameCodec.newStream();
        assertNotNull(stream);

        Future<Void> writeFuture = channel.writeAndFlush(
                new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream));

        Http2GoAwayFrame goAwayFrame = inboundHandler.readInbound();
        assertNotNull(goAwayFrame);
        assertEquals(NO_ERROR.code(), goAwayFrame.errorCode());
        assertEquals(Integer.MAX_VALUE, goAwayFrame.lastStreamId());
        goAwayFrame.release();
        assertThat(writeFuture.cause(), instanceOf(Http2NoMoreStreamIdsException.class));
    }

    @Test
    public void receivePing() {
        frameInboundWriter.writeInboundPing(false, 12345L);

        Http2PingFrame pingFrame = inboundHandler.readInbound();
        assertNotNull(pingFrame);

        assertEquals(12345, pingFrame.content());
        assertFalse(pingFrame.ack());
    }

    @Test
    public void sendPing() {
        channel.writeAndFlush(new DefaultHttp2PingFrame(12345));

        verify(frameWriter).writePing(any(ChannelHandlerContext.class), eq(false),
                eq(12345L));
    }

    @Test
    public void receiveSettings() {
        Http2Settings settings = new Http2Settings().maxConcurrentStreams(1);
        frameInboundWriter.writeInboundSettings(settings);

        Http2SettingsFrame settingsFrame = inboundHandler.readInbound();
        assertNotNull(settingsFrame);
        assertEquals(settings, settingsFrame.settings());
    }

    @Test
    public void sendSettings() {
        Http2Settings settings = new Http2Settings().maxConcurrentStreams(1);
        channel.writeAndFlush(new DefaultHttp2SettingsFrame(settings));

        verify(frameWriter).writeSettings(any(ChannelHandlerContext.class), eq(settings));
    }

    @Test
    public void iterateActiveStreams() throws Exception {
        setUp(Http2FrameCodecBuilder.forServer().encoderEnforceMaxConcurrentStreams(true),
              new Http2Settings().maxConcurrentStreams(1));

        frameInboundWriter.writeInboundHeaders(3, request, 0, false);

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        Http2FrameStream activeInbond = headersFrame.stream();

        Http2FrameStream activeOutbound = frameCodec.newStream();
        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(activeOutbound));

        Http2FrameStream bufferedOutbound = frameCodec.newStream();
        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(bufferedOutbound));

        @SuppressWarnings("unused")
        Http2FrameStream idleStream = frameCodec.newStream();

        final Set<Http2FrameStream> activeStreams = new HashSet<>();
        final AtomicReference<Http2Exception> errorRef = new AtomicReference<>();
        channel.executor().execute(() -> {
            try {
                frameCodec.forEachActiveStream(stream -> {
                    activeStreams.add(stream);
                    return true;
                });
            } catch (Http2Exception e) {
                errorRef.set(e);
            }
        });
        Http2Exception exception = errorRef.get();
        if (exception != null) {
            throw exception;
        }

        assertEquals(2, activeStreams.size());

        Set<Http2FrameStream> expectedStreams = new HashSet<>();
        expectedStreams.add(activeInbond);
        expectedStreams.add(activeOutbound);
        assertEquals(expectedStreams, activeStreams);
    }

    @Test
    public void autoAckPingTrue() throws Exception {
        setUp(Http2FrameCodecBuilder.forServer().autoAckPingFrame(true), new Http2Settings());
        frameInboundWriter.writeInboundPing(false, 8);
        Http2PingFrame frame = inboundHandler.readInbound();
        assertFalse(frame.ack());
        assertEquals(8, frame.content());
        verify(frameWriter).writePing(any(ChannelHandlerContext.class), eq(true), eq(8L));
    }

    @Test
    public void autoAckPingFalse() throws Exception {
        setUp(Http2FrameCodecBuilder.forServer().autoAckPingFrame(false), new Http2Settings());
        frameInboundWriter.writeInboundPing(false, 8);
        verify(frameWriter, never()).writePing(any(ChannelHandlerContext.class), eq(true), eq(8L));
        Http2PingFrame frame = inboundHandler.readInbound();
        assertFalse(frame.ack());
        assertEquals(8, frame.content());

        // Now ack the frame manually.
        channel.writeAndFlush(new DefaultHttp2PingFrame(8, true));
        verify(frameWriter).writePing(any(ChannelHandlerContext.class), eq(true), eq(8L));
    }

    @Test
    public void streamShouldBeOpenInListener() {
        final Http2FrameStream stream2 = frameCodec.newStream();
        assertEquals(State.IDLE, stream2.state());

        final AtomicBoolean listenerExecuted = new AtomicBoolean();
        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream2))
                .addListener(future -> {
                    assertTrue(future.isSuccess());
                    assertEquals(State.OPEN, stream2.state());
                    listenerExecuted.set(true);
                });

        assertTrue(listenerExecuted.get());
    }

    @Test
    public void upgradeEventNoRefCntError() throws Exception {
        frameInboundWriter.writeInboundHeaders(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID, request, 31, false);
        // Using reflect as the constructor is package-private and the class is final.
        Constructor<UpgradeEvent> constructor =
                UpgradeEvent.class.getDeclaredConstructor(CharSequence.class, FullHttpRequest.class);

        // Check if we could make it accessible which may fail on java9.
        Assumptions.assumeTrue(ReflectionUtil.trySetAccessible(constructor, true) == null);

        HttpServerUpgradeHandler.UpgradeEvent upgradeEvent = constructor.newInstance(
                "HTTP/2", new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/",
                                                     preferredAllocator().allocate(0)));
        assertTrue(upgradeEvent.isAccessible());
        channel.pipeline().fireUserEventTriggered(upgradeEvent);
        assertFalse(upgradeEvent.isAccessible());
    }

    @Test
    public void upgradeWithoutFlowControlling() throws Exception {
        channel.pipeline().addAfter(frameCodec.ctx.name(), null, new ChannelHandler() {
            @Override
            public void channelRead(final ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof Http2DataFrame) {
                    // Simulate consuming the frame and update the flow-controller.
                    Http2DataFrame data = (Http2DataFrame) msg;
                    ctx.writeAndFlush(new DefaultHttp2WindowUpdateFrame(data.initialFlowControlledBytes())
                                              .stream(data.stream())).addListener(future -> {
                        Throwable cause = future.cause();
                        if (cause != null) {
                            ctx.fireExceptionCaught(cause);
                        }
                    });
                }
                ReferenceCountUtil.release(msg);
            }
        });

        frameInboundWriter.writeInboundHeaders(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID, request, 31, false);

        // Using reflect as the constructor is package-private and the class is final.
        Constructor<UpgradeEvent> constructor =
                UpgradeEvent.class.getDeclaredConstructor(CharSequence.class, FullHttpRequest.class);

        // Check if we could make it accessible which may fail on java9.
        Assumptions.assumeTrue(ReflectionUtil.trySetAccessible(constructor, true) == null);

        byte[] longString = new String(new char[70000]).replace("\0", "*").getBytes(UTF_8);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/",
                preferredAllocator().copyOf(longString));

        HttpServerUpgradeHandler.UpgradeEvent upgradeEvent = constructor.newInstance(
            "HTTP/2", request);
        channel.pipeline().fireUserEventTriggered(upgradeEvent);
    }

    @Test
    public void priorityForNonExistingStream() {
        writeHeaderAndAssert(1);

        frameInboundWriter.writeInboundPriority(3, 1, (short) 31, true);
    }

    @Test
    public void priorityForExistingStream() {
        writeHeaderAndAssert(1);
        writeHeaderAndAssert(3);
        frameInboundWriter.writeInboundPriority(3, 1, (short) 31, true);

        assertInboundStreamFrame(3, new DefaultHttp2PriorityFrame(1, (short) 31, true));
    }

    private void writeHeaderAndAssert(int streamId) {
        frameInboundWriter.writeInboundHeaders(streamId, request, 31, false);

        Http2Stream stream = frameCodec.connection().stream(streamId);
        assertNotNull(stream);
        assertEquals(State.OPEN, stream.state());

        assertInboundStreamFrame(streamId, new DefaultHttp2HeadersFrame(request, false, 31));
    }

    private void assertInboundStreamFrame(int expectedId, Http2StreamFrame streamFrame) {
        Http2StreamFrame inboundFrame = inboundHandler.readInbound();
        Http2FrameStream stream2 = inboundFrame.stream();
        assertNotNull(stream2);
        assertEquals(expectedId, stream2.id());
        assertEquals(inboundFrame, streamFrame.stream(stream2));
    }
}
