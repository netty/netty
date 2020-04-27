/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.traffic;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertTrue;

public class FileRegionThrottleTest {
    private static final byte[] BYTES = new byte[64 * 1024 * 4];
    private static final long WRITE_LIMIT = 64 * 1024;
    private static File tmp;
    private EventLoopGroup group;

    @BeforeClass
    public static void beforeClass() throws IOException {
        final Random r = new Random();
        for (int i = 0; i < BYTES.length; i++) {
            BYTES[i] = (byte) r.nextInt(255);
        }

        tmp = File.createTempFile("netty-traffic", ".tmp");
        tmp.deleteOnExit();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            out.write(BYTES);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @Before
    public void setUp() {
        group = new NioEventLoopGroup();
    }

    @After
    public void tearDown() {
        group.shutdownGracefully();
    }

    @Test
    public void testGlobalWriteThrottle() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final GlobalTrafficShapingHandler gtsh = new GlobalTrafficShapingHandler(group, WRITE_LIMIT, 0);
        ServerBootstrap bs = new ServerBootstrap();
        bs.group(group).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new LineBasedFrameDecoder(Integer.MAX_VALUE));
                ch.pipeline().addLast(new MessageDecoder());
                ch.pipeline().addLast(gtsh);
            }
        });
        Channel sc = bs.bind(0).sync().channel();
        Channel cc = clientConnect(sc.localAddress(), new ReadHandler(latch)).channel();

        long start = TrafficCounter.milliSecondFromNano();
        cc.writeAndFlush(Unpooled.copiedBuffer("send-file\n", CharsetUtil.US_ASCII)).sync();
        latch.await();
        long timeTaken = TrafficCounter.milliSecondFromNano() - start;
        assertTrue("Data streamed faster than expected", timeTaken > 3000);
        sc.close().sync();
        cc.close().sync();
    }

    private ChannelFuture clientConnect(final SocketAddress server, final ReadHandler readHandler) throws Exception {
        Bootstrap bc = new Bootstrap();
        bc.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(readHandler);
            }
        });
        return bc.connect(server).sync();
    }

    private static final class MessageDecoder extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                String message = buf.toString(Charset.defaultCharset());
                buf.release();
                if (message.equals("send-file")) {
                    RandomAccessFile raf = new RandomAccessFile(tmp, "r");
                    ctx.channel().writeAndFlush(new DefaultFileRegion(raf.getChannel(), 0, tmp.length()));
                }
            }
        }
    }

    private static final class ReadHandler extends ChannelInboundHandlerAdapter {
        private long bytesTransferred;
        private CountDownLatch latch;

        ReadHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                bytesTransferred += buf.readableBytes();
                buf.release();
                if (bytesTransferred == tmp.length()) {
                    latch.countDown();
                }
            }
        }
    }
}
