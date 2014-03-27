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

package io.netty.handler.codec.http2.draft10.frame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.draft10.Http2Headers;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;

/**
 * Tests encoding/decoding each HTTP2 frame type.
 */
public class Http2FrameRoundtripTest {

    private static final EventLoopGroup group = new NioEventLoopGroup();

    private CaptureHandler captureHandler;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel clientChannel;

    @Before
    public void setup() throws Exception {
        captureHandler = new CaptureHandler();
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("codec", new Http2FrameCodec());
                p.addLast("handler", captureHandler);
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("codec", new Http2FrameCodec());
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
    }

    @After
    public void teardown() throws Exception {
        serverChannel.close().sync();
        sb.group().shutdownGracefully();
        cb.group().shutdownGracefully();
    }

    @AfterClass
    public static void destroy() throws Exception {
        group.shutdownGracefully().sync();
    }

    @Test
    public void dataFrameShouldMatch() throws Exception {
        String text = "hello world";
        Http2DataFrame in =
                new DefaultHttp2DataFrame.Builder()
                        .setContent(Unpooled.copiedBuffer(text.getBytes())).setEndOfStream(true)
                        .setStreamId(0x7FFFFFFF).setPaddingLength(100).build().retain();

        Http2Frame out = sendAndWaitForFrame(clientChannel, in, captureHandler);
        assertAndReleaseFrames(in, out);
    }

    @Test
    public void headersFrameWithoutPriorityShouldMatch() throws Exception {
        Http2Headers headers =
                new Http2Headers.Builder().setMethod("GET").setScheme("https")
                        .setAuthority("example.org").setPath("/some/path/resource2").build();
        Http2HeadersFrame in =
                new DefaultHttp2HeadersFrame.Builder().setHeaders(headers).setEndOfStream(true)
                        .setStreamId(0x7FFFFFFF).build();

        Http2Frame out = sendAndWaitForFrame(clientChannel, in, captureHandler);
        assertAndReleaseFrames(in, out);
    }

    @Test
    public void headersFrameWithPriorityShouldMatch() throws Exception {
        Http2Headers headers =
                new Http2Headers.Builder().setMethod("GET").setScheme("https")
                        .setAuthority("example.org").setPath("/some/path/resource2").build();
        Http2HeadersFrame in =
                new DefaultHttp2HeadersFrame.Builder().setHeaders(headers).setEndOfStream(true)
                        .setStreamId(0x7FFFFFFF).setPriority(0x7FFFFFFF).build();

        Http2Frame out = sendAndWaitForFrame(clientChannel, in, captureHandler);
        assertAndReleaseFrames(in, out);
    }

    @Test
    public void goAwayFrameShouldMatch() throws Exception {
        String text = "test";
        Http2GoAwayFrame in =
                new DefaultHttp2GoAwayFrame.Builder()
                        .setDebugData(Unpooled.copiedBuffer(text.getBytes()))
                        .setLastStreamId(0x7FFFFFFF).setErrorCode(0xFFFFFFFFL).build().retain();

        Http2Frame out = sendAndWaitForFrame(clientChannel, in, captureHandler);
        assertAndReleaseFrames(in, out);
    }

    @Test
    public void pingFrameShouldMatch() throws Exception {
        ByteBuf buf = Unpooled.copiedBuffer("01234567", Charsets.UTF_8);

        Http2PingFrame in =
                new DefaultHttp2PingFrame.Builder().setAck(true).setData(buf).build().retain();

        Http2Frame out = sendAndWaitForFrame(clientChannel, in, captureHandler);
        assertAndReleaseFrames(in, out);
    }

    @Test
    public void priorityFrameShouldMatch() throws Exception {
        Http2PriorityFrame in =
                new DefaultHttp2PriorityFrame.Builder().setStreamId(0x7FFFFFFF)
                        .setPriority(0x7FFFFFFF).build();

        Http2Frame out = sendAndWaitForFrame(clientChannel, in, captureHandler);
        assertEquals(in, out);
    }

    @Test
    public void pushPromiseFrameShouldMatch() throws Exception {
        Http2Headers headers =
                new Http2Headers.Builder().setMethod("GET").setScheme("https")
                        .setAuthority("example.org").setPath("/some/path/resource2").build();
        Http2PushPromiseFrame in =
                new DefaultHttp2PushPromiseFrame.Builder().setHeaders(headers)
                        .setStreamId(0x7FFFFFFF).setPromisedStreamId(0x7FFFFFFF).build();

        Http2Frame out = sendAndWaitForFrame(clientChannel, in, captureHandler);
        assertAndReleaseFrames(in, out);
    }

