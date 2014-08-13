/*
 * Copyright 2013 The Netty Project
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
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Native helper methods
 *
 * <strong>Internal usage only!</strong>
 */
final class Native {
    private static final byte[] IPV4_MAPPED_IPV6_PREFIX = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff };

    static {
        String name = SystemPropertyUtil.get("os.name").toLowerCase(Locale.UK).trim();
        if (!name.startsWith("linux")) {
            throw new IllegalStateException("Only supported on Linux");
        }
        NativeLibraryLoader.load("netty-transport-native-epoll", PlatformDependent.getClassLoader(Native.class));
    }

    // EventLoop operations and constants
    public static final int EPOLLIN = 0x01;
    public static final int EPOLLOUT = 0x02;
    public static final int EPOLLACCEPT = 0x04;
    public static final int EPOLLRDHUP = 0x08;
    public static final int IOV_MAX = iovMax();

    public static native int eventFd();
    public static native void eventFdWrite(int fd, long value);
    public static native void eventFdRead(int fd);
    public static native int epollCreate();
    public static native int epollWait(int efd, long[] events, int timeout);
    public static native void epollCtlAdd(int efd, final int fd, final int flags, final int id);
    public static native void epollCtlMod(int efd, final int fd, final int flags, final int id);
    public static native void epollCtlDel(int efd, final int fd);

    // File-descriptor operations
    public static native void close(int fd) throws IOException;

    public static native int write(int fd, ByteBuffer buf, int pos, int limit) throws IOException;
    public static native int writeAddress(int fd, long address, int pos, int limit) throws IOException;

    public static native long writev(int fd, ByteBuffer[] buffers, int offset, int length) throws IOException;
    public static native long writevAddresses(int fd, long memoryAddress, int length)
            throws IOException;

    public static native int read(int fd, ByteBuffer buf, int pos, int limit) throws IOException;
    public static native int readAddress(int fd, long address, int pos, int limit) throws IOException;

    public static native long sendfile(
            int dest, DefaultFileRegion src, long baseOffset, long offset, long length) throws IOException;

    public static int sendTo(
            int fd, ByteBuffer buf, int pos, int limit, InetAddress addr, int port) throws IOException {
        // just duplicate the toNativeInetAddress code here to minimize object creation as this method is expected
        // to be called frequently
        byte[] address;
        int scopeId;
        if (addr instanceof Inet6Address) {
            address = addr.getAddress();
            scopeId = ((Inet6Address) addr).getScopeId();
        } else {
            // convert to ipv4 mapped ipv6 address;
            scopeId = 0;
            address = ipv4MappedIpv6Address(addr.getAddress());
        }
        return sendTo(fd, buf, pos, limit, address, scopeId, port);
    }

    private static native int sendTo(
            int fd, ByteBuffer buf, int pos, int limit, byte[] address, int scopeId, int port) throws IOException;

    public static int sendToAddress(
            int fd, long memoryAddress, int pos, int limit, InetAddress addr, int port) throws IOException {
        // just duplicate the toNativeInetAddress code here to minimize object creation as this method is expected
        // to be called frequently
        byte[] address;
        int scopeId;
        if (addr instanceof Inet6Address) {
            address = addr.getAddress();
            scopeId = ((Inet6Address) addr).getScopeId();
        } else {
            // convert to ipv4 mapped ipv6 address;
            scopeId = 0;
            address = ipv4MappedIpv6Address(addr.getAddress());
        }
        return sendToAddress(fd, memoryAddress, pos, limit, address, scopeId, port);
    }

    private static native int sendToAddress(
            int fd, long memoryAddress, int pos, int limit, byte[] address, int scopeId, int port) throws IOException;

    public static native EpollDatagramChannel.DatagramSocketAddress recvFrom(
            int fd, ByteBuffer buf, int pos, int limit) throws IOException;

    public static native EpollDatagramChannel.DatagramSocketAddress recvFromAddress(
            int fd, long memoryAddress, int pos, int limit) throws IOException;

    // socket operations
    public static int socketStreamFd() {
        try {
            return socketStream();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    public static int socketDgramFd() {
        try {
            return socketDgram();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }
    private static native int socketStream() throws IOException;
    private static native int socketDgram() throws IOException;

    public static void bind(int fd, InetAddress addr, int port) throws IOException {
        NativeInetAddress address = toNativeInetAddress(addr);
        bind(fd, address.address, address.scopeId, port);
    }

    private static byte[] ipv4MappedIpv6Address(byte[] ipv4) {
        byte[] address = new byte[16];
        System.arraycopy(IPV4_MAPPED_IPV6_PREFIX, 0, address, 0, IPV4_MAPPED_IPV6_PREFIX.length);
        System.arraycopy(ipv4, 0, address, 12, ipv4.length);
        return address;
    }

    public static native void bind(int fd, byte[] address, int scopeId, int port) throws IOException;
    public static native void listen(int fd, int backlog) throws IOException;
    public static boolean connect(int fd, InetAddress addr, int port) throws IOException {
        NativeInetAddress address = toNativeInetAddress(addr);
        return connect(fd, address.address, address.scopeId, port);
    }
    public static native boolean connect(int fd, byte[] address, int scopeId, int port) throws IOException;
    public static native boolean finishConnect(int fd) throws IOException;

    public static native InetSocketAddress remoteAddress(int fd);
    public static native InetSocketAddress localAddress(int fd);
    public static native int accept(int fd) throws IOException;
    public static native void shutdown(int fd, boolean read, boolean write) throws IOException;

    // Socket option operations
    public static native int getReceiveBufferSize(int fd);
    public static native int getSendBufferSize(int fd);
    public static native int isKeepAlive(int fd);
    public static native int isReuseAddress(int fd);
    public static native int isReusePort(int fd);
    public static native int isTcpNoDelay(int fd);
    public static native int isTcpCork(int fd);
    public static native int getSoLinger(int fd);
    public static native int getTrafficClass(int fd);
    public static native int isBroadcast(int fd);
    public static native int getTcpKeepIdle(int fd);
    public static native int getTcpKeepIntvl(int fd);
    public static native int getTcpKeepCnt(int fd);

    public static native void setKeepAlive(int fd, int keepAlive);
    public static native void setReceiveBufferSize(int fd, int receiveBufferSize);
    public static native void setReuseAddress(int fd, int reuseAddress);
    public static native void setReusePort(int fd, int reuseAddress);
    public static native void setSendBufferSize(int fd, int sendBufferSize);
    public static native void setTcpNoDelay(int fd, int tcpNoDelay);
    public static native void setTcpCork(int fd, int tcpCork);
    public static native void setSoLinger(int fd, int soLinger);
    public static native void setTrafficClass(int fd, int tcpNoDelay);
    public static native void setBroadcast(int fd, int broadcast);
    public static native void setTcpKeepIdle(int fd, int seconds);
    public static native void setTcpKeepIntvl(int fd, int seconds);
    public static native void setTcpKeepCnt(int fd, int probes);

    private static NativeInetAddress toNativeInetAddress(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (addr instanceof Inet6Address) {
            return new NativeInetAddress(bytes, ((Inet6Address) addr).getScopeId());
        } else {
            // convert to ipv4 mapped ipv6 address;
            return new NativeInetAddress(ipv4MappedIpv6Address(bytes));
        }
    }

    private static class NativeInetAddress {
        final byte[] address;
        final int scopeId;

        NativeInetAddress(byte[] address, int scopeId) {
            this.address = address;
            this.scopeId = scopeId;
        }

        NativeInetAddress(byte[] address) {
            this(address, 0);
        }
    }

    public static native String kernelVersion();

    private static native int iovMax();

    private Native() {
        // utility
    }
}
