/*
 * Copyright 2024 The Netty Project
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
 */
final class NativeStaticallyReferencedJniMethods {

    private NativeStaticallyReferencedJniMethods() { }

    static native int sockNonblock();
    static native int sockCloexec();
    static native int afInet();
    static native int afInet6();
    static native int sizeofSockaddrIn();
    static native int sizeofSockaddrIn6();
    static native int pageSize();
    static native int sockaddrInOffsetofSinFamily();
    static native int sockaddrInOffsetofSinPort();
    static native int sockaddrInOffsetofSinAddr();
    static native int inAddressOffsetofSAddr();
    static native int sockaddrIn6OffsetofSin6Family();
    static native int sockaddrIn6OffsetofSin6Port();
    static native int sockaddrIn6OffsetofSin6Flowinfo();
    static native int sockaddrIn6OffsetofSin6Addr();
    static native int sockaddrIn6OffsetofSin6ScopeId();
    static native int in6AddressOffsetofS6Addr();
    static native int sizeofSockaddrStorage();
    static native int sizeofSizeT();
    static native int sizeofIovec();
    static native int iovecOffsetofIovBase();
    static native int iovecOffsetofIovLen();
    static native int sizeofMsghdr();
    static native int msghdrOffsetofMsgName();
    static native int msghdrOffsetofMsgNamelen();
    static native int msghdrOffsetofMsgIov();
    static native int msghdrOffsetofMsgIovlen();
    static native int msghdrOffsetofMsgControl();
    static native int msghdrOffsetofMsgControllen();
    static native int msghdrOffsetofMsgFlags();
    static native int etime();
    static native int ecanceled();
    static native int enobufs();
    static native int pollin();
    static native int pollout();
    static native int pollrdhup();
    static native int ioringEnterGetevents();
    static native int iosqeAsync();
    static native int iosqeLink();
    static native int iosqeDrain();
    static native int msgDontwait();
    static native int iosqeBufferSelect();
    static native int msgFastopen();
    static native int cmsgSpace();
    static native int cmsgLen();
    static native int solUdp();
    static native int udpSegment();
    static native int cmsghdrOffsetofCmsgLen();
    static native int cmsghdrOffsetofCmsgLevel();
    static native int cmsghdrOffsetofCmsgType();
    static native int ioUringBufferRingOffsetTail();
    static native int ioUringBufferOffsetAddr();
    static native int ioUringBufferOffsetLen();
    static native int ioUringBufferOffsetBid();
    static native int sizeofIoUringBuf();
    static native int tcpFastopenMode();
}
