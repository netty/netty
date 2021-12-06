/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.ChannelException;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.unix.NativeInetAddress;
import io.netty.channel.unix.PeerCredentials;
import io.netty.channel.unix.Socket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.UnstableApi;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;

import static io.netty.channel.unix.Errors.ioResult;

/**
 * A socket which provides access Linux native methods.
 */
@UnstableApi
public final class LinuxSocket extends Socket {
    static final InetAddress INET6_ANY = unsafeInetAddrByName("::");
    private static final InetAddress INET_ANY = unsafeInetAddrByName("0.0.0.0");
    private static final long MAX_UINT32_T = 0xFFFFFFFFL;

    LinuxSocket(int fd) {
        super(fd);
    }

    InternetProtocolFamily family() {
        return ipv6 ? InternetProtocolFamily.IPv6 : InternetProtocolFamily.IPv4;
    }

    int sendmmsg(NativeDatagramPacketArray.NativeDatagramPacket[] msgs,
                               int offset, int len) throws IOException {
        return Native.sendmmsg(intValue(), ipv6, msgs, offset, len);
    }

    int recvmmsg(NativeDatagramPacketArray.NativeDatagramPacket[] msgs,
                 int offset, int len) throws IOException {
        return Native.recvmmsg(intValue(), ipv6, msgs, offset, len);
    }

    int recvmsg(NativeDatagramPacketArray.NativeDatagramPacket msg) throws IOException {
        return Native.recvmsg(intValue(), ipv6, msg);
    }

    void setTimeToLive(int ttl) throws IOException {
        setTimeToLive(intValue(), ttl);
    }

    void setInterface(InetAddress address) throws IOException {
        final NativeInetAddress a = NativeInetAddress.newInstance(address);
        setInterface(intValue(), ipv6, a.address(), a.scopeId(), interfaceIndex(address));
    }

    void setNetworkInterface(NetworkInterface netInterface) throws IOException {
        InetAddress address = deriveInetAddress(netInterface, family() == InternetProtocolFamily.IPv6);
        if (address.equals(family() == InternetProtocolFamily.IPv4 ? INET_ANY : INET6_ANY)) {
            throw new IOException("NetworkInterface does not support " + family());
        }
        final NativeInetAddress nativeAddress = NativeInetAddress.newInstance(address);
        setInterface(intValue(), ipv6, nativeAddress.address(), nativeAddress.scopeId(), interfaceIndex(netInterface));
    }

    InetAddress getInterface() throws IOException {
        NetworkInterface inf = getNetworkInterface();
        if (inf != null) {
            Enumeration<InetAddress> addresses = SocketUtils.addressesFromNetworkInterface(inf);
            if (addresses.hasMoreElements()) {
                return addresses.nextElement();
            }
        }
        return null;
    }

    NetworkInterface getNetworkInterface() throws IOException {
        int ret = getInterface(intValue(), ipv6);
        if (ipv6) {
            return PlatformDependent.javaVersion() >= 7 ? NetworkInterface.getByIndex(ret) : null;
        }
        InetAddress address = inetAddress(ret);
        return address != null ? NetworkInterface.getByInetAddress(address) : null;
    }

    private static InetAddress inetAddress(int value) {
        byte[] var1 = {
                (byte) (value >>> 24 & 255),
                (byte) (value >>> 16 & 255),
                (byte) (value >>> 8 & 255),
                (byte) (value & 255)
        };

        try {
            return InetAddress.getByAddress(var1);
        } catch (UnknownHostException ignore) {
            return null;
        }
    }

    void joinGroup(InetAddress group, NetworkInterface netInterface, InetAddress source) throws IOException {
        final NativeInetAddress g = NativeInetAddress.newInstance(group);
        final boolean isIpv6 = group instanceof Inet6Address;
        final NativeInetAddress i = NativeInetAddress.newInstance(deriveInetAddress(netInterface, isIpv6));
        if (source != null) {
            if (source.getClass() != group.getClass()) {
                throw new IllegalArgumentException("Source address is different type to group");
            }
            final NativeInetAddress s = NativeInetAddress.newInstance(source);
            joinSsmGroup(intValue(), ipv6 && isIpv6, g.address(), i.address(),
                    g.scopeId(), interfaceIndex(netInterface), s.address());
        } else {
            joinGroup(intValue(), ipv6 && isIpv6, g.address(), i.address(), g.scopeId(), interfaceIndex(netInterface));
        }
    }

