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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
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
        testTooLargeHeaderNameOnSynStreamRequest(SpdyVersion.SPDY_3);
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
        StringBuilder headerName = new StringBuilder();
        for (int i = 0; i < headerNameSize; i++) {
            headerName.append('h');
        }
        StringBuilder headerValue = new StringBuilder();
        for (int i = 0; i < headerValueSize; i++) {
            headerValue.append('a');
        }
        frame.headers().add(headerName.toString(), headerValue.toString());
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