    @Test
    public void rstStreamFrameShouldMatch() throws Exception {
        Http2RstStreamFrame in =
                new DefaultHttp2RstStreamFrame.Builder().setStreamId(0x7FFFFFFF)
                        .setErrorCode(0xFFFFFFFFL).build();

        Http2Frame out = sendAndWaitForFrame(clientChannel, in, captureHandler);
        assertEquals(in, out);
    }

    @Test
    public void settingsFrameShouldMatch() throws Exception {
        Http2SettingsFrame in =
                new DefaultHttp2SettingsFrame.Builder().setAck(false).setHeaderTableSize(1)
                        .setInitialWindowSize(Integer.MAX_VALUE).setPushEnabled(true)
                        .setMaxConcurrentStreams(100L).build();

        Http2Frame out = sendAndWaitForFrame(clientChannel, in, captureHandler);
        assertEquals(in, out);
    }

    @Test
    public void windowUpdateFrameShouldMatch() throws Exception {
        Http2WindowUpdateFrame in =
                new DefaultHttp2WindowUpdateFrame.Builder().setStreamId(0x7FFFFFFF)
                        .setWindowSizeIncrement(0x7FFFFFFF).build();

        Http2Frame out = sendAndWaitForFrame(clientChannel, in, captureHandler);
        assertEquals(in, out);
    }

    @Test
    public void stressTest() throws Exception {
        Http2Headers headers =
                new Http2Headers.Builder().setMethod("GET").setScheme("https")
                        .setAuthority("example.org").setPath("/some/path/resource2").build();
        String text = "hello world";
        int numStreams = 1000;
        for (int i = 1; i < numStreams + 1; ++i) {
            Http2HeadersFrame headersFrame =
                    new DefaultHttp2HeadersFrame.Builder().setHeaders(headers).setStreamId(i)
                            .build();

            Http2DataFrame dataFrame =
                    new DefaultHttp2DataFrame.Builder()
                            .setContent(Unpooled.copiedBuffer(text.getBytes()))
                            .setEndOfStream(true).setStreamId(i).setPaddingLength(100).build()
                            .retain();

            clientChannel.writeAndFlush(headersFrame);
            clientChannel.writeAndFlush(dataFrame);
        }

        // Wait for all frames to be received.
        long theFuture = System.currentTimeMillis() + 5000;
        int expectedFrames = numStreams * 2;
        while (captureHandler.count < expectedFrames && System.currentTimeMillis() < theFuture) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) { // Ignore.
            }
        }
        assertEquals(expectedFrames, captureHandler.count);
        captureHandler.release();
    }

    private void assertAndReleaseFrames(Http2Frame in, Http2Frame out) {
        assertEquals(in, out);
        if (in instanceof ByteBufHolder) {
            assertEquals(1, ((ByteBufHolder) in).refCnt());
            ((ByteBufHolder) in).release();
        }
        if (out instanceof ByteBufHolder) {
            assertEquals(1, ((ByteBufHolder) out).refCnt());
            ((ByteBufHolder) out).release();
        }
    }

    private static Http2Frame sendAndWaitForFrame(Channel cc, Http2Frame frame,
            CaptureHandler captureHandler) {
        cc.writeAndFlush(frame);
        long theFuture = System.currentTimeMillis() + 3000;
        while (captureHandler.frame == null && System.currentTimeMillis() < theFuture) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) { // Ignore.
            }
        }
        assertNotNull("not null frame", captureHandler.frame);
        return captureHandler.frame;
    }

    private static class CaptureHandler extends ChannelHandlerAdapter {
        public volatile Http2Frame frame;
        public volatile int count;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // Release any allocated data for the previous frame if there was one.
            release();

            // Copy the frame if it contains allocated data.
            if (msg instanceof ByteBufHolder) {
                ByteBufHolder holder = (ByteBufHolder) msg;
                msg = holder.copy();
                holder.release();
            }

            this.frame = (Http2Frame) msg;
            count++;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
        }

        public void release() {
            if (frame != null) {
                ReferenceCountUtil.release(frame);
                frame = null;
            }
        }
    }
}