    void leaveGroup(InetAddress group, NetworkInterface netInterface, InetAddress source) throws IOException {
        final NativeInetAddress g = NativeInetAddress.newInstance(group);
        final boolean isIpv6 = group instanceof Inet6Address;
        final NativeInetAddress i = NativeInetAddress.newInstance(deriveInetAddress(netInterface, isIpv6));
        if (source != null) {
            if (source.getClass() != group.getClass()) {
                throw new IllegalArgumentException("Source address is different type to group");
            }
            final NativeInetAddress s = NativeInetAddress.newInstance(source);
            leaveSsmGroup(intValue(), ipv6 && isIpv6, g.address(), i.address(),
                    g.scopeId(), interfaceIndex(netInterface), s.address());
        } else {
            leaveGroup(intValue(), ipv6 && isIpv6, g.address(), i.address(), g.scopeId(), interfaceIndex(netInterface));
        }
    }

    private static int interfaceIndex(NetworkInterface networkInterface) {
        return PlatformDependent.javaVersion() >= 7 ? networkInterface.getIndex() : -1;
    }

    private static int interfaceIndex(InetAddress address) throws IOException {
        if (PlatformDependent.javaVersion() >= 7) {
            NetworkInterface iface = NetworkInterface.getByInetAddress(address);
            if (iface != null) {
                return iface.getIndex();
            }
        }
        return -1;
    }

    void setTcpDeferAccept(int deferAccept) throws IOException {
        setTcpDeferAccept(intValue(), deferAccept);
    }

    void setTcpQuickAck(boolean quickAck) throws IOException {
        setTcpQuickAck(intValue(), quickAck ? 1 : 0);
    }

    void setTcpCork(boolean tcpCork) throws IOException {
        setTcpCork(intValue(), tcpCork ? 1 : 0);
    }

    void setSoBusyPoll(int loopMicros) throws IOException {
        setSoBusyPoll(intValue(), loopMicros);
    }

    void setTcpNotSentLowAt(long tcpNotSentLowAt) throws IOException {
        if (tcpNotSentLowAt < 0 || tcpNotSentLowAt > MAX_UINT32_T) {
            throw new IllegalArgumentException("tcpNotSentLowAt must be a uint32_t");
        }
        setTcpNotSentLowAt(intValue(), (int) tcpNotSentLowAt);
    }

    void setTcpFastOpen(int tcpFastopenBacklog) throws IOException {
        setTcpFastOpen(intValue(), tcpFastopenBacklog);
    }

    void setTcpKeepIdle(int seconds) throws IOException {
        setTcpKeepIdle(intValue(), seconds);
    }

    void setTcpKeepIntvl(int seconds) throws IOException {
        setTcpKeepIntvl(intValue(), seconds);
    }

    void setTcpKeepCnt(int probes) throws IOException {
        setTcpKeepCnt(intValue(), probes);
    }

    void setTcpUserTimeout(int milliseconds) throws IOException {
        setTcpUserTimeout(intValue(), milliseconds);
    }

    void setIpFreeBind(boolean enabled) throws IOException {
        setIpFreeBind(intValue(), enabled ? 1 : 0);
    }

    void setIpTransparent(boolean enabled) throws IOException {
        setIpTransparent(intValue(), enabled ? 1 : 0);
    }

    void setIpRecvOrigDestAddr(boolean enabled) throws IOException {
        setIpRecvOrigDestAddr(intValue(), enabled ? 1 : 0);
    }

    int getTimeToLive() throws IOException {
        return getTimeToLive(intValue());
    }

