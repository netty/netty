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
package io.netty.channel.unix;

import io.netty.channel.ChannelOption;
import io.netty.util.AbstractConstant;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A control message (also known as ancillary data, see
 * <a href="https://man7.org/linux/man-pages/man3/cmsg.3.html">cmsg(3)</a>) that can be sent and / or received
 * together with a datagram when using one of the native transports (epoll, io_uring or kqueue).
 * <p>
 * A {@link ControlMessage} is used as a type-safe key in the same way as a {@link ChannelOption}: the values are
 * stored in {@link ControlMessages}, which are carried by a {@link ControlMessageDatagramPacket}.
 *
 * <h3>Sending control messages</h3>
 * Write a {@link ControlMessageDatagramPacket} that contains the control messages that should be sent together with
 * the datagram:
 * <pre>{@code
 * // Send the datagram with the ECT(0) ECN codepoint set.
 * channel.writeAndFlush(new ControlMessageDatagramPacket(content, recipient,
 *         ControlMessages.of(ControlMessage.IP_TOS, 0x02)));
 * }</pre>
 *
 * <h3>Receiving control messages</h3>
 * Specify the control messages that should be received via {@link UnixChannelOption#RECEIVE_CONTROL_MESSAGES}. Netty
 * will enable the reception of these control messages and deliver a {@link ControlMessageDatagramPacket} for each
 * received datagram, which contains the control messages that were received together with the datagram:
 * <pre>{@code
 * bootstrap.option(UnixChannelOption.RECEIVE_CONTROL_MESSAGES,
 *         new HashSet<>(Arrays.asList(ControlMessage.IP_PKTINFO, ControlMessage.IPV6_PKTINFO)));
 * ...
 * public void channelRead(ChannelHandlerContext ctx, Object msg) {
 *     ControlMessageDatagramPacket packet = (ControlMessageDatagramPacket) msg;
 *     PacketInfo info = packet.controlMessage(ControlMessage.IPV6_PKTINFO);
 *     ...
 * }
 * }</pre>
 *
 * <h3>IPv4 and IPv6</h3>
 * The control messages map directly to the control messages of the operating system, which means that IPv4 and
 * IPv6 have their own control messages (for example {@link #IP_TOS} and {@link #IPV6_TCLASS}). When using an IPv6
 * socket (which is the default if IPv6 is supported by the system) IPv4 datagrams are sent and received via
 * IPv4-mapped IPv6 addresses. In this case it depends on the operating system which of the control messages are
 * received for IPv4 datagrams: Linux uses the IPv4 control messages (and also delivers {@link #IPV6_PKTINFO}), while
 * other platforms (like macOS and the BSDs) may use the IPv6 control messages. This is why it is recommended to
 * always request both control messages if IPv4 and IPv6 datagrams are handled by the same socket. Requesting an IPv4
 * control message for an IPv6 socket is done on a best-effort basis, as not all platforms support it. Requesting an
 * IPv6 control message for an IPv4 socket is ignored if not supported, as such a socket never receives IPv6
 * datagrams. When sending an IPv4 datagram via an IPv6 socket, the IPv4 control messages should be used.
 *
 * <h3>Custom control messages</h3>
 * Control messages that are not provided by Netty out of the box can be used via {@link IntegerControlMessage} and
 * {@link RawControlMessage}.
 *
 * @param <T> the type of the value of the control message.
 */
public abstract class ControlMessage<T> extends AbstractConstant<ControlMessage<T>> {

    // Must be declared before any of the constants below as it is used by the constructor.
    private static final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * The size of the segments that the content of the datagram should be split into by the kernel (or the network
     * card) when sending, also known as UDP Generic Segmentation Offload (GSO). The last segment may be smaller. See
     * <a href="https://man7.org/linux/man-pages/man7/udp.7.html">UDP_SEGMENT</a>.
     * <p>
     * This can only be sent. It is only supported on Linux (4.18+).
     */
    public static final ControlMessage<Integer> UDP_SEGMENT = new UdpSegmentControlMessage();

    /**
     * The Type Of Service (TOS) field of IPv4 datagrams, which contains the DSCP and the ECN bits. See
     * <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TOS and IP_RECVTOS</a>.
     * <p>
     * This can be sent and received.
     */
    public static final ControlMessage<Integer> IP_TOS = new NativeIntegerControlMessage("IP_TOS", 0, 255);

    /**
     * The Traffic Class field of IPv6 datagrams, which contains the DSCP and the ECN bits. See
     * <a href="https://datatracker.ietf.org/doc/html/rfc3542#section-6.5">IPV6_TCLASS and IPV6_RECVTCLASS</a>.
     * <p>
     * This can be sent and received.
     */
    public static final ControlMessage<Integer> IPV6_TCLASS =
            new NativeIntegerControlMessage("IPV6_TCLASS", 0, 255);

    /**
     * The Time To Live (TTL) field of IPv4 datagrams. See
     * <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TTL and IP_RECVTTL</a>.
     * <p>
     * This can be received. Sending is only supported on Linux.
     */
    public static final ControlMessage<Integer> IP_TTL = new NativeIntegerControlMessage("IP_TTL", 1, 255);

    /**
     * The Hop Limit field of IPv6 datagrams. See
     * <a href="https://datatracker.ietf.org/doc/html/rfc3542#section-6.3">IPV6_HOPLIMIT and IPV6_RECVHOPLIMIT</a>.
     * <p>
     * This can be sent and received.
     */
    public static final ControlMessage<Integer> IPV6_HOPLIMIT =
            new NativeIntegerControlMessage("IPV6_HOPLIMIT", 0, 255);

    /**
     * The {@link PacketInfo} of IPv4 datagrams. See
     * <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_PKTINFO</a>.
     * <p>
     * When receiving, the {@link PacketInfo} contains the destination address of the datagram (which is useful if the
     * socket is bound to the wildcard address) and the index of the interface on which the datagram was received.
     * When sending, the {@link PacketInfo} contains the source address that should be used for the datagram and the
     * index of the interface that should be used to send the datagram.
     * <p>
     * This can be sent and received. It is supported on Linux and macOS.
     */
    public static final ControlMessage<PacketInfo> IP_PKTINFO = new PacketInfoControlMessage("IP_PKTINFO", false);

    /**
     * The {@link PacketInfo} of IPv6 datagrams. See
     * <a href="https://datatracker.ietf.org/doc/html/rfc3542#section-6.1">IPV6_PKTINFO and IPV6_RECVPKTINFO</a>.
     * <p>
     * When receiving, the {@link PacketInfo} contains the destination address of the datagram (which is useful if the
     * socket is bound to the wildcard address) and the index of the interface on which the datagram was received.
     * When sending, the {@link PacketInfo} contains the source address that should be used for the datagram and the
     * index of the interface that should be used to send the datagram.
     * <p>
     * This can be sent and received.
     */
    public static final ControlMessage<PacketInfo> IPV6_PKTINFO =
            new PacketInfoControlMessage("IPV6_PKTINFO", true);

    // Package-private as users should only be able to create custom control messages via the provided subclasses.
    ControlMessage(String name) {
        super(nextId.getAndIncrement(), ObjectUtil.checkNonEmpty(name, "name"));
    }

    /**
     * Validate the value of the control message. Sub-classes may override this for special checks.
     */
    public void validate(T value) {
        ObjectUtil.checkNotNull(value, "value");
    }

    // Methods that are used to encode the control message when sending.

    /**
     * Returns {@code true} if the control message can be sent.
     */
    abstract boolean isSendSupported();

    /**
     * Returns the {@code cmsg_level} of the control message when sending.
     */
    abstract int sendLevel();

    /**
     * Returns the {@code cmsg_type} of the control message when sending.
     */
    abstract int sendType();

    /**
     * Returns the length of the data of the control message for the given value.
     */
    abstract int dataLength(T value);

    /**
     * Encode the given value into the buffer starting at the given offset. Exactly {@link #dataLength(Object)} bytes
     * must be written.
     */
    abstract void encode(T value, ByteBuffer buffer, int offset);

    // Methods that are used to decode the control message when receiving.

    /**
     * Returns {@code true} if the control message can be received.
     */
    abstract boolean isReceiveSupported();

    /**
     * Returns the maximal length of the data of the control message when receiving. This is used to calculate the
     * buffer space that is needed to receive the control message.
     */
    abstract int maxDataLength();

    /**
     * Returns {@code true} if a received control message with the given {@code cmsg_level} and {@code cmsg_type}
     * should be decoded by this {@link ControlMessage}.
     */
    abstract boolean isReceived(int level, int type);

    /**
     * Decode the value from the data of a received control message or return {@code null} if the data is not valid.
     */
    abstract T decode(ByteBuffer buffer, int offset, int length);

    /**
     * Enable or disable the reception of the control message for the given socket.
     */
    void setReceiveEnabled(Socket socket, boolean enabled) throws IOException {
        // NOOP by default, users of custom control messages are responsible to enable the reception themselves.
    }
}
