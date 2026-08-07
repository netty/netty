/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.unix.IntegerUnixChannelOption;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.AbstractClientSocketTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class IoUringSocketSendSzSendmsgZcTest extends AbstractClientSocketTest {

    @Override
    protected List<TestsuitePermutation.BootstrapFactory<Bootstrap>> newFactories() {
        return IoUringSocketTestPermutation.INSTANCE.clientSocketIoUringOnly();
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testBufferLifecycleCorrectlyHandledUsingSendZc(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testBufferLifecycleCorrectlyHandled(bootstrap, false, Close.NONE);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testBufferLifecycleCorrectlyHandledUsingSendmsgZc(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testBufferLifecycleCorrectlyHandled(bootstrap, true, Close.NONE);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testBufferLifecycleCorrectlyHandledUsingSendZcWhenRemoteClose(TestInfo testInfo)
            throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testBufferLifecycleCorrectlyHandled(bootstrap, false, Close.REMOTE);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testBufferLifecycleCorrectlyHandledUsingSendmsgZcWhenRemoteClose(TestInfo testInfo)
            throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testBufferLifecycleCorrectlyHandled(bootstrap, true, Close.REMOTE);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testBufferLifecycleCorrectlyHandledUsingSendZcWhenLocalClose(TestInfo testInfo)
            throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testBufferLifecycleCorrectlyHandled(bootstrap, false, Close.LOCAL);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testBufferLifecycleCorrectlyHandledUsingSendmsgZcWhenLocalClose(TestInfo testInfo)
            throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testBufferLifecycleCorrectlyHandled(bootstrap, true, Close.LOCAL);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSendZcRetainsBufferWhenLocalCloseRacesPrimaryCqe(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testBufferRetainedWhenLocalCloseRacesPrimaryCqe(bootstrap, false);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSendmsgZcRetainsBuffersWhenLocalCloseRacesPrimaryCqe(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testBufferRetainedWhenLocalCloseRacesPrimaryCqe(bootstrap, true);
            }
        });
    }

    private enum Close {
        REMOTE,
        LOCAL,
        NONE
    }

    private static void testBufferLifecycleCorrectlyHandled(Bootstrap cb, boolean multiple, Close remoteClose)
            throws Throwable {
        cb.handler(new ChannelInboundHandlerAdapter());
        // Force to use send_zc / sendmsg_zc if supported.
        cb.option(IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, 0);
        if (remoteClose == Close.LOCAL) {
            // Configure TCP_USER_TIMEOUT to a small number so the buffers can be returned quickly.
            // See:
            // - https://man7.org/linux/man-pages/man7/tcp.7.html
            // - https://github.com/torvalds/linux/blob/v6.16/include/uapi/linux/tcp.h#L111
            cb.option(new IntegerUnixChannelOption("TCP_USER_TIMEOUT", 6, 18), 1000);
        }

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(0));
            ChannelFuture future = cb.connect(serverSocket.getLocalSocketAddress());
            final AtomicReference<Throwable> causeRef = new AtomicReference<>();

            try (Socket socket = serverSocket.accept()) {
                // We accept the socket but don't read data, this way we will not receive the second notification
                // for the send as we never see a TCP ack until we start reading.
                Channel channel = future.sync().channel();
                try {
                    final int numBuffers = multiple ? 2: 1;
                    CountDownLatch latch = new CountDownLatch(numBuffers);
                    int bufferSize = 1024 * 1024;
                    final ByteBuf buffer = channel.alloc().buffer(bufferSize);
                    future.addListener(f -> {
                        if (f.isSuccess()) {
                            ChannelFutureListener writeListener = f2 -> {
                                if (!f2.isSuccess()) {
                                    causeRef.compareAndSet(null, f2.cause());
                                }
                                latch.countDown();
                            };

                            buffer.writerIndex(buffer.capacity());
                            if (multiple) {
                                channel.write(buffer.readRetainedSlice(buffer.readableBytes() / 2))
                                        .addListener(writeListener);
                            }
                            channel.writeAndFlush(buffer)
                                    .addListener(writeListener);
                        } else {
                            buffer.release();
                            causeRef.set(f.cause());

                            for (int i = 0; i < numBuffers; i++) {
                                latch.countDown();
                            }
                        }
                    });
                    latch.await();
                    Throwable cause = causeRef.get();
                    if (cause != null) {
                        fail(cause);
                    }
                    // This is the primary CQE with IORING_CQE_F_MORE. The zero-copy references must remain live
                    // until the following IORING_CQE_F_NOTIF, because the peer has neither acknowledged nor closed.
                    assertEquals(numBuffers, buffer.refCnt());

                    switch (remoteClose) {
                        case REMOTE:
                            // Don't read any data but just close the socket. This should trigger the required
                            // notifications to release the buffers.
                            socket.close();
                            break;
                        case LOCAL:
                            // Don't read any data but just close the channel. Once we did not see an ack for the
                            // configured TCP_USER_TIMEOUT we will get the required notifications to release the buffers
                            channel.close().sync();
                            break;
                        case NONE:
                            // Let's read the bytes now so the buffer can be released again from the NIC.
                            try (InputStream stream = socket.getInputStream()) {
                                byte[] bytes = new byte[64 * 1024];
                                int r;
                                while (bufferSize != 0 &&
                                        (r = stream.read(bytes, 0, Math.min(bufferSize, bytes.length))) != -1) {
                                    bufferSize -= r;
                                }
                            }
                            break;
                    }

                    // Wait till the buffer was finally released, which should be done in a timely fashion.
                    assertTrue(awaitRefCntZero(channel, buffer, 5, TimeUnit.SECONDS),
                            "zero-copy buffer was not released in time");
                    // The notification releases the retained zero-copy reference exactly once.
                    assertEquals(0, buffer.refCnt());
                } finally {
                    // Close the channel now
                    channel.close().sync();
                }
            }
        }
    }

    private static void testBufferRetainedWhenLocalCloseRacesPrimaryCqe(Bootstrap cb, boolean multiple)
            throws Throwable {
        cb.handler(new ChannelInboundHandlerAdapter());
        cb.option(IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, 0);

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(0));
            Channel channel = cb.connect(serverSocket.getLocalSocketAddress()).sync().channel();
            try (Socket socket = serverSocket.accept()) {
                final AtomicReference<ByteBuf> firstBufferRef = new AtomicReference<>();
                final AtomicReference<ByteBuf> secondBufferRef = new AtomicReference<>();
                final AtomicReference<Throwable> causeRef = new AtomicReference<>();
                final AtomicInteger firstRefCntAfterClose = new AtomicInteger(-1);
                final AtomicInteger secondRefCntAfterClose = new AtomicInteger(-1);
                final CountDownLatch localCloseIssued = new CountDownLatch(1);

                // Submit the write and close in one event-loop task. CQEs cannot be reaped until the task returns,
                // so the assertion below observes the window where the kernel still owns the submitted memory.
                channel.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ByteBuf first = channel.alloc().directBuffer(1024 * 1024);
                            first.writeZero(first.capacity());
                            firstBufferRef.set(first);
                            if (multiple) {
                                ByteBuf second = channel.alloc().directBuffer(1024 * 1024);
                                second.writeZero(second.capacity());
                                secondBufferRef.set(second);
                                channel.write(first);
                                channel.writeAndFlush(second);
                            } else {
                                channel.writeAndFlush(first);
                            }
                            channel.close();
                            firstRefCntAfterClose.set(first.refCnt());
                            if (multiple) {
                                secondRefCntAfterClose.set(secondBufferRef.get().refCnt());
                            }
                        } catch (Throwable cause) {
                            causeRef.set(cause);
                        } finally {
                            localCloseIssued.countDown();
                        }
                    }
                });

                assertTrue(localCloseIssued.await(5, TimeUnit.SECONDS), "local close was not issued");
                Throwable cause = causeRef.get();
                if (cause != null) {
                    fail(cause);
                }

                ByteBuf first = firstBufferRef.get();
                assertTrue(firstRefCntAfterClose.get() > 0,
                        "local close must keep SEND_ZC memory live before its notification CQE");
                if (multiple) {
                    ByteBuf second = secondBufferRef.get();
                    assertTrue(secondRefCntAfterClose.get() > 0,
                            "local close must keep SENDMSG_ZC memory live before its notification CQE");
                }
                socket.close();

                assertTrue(channel.closeFuture().await(5, TimeUnit.SECONDS), "channel did not close in time");
                assertTrue(awaitRefCntZero(channel, first, 5, TimeUnit.SECONDS),
                        "SEND_ZC memory was not released by its terminal CQE");
                assertEquals(0, first.refCnt(), "SEND_ZC memory was not released by its terminal CQE");
                if (multiple) {
                    ByteBuf second = secondBufferRef.get();
                    assertTrue(awaitRefCntZero(channel, second, 5, TimeUnit.SECONDS),
                            "SENDMSG_ZC memory was not released by its terminal CQE");
                    assertEquals(0, second.refCnt(), "SENDMSG_ZC memory was not released by its terminal CQE");
                }
            } finally {
                channel.close().sync();
            }
        }
    }

    private static boolean awaitRefCntZero(Channel channel, ByteBuf buffer, long timeout, TimeUnit unit)
            throws InterruptedException {
        CountDownLatch released = new CountDownLatch(1);
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (buffer.refCnt() == 0) {
                    released.countDown();
                } else {
                    channel.eventLoop().schedule(this, 10, TimeUnit.MILLISECONDS);
                }
            }
        });
        return released.await(timeout, unit);
    }
}
