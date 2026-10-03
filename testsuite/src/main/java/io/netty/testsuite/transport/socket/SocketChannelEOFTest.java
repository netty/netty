/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SocketChannelEOFTest extends AbstractClientSocketTest {

    private static final long PAYLOAD_BYTES = 32L * 1024 * 1024;
    private static final int CHUNK_BYTES = 8 * 1024;
    private static final long PAUSE_AFTER_BYTES = 128L * 1024;
    private static final long PAUSE_MILLIS = 750;
    private static final byte[] CHUNK = createChunk();
    private static final byte[] EXPECTED_DIGEST = digest(PAYLOAD_BYTES);

    @Test
    @Timeout(30)
    public void readAllPendingOnEOF(TestInfo info) throws Throwable {
        run(info, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                PayloadServer server = new PayloadServer(PAYLOAD_BYTES);
                Channel ch = null;
                try {
                    SocketAddress address = server.bindAndAccept();
                    final ReadHandler handler = new ReadHandler(PAYLOAD_BYTES, PAUSE_AFTER_BYTES, PAUSE_MILLIS);
                    bootstrap.option(ChannelOption.AUTO_READ, false)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(CHUNK_BYTES))
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel channel) {
                                channel.pipeline().addLast(handler);
                            }
                        });
                    ch = bootstrap.connect(address).sync().channel();
                    handler.result().get(10, TimeUnit.SECONDS);
                    server.result().get(10, TimeUnit.SECONDS);
                } finally {
                    server.close();
                    if (ch != null) {
                        ch.close().sync();
                    }
                }
            }
        });
    }

    private static byte[] createChunk() {
        byte[] bytes = new byte[CHUNK_BYTES];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) ((index * 31 + 7) & 0xff);
        }
        return bytes;
    }

    private static byte[] digest(long length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (long remaining = length; remaining > 0; remaining -= CHUNK.length) {
                digest.update(CHUNK, 0, (int) Math.min(remaining, CHUNK.length));
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class ReadHandler extends ChannelInboundHandlerAdapter {

        private final long expectedBytes;
        private final long pauseAfterBytes;
        private final long pauseMillis;
        private final MessageDigest digest;
        private final Promise<Void> result = ImmediateEventExecutor.INSTANCE.newPromise();
        private Future<?> readFuture;
        private long receivedBytes;
        private Throwable failure;

        private ReadHandler(long expectedBytes, long pauseAfterBytes, long pauseMillis) {
            this.expectedBytes = expectedBytes;
            this.pauseAfterBytes = pauseAfterBytes;
            this.pauseMillis = pauseMillis;
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException error) {
                throw new IllegalStateException(error);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext context) {
            requestRead(context);
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) {
            try {
                ByteBuf bytes = (ByteBuf) message;
                receivedBytes += bytes.readableBytes();
                digest.update(bytes.nioBuffer());
            } finally {
                ReferenceCountUtil.release(message);
            }
        }

        @Override
        public void channelReadComplete(final ChannelHandlerContext context) {
            if (receivedBytes < expectedBytes
                && receivedBytes >= pauseAfterBytes
                && pauseMillis > 0
                && readFuture == null) {
                readFuture = context.executor().schedule(new Runnable() {
                    @Override
                    public void run() {
                        requestRead(context);
                    }
                }, pauseMillis, TimeUnit.MILLISECONDS);
            } else {
                requestRead(context);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) {
            try {
                if (failure != null) {
                    fail(failure);
                }
                assertEquals(PAYLOAD_BYTES, receivedBytes);
                assertArrayEquals(EXPECTED_DIGEST, digest.digest());
            } catch (Throwable cause) {
                result.setFailure(cause);
                return;
            }
            result.setSuccess(null);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable error) {
            if (!(error instanceof ClosedChannelException)) {
                failure = error;
            }
            context.close();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            super.handlerRemoved(ctx);
            if (readFuture != null) {
                readFuture.cancel(true);
            }
        }

        private void requestRead(ChannelHandlerContext context) {
            if (context.channel().isActive()) {
                context.read();
            }
        }

        Future<Void> result() {
            return result;
        }
    }

    private static final class PayloadServer implements AutoCloseable {
        private final long sendBytes;
        private final ServerSocket serverSocket;
        private final Promise<Void> result = ImmediateEventExecutor.INSTANCE.newPromise();
        private final AtomicReference<Socket> accepted = new AtomicReference<Socket>();
        private Thread thread;

        PayloadServer(long sendBytes) throws IOException {
            this.sendBytes = sendBytes;
            this.serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0), 1);
        }

        SocketAddress bindAndAccept() {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    long sent = 0;
                    Throwable failure = null;
                    try {
                        Socket socket = serverSocket.accept();
                        accepted.set(socket);
                        socket.setTcpNoDelay(true);
                        OutputStream output = socket.getOutputStream();
                        for (long remaining = sendBytes; remaining > 0; remaining -= CHUNK.length) {
                            int length = (int) Math.min(remaining, CHUNK.length);
                            output.write(CHUNK, 0, length);
                            sent += length;
                        }
                        output.flush();
                        socket.shutdownOutput();
                    } catch (Throwable error) {
                        failure = error;
                    }
                    try {
                        if (failure != null) {
                            fail(failure);
                        }
                        assertEquals(PAYLOAD_BYTES, sent);
                    }  catch (Throwable error) {
                        result.setFailure(error);
                        return;
                    }
                    result.setSuccess(null);
                }
            }, "payload-server");
            thread.setDaemon(true);
            thread.start();
            return serverSocket.getLocalSocketAddress();
        }

        Future<Void> result() {
            return result;
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            Socket socket = accepted.get();
            if (socket != null) {
                socket.close();
            }
            if (thread != null) {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            }
        }
    }
}
