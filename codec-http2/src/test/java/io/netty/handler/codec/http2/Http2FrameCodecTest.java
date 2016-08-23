/*
 * Copyright 2016 The Netty Project
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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.handler.codec.http2.Http2Stream.State;
import io.netty.handler.logging.LogLevel;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import static io.netty.handler.codec.http2.Http2CodecUtil.isStreamIdValid;
import static io.netty.handler.codec.http2.Http2Stream2.CONNECTION_STREAM;
import static io.netty.util.ReferenceCountUtil.releaseLater;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link Http2FrameCodec}.
 */
public class Http2FrameCodecTest {

    // For verifying outbound frames
    private Http2FrameWriter frameWriter;
    private Http2FrameCodec frameCodec;
    private EmbeddedChannel channel;
    // For injecting inbound frames
    private Http2FrameListener frameListener;
    private ChannelHandlerContext http2HandlerCtx;
    private LastInboundHandler inboundHandler;

    private final Http2Headers request = new DefaultHttp2Headers()
            .method(HttpMethod.GET.asciiName()).scheme(HttpScheme.HTTPS.name())
            .authority(new AsciiString("example.org")).path(new AsciiString("/foo"));
    private final Http2Headers response = new DefaultHttp2Headers()
            .status(HttpResponseStatus.OK.codeAsText());

    @Before
    public void setUp() throws Exception {
        setUp(Http2FrameCodecBuilder.forServer(), new Http2Settings());
    }

    @After
    public void tearDown() throws Exception {
        inboundHandler.finishAndReleaseAll();
        channel.close();
    }

    private void setUp(Http2FrameCodecBuilder frameCodecBuilder, Http2Settings initialRemoteSettings) throws Exception {
        frameWriter = spy(new VerifiableHttp2FrameWriter());
        frameCodec = frameCodecBuilder.frameWriter(frameWriter).frameLogger(new Http2FrameLogger(LogLevel.TRACE))
                .initialSettings(initialRemoteSettings).build();
        frameListener = ((DefaultHttp2ConnectionDecoder) frameCodec.connectionHandler().decoder())
                .internalFrameListener();
        inboundHandler = new LastInboundHandler();

        channel = new EmbeddedChannel();
        channel.connect(new InetSocketAddress(0));
        channel.pipeline().addLast(frameCodec);
        channel.pipeline().addLast(inboundHandler);
        channel.pipeline().fireChannelActive();

        http2HandlerCtx = channel.pipeline().context(frameCodec.connectionHandler());

        // Handshake
        verify(frameWriter).writeSettings(eq(http2HandlerCtx),
                                          anyHttp2Settings(), anyChannelPromise());
        verifyNoMoreInteractions(frameWriter);
        channel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());
        frameListener.onSettingsRead(http2HandlerCtx, initialRemoteSettings);
        verify(frameWriter).writeSettingsAck(eq(http2HandlerCtx), anyChannelPromise());
        frameListener.onSettingsAckRead(http2HandlerCtx);

