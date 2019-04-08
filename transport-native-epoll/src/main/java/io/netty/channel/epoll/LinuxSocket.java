/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.ChannelException;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.unix.Errors.NativeIoException;
import io.netty.channel.unix.NativeInetAddress;
import io.netty.channel.unix.PeerCredentials;
import io.netty.channel.unix.Socket;
import io.netty.util.internal.ThrowableUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.Enumeration;

import static io.netty.channel.unix.Errors.ERRNO_EPIPE_NEGATIVE;
import static io.netty.channel.unix.Errors.ioResult;
import static io.netty.channel.unix.Errors.newConnectionResetException;

/**
 * A socket which provides access Linux native methods.
 */
final class LinuxSocket extends Socket {
    private static final InetAddress INET_ANY = unsafeInetAddrByName("0.0.0.0");
    private static final InetAddress INET6_ANY = unsafeInetAddrByName("::");
    private static final long MAX_UINT32_T = 0xFFFFFFFFL;
    private static final NativeIoException SENDFILE_CONNECTION_RESET_EXCEPTION =
            newConnectionResetException("syscall:sendfile(...)", ERRNO_EPIPE_NEGATIVE);
    private static final ClosedChannelException SENDFILE_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ClosedChannelException(), Native.class, "sendfile(...)");

    LinuxSocket(int fd) {
        super(fd);
    }

    void setTimeToLive(int ttl) throws IOException {
        setTimeToLive(intValue(), ttl);
    }

    void setInterface(InetAddress address) throws IOException {
        final NativeInetAddress a = NativeInetAddress.newInstance(address);
        setInterface(intValue(), a.address(), a.scopeId());
    }

    void setNetworkInterface(NetworkInterface netInterface) throws IOException {
        final NativeInetAddress i = NativeInetAddress.newInstance(deriveInetAddress(netInterface, false));
        if (i.equals(INET_ANY)) {
            final NativeInetAddress i6 = NativeInetAddress.newInstance(deriveInetAddress(netInterface, true));
            setInterface(intValue(), i6.address(), i6.scopeId());
        } else {
            setInterface(intValue(), i.address(), i.scopeId());
        }
    }

    void joinGroup(InetAddress group, NetworkInterface netInterface, InetAddress source) throws IOException {
        final NativeInetAddress g = NativeInetAddress.newInstance(group);
        final boolean isIpv6 = group instanceof Inet6Address;
        final NativeInetAddress i = NativeInetAddress.newInstance(deriveInetAddress(netInterface, isIpv6));
        if (source != null) {
            final NativeInetAddress s = NativeInetAddress.newInstance(source);
            joinSsmGroup(intValue(), g.address(), i.address(), g.scopeId(), s.address());
        } else {
            joinGroup(intValue(), g.address(), i.address(), g.scopeId());
        }
    }

    void leaveGroup(InetAddress group, NetworkInterface netInterface, InetAddress source) throws IOException {
        final NativeInetAddress g = NativeInetAddress.newInstance(group);
        final boolean isIpv6 = group instanceof Inet6Address;
        final NativeInetAddress i = NativeInetAddress.newInstance(deriveInetAddress(netInterface, isIpv6));
        if (source != null) {
            final NativeInetAddress s = NativeInetAddress.newInstance(source);
            leaveSsmGroup(intValue(), g.address(), i.address(), g.scopeId(), s.address());
        } else {
            leaveGroup(intValue(), g.address(), i.address(), g.scopeId());
        }
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

    void setTcpFastOpenConnect(boolean tcpFastOpenConnect) throws IOException {
        setTcpFastOpenConnect(intValue(), tcpFastOpenConnect ? 1 : 0);
    }

    boolean isTcpFastOpenConnect() throws IOException {
        return isTcpFastOpenConnect(intValue()) != 0;
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
        setTcpMd5Sig(intValue(), a.address(), a.scopeId(), key);
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

    long sendFile(DefaultFileRegion src, long baseOffset, long offset, long length) throws IOException {
        // Open the file-region as it may be created via the lazy constructor. This is needed as we directly access
        // the FileChannel field via JNI.
        src.open();

        long res = sendFile(intValue(), src, baseOffset, offset, length);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendfile", (int) res, SENDFILE_CONNECTION_RESET_EXCEPTION, SENDFILE_CLOSED_CHANNEL_EXCEPTION);
    }

    private InetAddress deriveInetAddress(NetworkInterface netInterface, boolean ipv6) throws IOException {
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

    public static LinuxSocket newSocketStream() {
        return new LinuxSocket(newSocketStream0());
    }

    public static LinuxSocket newSocketDgram() {
        return new LinuxSocket(newSocketDgram0());
    }

    public static LinuxSocket newSocketDomain() {
        return new LinuxSocket(newSocketDomain0());
    }

    private static InetAddress unsafeInetAddrByName(String inetName) {
        try {
            return InetAddress.getByName(inetName);
        } catch (UnknownHostException uhe) {
            throw new ChannelException(uhe);
        }
    }

    private static native void joinGroup(int fd, byte[] group, byte[] interfaceAddress,
                                         int scopeId) throws IOException;
    private static native void joinSsmGroup(int fd, byte[] group, byte[] interfaceAddress,
                                            int scopeId, byte[] source) throws IOException;
    private static native void leaveGroup(int fd, byte[] group, byte[] interfaceAddress,
                                          int scopeId) throws IOException;
    private static native void leaveSsmGroup(int fd, byte[] group, byte[] interfaceAddress,
                                             int scopeId, byte[] source) throws IOException;
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
    private static native int isTcpFastOpenConnect(int fd) throws IOException;

    private static native void setTcpDeferAccept(int fd, int deferAccept) throws IOException;
    private static native void setTcpQuickAck(int fd, int quickAck) throws IOException;
    private static native void setTcpCork(int fd, int tcpCork) throws IOException;
    private static native void setSoBusyPoll(int fd, int loopMicros) throws IOException;
    private static native void setTcpNotSentLowAt(int fd, int tcpNotSentLowAt) throws IOException;
    private static native void setTcpFastOpen(int fd, int tcpFastopenBacklog) throws IOException;
    private static native void setTcpFastOpenConnect(int fd, int tcpFastOpenConnect) throws IOException;
    private static native void setTcpKeepIdle(int fd, int seconds) throws IOException;
    private static native void setTcpKeepIntvl(int fd, int seconds) throws IOException;
    private static native void setTcpKeepCnt(int fd, int probes) throws IOException;
    private static native void setTcpUserTimeout(int fd, int milliseconds)throws IOException;
    private static native void setIpFreeBind(int fd, int freeBind) throws IOException;
    private static native void setIpTransparent(int fd, int transparent) throws IOException;
    private static native void setIpRecvOrigDestAddr(int fd, int transparent) throws IOException;
    private static native void setTcpMd5Sig(int fd, byte[] address, int scopeId, byte[] key) throws IOException;
    private static native void setInterface(int fd, byte[] interfaceAddress, int scopeId) throws IOException;
    private static native void setTimeToLive(int fd, int ttl) throws IOException;
}
