/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.traffic;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.DefaultFileRegion;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.codec.LineBasedFrameDecoder;
import io.netty5.util.internal.PlatformDependent;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class FileRegionThrottleTest {
    private static final byte[] BYTES = new byte[64 * 1024 * 4];
    private static final long WRITE_LIMIT = 64 * 1024;
    private static File tmp;
    @AutoClose("shutdownGracefully")
    private EventLoopGroup group;

    @BeforeAll
    public static void beforeClass() throws IOException {
        final Random r = new Random();
        for (int i = 0; i < BYTES.length; i++) {
            BYTES[i] = (byte) r.nextInt(255);
        }

        tmp = PlatformDependent.createTempFile("netty-traffic", ".tmp", null);
        tmp.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(BYTES);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void setUp() {
        group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
    }

    @Disabled("This test is flaky, need more investigation")
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
        Channel sc = bs.bind(0).asStage().get();
        Channel cc = clientConnect(sc.localAddress(), new ReadHandler(latch));

        long start = TrafficCounter.milliSecondFromNano();
        cc.writeAndFlush(preferredAllocator().copyOf("send-file\n", StandardCharsets.US_ASCII)).asStage().sync();
        latch.await();
        long timeTaken = TrafficCounter.milliSecondFromNano() - start;
        assertTrue(timeTaken > 3000, "Data streamed faster than expected");
        sc.close().asStage().sync();
        cc.close().asStage().sync();
    }

    private Channel clientConnect(final SocketAddress server, final ReadHandler readHandler) throws Exception {
        Bootstrap bc = new Bootstrap();
        bc.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(readHandler);
            }
        });
        return bc.connect(server).asStage().get();
    }

    private static final class MessageDecoder implements ChannelHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Buffer) {
                try (Buffer buf = (Buffer) msg) {
                    String message = buf.toString(Charset.defaultCharset());
                    if ("send-file".equals(message)) {
                        RandomAccessFile raf = new RandomAccessFile(tmp, "r");
                        ctx.channel().writeAndFlush(new DefaultFileRegion(raf.getChannel(), 0, tmp.length()));
                    }
                }
            }
        }
    }

    private static final class ReadHandler implements ChannelHandler {
        private long bytesTransferred;
        private final CountDownLatch latch;

        ReadHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Buffer) {
                try (Buffer buf = (Buffer) msg) {
                    bytesTransferred += buf.readableBytes();
                }
                if (bytesTransferred == tmp.length()) {
                    latch.countDown();
                }
            }
        }
    }
}
