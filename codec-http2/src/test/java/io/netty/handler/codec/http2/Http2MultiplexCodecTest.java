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

import static io.netty.util.ReferenceCountUtil.releaseLater;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.util.AsciiString;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;

import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link Http2MultiplexCodec}.
 */
public class Http2MultiplexCodecTest {
    private final TestChannelInitializer streamInit = new TestChannelInitializer();
    // For verifying outbound frames
    private final Http2FrameWriter frameWriter = spy(new VerifiableHttp2FrameWriter());
    private final Http2MultiplexCodec serverCodec = new Http2MultiplexCodec(true, streamInit, null, frameWriter);
    private final EmbeddedChannel channel = new EmbeddedChannel();
    // For injecting inbound frames
    private final Http2FrameListener frameListener
            = ((DefaultHttp2ConnectionDecoder) serverCodec.connectionHandler().decoder())
            .internalFrameListener();
    private ChannelHandlerContext http2HandlerCtx;
    private Http2Headers request = new DefaultHttp2Headers()
            .method(HttpMethod.GET.asciiName()).scheme(HttpScheme.HTTPS.name())
            .authority(new AsciiString("example.org")).path(new AsciiString("/foo"));
    private Http2Headers response = new DefaultHttp2Headers()
            .status(HttpResponseStatus.OK.codeAsText());

