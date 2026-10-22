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

/**
 * This class is necessary to break the following cyclic dependency:
 * <ol>
 * <li>JNI_OnLoad</li>
 * <li>JNI Calls FindClass because RegisterNatives (used to register JNI methods) requires a class</li>
 * <li>FindClass loads the class, but static members variables of that class attempt to call a JNI method which has not
 * yet been registered.</li>
 * <li>java.lang.UnsatisfiedLinkError is thrown because native method has not yet been registered.</li>
 * </ol>
 * Static members which call JNI methods must not be declared in this class!
 * <p>
 * All methods return {@code -1} if the constant is not supported on the platform.
 */
final class ControlMessageStaticallyReferencedJniMethods {

    private ControlMessageStaticallyReferencedJniMethods() { }

    // struct cmsghdr and the CMSG_* macros
    static native int cmsghdrSize();
    static native int cmsghdrOffsetofCmsgLen();
    static native int cmsghdrSizeofCmsgLen();
    static native int cmsghdrOffsetofCmsgLevel();
    static native int cmsghdrOffsetofCmsgType();
    static native int cmsgDataOffset();
    static native int cmsgAlignment();

    // Levels
    static native int ipprotoIp();
    static native int ipprotoIpv6();
    static native int solUdp();

    // IPPROTO_IP
    static native int ipTos();
    static native int ipTosDataSize();
    static native int ipRecvTos();
    static native int ipRecvTosCmsgType();
    static native int ipTtlSendType();
    static native int ipRecvTtl();
    static native int ipRecvTtlCmsgType();
    static native int ipPktinfo();
    static native int ipRecvPktinfo();

    // IPPROTO_IPV6
    static native int ipv6Tclass();
    static native int ipv6RecvTclass();
    static native int ipv6Hoplimit();
    static native int ipv6RecvHoplimit();
    static native int ipv6Pktinfo();
    static native int ipv6RecvPktinfo();

    // SOL_UDP, this is -1 if the kernel does not support UDP_SEGMENT.
    static native int udpSegment();

    // struct in_pktinfo
    static native int sizeofInPktinfo();
    static native int inPktinfoOffsetofIpiIfindex();
    static native int inPktinfoOffsetofIpiSpecDst();
    static native int inPktinfoOffsetofIpiAddr();

    // struct in6_pktinfo
    static native int sizeofIn6Pktinfo();
    static native int in6PktinfoOffsetofIpi6Addr();
    static native int in6PktinfoOffsetofIpi6Ifindex();
}