    void getTcpInfo(EpollTcpInfo info) throws IOException {
        getTcpInfo(intValue(), info.info);
    }

    void setTcpMd5Sig(InetAddress address, byte[] key) throws IOException {
        final NativeInetAddress a = NativeInetAddress.newInstance(address);
        setTcpMd5Sig(intValue(), ipv6, a.address(), a.scopeId(), key);
    }

    boolean isTcpCork() throws IOException  {
        return isTcpCork(intValue()) != 0;
    }

    int getSoBusyPoll() throws IOException  {
        return getSoBusyPoll(intValue());
    }

    int getTcpDeferAccept() throws IOException {
        return getTcpDeferAccept(intValue());
    }

    boolean isTcpQuickAck() throws IOException {
        return isTcpQuickAck(intValue()) != 0;
    }

    long getTcpNotSentLowAt() throws IOException {
        return getTcpNotSentLowAt(intValue()) & MAX_UINT32_T;
    }

    int getTcpKeepIdle() throws IOException {
        return getTcpKeepIdle(intValue());
    }

    int getTcpKeepIntvl() throws IOException {
        return getTcpKeepIntvl(intValue());
    }

    int getTcpKeepCnt() throws IOException {
        return getTcpKeepCnt(intValue());
    }

    int getTcpUserTimeout() throws IOException {
        return getTcpUserTimeout(intValue());
    }

    boolean isIpFreeBind() throws IOException {
        return isIpFreeBind(intValue()) != 0;
    }

    boolean isIpTransparent() throws IOException {
        return isIpTransparent(intValue()) != 0;
    }

    boolean isIpRecvOrigDestAddr() throws IOException {
        return isIpRecvOrigDestAddr(intValue()) != 0;
    }

    PeerCredentials getPeerCredentials() throws IOException {
        return getPeerCredentials(intValue());
    }

    boolean isLoopbackModeDisabled() throws IOException {
        return getIpMulticastLoop(intValue(), ipv6) == 0;
    }

    void setLoopbackModeDisabled(boolean loopbackModeDisabled) throws IOException {
        setIpMulticastLoop(intValue(), ipv6, loopbackModeDisabled ? 0 : 1);
    }

    boolean isUdpGro() throws IOException {
        return isUdpGro(intValue()) != 0;
    }

    void setUdpGro(boolean gro) throws IOException {
        setUdpGro(intValue(), gro ? 1 : 0);
    }

    long sendFile(DefaultFileRegion src, long baseOffset, long offset, long length) throws IOException {
        // Open the file-region as it may be created via the lazy constructor. This is needed as we directly access
        // the FileChannel field via JNI.
        src.open();

        long res = sendFile(intValue(), src, baseOffset, offset, length);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendfile", (int) res);
    }

    private static InetAddress deriveInetAddress(NetworkInterface netInterface, boolean ipv6) {
        final InetAddress ipAny = ipv6 ? INET6_ANY : INET_ANY;
        if (netInterface != null) {
            final Enumeration<InetAddress> ias = netInterface.getInetAddresses();
            while (ias.hasMoreElements()) {
                final InetAddress ia = ias.nextElement();
                final boolean isV6 = ia instanceof Inet6Address;
                if (isV6 == ipv6) {
                    return ia;
                }
            }
        }
        return ipAny;
    }

    public static LinuxSocket newSocketStream(boolean ipv6) {
        return new LinuxSocket(newSocketStream0(ipv6));
    }

    public static LinuxSocket newSocketStream() {
        return newSocketStream(isIPv6Preferred());
    }

    public static LinuxSocket newSocketDgram(boolean ipv6) {
        return new LinuxSocket(newSocketDgram0(ipv6));
    }

    public static LinuxSocket newSocketDgram() {
        return newSocketDgram(isIPv6Preferred());
    }

    public static LinuxSocket newSocketDomain() {
        return new LinuxSocket(newSocketDomain0());
    }

    public static LinuxSocket newSocketDomainDgram() {
        return new LinuxSocket(newSocketDomainDgram0());
    }

