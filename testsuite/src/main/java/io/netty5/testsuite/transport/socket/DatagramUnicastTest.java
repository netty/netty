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
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.CompositeBuffer;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.EmptyArrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.opentest4j.TestAbortedException;

import java.net.BindException;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
            CompositeBuffer buf = alloc.compose(asList(data.readSplit(2), data.readSplit(2)));
            testSimpleSend(sb, cb, buf, true, BYTES, 1);
        }
        try (Buffer data = alloc.copyOf(BYTES)) {
            CompositeBuffer buf = alloc.compose(asList(data.readSplit(2), data.readSplit(2)));
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
            CompositeBuffer buf = alloc.compose(asList(data.readSplit(2), data.readSplit(2)));
            testSimpleSend(sb, cb, buf, true, BYTES, 1);
        }
        try (Buffer data = alloc.copyOf(BYTES)) {
            CompositeBuffer buf = alloc.compose(asList(data.readSplit(2), data.readSplit(2)));
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
                offHeap.allocate(2).writeBytes(BYTES, 0, 2),
                onHeap.allocate(2).writeBytes(BYTES, 0, 2)));
        testSimpleSend(sb, cb, buf, true, BYTES, 1);

        CompositeBuffer buf2 = offHeap.compose(asList(
                offHeap.allocate(2).writeBytes(BYTES, 0, 2),
                onHeap.allocate(2).writeBytes(BYTES, 0, 2)));
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

    @Test
    public void testReceiveEmptyDatagrams(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testReceiveEmptyDatagrams(bootstrap, bootstrap2);
            }
        });
    }

    public void testReceiveEmptyDatagrams(Bootstrap sb, Bootstrap cb) throws Throwable {
        final Semaphore semaphore = new Semaphore(0);
        Channel server = sb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                        semaphore.release();
                    }
                });
            }
        }).bind(newSocketAddress()).asStage().get();

        SocketAddress address = server.localAddress();
        DatagramSocket client;
        try {
            client = new DatagramSocket(newSocketAddress());
        } catch (IllegalArgumentException e) {
            assumeThat(e.getMessage()).doesNotContainIgnoringCase("unsupported address type");
            throw e;
        }
        SocketAddress sendAddress = address instanceof InetSocketAddress ?
                sendToAddress((InetSocketAddress) address) : address;
        for (int i = 0; i < 100; i++) {
            try {
                client.send(new java.net.DatagramPacket(EmptyArrays.EMPTY_BYTES, 0, sendAddress));
            } catch (BindException e) {
                throw new TestAbortedException("JDK sockets do not support binding to these addresses.", e);
            }
            semaphore.acquire();
        }
        client.close();
    }

    @Test
    public void testSendToUnresolvableAddress(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSendToUnresolvableAddress);
    }

    public void testSendToUnresolvableAddress(Bootstrap sb, Bootstrap cb) throws Throwable {
        SocketAddress serverAddress = newSocketAddress();
        if (!(serverAddress instanceof InetSocketAddress)) {
            return;
        }
        Channel sc = sb.handler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg) {
                        // Just drop
                    }
                });
            }
        }).bind(serverAddress).asStage().get();

        Channel cc = cb.option(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true).
                handler(new ChannelHandlerAdapter() { }).register().asStage().get();
        try {
            InetSocketAddress goodHost = sendToAddress((InetSocketAddress) sc.localAddress());
            InetSocketAddress unresolvedHost = new InetSocketAddress("unresolved.netty.io", goodHost.getPort());

            assertFalse(goodHost.isUnresolved());
            assertTrue(unresolvedHost.isUnresolved());

            String message = "hello world!";
            cc.writeAndFlush(new DatagramPacket(cc.bufferAllocator()
                    .copyOf(message, StandardCharsets.US_ASCII), goodHost)).asStage().get();
            assertInstanceOf(UnresolvedAddressException.class, cc.writeAndFlush(new DatagramPacket(
                    cc.bufferAllocator().copyOf(message, StandardCharsets.US_ASCII), unresolvedHost))
                    .asStage().await().cause());

            // DatagramChannel should still be open after sending to unresolved address
            assertTrue(cc.isOpen());

            // DatagramChannel should still be able to send messages outbound
            cc.writeAndFlush(new DatagramPacket(cc.bufferAllocator()
                    .copyOf(message, StandardCharsets.US_ASCII), goodHost)).asStage().get();
            assertInstanceOf(UnresolvedAddressException.class, cc.writeAndFlush(new DatagramPacket(
                            cc.bufferAllocator().copyOf(message, StandardCharsets.US_ASCII), unresolvedHost))
                    .asStage().await().cause());
            assertTrue(cc.isOpen());
        } finally {
            closeChannel(cc);
            closeChannel(sc);
        }
    }

    @SuppressWarnings("deprecation")
    private void testSimpleSend0(Bootstrap sb, Bootstrap cb, Buffer buf, boolean bindClient,
                                 final byte[] bytes, int count) throws Throwable {
        Channel sc = null;
        Channel cc = null;

        try (buf) {
            cb.handler(new SimpleChannelInboundHandler<>() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, Object msgs) {
                    // Nothing will be sent.
                }
            });

            final SocketAddress sender;
            if (bindClient) {
                cc = cb.bind(newSocketAddress()).asStage().get();
                sender = cc.localAddress();
            } else {
                cb.option(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
                cc = cb.register().asStage().get();
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
                future.asStage().sync();
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
            cc.connect(addr).asStage().sync();

            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(cc.write(buf.copy()));
            }
            cc.flush();

            for (Future<Void> future: futures) {
                future.asStage().sync();
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
                try {
                    // Test what happens when we call disconnect()
                    cc.disconnect().asStage().sync();
                } catch (CompletionException e) {
                    if (e.getCause() instanceof SocketException) {
                        if (disconnectMightFail((DatagramChannel) cc)) {
                            return;
                        }
                    }
                    throw e;
                }

                // Test what happens when we call disconnect()
                assertFalse(isConnected(cc));
                assertNotNull(cc.localAddress());
                assertNull(cc.remoteAddress());

                Throwable cause = cc.writeAndFlush(buf.copy()).asStage().getCause();
                assertThat(cause).isInstanceOf(NotYetConnectedException.class);
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

    protected boolean disconnectMightFail(DatagramChannel channel) {
        return false;
    }

    protected abstract Future<Void> write(Channel cc, Buffer buf, SocketAddress remote);

    protected static void closeChannel(Channel channel) throws Exception {
        if (channel != null) {
            channel.close().asStage().sync();
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