        Http2SettingsFrame settingsFrame = inboundHandler.readInbound();
        assertNotNull(settingsFrame);
    }

    @Test
    public void connectionHandlerShouldBeAddedBeforeFramingHandler() {
        Iterator<Entry<String, ChannelHandler>> iter = channel.pipeline().iterator();
        while (iter.hasNext()) {
            ChannelHandler handler = iter.next().getValue();
            if (handler instanceof Http2ConnectionHandler) {
                break;
            }
        }
        assertTrue(iter.hasNext());
        assertThat(iter.next().getValue(), instanceOf(Http2FrameCodec.class));
    }

    @Test
    public void headerRequestHeaderResponse() throws Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 1, request, 31, true);

        Http2Stream stream = frameCodec.connectionHandler().connection().stream(1);
        assertNotNull(stream);
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());

        Http2StreamFrame inboundFrame = inboundHandler.readInbound();
        Http2Stream2 stream2 = inboundFrame.stream();
        assertNotNull(stream2);
        assertEquals(1, stream2.id());
        assertEquals(inboundFrame, new DefaultHttp2HeadersFrame(request, true, 31).stream(stream2));
        assertNull(inboundHandler.readInbound());

        inboundHandler.writeOutbound(new DefaultHttp2HeadersFrame(response, true, 27).stream(stream2));
        verify(frameWriter).writeHeaders(
                eq(http2HandlerCtx), eq(1), eq(response), anyInt(), anyShort(), anyBoolean(),
                eq(27), eq(true), anyChannelPromise());
        verify(frameWriter, never()).writeRstStream(
                any(ChannelHandlerContext.class), anyInt(), anyLong(), anyChannelPromise());

        assertEquals(State.CLOSED, stream.state());
        assertTrue(channel.isActive());
    }

    @Test
    public void entityRequestEntityResponse() throws Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 1, request, 0, false);

        Http2Stream stream = frameCodec.connectionHandler().connection().stream(1);
        assertNotNull(stream);
        assertEquals(State.OPEN, stream.state());

        Http2HeadersFrame inboundHeaders = inboundHandler.readInbound();
        Http2Stream2 stream2 = inboundHeaders.stream();
        assertNotNull(stream2);
        assertEquals(1, stream2.id());
        assertEquals(new DefaultHttp2HeadersFrame(request, false).stream(stream2), inboundHeaders);
        assertNull(inboundHandler.readInbound());

        ByteBuf hello = bb("hello");
        frameListener.onDataRead(http2HandlerCtx, 1, hello, 31, true);
        // Release hello to emulate ByteToMessageDecoder
        hello.release();
        Http2DataFrame inboundData = inboundHandler.readInbound();
        Http2DataFrame expected = new DefaultHttp2DataFrame(bb("hello"), true, 31).stream(stream2);
        assertEquals(expected, inboundData);

        assertEquals(1, inboundData.refCnt());
        expected.release();
        inboundData.release();
        assertNull(inboundHandler.readInbound());

        inboundHandler.writeOutbound(new DefaultHttp2HeadersFrame(response, false).stream(stream2));
        verify(frameWriter).writeHeaders(eq(http2HandlerCtx), eq(1), eq(response), anyInt(),
                                         anyShort(), anyBoolean(), eq(0), eq(false), anyChannelPromise());

        inboundHandler.writeOutbound(new DefaultHttp2DataFrame(bb("world"), true, 27).stream(stream2));
        ArgumentCaptor<ByteBuf> outboundData = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeData(eq(http2HandlerCtx), eq(1), outboundData.capture(), eq(27),
                                      eq(true), anyChannelPromise());

        ByteBuf bb = bb("world");
        assertEquals(bb, outboundData.getValue());
        assertEquals(1, outboundData.getValue().refCnt());
        bb.release();
        verify(frameWriter, never()).writeRstStream(
                any(ChannelHandlerContext.class), anyInt(), anyLong(), anyChannelPromise());
        assertTrue(channel.isActive());
    }

    @Test
    public void sendRstStream() throws Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 3, request, 31, true);

        Http2Stream stream = frameCodec.connectionHandler().connection().stream(3);
        assertNotNull(stream);
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());

        Http2HeadersFrame inboundHeaders = inboundHandler.readInbound();
        assertNotNull(inboundHeaders);
        assertTrue(inboundHeaders.endStream());

        Http2Stream2 stream2 = inboundHeaders.stream();
        assertNotNull(stream2);
        assertEquals(3, stream2.id());

        inboundHandler.writeOutbound(new DefaultHttp2ResetFrame(314 /* non-standard error */).stream(stream2));
        verify(frameWriter).writeRstStream(
                eq(http2HandlerCtx), eq(3), eq(314L), anyChannelPromise());
        assertEquals(State.CLOSED, stream.state());
        assertTrue(channel.isActive());
    }

    @Test
    public void receiveRstStream() throws Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 3, request, 31, false);

        Http2Stream stream = frameCodec.connectionHandler().connection().stream(3);
        assertNotNull(stream);
        assertEquals(State.OPEN, stream.state());

        Http2HeadersFrame expectedHeaders = new DefaultHttp2HeadersFrame(request, false, 31);
        Http2HeadersFrame actualHeaders = inboundHandler.readInbound();
        assertEquals(expectedHeaders.stream(actualHeaders.stream()), actualHeaders);

        frameListener.onRstStreamRead(http2HandlerCtx, 3, Http2Error.NO_ERROR.code());

        Http2ResetFrame expectedRst = new DefaultHttp2ResetFrame(Http2Error.NO_ERROR).stream(actualHeaders.stream());
        Http2ResetFrame actualRst = inboundHandler.readInbound();
        assertEquals(expectedRst, actualRst);

        assertNull(inboundHandler.readInbound());
    }

    @Test
    public void sendGoAway() throws Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 3, request, 31, false);

        Http2Stream stream = frameCodec.connectionHandler().connection().stream(3);
        assertNotNull(stream);
        assertEquals(State.OPEN, stream.state());

        ByteBuf debugData = bb("debug");
        ByteBuf expected = debugData.copy();

        Http2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR.code(), debugData.slice());
        goAwayFrame.setExtraStreamIds(2);

        inboundHandler.writeOutbound(goAwayFrame);
        verify(frameWriter).writeGoAway(
                eq(http2HandlerCtx), eq(7), eq(Http2Error.NO_ERROR.code()), eq(expected), anyChannelPromise());
        assertEquals(1, debugData.refCnt());
        assertEquals(State.OPEN, stream.state());
        assertTrue(channel.isActive());
        expected.release();
    }

    @Test
    public void receiveGoaway() throws Exception {
        ByteBuf debugData = bb("foo");
        frameListener.onGoAwayRead(http2HandlerCtx, 2, Http2Error.NO_ERROR.code(), debugData);
        // Release debugData to emulate ByteToMessageDecoder
        debugData.release();
        Http2GoAwayFrame expectedFrame = new DefaultHttp2GoAwayFrame(2, Http2Error.NO_ERROR.code(), bb("foo"));
        Http2GoAwayFrame actualFrame = inboundHandler.readInbound();

        assertEquals(expectedFrame, actualFrame);
        assertNull(inboundHandler.readInbound());

        expectedFrame.release();
        actualFrame.release();
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

        ChannelFuture f = channel.write(frame);
        f.await();
        assertTrue(f.isDone());
        assertFalse(f.isSuccess());
        assertThat(f.cause(), instanceOf(UnsupportedMessageTypeException.class));
        assertEquals(0, frame.refCnt());
    }

    @Test
    public void goAwayLastStreamIdOverflowed() throws Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 5, request, 31, false);

        Http2Stream stream = frameCodec.connectionHandler().connection().stream(5);
        assertNotNull(stream);
        assertEquals(State.OPEN, stream.state());

        ByteBuf debugData = bb("debug");
        Http2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR.code(), debugData.slice());
        goAwayFrame.setExtraStreamIds(Integer.MAX_VALUE);

        inboundHandler.writeOutbound(goAwayFrame);
        // When the last stream id computation overflows, the last stream id should just be set to 2^31 - 1.
        verify(frameWriter).writeGoAway(eq(http2HandlerCtx), eq(Integer.MAX_VALUE), eq(Http2Error.NO_ERROR.code()),
                                        eq(debugData), anyChannelPromise());
        assertEquals(1, debugData.refCnt());
        assertEquals(State.OPEN, stream.state());
        assertTrue(channel.isActive());
    }

    @Test
    public void streamErrorShouldFireException() throws Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 3, request, 31, false);

        Http2Stream stream = frameCodec.connectionHandler().connection().stream(3);
        assertNotNull(stream);

        StreamException streamEx = new StreamException(3, Http2Error.INTERNAL_ERROR, "foo");
        frameCodec.connectionHandler().onError(http2HandlerCtx, streamEx);

        Http2HeadersFrame headersFrame = inboundHandler.readInboundMessageOrUserEvent();
        assertNotNull(headersFrame);

        try {
            inboundHandler.checkException();
            fail("stream exception expected");
        } catch (Http2Stream2Exception e) {
            assertEquals(streamEx, e.getCause());
        }

        assertNull(inboundHandler.readInboundMessageOrUserEvent());
    }

    @Test
    public void windowUpdateFrameDecrementsConsumedBytes() throws Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 3, request, 31, false);

        Http2Connection connection = frameCodec.connectionHandler().connection();
        Http2Stream stream = connection.stream(3);
        assertNotNull(stream);

        ByteBuf data = Unpooled.buffer(100).writeZero(100);
        frameListener.onDataRead(http2HandlerCtx, 3, data, 0, true);

        Http2HeadersFrame inboundHeaders = inboundHandler.readInbound();
        assertNotNull(inboundHeaders);
        assertNotNull(inboundHeaders.stream());

        Http2Stream2 stream2 = inboundHeaders.stream();

        int before = connection.local().flowController().unconsumedBytes(stream);
        ChannelFuture f = channel.write(new DefaultHttp2WindowUpdateFrame(100).stream(stream2));
        int after = connection.local().flowController().unconsumedBytes(stream);
        assertEquals(100, before - after);
        assertTrue(f.isSuccess());
        data.release();
    }

    @Test
    public void windowUpdateMayFail() throws Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 3, request, 31, false);
        Http2Connection connection = frameCodec.connectionHandler().connection();
        Http2Stream stream = connection.stream(3);
        assertNotNull(stream);

        Http2HeadersFrame inboundHeaders = inboundHandler.readInbound();
        assertNotNull(inboundHeaders);

        Http2Stream2 stream2 = inboundHeaders.stream();

        // Fails, cause trying to return too many bytes to the flow controller
        ChannelFuture f = channel.write(new DefaultHttp2WindowUpdateFrame(100).stream(stream2));
        assertTrue(f.isDone());
        assertFalse(f.isSuccess());
        assertThat(f.cause(), instanceOf(Http2Exception.class));
    }

    @Test
    public void inboundWindowUpdateShouldBeForwarded() throws Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 3, request, 31, false);
        frameListener.onWindowUpdateRead(http2HandlerCtx, 3, 100);
        // Connection-level window update
        frameListener.onWindowUpdateRead(http2HandlerCtx, 0, 100);

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
    public void streamZeroWindowUpdateIncrementsConnectionWindow() throws Exception {
        Http2Connection connection = frameCodec.connectionHandler().connection();
        Http2LocalFlowController localFlow = connection.local().flowController();
        int initialWindowSizeBefore = localFlow.initialWindowSize();

        int windowUpdate = 1024;

        channel.write(new DefaultHttp2WindowUpdateFrame(windowUpdate).stream(CONNECTION_STREAM));

        assertEquals(initialWindowSizeBefore + windowUpdate, localFlow.initialWindowSize());
    }

    @Test
    public void sendSettingsFrame() {
        Http2Settings settings = new Http2Settings();
        channel.write(new DefaultHttp2SettingsFrame(settings));

        verify(frameWriter).writeSettings(eq(http2HandlerCtx), same(settings), any(ChannelPromise.class));
    }

    @Test(timeout = 1000)
    public void createAndCloseIdleStreamObject() {
        Http2Stream2 stream = frameCodec.newStream();

        assertNotNull(stream);
        assertFalse(isStreamIdValid(stream.id()));
        assertFalse(stream.closeFuture().isDone());
        assertFalse(stream.closeFuture().isCancellable());

        channel.close().syncUninterruptibly();

        assertTrue(stream.closeFuture().isDone());
    }

    @Test(timeout = 1000)
    public void newOutboundStream() {
        final Http2Stream2 stream = frameCodec.newStream();

        assertNotNull(stream);
        assertFalse(isStreamIdValid(stream.id()));

        final Promise<Void> listenerExecuted = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);

        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(), false).stream(stream))
               .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        assertTrue(future.isSuccess());
                        assertTrue(isStreamIdValid(stream.id()));
                        assertFalse(stream.closeFuture().isDone());

                        listenerExecuted.setSuccess(null);
                    }
                }
        );
        ByteBuf data = Unpooled.buffer().writeZero(100);
        ChannelFuture f = channel.writeAndFlush(new DefaultHttp2DataFrame(data).stream(stream));
        assertTrue(f.isSuccess());

        listenerExecuted.syncUninterruptibly();
        assertTrue(listenerExecuted.isSuccess());
    }

    @Test
    public void newOutboundStreamsShouldBeBuffered() throws Exception {
        setUp(Http2FrameCodecBuilder.forServer().bufferOutboundStreams(true),
              new Http2Settings().maxConcurrentStreams(1));

        Http2Stream2 stream1 = frameCodec.newStream();
        Http2Stream2 stream2 = frameCodec.newStream();

        ChannelPromise promise1 = channel.newPromise();
        ChannelPromise promise2 = channel.newPromise();

        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream1), promise1);
        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream2), promise2);

        assertTrue(isStreamIdValid(stream1.id()));
        assertTrue(isStreamIdValid(stream2.id()));

        assertTrue(promise1.syncUninterruptibly().isSuccess());
        assertFalse(promise2.isDone());

        // Increase concurrent streams limit to 2
        frameListener.onSettingsRead(http2HandlerCtx, new Http2Settings().maxConcurrentStreams(2));

        channel.flush();

        assertTrue(promise2.syncUninterruptibly().isSuccess());
    }

    @Test
    public void closeFutureShouldCompleteIfStreamFailsToBecomeActive() throws Exception {
        setUp(Http2FrameCodecBuilder.forServer().bufferOutboundStreams(true),
              new Http2Settings().maxConcurrentStreams(0));

        Http2Stream2 stream = frameCodec.newStream();
        ChannelPromise promise = channel.newPromise();

        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream), promise);

        assertTrue(isStreamIdValid(stream.id()));
        assertFalse(promise.isDone());
        assertFalse(stream.closeFuture().isDone());

        promise.setFailure(new Exception());

        assertTrue(stream.closeFuture().isDone());
    }

    @Test
    public void streamIdentifiersExhausted() throws Http2Exception {
        int maxServerStreamId = Integer.MAX_VALUE - 1;

        assertNotNull(frameCodec.connectionHandler().connection().local().createStream(maxServerStreamId, false));

        Http2Stream2 stream = frameCodec.newStream();
        assertNotNull(stream);

        ChannelPromise writePromise = channel.newPromise();
        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(stream), writePromise);

        assertThat(writePromise.cause(), instanceOf(Http2NoMoreStreamIdsException.class));
    }

    @Test
    public void receivePing() throws Http2Exception {
        ByteBuf data = Unpooled.buffer(8).writeLong(12345);
        frameListener.onPingRead(http2HandlerCtx, releaseLater(data));

        Http2PingFrame pingFrame = inboundHandler.readInbound();
        assertNotNull(pingFrame);

        assertEquals(data, pingFrame.content());
        assertFalse(pingFrame.ack());
        pingFrame.release();
    }

    @Test
    public void sendPing() {
        ByteBuf data = Unpooled.buffer(8).writeLong(12345);
        channel.writeAndFlush(new DefaultHttp2PingFrame(data));

        verify(frameWriter).writePing(eq(http2HandlerCtx), eq(false), eq(data), anyChannelPromise());
    }

    @Test
    public void receiveSettings() throws Http2Exception {
        Http2Settings settings = new Http2Settings().maxConcurrentStreams(1);
        frameListener.onSettingsRead(http2HandlerCtx, settings);

        Http2SettingsFrame settingsFrame = inboundHandler.readInbound();
        assertNotNull(settingsFrame);
        assertEquals(settings, settingsFrame.settings());
    }

    @Test
    public void sendSettings() {
        Http2Settings settings = new Http2Settings().maxConcurrentStreams(1);
        channel.writeAndFlush(new DefaultHttp2SettingsFrame(settings));

        verify(frameWriter).writeSettings(eq(http2HandlerCtx), eq(settings), anyChannelPromise());
    }

    @Test
    public void managedStateShouldPersist() throws Http2Exception {
        frameListener.onHeadersRead(http2HandlerCtx, 3, request, 0, false);

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        Http2Stream2 inboundStream = headersFrame.stream();
        assertNotNull(inboundStream);
        assertNull(inboundStream.managedState());

        Object inboundState = new Object();
        inboundStream.managedState(inboundState);

        Http2Stream2 outboundStream = frameCodec.newStream();
        Object outboundState = new Object();
        outboundStream.managedState(outboundState);
        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(outboundStream));

        ByteBuf inboundData = Unpooled.buffer(100).writeZero(100);
        frameListener.onDataRead(http2HandlerCtx, inboundStream.id(), inboundData, 0, false);

        Http2DataFrame dataFrame = inboundHandler.readInbound();
        assertNotNull(dataFrame);
        assertSame(inboundStream, dataFrame.stream());
        assertSame(inboundState, dataFrame.stream().managedState());
        dataFrame.release();

        frameListener.onHeadersRead(http2HandlerCtx, outboundStream.id(), new DefaultHttp2Headers(), 0, false);
        headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);
        assertSame(outboundStream, headersFrame.stream());
        assertSame(outboundState, headersFrame.stream().managedState());
    }

    @Test
    public void iterateActiveStreams() throws Exception {
        setUp(Http2FrameCodecBuilder.forServer().bufferOutboundStreams(true),
              new Http2Settings().maxConcurrentStreams(1));

        frameListener.onHeadersRead(http2HandlerCtx, 3, request, 0, false);

        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        Http2Stream2 activeInbond = headersFrame.stream();

        Http2Stream2 activeOutbound = frameCodec.newStream();
        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(activeOutbound));

        Http2Stream2 bufferedOutbound = frameCodec.newStream();
        channel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()).stream(bufferedOutbound));

        @SuppressWarnings("unused")
        Http2Stream2 idleStream = frameCodec.newStream();

        final Set<Http2Stream2> activeStreams = new HashSet<Http2Stream2>();
        frameCodec.forEachActiveStream(new Http2Stream2Visitor() {
            @Override
            public boolean visit(Http2Stream2 stream) {
                activeStreams.add(stream);
                return true;
            }
        });

        assertEquals(2, activeStreams.size());

        Set<Http2Stream2> expectedStreams = new HashSet<Http2Stream2>();
        expectedStreams.add(activeInbond);
        expectedStreams.add(activeOutbound);
        assertEquals(expectedStreams, activeStreams);
    }

    private static ChannelPromise anyChannelPromise() {
        return any(ChannelPromise.class);
    }

    private static Http2Settings anyHttp2Settings() {
        return any(Http2Settings.class);
    }

    private static ByteBuf bb(String s) {
        return ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, s);
    }

    private static class VerifiableHttp2FrameWriter extends DefaultHttp2FrameWriter {
        @Override
        public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                       int padding, boolean endStream, ChannelPromise promise) {
            // duplicate 'data' to prevent readerIndex from being changed, to ease verification
            return super.writeData(ctx, streamId, data.duplicate(), padding, endStream, promise);
        }
    }
}
