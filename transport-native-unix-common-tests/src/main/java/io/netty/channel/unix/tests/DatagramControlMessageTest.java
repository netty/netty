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
package io.netty.channel.unix.tests;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.ControlMessage;
import io.netty.channel.unix.ControlMessageDatagramPacket;
import io.netty.channel.unix.ControlMessages;
import io.netty.channel.unix.IntegerControlMessage;
import io.netty.channel.unix.IntegerUnixChannelOption;
import io.netty.channel.unix.PacketInfo;
import io.netty.channel.unix.RawControlMessage;
import io.netty.channel.unix.Socket;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests the sending and receiving of {@link ControlMessage}s via {@link ControlMessageDatagramPacket}s.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
public abstract class DatagramControlMessageTest {

    private static final InetAddress ANY4 = ipv4Any();
    private static final InetAddress ANY6 = ipv6Any();

    private final List<Channel> channels = new ArrayList<Channel>();
    private EventLoopGroup group;

    protected abstract EventLoopGroup newGroup();

    protected abstract DatagramChannel newChannel(SocketProtocolFamily family);

    /**
     * Returns {@code true} if {@link ControlMessage#UDP_SEGMENT} is supported.
     */
    protected boolean isUdpSegmentSupported() {
        return false;
    }

    /**
     * Allows to customize the {@link Bootstrap} that is used to create the {@link Channel} which receives the
     * datagrams.
     */
    protected void configureReceiver(Bootstrap bootstrap) {
        // NOOP
    }

    @BeforeEach
    public void setUp() {
        group = newGroup();
    }

    @AfterEach
    public void tearDown() throws Exception {
        for (Channel channel : channels) {
            channel.close().sync();
        }
        group.shutdownGracefully().sync();
    }