    @Before
    public void setUp() throws Exception {
        channel.connect(null);
        channel.pipeline().addLast(serverCodec);
        http2HandlerCtx = channel.pipeline().context(serverCodec.connectionHandler());

        // Handshake
        verify(frameWriter).writeSettings(eq(http2HandlerCtx),
            anyHttp2Settings(), anyChannelPromise());
        verifyNoMoreInteractions(frameWriter);
        channel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());
        frameListener.onSettingsRead(http2HandlerCtx, new Http2Settings());
        verify(frameWriter).writeSettingsAck(eq(http2HandlerCtx), anyChannelPromise());
        frameListener.onSettingsAckRead(http2HandlerCtx);
    }

    @After
    public void tearDown() {
        Object o;
        while ((o = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(o);
        }
    }

    @Test
    public void startStop() throws Exception {
        assertTrue(channel.isActive());
        channel.close();
        verify(frameWriter).writeGoAway(
                eq(http2HandlerCtx), eq(0), eq(0L), eq(Unpooled.EMPTY_BUFFER), anyChannelPromise());
        assertTrue(!channel.isActive());
    }

    @Test
    public void headerRequestHeaderResponse() throws Exception {
        LastInboundHandler stream = new LastInboundHandler();
        streamInit.handler = stream;
        frameListener.onHeadersRead(http2HandlerCtx, 1, request, 31, true);
        assertNull(streamInit.handler);
        assertEquals(new DefaultHttp2HeadersFrame(request, true, 31), stream.readInbound());
        assertNull(stream.readInbound());
        assertTrue(stream.channel().isActive());

        stream.writeOutbound(new DefaultHttp2HeadersFrame(response, true, 27));
        verify(frameWriter).writeHeaders(
                eq(http2HandlerCtx), eq(1), eq(response), anyInt(), anyShort(), anyBoolean(),
                eq(27), eq(true), anyChannelPromise());
        verify(frameWriter, never()).writeRstStream(
            any(ChannelHandlerContext.class), anyInt(), anyLong(), anyChannelPromise());
        assertFalse(stream.channel().isActive());
        assertTrue(channel.isActive());
    }

    @Test
    public void entityRequestEntityResponse() throws Exception {
        LastInboundHandler stream = new LastInboundHandler();
        streamInit.handler = stream;
        frameListener.onHeadersRead(http2HandlerCtx, 1, request, 0, false);
        assertEquals(new DefaultHttp2HeadersFrame(request, false), stream.readInbound());
        assertNull(stream.readInbound());
        assertTrue(stream.channel().isActive());

        ByteBuf hello = bb("hello");
        frameListener.onDataRead(http2HandlerCtx, 1, hello, 31, true);
        // Release hello to emulate ByteToMessageDecoder
        hello.release();
        Http2DataFrame inboundData = stream.readInbound();
        assertEquals(releaseLater(new DefaultHttp2DataFrame(bb("hello"), true, 31)), inboundData);
        assertEquals(1, inboundData.refCnt());
        assertNull(stream.readInbound());
        assertTrue(stream.channel().isActive());

        stream.writeOutbound(new DefaultHttp2HeadersFrame(response, false));
        verify(frameWriter).writeHeaders(eq(http2HandlerCtx), eq(1), eq(response), anyInt(),
            anyShort(), anyBoolean(), eq(0), eq(false), anyChannelPromise());
        assertTrue(stream.channel().isActive());

        stream.writeOutbound(new DefaultHttp2DataFrame(bb("world"), true, 27));
        ArgumentCaptor<ByteBuf> outboundData = ArgumentCaptor.forClass(ByteBuf.class);
        verify(frameWriter).writeData(eq(http2HandlerCtx), eq(1), outboundData.capture(), eq(27),
            eq(true), anyChannelPromise());
        assertEquals(releaseLater(bb("world")), outboundData.getValue());
        assertEquals(1, outboundData.getValue().refCnt());
        verify(frameWriter, never()).writeRstStream(
            any(ChannelHandlerContext.class), anyInt(), anyLong(), anyChannelPromise());
        assertFalse(stream.channel().isActive());
        assertTrue(channel.isActive());
    }

    @Test
    public void closeCausesReset() throws Exception {
        LastInboundHandler stream = new LastInboundHandler();
        streamInit.handler = stream;
        frameListener.onHeadersRead(http2HandlerCtx, 3, request, 31, true);

        stream.channel().close();
        channel.runPendingTasks();
        channel.checkException();
        stream.checkException();
        verify(frameWriter).writeRstStream(
            eq(http2HandlerCtx), eq(3), eq(8L), anyChannelPromise());
        assertFalse(stream.channel().isActive());
        assertTrue(channel.isActive());
    }

    @Test
    public void sendRstStream() throws Exception {
        LastInboundHandler stream = new LastInboundHandler();
        streamInit.handler = stream;
        frameListener.onHeadersRead(http2HandlerCtx, 5, request, 31, true);

        stream.writeOutbound(new DefaultHttp2ResetFrame(314 /* non-standard error */));
        verify(frameWriter).writeRstStream(
            eq(http2HandlerCtx), eq(5), eq(314L), anyChannelPromise());
        assertFalse(stream.channel().isActive());
        assertTrue(channel.isActive());
    }

    private static ChannelPromise anyChannelPromise() {
        return any(ChannelPromise.class);
    }

    private static Http2Settings anyHttp2Settings() {
        return any(Http2Settings.class);
    }

    private static ByteBuf bb(String s) {
        ByteBuf buf = Unpooled.buffer(s.length() * 4);
        ByteBufUtil.writeUtf8(buf, s);
        return buf;
    }

    static class TestChannelInitializer extends ChannelInitializer<Channel> {
        ChannelHandler handler;

        @Override
        public void initChannel(Channel channel) {
            if (handler != null) {
                channel.pipeline().addLast(handler);
                handler = null;
            }
        }
    }

    static class LastInboundHandler extends ChannelDuplexHandler {
        private final Queue<Object> inboundMessages = new ArrayDeque<Object>();
        private final Queue<Object> userEvents = new ArrayDeque<Object>();
        private Throwable lastException;
        private ChannelHandlerContext ctx;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            inboundMessages.add(msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            userEvents.add(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (lastException != null) {
                cause.printStackTrace();
            } else {
                lastException = cause;
            }
        }

        public void checkException() throws Exception {
            if (lastException == null) {
                return;
            }
            Throwable t = lastException;
            lastException = null;
            PlatformDependent.throwException(t);
        }

        @SuppressWarnings("unchecked")
        public <T> T readInbound() {
            return (T) inboundMessages.poll();
        }

        @SuppressWarnings("unchecked")
        public <T> T readUserEvent() {
            return (T) userEvents.poll();
        }

        public void writeOutbound(Object... msgs) throws Exception {
            for (Object msg : msgs) {
                ctx.write(msg);
            }
            ctx.flush();
            EmbeddedChannel parent = (EmbeddedChannel) ctx.channel().parent();
            parent.runPendingTasks();
            parent.checkException();
            checkException();
        }

        public Channel channel() {
            return ctx.channel();
        }
    }

    public static class VerifiableHttp2FrameWriter extends DefaultHttp2FrameWriter {
        @Override
        public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                int padding, boolean endStream, ChannelPromise promise) {
            // duplicate 'data' to prevent readerIndex from being changed, to ease verification
            return super.writeData(ctx, streamId, data.duplicate(), padding, endStream, promise);
        }
    }
}
