/*
 * Copyright 2012 The Netty Project
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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.NotYetConnectedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class DatagramUnicastTest extends AbstractDatagramTest {

    private static final byte[] BYTES = {0, 1, 2, 3};

    @Test
    public void testSimpleSendDirectBuffer(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleSendDirectBuffer);
    }

    public void testSimpleSendDirectBuffer(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend(sb, cb, DefaultBufferAllocators.offHeapAllocator().copyOf(BYTES), true, BYTES, 1);
        testSimpleSend(sb, cb, DefaultBufferAllocators.offHeapAllocator().copyOf(BYTES), true, BYTES, 4);
    }

    @Test
    public void testSimpleSendHeapBuffer(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleSendHeapBuffer);
    }

    public void testSimpleSendHeapBuffer(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend(sb, cb, DefaultBufferAllocators.onHeapAllocator().copyOf(BYTES), true, BYTES, 1);
        testSimpleSend(sb, cb, DefaultBufferAllocators.onHeapAllocator().copyOf(BYTES), true, BYTES, 4);
    }

    @Test
    public void testSimpleSendCompositeDirectBuffer(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleSendCompositeDirectBuffer);
    }

    public void testSimpleSendCompositeDirectBuffer(Bootstrap sb, Bootstrap cb) throws Throwable {
        BufferAllocator alloc = DefaultBufferAllocators.offHeapAllocator();
        try (Buffer data = alloc.copyOf(BYTES)) {
            CompositeBuffer buf = alloc.compose(asList(data.readSplit(2).send(), data.readSplit(2).send()));
            testSimpleSend(sb, cb, buf, true, BYTES, 1);
        }
        try (Buffer data = alloc.copyOf(BYTES)) {
            CompositeBuffer buf = alloc.compose(asList(data.readSplit(2).send(), data.readSplit(2).send()));
            testSimpleSend(sb, cb, buf, true, BYTES, 4);
        }
    }

    @Test
    public void testSimpleSendCompositeHeapBuffer(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleSendCompositeHeapBuffer);
    }

    public void testSimpleSendCompositeHeapBuffer(Bootstrap sb, Bootstrap cb) throws Throwable {
        BufferAllocator alloc = DefaultBufferAllocators.onHeapAllocator();
        try (Buffer data = alloc.copyOf(BYTES)) {
            CompositeBuffer buf = alloc.compose(asList(data.readSplit(2).send(), data.readSplit(2).send()));
            testSimpleSend(sb, cb, buf, true, BYTES, 1);
        }
        try (Buffer data = alloc.copyOf(BYTES)) {
            CompositeBuffer buf = alloc.compose(asList(data.readSplit(2).send(), data.readSplit(2).send()));
            testSimpleSend(sb, cb, buf, true, BYTES, 4);
        }
    }

    @Test
    public void testSimpleSendCompositeMixedBuffer(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleSendCompositeMixedBuffer);
    }

    public void testSimpleSendCompositeMixedBuffer(Bootstrap sb, Bootstrap cb) throws Throwable {
        BufferAllocator offHeap = DefaultBufferAllocators.offHeapAllocator();
        BufferAllocator onHeap = DefaultBufferAllocators.onHeapAllocator();
        CompositeBuffer buf = offHeap.compose(asList(
                offHeap.allocate(2).writeBytes(BYTES, 0, 2).send(),
                onHeap.allocate(2).writeBytes(BYTES, 0, 2).send()));
        testSimpleSend(sb, cb, buf, true, BYTES, 1);

        CompositeBuffer buf2 = offHeap.compose(asList(
                offHeap.allocate(2).writeBytes(BYTES, 0, 2).send(),
                onHeap.allocate(2).writeBytes(BYTES, 0, 2).send()));
        testSimpleSend(sb, cb, buf2, true, BYTES, 4);
    }

    @Test
    public void testSimpleSendWithoutBind(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleSendWithoutBind);
    }

    public void testSimpleSendWithoutBind(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSend(sb, cb, DefaultBufferAllocators.offHeapAllocator().copyOf(BYTES), false, BYTES, 1);
        testSimpleSend(sb, cb, DefaultBufferAllocators.offHeapAllocator().copyOf(BYTES), false, BYTES, 4);
    }

    private void testSimpleSend(Bootstrap sb, Bootstrap cb, Buffer buf, boolean bindClient,
                                final byte[] bytes, int count) throws Throwable {
        testSimpleSend0(sb, cb, buf, bindClient, bytes, count);
        assertFalse(buf.isAccessible());
    }

    @Test
    public void testSimpleSendWithConnect(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSimpleSendWithConnect);
    }

    public void testSimpleSendWithConnect(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleSendWithConnect(sb, cb, DefaultBufferAllocators.offHeapAllocator().copyOf(BYTES), BYTES, 1);
        testSimpleSendWithConnect(sb, cb, DefaultBufferAllocators.offHeapAllocator().copyOf(BYTES), BYTES, 4);
    }

    @SuppressWarnings("deprecation")
    private void testSimpleSend0(Bootstrap sb, Bootstrap cb, Buffer buf, boolean bindClient,
                                final byte[] bytes, int count) throws Throwable {
        Channel sc = null;
        Channel cc = null;

        try (buf) {
            cb.handler(new SimpleChannelInboundHandler<Object>() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, Object msgs) {
                    // Nothing will be sent.
                }
            });

            final SocketAddress sender;
            if (bindClient) {
                cc = cb.bind(newSocketAddress()).get();
                sender = cc.localAddress();
            } else {
                cb.option(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
                cc = cb.register().get();
                sender = null;
            }

            final CountDownLatch latch = new CountDownLatch(count);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            sc = setupServerChannel(sb, bytes, sender, latch, errorRef, false);

            SocketAddress localAddr = sc.localAddress();
            SocketAddress addr = localAddr instanceof InetSocketAddress ?
                    sendToAddress((InetSocketAddress) localAddr) : localAddr;
            List<Future<Void>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                futures.add(write(cc, buf.copy(), addr));
            }
            // release as we used buf.retain() before
            cc.flush();

            for (Future<Void> future: futures) {
                future.sync();
            }
            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable error = errorRef.get();
                if (error != null) {
                    throw error;
                }
                fail();
            }
        } finally {
            closeChannel(cc);
            closeChannel(sc);
        }
    }

    private void testSimpleSendWithConnect(Bootstrap sb, Bootstrap cb, Buffer buf, final byte[] bytes, int count)
            throws Throwable {
        testSimpleSendWithConnect0(sb, cb, buf, bytes, count);
        assertFalse(buf.isAccessible());
    }

    private void testSimpleSendWithConnect0(Bootstrap sb, Bootstrap cb, Buffer buf, final byte[] bytes, int count)
            throws Throwable {
        Channel sc = null;
        Channel cc = null;
        try (buf) {
            final CountDownLatch latch = new CountDownLatch(count);
            final AtomicReference<Throwable> errorRef = new AtomicReference<>();
            final CountDownLatch clientLatch = new CountDownLatch(count);
            final AtomicReference<Throwable> clientErrorRef = new AtomicReference<>();
            cc = setupClientChannel(cb, bytes, clientLatch, clientErrorRef);
            sc = setupServerChannel(sb, bytes, cc.localAddress(), latch, errorRef, true);

            SocketAddress localAddr = sc.localAddress();
            SocketAddress addr = localAddr instanceof InetSocketAddress ?
                    sendToAddress((InetSocketAddress) localAddr) : localAddr;
            cc.connect(addr).syncUninterruptibly();

            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(cc.write(buf.copy()));
            }
            cc.flush();

            for (Future<Void> future: futures) {
                future.sync();
            }

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable cause = errorRef.get();
                if (cause != null) {
                    throw cause;
                }
                fail("timed out waiting for latch(" +
                     "initial count: " + count + ", current count: " + latch.getCount() + ')');
            }
            if (!clientLatch.await(10, TimeUnit.SECONDS)) {
                Throwable cause = clientErrorRef.get();
                if (cause != null) {
                    throw cause;
                }
                fail("timed out waiting for clientLatch(" +
                     "initial count: " + count + ", current count: " + clientLatch.getCount() +
                     ')');
            }
            assertTrue(isConnected(cc));

            assertNotNull(cc.localAddress());
            assertNotNull(cc.remoteAddress());

            if (supportDisconnect()) {
                // Test what happens when we call disconnect()
                cc.disconnect().syncUninterruptibly();
                assertFalse(isConnected(cc));
                assertNotNull(cc.localAddress());
                assertNull(cc.remoteAddress());

                Future<Void> future = cc.writeAndFlush(buf.copy()).awaitUninterruptibly();
                assertThat(future.cause()).isInstanceOf(NotYetConnectedException.class);
            }
        } finally {
            closeChannel(cc);
            closeChannel(sc);
        }
    }

    protected abstract boolean isConnected(Channel channel);

    protected abstract Channel setupClientChannel(Bootstrap cb, byte[] bytes, CountDownLatch latch,
                                                  AtomicReference<Throwable> errorRef) throws Throwable;

    protected abstract Channel setupServerChannel(Bootstrap sb, byte[] bytes, SocketAddress sender,
                                                  CountDownLatch latch, AtomicReference<Throwable> errorRef,
                                                  boolean echo) throws Throwable;

    protected abstract boolean supportDisconnect();

    protected abstract Future<Void> write(Channel cc, Buffer buf, SocketAddress remote);

    protected static void closeChannel(Channel channel) throws Exception {
        if (channel != null) {
            channel.close().sync();
        }
    }

    protected InetSocketAddress sendToAddress(InetSocketAddress serverAddress) {
        InetAddress addr = serverAddress.getAddress();
        if (addr.isAnyLocalAddress()) {
            if (addr instanceof Inet6Address) {
                return new InetSocketAddress(NetUtil.LOCALHOST6, serverAddress.getPort());
            }
            return new InetSocketAddress(NetUtil.LOCALHOST4, serverAddress.getPort());
        }
        return serverAddress;
    }
}