    private static InetAddress unsafeInetAddrByName(String inetName) {
        try {
            return InetAddress.getByName(inetName);
        } catch (UnknownHostException uhe) {
            throw new ChannelException(uhe);
        }
    }

    private static native void joinGroup(int fd, boolean ipv6, byte[] group, byte[] interfaceAddress,
                                         int scopeId, int interfaceIndex) throws IOException;
    private static native void joinSsmGroup(int fd, boolean ipv6, byte[] group, byte[] interfaceAddress,
                                            int scopeId, int interfaceIndex, byte[] source) throws IOException;
    private static native void leaveGroup(int fd, boolean ipv6, byte[] group, byte[] interfaceAddress,
                                          int scopeId, int interfaceIndex) throws IOException;
    private static native void leaveSsmGroup(int fd, boolean ipv6, byte[] group, byte[] interfaceAddress,
                                             int scopeId, int interfaceIndex, byte[] source) throws IOException;
    private static native long sendFile(int socketFd, DefaultFileRegion src, long baseOffset,
                                        long offset, long length) throws IOException;

    private static native int getTcpDeferAccept(int fd) throws IOException;
    private static native int isTcpQuickAck(int fd) throws IOException;
    private static native int isTcpCork(int fd) throws IOException;
    private static native int getSoBusyPoll(int fd) throws IOException;
    private static native int getTcpNotSentLowAt(int fd) throws IOException;
    private static native int getTcpKeepIdle(int fd) throws IOException;
    private static native int getTcpKeepIntvl(int fd) throws IOException;
    private static native int getTcpKeepCnt(int fd) throws IOException;
    private static native int getTcpUserTimeout(int fd) throws IOException;
    private static native int getTimeToLive(int fd) throws IOException;
    private static native int isIpFreeBind(int fd) throws IOException;
    private static native int isIpTransparent(int fd) throws IOException;
    private static native int isIpRecvOrigDestAddr(int fd) throws IOException;
    private static native void getTcpInfo(int fd, long[] array) throws IOException;
    private static native PeerCredentials getPeerCredentials(int fd) throws IOException;

    private static native void setTcpDeferAccept(int fd, int deferAccept) throws IOException;
    private static native void setTcpQuickAck(int fd, int quickAck) throws IOException;
    private static native void setTcpCork(int fd, int tcpCork) throws IOException;
    private static native void setSoBusyPoll(int fd, int loopMicros) throws IOException;
    private static native void setTcpNotSentLowAt(int fd, int tcpNotSentLowAt) throws IOException;
    private static native void setTcpFastOpen(int fd, int tcpFastopenBacklog) throws IOException;
    private static native void setTcpKeepIdle(int fd, int seconds) throws IOException;
    private static native void setTcpKeepIntvl(int fd, int seconds) throws IOException;
    private static native void setTcpKeepCnt(int fd, int probes) throws IOException;
    private static native void setTcpUserTimeout(int fd, int milliseconds)throws IOException;
    private static native void setIpFreeBind(int fd, int freeBind) throws IOException;
    private static native void setIpTransparent(int fd, int transparent) throws IOException;
    private static native void setIpRecvOrigDestAddr(int fd, int transparent) throws IOException;
    private static native void setTcpMd5Sig(
            int fd, boolean ipv6, byte[] address, int scopeId, byte[] key) throws IOException;
    private static native void setInterface(
            int fd, boolean ipv6, byte[] interfaceAddress, int scopeId, int networkInterfaceIndex) throws IOException;
    private static native int getInterface(int fd, boolean ipv6);
    private static native int getIpMulticastLoop(int fd, boolean ipv6) throws IOException;
    private static native void setIpMulticastLoop(int fd, boolean ipv6, int enabled) throws IOException;
    private static native void setTimeToLive(int fd, int ttl) throws IOException;
    private static native int isUdpGro(int fd) throws IOException;
    private static native void setUdpGro(int fd, int gro) throws IOException;
}
