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

import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.cmsgAlignment;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.cmsgDataOffset;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.cmsghdrOffsetofCmsgLen;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.cmsghdrOffsetofCmsgLevel;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.cmsghdrOffsetofCmsgType;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.cmsghdrSize;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.cmsghdrSizeofCmsgLen;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.in6PktinfoOffsetofIpi6Addr;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.in6PktinfoOffsetofIpi6Ifindex;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.inPktinfoOffsetofIpiAddr;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.inPktinfoOffsetofIpiIfindex;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.inPktinfoOffsetofIpiSpecDst;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipPktinfo;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipRecvPktinfo;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipRecvTos;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipRecvTosCmsgType;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipRecvTtl;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipRecvTtlCmsgType;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipTos;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipTosDataSize;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipTtlSendType;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipprotoIp;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipprotoIpv6;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipv6Hoplimit;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipv6Pktinfo;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipv6RecvHoplimit;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipv6RecvPktinfo;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipv6RecvTclass;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.ipv6Tclass;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.sizeofIn6Pktinfo;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.sizeofInPktinfo;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.solUdp;
import static io.netty.channel.unix.ControlMessageStaticallyReferencedJniMethods.udpSegment;

/**
 * The native constants that are needed to encode and decode the {@link ControlMessage}s that are supported out of the
 * box.
 * <p>
 * The constants are loaded from the native library once this class is initialized, which is the case once the first
 * {@link ControlMessage} is encoded or decoded by one of the native transports. This must not happen before the
 * native library was loaded, which is why the {@link ControlMessage} constants itself must never reference this class
 * during their own initialization.
 */
final class ControlMessageConstants {

    static final ControlMessageLayout LAYOUT = new ControlMessageLayout(cmsghdrSize(), cmsghdrOffsetofCmsgLen(),
            cmsghdrSizeofCmsgLen(), cmsghdrOffsetofCmsgLevel(), cmsghdrOffsetofCmsgType(), cmsgDataOffset(),
            cmsgAlignment());

    private static final int SIZEOF_INT = 4;
    private static final int SIZEOF_UINT16 = 2;
    static final int IPPROTO_IP = ipprotoIp();
    static final int IPPROTO_IPV6 = ipprotoIpv6();

    static final int SIZEOF_IN_PKTINFO = sizeofInPktinfo();
    static final int IN_PKTINFO_OFFSETOF_IPI_IFINDEX = inPktinfoOffsetofIpiIfindex();
    static final int IN_PKTINFO_OFFSETOF_IPI_SPEC_DST = inPktinfoOffsetofIpiSpecDst();
    static final int IN_PKTINFO_OFFSETOF_IPI_ADDR = inPktinfoOffsetofIpiAddr();

    static final int SIZEOF_IN6_PKTINFO = sizeofIn6Pktinfo();
    static final int IN6_PKTINFO_OFFSETOF_IPI6_ADDR = in6PktinfoOffsetofIpi6Addr();
    static final int IN6_PKTINFO_OFFSETOF_IPI6_IFINDEX = in6PktinfoOffsetofIpi6Ifindex();

    private static final Descriptor UDP_SEGMENT = new Descriptor(solUdp(), udpSegment(), SIZEOF_UINT16, -1, -1);
    private static final Descriptor IP_TOS = new Descriptor(
            IPPROTO_IP, ipTos(), ipTosDataSize(), ipRecvTosCmsgType(), ipRecvTos());
    private static final Descriptor IP_TTL = new Descriptor(
            IPPROTO_IP, ipTtlSendType(), SIZEOF_INT, ipRecvTtlCmsgType(), ipRecvTtl());
    private static final Descriptor IP_PKTINFO = new Descriptor(
            IPPROTO_IP, ipPktinfo(), SIZEOF_IN_PKTINFO, ipPktinfo(), ipRecvPktinfo());
    private static final Descriptor IPV6_TCLASS = new Descriptor(
            IPPROTO_IPV6, ipv6Tclass(), SIZEOF_INT, ipv6Tclass(), ipv6RecvTclass());
    private static final Descriptor IPV6_HOPLIMIT = new Descriptor(
            IPPROTO_IPV6, ipv6Hoplimit(), SIZEOF_INT, ipv6Hoplimit(), ipv6RecvHoplimit());
    private static final Descriptor IPV6_PKTINFO = new Descriptor(
            IPPROTO_IPV6, ipv6Pktinfo(), SIZEOF_IN6_PKTINFO, ipv6Pktinfo(), ipv6RecvPktinfo());

    private ControlMessageConstants() { }

    /**
     * Returns the {@link Descriptor} for the {@link ControlMessage} with the given name.
     */
    static Descriptor descriptor(String name) {
        switch (name) {
            case "UDP_SEGMENT":
                return UDP_SEGMENT;
            case "IP_TOS":
                return IP_TOS;
            case "IP_TTL":
                return IP_TTL;
            case "IP_PKTINFO":
                return IP_PKTINFO;
            case "IPV6_TCLASS":
                return IPV6_TCLASS;
            case "IPV6_HOPLIMIT":
                return IPV6_HOPLIMIT;
            case "IPV6_PKTINFO":
                return IPV6_PKTINFO;
            default:
                throw new IllegalArgumentException("Unknown control message: " + name);
        }
    }

    /**
     * Describes how a {@link ControlMessage} is represented on the platform.
     */
    static final class Descriptor {
        // The level of the control message and of the socket option that enables its reception.
        final int level;
        // The type of the control message when sending or -1 if it can't be sent.
        final int sendType;
        // The length of the data when sending.
        final int sendDataLength;
        // The type of the control message when receiving or -1 if it can't be received.
        final int receiveType;
        // The socket option that enables the reception of the control message or -1 if it can't be received.
        final int receiveOption;

        Descriptor(int level, int sendType, int sendDataLength, int receiveType, int receiveOption) {
            this.level = level;
            this.sendType = sendType;
            this.sendDataLength = sendDataLength;
            // We can only receive it if we also know how to enable it.
            this.receiveType = receiveOption == -1 ? -1 : receiveType;
            this.receiveOption = receiveOption;
        }
    }
}
