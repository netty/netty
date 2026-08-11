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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringDatagramWriteLifecycleTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testSendmsgRetainsDatagramUntilTerminalCqe() throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        Channel channel = null;
        try {
            channel = new Bootstrap()
                    .group(group)
                    .channelFactory(() -> new IoUringDatagramChannel(SocketProtocolFamily.INET))
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).sync().channel();

            AtomicReference<ByteBuf> bufferRef = new AtomicReference<>();
            AtomicInteger refCntAfterClose = new AtomicInteger(-1);
            CountDownLatch closeIssued = new CountDownLatch(1);
            Channel finalChannel = channel;
            channel.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    ByteBuf buffer = finalChannel.alloc().directBuffer(1024);
                    buffer.writeZero(buffer.capacity());
                    bufferRef.set(buffer);
                    finalChannel.writeAndFlush(new DatagramPacket(buffer,
                            new InetSocketAddress(NetUtil.LOCALHOST4, 9)));
                    // CQEs can only be reaped once this task returns, so the sendmsg is still in flight here.
                    finalChannel.close();
                    refCntAfterClose.set(buffer.refCnt());
                    closeIssued.countDown();
                }
            });

            assertTrue(closeIssued.await(5, TimeUnit.SECONDS), "local close was not issued");
            ByteBuf buffer = bufferRef.get();
            assertNotNull(buffer, "datagram buffer was not allocated");
            // WRITE_SCHEDULED is set, so close() parks in delayedClose instead of releasing the outbound
            // buffer, keeping the flushed DatagramPacket alive on its own single reference until the CQE.
            assertEquals(1, refCntAfterClose.get(), "close must keep sendmsg memory live before its terminal CQE");

            assertTrue(channel.closeFuture().await(5, TimeUnit.SECONDS), "channel did not close in time");
            assertTrue(awaitRefCntZero(channel, buffer, 5, TimeUnit.SECONDS),
                    "sendmsg memory was not released by its terminal CQE");
            assertEquals(0, buffer.refCnt(), "sendmsg memory was not released by its terminal CQE");
        } finally {
            if (channel != null) {
                channel.close().syncUninterruptibly();
            }
            group.shutdownGracefully().syncUninterruptibly();
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