    @Test
    public void testNoControlMessagesRequested() throws Exception {
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, NetUtil.LOCALHOST4,
                Collections.<ControlMessage<?>>emptySet(), received);
        assertTrue(receiver.config().getOption(UnixChannelOption.RECEIVE_CONTROL_MESSAGES).isEmpty());
        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);

        send(sender, receiver, ControlMessages.of(ControlMessage.IP_TOS, 0x02));
        Object msg = poll(received);
        try {
            assertInstanceOf(DatagramPacket.class, msg);
            assertFalse(msg instanceof ControlMessageDatagramPacket);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Test
    public void testGetAndSetOption() throws Exception {
        Set<ControlMessage<?>> types = new HashSet<ControlMessage<?>>(
                Arrays.asList(ControlMessage.IP_TOS, ControlMessage.IP_TTL));
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, NetUtil.LOCALHOST4, types, received);
        Set<ControlMessage<?>> configured = receiver.config().getOption(UnixChannelOption.RECEIVE_CONTROL_MESSAGES);
        assertEquals(types, configured);
        assertThrows(UnsupportedOperationException.class, () -> configured.add(ControlMessage.IPV6_TCLASS));

        // UDP_SEGMENT can't be received.
        assertThrows(UnsupportedOperationException.class, () -> receiver.config().setOption(
                UnixChannelOption.RECEIVE_CONTROL_MESSAGES,
                Collections.<ControlMessage<?>>singleton(ControlMessage.UDP_SEGMENT)));
        assertEquals(types, receiver.config().getOption(UnixChannelOption.RECEIVE_CONTROL_MESSAGES));

        // Disable again.
        assertTrue(receiver.config().setOption(UnixChannelOption.RECEIVE_CONTROL_MESSAGES,
                Collections.<ControlMessage<?>>emptySet()));
        assertTrue(receiver.config().getOption(UnixChannelOption.RECEIVE_CONTROL_MESSAGES).isEmpty());

        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);
        send(sender, receiver, ControlMessages.EMPTY);
        Object msg = poll(received);
        try {
            assertFalse(msg instanceof ControlMessageDatagramPacket);
        } finally {
            ReferenceCountUtil.release(msg);
        }

        // And enable again.
        receiver.config().setOption(UnixChannelOption.RECEIVE_CONTROL_MESSAGES,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IP_TOS));
        // Depending on the transport a read might already be in progress (io_uring), in which case the newly
        // requested control messages might be missing for the first datagram.
        for (int i = 0;; i++) {
            send(sender, receiver, ControlMessages.of(ControlMessage.IP_TOS, 0x02));
            ControlMessageDatagramPacket packet = pollPacket(received);
            try {
                assertNull(packet.controlMessage(ControlMessage.IP_TTL));
                Integer tos = packet.controlMessage(ControlMessage.IP_TOS);
                if (tos != null || i == 1) {
                    assertEquals(0x02, tos);
                    break;
                }
                assertTrue(packet.controlMessages().isEmpty());
            } finally {
                packet.release();
            }
        }
    }

    @Test
    public void testIpTos() throws Exception {
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, NetUtil.LOCALHOST4,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IP_TOS), received);
        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);

        // ECN codepoints: ECT(0), ECT(1) and CE, and some DSCP.
        int[] values = { 0x00, 0x01, 0x02, 0x03, 0xB8 };
        for (int tos : values) {
            send(sender, receiver, ControlMessages.of(ControlMessage.IP_TOS, tos));
        }
        for (int tos : values) {
            ControlMessageDatagramPacket packet = pollPacket(received);
            try {
                assertEquals(tos, packet.controlMessage(ControlMessage.IP_TOS));
                assertEquals(1, packet.controlMessages().size());
            } finally {
                packet.release();
            }
        }
    }

    @Test
    public void testIpTosViaChannelOption() throws Exception {
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, NetUtil.LOCALHOST4,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IP_TOS), received);
        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);
        sender.config().setOption(ChannelOption.IP_TOS, 0x02);

        // The TOS is set via the socket option, so we don't need to send any control messages.
        sender.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer("hello", CharsetUtil.US_ASCII),
                (InetSocketAddress) receiver.localAddress())).sync();
        ControlMessageDatagramPacket packet = pollPacket(received);
        try {
            assertEquals(0x02, packet.controlMessage(ControlMessage.IP_TOS));
            assertEquals("hello", packet.content().toString(CharsetUtil.US_ASCII));
            assertEquals(sender.localAddress(), packet.sender());
        } finally {
            packet.release();
        }
    }

    @Test
    public void testIpv6TrafficClass() throws Exception {
        assumeIpv6();
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET6, NetUtil.LOCALHOST6,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IPV6_TCLASS), received);
        Channel sender = newSender(SocketProtocolFamily.INET6, NetUtil.LOCALHOST6);

        int[] values = { 0x00, 0x01, 0x02, 0x03, 0xB8 };
        for (int tclass : values) {
            send(sender, receiver, ControlMessages.of(ControlMessage.IPV6_TCLASS, tclass));
        }
        for (int tclass : values) {
            ControlMessageDatagramPacket packet = pollPacket(received);
            try {
                assertEquals(tclass, packet.controlMessage(ControlMessage.IPV6_TCLASS));
            } finally {
                packet.release();
            }
        }
    }

    @Test
    public void testIpTtl() throws Exception {
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, NetUtil.LOCALHOST4,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IP_TTL), received);
        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);

        send(sender, receiver, ControlMessages.EMPTY);
        ControlMessageDatagramPacket packet = pollPacket(received);
        try {
            Integer ttl = packet.controlMessage(ControlMessage.IP_TTL);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= 255, "ttl: " + ttl);
        } finally {
            packet.release();
        }

        if (!"linux".equals(PlatformDependent.normalizedOs())) {
            // Sending IP_TTL is only supported on linux.
            return;
        }
        send(sender, receiver, ControlMessages.of(ControlMessage.IP_TTL, 7));
        packet = pollPacket(received);
        try {
            assertEquals(7, packet.controlMessage(ControlMessage.IP_TTL));
        } finally {
            packet.release();
        }
    }

    @Test
    public void testIpv6HopLimit() throws Exception {
        assumeIpv6();
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET6, NetUtil.LOCALHOST6,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IPV6_HOPLIMIT), received);
        Channel sender = newSender(SocketProtocolFamily.INET6, NetUtil.LOCALHOST6);

        send(sender, receiver, ControlMessages.of(ControlMessage.IPV6_HOPLIMIT, 7));
        ControlMessageDatagramPacket packet = pollPacket(received);
        try {
            assertEquals(7, packet.controlMessage(ControlMessage.IPV6_HOPLIMIT));
        } finally {
            packet.release();
        }
    }

    @Test
    public void testIpPktinfo() throws Exception {
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        // Bind to the wildcard address so the destination address is interesting.
        Channel receiver = newReceiver(SocketProtocolFamily.INET, ANY4,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IP_PKTINFO), received);
        Channel sender = newSender(SocketProtocolFamily.INET, ANY4);

        InetSocketAddress recipient = new InetSocketAddress(NetUtil.LOCALHOST4,
                ((InetSocketAddress) receiver.localAddress()).getPort());
        send(sender, recipient, ControlMessages.EMPTY);
        ControlMessageDatagramPacket packet = pollPacket(received);
        try {
            PacketInfo info = packet.controlMessage(ControlMessage.IP_PKTINFO);
            assertNotNull(info);
            assertEquals(NetUtil.LOCALHOST4, info.address());
            assertEquals(loopbackIndex(NetUtil.LOCALHOST4), info.interfaceIndex());
        } finally {
            packet.release();
        }

        // Select the source address via IP_PKTINFO.
        send(sender, recipient, ControlMessages.of(ControlMessage.IP_PKTINFO,
                new PacketInfo(NetUtil.LOCALHOST4, 0)));
        packet = pollPacket(received);
        try {
            assertEquals(NetUtil.LOCALHOST4, packet.sender().getAddress());
            assertEquals(((InetSocketAddress) sender.localAddress()).getPort(), packet.sender().getPort());
        } finally {
            packet.release();
        }

        // Select the interface via IP_PKTINFO.
        send(sender, recipient, ControlMessages.of(ControlMessage.IP_PKTINFO,
                new PacketInfo(null, loopbackIndex(NetUtil.LOCALHOST4))));
        packet = pollPacket(received);
        try {
            assertEquals(NetUtil.LOCALHOST4, packet.controlMessage(ControlMessage.IP_PKTINFO).address());
        } finally {
            packet.release();
        }
    }

    @Test
    public void testIpv6Pktinfo() throws Exception {
        assumeIpv6();
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET6, ANY6,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IPV6_PKTINFO), received);
        Channel sender = newSender(SocketProtocolFamily.INET6, ANY6);

        InetSocketAddress recipient = new InetSocketAddress(NetUtil.LOCALHOST6,
                ((InetSocketAddress) receiver.localAddress()).getPort());
        send(sender, recipient, ControlMessages.EMPTY);
        ControlMessageDatagramPacket packet = pollPacket(received);
        try {
            PacketInfo info = packet.controlMessage(ControlMessage.IPV6_PKTINFO);
            assertNotNull(info);
            assertEquals(NetUtil.LOCALHOST6, info.address());
            assertEquals(loopbackIndex(NetUtil.LOCALHOST6), info.interfaceIndex());
        } finally {
            packet.release();
        }

        // Select the source address via IPV6_PKTINFO.
        send(sender, recipient, ControlMessages.of(ControlMessage.IPV6_PKTINFO,
                new PacketInfo(NetUtil.LOCALHOST6, 0)));
        packet = pollPacket(received);
        try {
            assertEquals(NetUtil.LOCALHOST6, packet.sender().getAddress());
        } finally {
            packet.release();
        }
    }

    @Test
    public void testDualStack() throws Exception {
        assumeIpv6();
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        // Request both, as it depends on the platform which control messages are used for IPv4 datagrams
        // on an IPv6 socket.
        Set<ControlMessage<?>> types = new HashSet<ControlMessage<?>>(Arrays.asList(
                ControlMessage.IP_PKTINFO, ControlMessage.IPV6_PKTINFO,
                ControlMessage.IP_TOS, ControlMessage.IPV6_TCLASS));
        Channel receiver = newReceiver(SocketProtocolFamily.INET6, ANY6, types, received);
        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);
        sender.config().setOption(ChannelOption.IP_TOS, 0x02);

        InetSocketAddress recipient = new InetSocketAddress(NetUtil.LOCALHOST4,
                ((InetSocketAddress) receiver.localAddress()).getPort());
        send(sender, recipient, ControlMessages.EMPTY);
        ControlMessageDatagramPacket packet = pollPacket(received);
        try {
            PacketInfo info = packet.controlMessage(ControlMessage.IP_PKTINFO);
            if (info == null) {
                info = packet.controlMessage(ControlMessage.IPV6_PKTINFO);
            }
            assertNotNull(info, packet.controlMessages().toString());
            // IPv4-mapped IPv6 addresses are returned as Inet4Address.
            assertEquals(NetUtil.LOCALHOST4, info.address());

            Integer tos = packet.controlMessage(ControlMessage.IP_TOS);
            if (tos == null) {
                tos = packet.controlMessage(ControlMessage.IPV6_TCLASS);
            }
            assertEquals(0x02, tos, packet.controlMessages().toString());
        } finally {
            packet.release();
        }
    }

    @Test
    public void testCustomControlMessages() throws Exception {
        final int ipprotoIp = 0;
        final int ipTos;
        final int ipRecvTos;
        final int ipRecvTosType;
        if (PlatformDependent.isOsx()) {
            ipTos = 3;
            ipRecvTos = 27;
            ipRecvTosType = 27;
        } else if ("linux".equals(PlatformDependent.normalizedOs())) {
            ipTos = 1;
            ipRecvTos = 13;
            ipRecvTosType = 1;
        } else {
            assumeTrue(false, "Unknown constants for " + PlatformDependent.normalizedOs());
            return;
        }
        IntegerControlMessage customTos = new IntegerControlMessage("CUSTOM_IP_TOS", ipprotoIp, ipTos);
        IntegerControlMessage customRecvTos = new IntegerControlMessage("CUSTOM_IP_RECVTOS", ipprotoIp, ipRecvTosType);
        RawControlMessage rawRecvTos = new RawControlMessage("RAW_IP_RECVTOS", ipprotoIp, ipRecvTosType, 4);

        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, NetUtil.LOCALHOST4,
                new HashSet<ControlMessage<?>>(Arrays.asList(customRecvTos, rawRecvTos)), received);
        // Users are responsible to enable the reception of custom control messages.
        receiver.config().setOption(new IntegerUnixChannelOption("IP_RECVTOS", ipprotoIp, ipRecvTos), 1);
        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);

        send(sender, receiver, ControlMessages.of(customTos, 0x02));
        ControlMessageDatagramPacket packet = pollPacket(received);
        try {
            assertEquals(0x02, packet.controlMessage(customRecvTos));
            ByteBuffer raw = packet.controlMessage(rawRecvTos);
            assertEquals(1, raw.remaining());
            assertEquals(0x02, raw.get(raw.position()));
        } finally {
            packet.release();
        }

        // Sending via a RawControlMessage.
        RawControlMessage rawTos = new RawControlMessage("RAW_IP_TOS", ipprotoIp, ipTos, 4);
        ByteBuffer value = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        value.putInt(0, 0x01);
        send(sender, receiver, ControlMessages.of(rawTos, value));
        packet = pollPacket(received);
        try {
            assertEquals(0x01, packet.controlMessage(customRecvTos));
        } finally {
            packet.release();
        }
    }

    @Test
    public void testMultipleControlMessages() throws Exception {
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, ANY4, new HashSet<ControlMessage<?>>(
                Arrays.asList(ControlMessage.IP_TOS, ControlMessage.IP_TTL, ControlMessage.IP_PKTINFO)), received);
        Channel sender = newSender(SocketProtocolFamily.INET, ANY4);
        InetSocketAddress recipient = new InetSocketAddress(NetUtil.LOCALHOST4,
                ((InetSocketAddress) receiver.localAddress()).getPort());

        send(sender, recipient, ControlMessages.of(ControlMessage.IP_TOS, 0x01,
                ControlMessage.IP_PKTINFO, new PacketInfo(NetUtil.LOCALHOST4, 0)));
        ControlMessageDatagramPacket packet = pollPacket(received);
        try {
            assertEquals(3, packet.controlMessages().size(), packet.controlMessages().toString());
            assertEquals(0x01, packet.controlMessage(ControlMessage.IP_TOS));
            assertNotNull(packet.controlMessage(ControlMessage.IP_TTL));
            assertEquals(NetUtil.LOCALHOST4, packet.controlMessage(ControlMessage.IP_PKTINFO).address());
            assertEquals(NetUtil.LOCALHOST4, packet.sender().getAddress());
        } finally {
            packet.release();
        }
    }

    @Test
    public void testManyDatagrams() throws Exception {
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, NetUtil.LOCALHOST4,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IP_TOS), received);
        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);
        InetSocketAddress recipient = (InetSocketAddress) receiver.localAddress();

        // Write multiple datagrams before flushing, so these are written in one batch if supported.
        int numDatagrams = 64;
        List<ChannelFuture> futures = new ArrayList<ChannelFuture>();
        for (int i = 0; i < numDatagrams; i++) {
            ControlMessages controlMessages = i % 2 == 0 ?
                    ControlMessages.of(ControlMessage.IP_TOS, i % 4) : ControlMessages.EMPTY;
            // Mix in plain DatagramPackets as well.
            Object msg = i % 3 == 0 ? new DatagramPacket(newContent(i), recipient) :
                    new ControlMessageDatagramPacket(newContent(i), recipient, controlMessages);
            futures.add(sender.write(msg));
        }
        sender.flush();
        for (ChannelFuture future : futures) {
            future.sync();
        }

        // Datagrams might be reordered so we need to collect all of them first.
        Integer[] tos = new Integer[numDatagrams];
        for (int i = 0; i < numDatagrams; i++) {
            ControlMessageDatagramPacket packet = pollPacket(received);
            try {
                int index = packet.content().readInt();
                assertNull(tos[index]);
                tos[index] = packet.controlMessage(ControlMessage.IP_TOS);
            } finally {
                packet.release();
            }
        }
        for (int i = 0; i < numDatagrams; i++) {
            int expected = i % 2 == 0 && i % 3 != 0 ? i % 4 : 0;
            assertEquals(expected, tos[i], "index: " + i);
        }
    }

    @Test
    public void testUdpSegment() throws Exception {
        assumeTrue(isUdpSegmentSupported());
        final int segmentSize = 512;
        final int numSegments = 8;
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, NetUtil.LOCALHOST4,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IP_TOS), received);
        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);

        ByteBuf content = Unpooled.directBuffer(segmentSize * numSegments);
        for (int i = 0; i < numSegments; i++) {
            content.writeBytes(new byte[segmentSize]);
            content.setInt(i * segmentSize, i);
        }
        sender.writeAndFlush(new ControlMessageDatagramPacket(content, (InetSocketAddress) receiver.localAddress(),
                ControlMessages.of(ControlMessage.UDP_SEGMENT, segmentSize, ControlMessage.IP_TOS, 0x02))).sync();

        boolean[] seen = new boolean[numSegments];
        for (int i = 0; i < numSegments; i++) {
            ControlMessageDatagramPacket packet = pollPacket(received);
            try {
                assertEquals(segmentSize, packet.content().readableBytes());
                seen[packet.content().getInt(packet.content().readerIndex())] = true;
                assertEquals(0x02, packet.controlMessage(ControlMessage.IP_TOS));
            } finally {
                packet.release();
            }
        }
        for (boolean s : seen) {
            assertTrue(s);
        }
    }

    @Test
    public void testUnsupportedControlMessage() throws Exception {
        assumeTrue(!isUdpSegmentSupported());
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, NetUtil.LOCALHOST4,
                Collections.<ControlMessage<?>>emptySet(), received);
        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);

        ChannelFuture future = sender.writeAndFlush(new ControlMessageDatagramPacket(newContent(0),
                (InetSocketAddress) receiver.localAddress(), ControlMessages.of(ControlMessage.UDP_SEGMENT, 1)));
        ExecutionException e = assertThrows(ExecutionException.class, () -> future.get());
        assertInstanceOf(UnsupportedOperationException.class, e.getCause());

        // The channel must still be usable.
        send(sender, receiver, ControlMessages.EMPTY);
        ReferenceCountUtil.release(poll(received));
    }

    @Test
    public void testConnected() throws Exception {
        BlockingQueue<Object> received = new LinkedBlockingQueue<Object>();
        Channel receiver = newReceiver(SocketProtocolFamily.INET, NetUtil.LOCALHOST4,
                Collections.<ControlMessage<?>>singleton(ControlMessage.IP_TOS), received);
        Channel sender = newSender(SocketProtocolFamily.INET, NetUtil.LOCALHOST4);
        receiver.connect(sender.localAddress()).sync();
        sender.connect(receiver.localAddress()).sync();

        // Writing a ByteBuf does not support any control messages.
        sender.writeAndFlush(newContent(0)).sync();
        ControlMessageDatagramPacket packet = pollPacket(received);
        try {
            assertEquals(0, packet.controlMessage(ControlMessage.IP_TOS));
            assertEquals(sender.localAddress(), packet.sender());
        } finally {
            packet.release();
        }

        send(sender, receiver, ControlMessages.of(ControlMessage.IP_TOS, 0x02));
        packet = pollPacket(received);
        try {
            assertEquals(0x02, packet.controlMessage(ControlMessage.IP_TOS));
            assertEquals(sender.localAddress(), packet.sender());
        } finally {
            packet.release();
        }
    }

    private void assumeIpv6() {
        assumeTrue(Socket.isIPv6Preferred(), "IPv6 not supported");
    }

    private static int loopbackIndex(InetAddress address) throws Exception {
        NetworkInterface loopback = NetworkInterface.getByInetAddress(address);
        assertNotNull(loopback);
        return loopback.getIndex();
    }

    private static ByteBuf newContent(int index) {
        return Unpooled.directBuffer(4).writeInt(index);
    }

    private static void send(Channel sender, Channel receiver, ControlMessages controlMessages) throws Exception {
        send(sender, (InetSocketAddress) receiver.localAddress(), controlMessages);
    }

    private static void send(Channel sender, InetSocketAddress recipient, ControlMessages controlMessages)
            throws Exception {
        sender.writeAndFlush(new ControlMessageDatagramPacket(newContent(0), recipient, controlMessages)).sync();
    }

    private static Object poll(BlockingQueue<Object> received) throws Exception {
        Object msg = received.poll(10, TimeUnit.SECONDS);
        assertNotNull(msg, "Did not receive datagram in time");
        if (msg instanceof Throwable) {
            throw new AssertionError("Unexpected exception", (Throwable) msg);
        }
        return msg;
    }

    private static ControlMessageDatagramPacket pollPacket(BlockingQueue<Object> received) throws Exception {
        Object msg = poll(received);
        if (!(msg instanceof ControlMessageDatagramPacket)) {
            ReferenceCountUtil.release(msg);
            throw new AssertionError("Unexpected message: " + msg);
        }
        return (ControlMessageDatagramPacket) msg;
    }

    private Channel newReceiver(SocketProtocolFamily family, InetAddress address, Set<ControlMessage<?>> types,
                                final BlockingQueue<Object> received) throws Exception {
        Bootstrap bootstrap = newBootstrap(family)
                .option(ChannelOption.RECVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(8192))
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        received.add(msg);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        received.add(cause);
                    }
                });
        if (!types.isEmpty()) {
            bootstrap.option(UnixChannelOption.RECEIVE_CONTROL_MESSAGES, types);
        }
        configureReceiver(bootstrap);
        Channel channel = bootstrap.bind(address, 0).sync().channel();
        channels.add(channel);
        assertEquals(types, channel.config().getOption(UnixChannelOption.RECEIVE_CONTROL_MESSAGES));
        return channel;
    }

    private Channel newSender(SocketProtocolFamily family, InetAddress address) throws Exception {
        Channel channel = newBootstrap(family).handler(new ChannelInboundHandlerAdapter()).bind(address, 0)
                .sync().channel();
        channels.add(channel);
        return channel;
    }

    private Bootstrap newBootstrap(final SocketProtocolFamily family) {
        return new Bootstrap().group(group).channelFactory(new ChannelFactory<Channel>() {
            @Override
            public Channel newChannel() {
                return DatagramControlMessageTest.this.newChannel(family);
            }
        });
    }

    private static InetAddress ipv4Any() {
        try {
            return Inet4Address.getByAddress(new byte[4]);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static InetAddress ipv6Any() {
        try {
            return Inet6Address.getByAddress(new byte[16]);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
