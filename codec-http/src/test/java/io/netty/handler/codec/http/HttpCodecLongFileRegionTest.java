/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.NetUtil;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static org.junit.Assert.*;

public class HttpCodecLongFileRegionTest {

    private static final long INTEGER_OVERLFLOW = (long) Integer.MAX_VALUE + 1;
    private static final byte[] PAYLOAD = toByteArray(0xCA, 0xFE, 0xBA, 0xBE);

    private final EventLoopGroup group = new NioEventLoopGroup(2);

    private final ServerBootstrap sb = new ServerBootstrap();
    private final Bootstrap cb = new Bootstrap();

    @Test(timeout = 3000)
    public void testFileRegionWriteWithContentSizeLargerThanIntegerMaxvalueMakesItThroughToTheOtherEnd()
            throws Throwable {
        final ServerHandler sh = new ServerHandler();
        final ClientHandler ch = new ClientHandler();

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel c) throws Exception {
                final ChannelPipeline p = c.pipeline();
                p.addLast(new HttpServerCodec());
                p.addLast(new HttpObjectAggregator(65536));
                p.addLast(new ChunkedWriteHandler());
                p.addLast(sh);
            }
        });
        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel c) throws Exception {
                final ChannelPipeline p = c.pipeline();
                p.addLast(new HttpClientCodec());
                p.addLast(ch);
            }
        });

        final int port = getFreePort();
        final Channel sc = sb.bind(port).sync().channel();
        final Channel cc = cb.connect(NetUtil.LOCALHOST, port).sync().channel();

        cc.writeAndFlush(new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/"));

        while (ch.response == null) { // if this timeouts, we have a problem
            safeSleep(50);
        }

        sh.channel.close().sync();
        ch.channel.close().sync();
        sc.close().sync();

        assertNoExceptions(sh.exception.get(), ch.exception.get());

        assertThat(ch.response, Matchers.equalTo(PAYLOAD));
    }

    private static class ServerHandler extends BaseHandler<FullHttpRequest> {
        @Override
        protected void messageReceived(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            ctx.write(new DefaultHttpResponse(HTTP_1_1, OK));
            ctx.write(new DummyLongFileRegion());
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        }
    }

    private static class ClientHandler extends BaseHandler<HttpObject> {

        private volatile byte[] response;

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, HttpObject in) throws Exception {
            if (in instanceof HttpContent) {
                final HttpContent content = (HttpContent) in;
                final ByteBuf buf = content.content();
                if (buf.readableBytes() > 0) {
                    final byte[] data = new byte[buf.readableBytes()];
                    buf.getBytes(0, data);
                    response = data;
                }
            }
        }
    }

    private static class DummyLongFileRegion extends AbstractReferenceCounted implements FileRegion {

        private long transfered;

        @Override
        public long position() {
            return 0;
        }

        @Override
        public long transfered() {
            return transfered;
        }

        @Override
        public long count() {
            return INTEGER_OVERLFLOW;
        }

        @Override
        public long transferTo(WritableByteChannel target, long position) throws IOException {
            // transmits small buffer and cunningly pretends it's done
            target.write(ByteBuffer.wrap(PAYLOAD));

            transfered = count();
            return transfered;
        }

        @Override
        protected void deallocate() {
            // nothing to deallocate
        }

        @Override
        public FileRegion touch(Object hint) {
            return this;
        }

        @Override
        public FileRegion touch() {
            return this;
        }

        @Override
        public FileRegion retain() {
            super.retain();
            return this;
        }

        @Override
        public FileRegion retain(int increment) {
            super.retain(increment);
            return this;
        }
    }

    private abstract static class BaseHandler<T> extends SimpleChannelInboundHandler<T> {
        protected volatile Channel channel;
        protected final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }

    }

    @Before
    public void setUp() throws Exception {
        sb.group(group);
        sb.channel(NioServerSocketChannel.class);
        cb.group(group);
        cb.channel(NioSocketChannel.class);
    }

    @After
    public void tearDown() throws Exception {
        group.shutdownGracefully().sync();
    }

    private static void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            // Ignore.
        }
    }

    private static void assertNoExceptions(Throwable... throwables) throws Throwable {
        for (Throwable t : throwables) {
            if (t != null && !(t instanceof IOException)) {
                throw t;
            }
        }
        for (Throwable t : throwables) {
            if (t != null) {
                throw t;
            }
        }
    }

    /**
     * careful, might be very slow
     */
    private static int getFreePort() {
        for (int port = 32768; port < 65536; port ++) {
            try {
                // Ensure it is possible to bind on both wildcard and loopback.
                ServerSocket ss;
                ss = new ServerSocket();
                ss.setReuseAddress(false);
                ss.bind(new InetSocketAddress(port));
                ss.close();

                ss = new ServerSocket();
                ss.setReuseAddress(false);
                ss.bind(new InetSocketAddress(NetUtil.LOCALHOST, port));
                ss.close();

                return port;
            } catch (IOException ignored) {
                // ignore
            }
        }

        throw new RuntimeException("unable to find a free port");
    }

    private static byte[] toByteArray(int... bytes) {
        final byte[] array = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            array[i] = (byte) bytes[i];
        }
        return array;
    }

}
