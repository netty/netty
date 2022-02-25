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
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.socket.BufferDatagramPacket;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.InternetProtocolFamily;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.util.internal.SocketUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class DatagramMulticastTest extends AbstractDatagramTest {

    @Test
    public void testMulticastByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testMulticastByteBuf);
    }

    public void testMulticastByteBuf(Bootstrap sb, Bootstrap cb) throws Throwable {
        NetworkInterface iface = multicastNetworkInterface();
        assumeTrue(iface != null, "No NetworkInterface found that supports multicast and " +
                             socketInternetProtocalFamily());

        MulticastTestHandler mhandler = new MulticastTestHandler();

        sb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
                // Nothing will be sent.
            }
        });

        cb.handler(mhandler);

        sb.option(ChannelOption.IP_MULTICAST_IF, iface);
        sb.option(ChannelOption.SO_REUSEADDR, true);

        cb.option(ChannelOption.IP_MULTICAST_IF, iface);
        cb.option(ChannelOption.SO_REUSEADDR, true);

        DatagramChannel sc = (DatagramChannel) sb.bind(newSocketAddress(iface)).get();
        assertEquals(iface, sc.config().getNetworkInterface());
        assertInterfaceAddress(iface, sc.config().getInterface());

        InetSocketAddress addr = sc.localAddress();
        cb.localAddress(addr.getPort());

        DatagramChannel cc = (DatagramChannel) cb.bind().get();
        assertEquals(iface, cc.config().getNetworkInterface());
        assertInterfaceAddress(iface, cc.config().getInterface());

        InetSocketAddress groupAddress = SocketUtils.socketAddress(groupAddress(), addr.getPort());

        cc.joinGroup(groupAddress, iface).sync();

        sc.writeAndFlush(new DatagramPacket(Unpooled.copyInt(1), groupAddress)).sync();
        assertTrue(mhandler.await());

        // leave the group
        cc.leaveGroup(groupAddress, iface).sync();

        // sleep a second to make sure we left the group
        Thread.sleep(1000);

        // we should not receive a message anymore as we left the group before
        sc.writeAndFlush(new DatagramPacket(Unpooled.copyInt(1), groupAddress)).sync();
        mhandler.await();

        cc.config().setLoopbackModeDisabled(false);
        sc.config().setLoopbackModeDisabled(false);

        assertFalse(cc.config().isLoopbackModeDisabled());
        assertFalse(sc.config().isLoopbackModeDisabled());

        cc.config().setLoopbackModeDisabled(true);
        sc.config().setLoopbackModeDisabled(true);

        assertTrue(cc.config().isLoopbackModeDisabled());
        assertTrue(sc.config().isLoopbackModeDisabled());

        sc.close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();
    }

    @Test
    public void testMulticast(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testMulticast);
    }

    public void testMulticast(Bootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        NetworkInterface iface = multicastNetworkInterface();
        assumeTrue(iface != null, "No NetworkInterface found that supports multicast and " +
                             socketInternetProtocalFamily());

        MulticastBufferTestHandler mhandler = new MulticastBufferTestHandler();

        sb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
                // Nothing will be sent.
            }
        });

        cb.handler(mhandler);

        sb.option(ChannelOption.IP_MULTICAST_IF, iface);
        sb.option(ChannelOption.SO_REUSEADDR, true);

        cb.option(ChannelOption.IP_MULTICAST_IF, iface);
        cb.option(ChannelOption.SO_REUSEADDR, true);

        DatagramChannel sc = (DatagramChannel) sb.bind(newSocketAddress(iface)).get();
        assertEquals(iface, sc.config().getNetworkInterface());
        assertInterfaceAddress(iface, sc.config().getInterface());

        InetSocketAddress addr = sc.localAddress();
        cb.localAddress(addr.getPort());

        DatagramChannel cc = (DatagramChannel) cb.bind().get();
        assertEquals(iface, cc.config().getNetworkInterface());
        assertInterfaceAddress(iface, cc.config().getInterface());

        InetSocketAddress groupAddress = SocketUtils.socketAddress(groupAddress(), addr.getPort());

        cc.joinGroup(groupAddress, iface).sync();

        BufferAllocator allocator = sc.bufferAllocator();
        sc.writeAndFlush(new BufferDatagramPacket(allocator.allocate(4).writeInt(1), groupAddress)).sync();
        assertTrue(mhandler.await());

        // leave the group
        cc.leaveGroup(groupAddress, iface).sync();

        // sleep a second to make sure we left the group
        Thread.sleep(1000);

        // we should not receive a message anymore as we left the group before
        sc.writeAndFlush(new BufferDatagramPacket(allocator.allocate(4).writeInt(1), groupAddress)).sync();
        mhandler.await();

        cc.config().setLoopbackModeDisabled(false);
        sc.config().setLoopbackModeDisabled(false);

        assertFalse(cc.config().isLoopbackModeDisabled());
        assertFalse(sc.config().isLoopbackModeDisabled());

        cc.config().setLoopbackModeDisabled(true);
        sc.config().setLoopbackModeDisabled(true);

        assertTrue(cc.config().isLoopbackModeDisabled());
        assertTrue(sc.config().isLoopbackModeDisabled());

        sc.close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();
    }

    private static void assertInterfaceAddress(NetworkInterface networkInterface, InetAddress expected) {
        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            if (expected.equals(addresses.nextElement())) {
                return;
            }
        }
        fail();
    }

    private static final class MulticastTestHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final CountDownLatch latch = new CountDownLatch(1);

        private boolean done;
        private volatile boolean fail;

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            if (done) {
                fail = true;
            }

            assertEquals(1, msg.content().readInt());

            latch.countDown();

            // mark the handler as done as we only are supposed to receive one message
            done = true;
        }

        public boolean await() throws Exception {
            boolean success = latch.await(10, TimeUnit.SECONDS);
            if (fail) {
                // fail if we receive an message after we are done
                fail();
            }
            return success;
        }
    }

    private static final class MulticastBufferTestHandler extends SimpleChannelInboundHandler<BufferDatagramPacket> {
        private final CountDownLatch latch = new CountDownLatch(1);

        private boolean done;
        private volatile boolean fail;
        private volatile Throwable error;

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, BufferDatagramPacket msg) throws Exception {
            if (done) {
                fail = true;
            }

            try {
                assertEquals(1, msg.content().readInt());
            } catch (Throwable e) {
                error = e;
            }

            latch.countDown();

            // mark the handler as done as we only are supposed to receive one message
            done = true;
        }

        public boolean await() throws Exception {
            boolean success = latch.await(10, TimeUnit.SECONDS);
            Throwable error = this.error;
            if (error != null) {
                throw new Exception("Exception thrown in messageReceived", error);
            }
            if (fail) {
                // fail if we receive a message after we are done
                fail();
            }
            return success;
        }
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.datagram(socketInternetProtocalFamily());
    }

    private InetSocketAddress newAnySocketAddress() throws UnknownHostException {
        switch (socketInternetProtocalFamily()) {
            case IPv4:
                return new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0);
            case IPv6:
                return new InetSocketAddress(InetAddress.getByName("::"), 0);
            default:
                throw new AssertionError();
        }
    }

    private InetSocketAddress newSocketAddress(NetworkInterface iface) {
        Enumeration<InetAddress> addresses = iface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress address = addresses.nextElement();
            if (socketInternetProtocalFamily().addressType().isAssignableFrom(address.getClass())) {
                return new InetSocketAddress(address, 0);
            }
        }
        throw new AssertionError();
    }

    private NetworkInterface multicastNetworkInterface() throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isUp() && iface.supportsMulticast()) {
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (socketInternetProtocalFamily().addressType().isAssignableFrom(address.getClass())) {
                        MulticastSocket socket = new MulticastSocket(newAnySocketAddress());
                        socket.setReuseAddress(true);
                        socket.setNetworkInterface(iface);
                        try {
                            socket.send(new java.net.DatagramPacket(new byte[] { 1, 2, 3, 4 }, 4,
                                                                    new InetSocketAddress(groupAddress(), 12345)));
                            return iface;
                        } catch (IOException ignore) {
                            // Try the next interface
                        } finally {
                            socket.close();
                        }
                    }
                }
            }
        }
        return null;
    }

    private String groupAddress() {
        return groupInternetProtocalFamily() == InternetProtocolFamily.IPv4?
                "230.0.0.1" : "FF01:0:0:0:0:0:0:101";
    }
}
