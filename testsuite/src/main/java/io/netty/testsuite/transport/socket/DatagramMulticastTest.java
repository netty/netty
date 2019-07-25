/*
 * Copyright 2012 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.oio.OioDatagramChannel;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.util.internal.SocketUtils;
import org.junit.Assume;
import org.junit.Test;

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

import static org.junit.Assert.*;

public class DatagramMulticastTest extends AbstractDatagramTest {

    @Test
    public void testMulticast() throws Throwable {
        run();
    }

    public void testMulticast(Bootstrap sb, Bootstrap cb) throws Throwable {
        NetworkInterface iface = multicastNetworkInterface();
        Assume.assumeNotNull("No NetworkInterface found that supports multicast and " +
                internetProtocolFamily(), iface);

        MulticastTestHandler mhandler = new MulticastTestHandler();

        sb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                // Nothing will be sent.
            }
        });

        cb.handler(mhandler);

        sb.option(ChannelOption.IP_MULTICAST_IF, iface);
        sb.option(ChannelOption.SO_REUSEADDR, true);

        cb.option(ChannelOption.IP_MULTICAST_IF, iface);
        cb.option(ChannelOption.SO_REUSEADDR, true);

        DatagramChannel sc = (DatagramChannel) sb.bind(newSocketAddress(iface)).sync().channel();
        assertEquals(iface, sc.config().getNetworkInterface());
        assertInterfaceAddress(iface, sc.config().getInterface());

        InetSocketAddress addr = sc.localAddress();
        cb.localAddress(addr.getPort());

        if (sc instanceof OioDatagramChannel) {
            // skip the test for OIO, as it fails because of
            // No route to host which makes no sense.
            // Maybe a JDK bug ?
            sc.close().awaitUninterruptibly();
            return;
        }
        DatagramChannel cc = (DatagramChannel) cb.bind().sync().channel();
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
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
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

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.datagram(internetProtocolFamily());
    }

    private InetSocketAddress newAnySocketAddress() throws UnknownHostException {
        switch (internetProtocolFamily()) {
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
            if (internetProtocolFamily().addressType().isAssignableFrom(address.getClass())) {
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
                    if (internetProtocolFamily().addressType().isAssignableFrom(address.getClass())) {
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
        return internetProtocolFamily() == InternetProtocolFamily.IPv4 ?
                "230.0.0.1" : "FF01:0:0:0:0:0:0:101";
    }
}
