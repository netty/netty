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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                testBufferLifecycleCorrectlyHandled(bootstrap, false);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testBufferLifecycleCorrectlyHandledUsingSendmsgZc(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testBufferLifecycleCorrectlyHandled(bootstrap, true);
            }
        });
    }

    private static void testBufferLifecycleCorrectlyHandled(Bootstrap cb, boolean multiple) throws Throwable {
        cb.handler(new ChannelInboundHandlerAdapter());
        // Force to use send_zc / sendmsg_zc if supported.
        cb.option(IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, 0);

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
                            latch.countDown();
                        }
                    });
                    latch.await();
                    Throwable cause = causeRef.get();
                    if (cause != null) {
                        fail(cause);
                    }
                    // The buffer should still have a reference count of 1 as we did not receive the second notification
                    // yet as the remote peer did not start reading.
                    if (multiple) {
                        assertEquals(numBuffers, buffer.refCnt());
                    } else {
                        assertEquals(numBuffers, buffer.refCnt());
                    }

                    // Let's read the bytes now so the buffer can be released again from the NIC.
                    try (InputStream stream = socket.getInputStream()) {
                        byte[] bytes = new byte[64 * 1024];
                        int r;
                        while (bufferSize != 0 &&
                                (r = stream.read(bytes, 0, Math.min(bufferSize, bytes.length))) != -1) {
                            bufferSize -= r;
                        }
                    }

                    // Wait till the buffer was finally released, which should be done in a timely fashion.
                    while (buffer.refCnt() != 0) {
                        Thread.sleep(50);
                    }
                } finally {
                    // Close the channel now
                    channel.close().sync();
                }
            }
        }
    }
}
