/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.NetUtil;
import org.junit.AfterClass;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class SpdyFrameDecoderTest {

    private static final EventLoopGroup group = new NioEventLoopGroup();

    @AfterClass
    public static void destroy() throws Exception {
        group.shutdownGracefully().sync();
    }

    @Test
    public void testTooLargeHeaderNameOnSynStreamRequest() throws Exception {
        testTooLargeHeaderNameOnSynStreamRequest(SpdyVersion.SPDY_3_1);
    }

    private static void testTooLargeHeaderNameOnSynStreamRequest(final SpdyVersion version) throws Exception {
        List<Integer> headerSizes = Arrays.asList(90, 900);
        for (final int maxHeaderSize : headerSizes) { // 90 catches the header name, 900 the value
            SpdyHeadersFrame frame = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
            addHeader(frame, 100, 1000);
            final CaptureHandler captureHandler = new CaptureHandler();
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(group);
            sb.channel(NioServerSocketChannel.class);
            sb.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(
                            new SpdyFrameDecoder(version, 10000, maxHeaderSize),
                            new SpdySessionHandler(version, true),
                            captureHandler);
                }
            });

            Bootstrap cb = new Bootstrap();
            cb.group(group);
            cb.channel(NioSocketChannel.class);
            cb.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new SpdyFrameEncoder(version));
                }
            });
            Channel sc = sb.bind(0).sync().channel();
            int port = ((InetSocketAddress) sc.localAddress()).getPort();

            Channel cc = cb.connect(NetUtil.LOCALHOST, port).sync().channel();

            sendAndWaitForFrame(cc, frame, captureHandler);

            assertNotNull("version " + version + ", not null message",
                    captureHandler.message);
            String message = "version " + version + ", should be SpdyHeadersFrame, was " +
                    captureHandler.message.getClass();
            assertTrue(
                    message,
                    captureHandler.message instanceof SpdyHeadersFrame);
            SpdyHeadersFrame writtenFrame = (SpdyHeadersFrame) captureHandler.message;

            assertTrue("should be truncated", writtenFrame.isTruncated());
            assertFalse("should not be invalid", writtenFrame.isInvalid());

            sc.close().sync();
            cc.close().sync();
        }
    }

    @Test
    public void testLargeHeaderNameOnSynStreamRequest() throws Exception {
        testLargeHeaderNameOnSynStreamRequest(SpdyVersion.SPDY_3_1);
    }

    private static void testLargeHeaderNameOnSynStreamRequest(final SpdyVersion spdyVersion) throws Exception {
        final int maxHeaderSize = 8192;

        String expectedName = createString('h', 100);
        String expectedValue = createString('v', 5000);

        SpdyHeadersFrame frame = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
        SpdyHeaders headers = frame.headers();
        headers.add(expectedName, expectedValue);

        final CaptureHandler captureHandler = new CaptureHandler();
        final ServerBootstrap sb = new ServerBootstrap();
        Bootstrap cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(1));
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("decoder", new SpdyFrameDecoder(spdyVersion, 10000, maxHeaderSize));
                p.addLast("sessionHandler", new SpdySessionHandler(spdyVersion, true));
                p.addLast("handler", captureHandler);
            }
        });

        cb.group(new NioEventLoopGroup(1));
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast("encoder", new SpdyFrameEncoder(spdyVersion));
            }
        });

        Channel sc = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) sc.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        Channel cc = ccf.channel();

        sendAndWaitForFrame(cc, frame, captureHandler);

        assertNotNull("version " + spdyVersion.getVersion() + ", not null message",
                captureHandler.message);
        String message = "version " + spdyVersion.getVersion() + ", should be SpdyHeadersFrame, was " +
                captureHandler.message.getClass();
        assertTrue(message, captureHandler.message instanceof SpdyHeadersFrame);
        SpdyHeadersFrame writtenFrame = (SpdyHeadersFrame) captureHandler.message;

        assertFalse("should not be truncated", writtenFrame.isTruncated());
        assertFalse("should not be invalid", writtenFrame.isInvalid());

        String val = writtenFrame.headers().get(expectedName);
        assertEquals(expectedValue, val);

        sc.close().sync();
        sb.group().shutdownGracefully();
        cb.group().shutdownGracefully();
    }

    @Test
    public void testZlibHeaders() throws Exception {

        SpdyHeadersFrame frame = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
        SpdyHeaders headers = frame.headers();

        headers.add(createString('a', 100), createString('b', 100));
        SpdyHeadersFrame actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        headers.clear();
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(frame.headers().isEmpty());
        assertTrue(equals(frame.headers(), actual.headers()));

        headers.clear();
        actual = roundTrip(frame, 4096);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(frame.headers().isEmpty());
        assertTrue(equals(frame.headers(), actual.headers()));

        headers.clear();
        actual = roundTrip(frame, 128);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(frame.headers().isEmpty());
        assertTrue(equals(frame.headers(), actual.headers()));

        headers.clear();
        headers.add(createString('c', 100), createString('d', 5000));
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        headers.clear();
        headers.add(createString('e', 5000), createString('f', 100));
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        headers.clear();
        headers.add(createString('g', 100), createString('h', 5000));
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        headers.clear();
        headers.add(createString('i', 100), createString('j', 5000));
        actual = roundTrip(frame, 4096);
        assertTrue("should be truncated", actual.isTruncated());
        assertTrue("headers should be empty", actual.headers().isEmpty());

        headers.clear();
        headers.add(createString('k', 5000), createString('l', 100));
        actual = roundTrip(frame, 4096);
        assertTrue("should be truncated", actual.isTruncated());
        assertTrue("headers should be empty", actual.headers().isEmpty());

        headers.clear();
        headers.add(createString('m', 100), createString('n', 1000));
        headers.add(createString('m', 100), createString('n', 1000));
        headers.add(createString('m', 100), createString('n', 1000));
        headers.add(createString('m', 100), createString('n', 1000));
        headers.add(createString('m', 100), createString('n', 1000));
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertEquals(1, actual.headers().names().size());
        assertEquals(5, actual.headers().getAll(createString('m', 100)).size());

        headers.clear();
        headers.add(createString('o', 1000), createString('p', 100));
        headers.add(createString('o', 1000), createString('p', 100));
        headers.add(createString('o', 1000), createString('p', 100));
        headers.add(createString('o', 1000), createString('p', 100));
        headers.add(createString('o', 1000), createString('p', 100));
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertEquals(1, actual.headers().names().size());
        assertEquals(5, actual.headers().getAll(createString('o', 1000)).size());

        headers.clear();
        headers.add(createString('q', 100), createString('r', 1000));
        headers.add(createString('q', 100), createString('r', 1000));
        headers.add(createString('q', 100), createString('r', 1000));
        headers.add(createString('q', 100), createString('r', 1000));
        headers.add(createString('q', 100), createString('r', 1000));
        actual = roundTrip(frame, 4096);
        assertTrue("should be truncated", actual.isTruncated());
        assertEquals(0, actual.headers().names().size());

        headers.clear();
        headers.add(createString('s', 1000), createString('t', 100));
        headers.add(createString('s', 1000), createString('t', 100));
        headers.add(createString('s', 1000), createString('t', 100));
        headers.add(createString('s', 1000), createString('t', 100));
        headers.add(createString('s', 1000), createString('t', 100));
        actual = roundTrip(frame, 4096);
        assertFalse("should be truncated", actual.isTruncated());
        assertEquals(1, actual.headers().names().size());
        assertEquals(5, actual.headers().getAll(createString('s', 1000)).size());
    }

    @Test
    public void testZlibReuseEncoderDecoder() throws Exception {
        SpdyHeadersFrame frame = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
        SpdyHeaders headers = frame.headers();

        SpdyHeaderBlockEncoder encoder = SpdyHeaderBlockEncoder.newInstance(SpdyVersion.SPDY_3_1, 6, 15, 8);
        SpdyHeaderBlockDecoder decoder = SpdyHeaderBlockDecoder.newInstance(SpdyVersion.SPDY_3_1, 8192);

        headers.add(createString('a', 100), createString('b', 100));
        SpdyHeadersFrame actual = roundTrip(encoder, decoder, frame);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        encoder.end();
        decoder.end();
        decoder.reset();

        headers.clear();
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(frame.headers().isEmpty());
        assertTrue(equals(frame.headers(), actual.headers()));

        encoder.end();
        decoder.end();
        decoder.reset();

        headers.clear();
        headers.add(createString('e', 5000), createString('f', 100));
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        encoder.end();
        decoder.end();
        decoder.reset();

        headers.clear();
        headers.add(createString('g', 100), createString('h', 5000));
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        encoder.end();
        decoder.end();
        decoder.reset();

        headers.clear();
        headers.add(createString('m', 100), createString('n', 1000));
        headers.add(createString('m', 100), createString('n', 1000));
        headers.add(createString('m', 100), createString('n', 1000));
        headers.add(createString('m', 100), createString('n', 1000));
        headers.add(createString('m', 100), createString('n', 1000));
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertEquals(1, actual.headers().names().size());
        assertEquals(5, actual.headers().getAll(createString('m', 100)).size());

        encoder.end();
        decoder.end();
        decoder.reset();

        headers.clear();
        headers.add(createString('o', 1000), createString('p', 100));
        headers.add(createString('o', 1000), createString('p', 100));
        headers.add(createString('o', 1000), createString('p', 100));
        headers.add(createString('o', 1000), createString('p', 100));
        headers.add(createString('o', 1000), createString('p', 100));
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertEquals(1, actual.headers().names().size());
        assertEquals(5, actual.headers().getAll(createString('o', 1000)).size());
    }

    private static SpdyHeadersFrame roundTrip(SpdyHeadersFrame frame, int maxHeaderSize) throws Exception {
        SpdyHeaderBlockEncoder encoder = SpdyHeaderBlockEncoder.newInstance(SpdyVersion.SPDY_3_1, 6, 15, 8);
        SpdyHeaderBlockDecoder decoder = SpdyHeaderBlockDecoder.newInstance(SpdyVersion.SPDY_3_1, maxHeaderSize);
        return roundTrip(encoder, decoder, frame);
    }

    private static SpdyHeadersFrame roundTrip(SpdyHeaderBlockEncoder encoder, SpdyHeaderBlockDecoder decoder,
                                              SpdyHeadersFrame frame) throws Exception {
        ByteBuf encoded = encoder.encode(frame);

        SpdyHeadersFrame actual = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);

        decoder.decode(encoded, actual);
        return actual;
    }

    private static boolean equals(SpdyHeaders h1, SpdyHeaders h2) {
        if (!h1.names().equals(h2.names())) {
            return false;
        }

        for (String name : h1.names()) {
            if (!h1.getAll(name).equals(h2.getAll(name))) {
                return false;
            }
        }
        return true;
    }

    private static void sendAndWaitForFrame(Channel cc, SpdyFrame frame, CaptureHandler handler) {
        cc.writeAndFlush(frame);
        long theFuture = System.currentTimeMillis() + 3000;
        while (handler.message == null && System.currentTimeMillis() < theFuture) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }
    }

    private static void addHeader(SpdyHeadersFrame frame, int headerNameSize, int headerValueSize) {
        frame.headers().add("k", "v");
        String headerName = createString('h', headerNameSize);
        String headerValue = createString('h', headerValueSize);
        frame.headers().add(headerName, headerValue);
    }

    private static String createString(char c, int rep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rep; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static class CaptureHandler extends ChannelInboundHandlerAdapter {
        public volatile Object message;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object m) throws Exception {
            message = m;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            message = cause;
        }
    }
}
