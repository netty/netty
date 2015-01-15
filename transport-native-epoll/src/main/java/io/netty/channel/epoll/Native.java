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
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Locale;

/**
 * Native helper methods
 *
 * <strong>Internal usage only!</strong>
 */
final class Native {

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
    public static final int UIO_MAX_IOV = uioMaxIov();
    public static final boolean IS_SUPPORTING_SENDMMSG = isSupportingSendmmsg();

    private static final byte[] IPV4_MAPPED_IPV6_PREFIX = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff };

    // As all our JNI methods return -errno on error we need to compare with the negative errno codes.
    private static final int ERRNO_EBADF_NEGATIVE = -errnoEBADF();
    private static final int ERRNO_EPIPE_NEGATIVE = -errnoEPIPE();
    private static final int ERRNO_EAGAIN_NEGATIVE = -errnoEAGAIN();
    private static final int ERRNO_EWOULDBLOCK_NEGATIVE = -errnoEWOULDBLOCK();
    private static final int ERRNO_EINPROGRESS_NEGATIVE = -errnoEINPROGRESS();

    /**
     * Holds the mappings for errno codes to String messages.
     * This eliminates the need to call back into JNI to get the right String message on an exception
     * and thus is faster.
     *
     * The array length of 1024 should be more then enough because errno.h only holds < 200 codes.
     */
    private static final String[] ERRORS = new String[1024]; //

    // Pre-instantiated exceptions which does not need any stacktrace and
    // can be thrown multiple times for performance reasons.
    private static final ClosedChannelException CLOSED_CHANNEL_EXCEPTION;
    private static final IOException CONNECTION_RESET_EXCEPTION_WRITE;
    private static final IOException CONNECTION_RESET_EXCEPTION_WRITEV;
    private static final IOException CONNECTION_RESET_EXCEPTION_READ;
    private static final IOException CONNECTION_RESET_EXCEPTION_SENDFILE;
    private static final IOException CONNECTION_RESET_EXCEPTION_SENDTO;
    private static final IOException CONNECTION_RESET_EXCEPTION_SENDMSG;
    private static final IOException CONNECTION_RESET_EXCEPTION_SENDMMSG;

    static {
        for (int i = 0; i < ERRORS.length; i++) {
            // This is ok as strerror returns 'Unknown error i' when the message is not known.
            ERRORS[i] = strError(i);
        }
        CONNECTION_RESET_EXCEPTION_WRITE = newConnectionResetException("write");
        CONNECTION_RESET_EXCEPTION_WRITEV = newConnectionResetException("writev");
        CONNECTION_RESET_EXCEPTION_READ = newConnectionResetException("read");
        CONNECTION_RESET_EXCEPTION_SENDFILE = newConnectionResetException("sendfile");
        CONNECTION_RESET_EXCEPTION_SENDTO = newConnectionResetException("sendto");
        CONNECTION_RESET_EXCEPTION_SENDMSG = newConnectionResetException("sendmsg");
        CONNECTION_RESET_EXCEPTION_SENDMMSG = newConnectionResetException("sendmmsg");
        CLOSED_CHANNEL_EXCEPTION = new ClosedChannelException();
        CLOSED_CHANNEL_EXCEPTION.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    private static IOException newConnectionResetException(String method) {
        IOException exception = newIOException(method, ERRNO_EPIPE_NEGATIVE);
        exception.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
        return exception;
    }

    private static IOException newIOException(String method, int err) {
        return new IOException(method + "() failed: " + ERRORS[-err]);
    }

    private static int ioResult(String method, int err, IOException resetCause) throws IOException {
        // network stack saturated... try again later
        if (err == ERRNO_EAGAIN_NEGATIVE || err == ERRNO_EWOULDBLOCK_NEGATIVE) {
            return 0;
        }
        if (err == ERRNO_EPIPE_NEGATIVE) {
            throw resetCause;
        }
        if (err == ERRNO_EBADF_NEGATIVE) {
            throw CLOSED_CHANNEL_EXCEPTION;
        }
        // TODO: We could even go futher and use a pre-instanced IOException for the other error codes, but for
        //       all other errors it may be better to just include a stacktrace.
        throw newIOException(method, err);
    }

    public static native int eventFd();
    public static native void eventFdWrite(int fd, long value);
    public static native void eventFdRead(int fd);
    public static native int epollCreate();
    public static native int epollWait(int efd, long[] events, int timeout);
    public static native void epollCtlAdd(int efd, final int fd, final int flags, final int id);
    public static native void epollCtlMod(int efd, final int fd, final int flags, final int id);
    public static native void epollCtlDel(int efd, final int fd);

    private static native int errnoEBADF();
    private static native int errnoEPIPE();
    private static native int errnoEAGAIN();
    private static native int errnoEWOULDBLOCK();
    private static native int errnoEINPROGRESS();
    private static native String strError(int err);

    // File-descriptor operations
    public static void close(int fd) throws IOException {
        int res = close0(fd);
        if (res < 0) {
            throw newIOException("close", res);
        }
    }

    private static native int close0(int fd);

    public static int write(int fd, ByteBuffer buf, int pos, int limit) throws IOException {
        int res = write0(fd, buf, pos, limit);
        if (res >= 0) {
            return res;
        }
        return ioResult("write", res, CONNECTION_RESET_EXCEPTION_WRITE);
    }

    private static native int write0(int fd, ByteBuffer buf, int pos, int limit);

    public static int writeAddress(int fd, long address, int pos, int limit) throws IOException {
        int res = writeAddress0(fd, address, pos, limit);
        if (res >= 0) {
            return res;
        }
        return ioResult("writeAddress", res, CONNECTION_RESET_EXCEPTION_WRITE);
    }

    private static native int writeAddress0(int fd, long address, int pos, int limit);

    public static long writev(int fd, ByteBuffer[] buffers, int offset, int length) throws IOException {
        long res = writev0(fd, buffers, offset, length);
        if (res >= 0) {
            return res;
        }
        return ioResult("writev", (int) res, CONNECTION_RESET_EXCEPTION_WRITEV);
    }

    private static native long writev0(int fd, ByteBuffer[] buffers, int offset, int length);

    public static long writevAddresses(int fd, long memoryAddress, int length)
            throws IOException {
        long res = writevAddresses0(fd, memoryAddress, length);
        if (res >= 0) {
            return res;
        }
        return ioResult("writevAddresses", (int) res, CONNECTION_RESET_EXCEPTION_WRITEV);
    }

    private static native long writevAddresses0(int fd, long memoryAddress, int length);

    public static int read(int fd, ByteBuffer buf, int pos, int limit) throws IOException {
        int res = read0(fd, buf, pos, limit);
        if (res > 0) {
            return res;
        }
        if (res == 0) {
            return -1;
        }
        return ioResult("read", res, CONNECTION_RESET_EXCEPTION_READ);
    }

    private static native int read0(int fd, ByteBuffer buf, int pos, int limit);

    public static int readAddress(int fd, long address, int pos, int limit) throws IOException {
        int res = readAddress0(fd, address, pos, limit);
        if (res > 0) {
            return res;
        }
        if (res == 0) {
            return -1;
        }
        return ioResult("readAddress", res, CONNECTION_RESET_EXCEPTION_READ);
    }

    private static native int readAddress0(int fd, long address, int pos, int limit);

    public static long sendfile(
            int dest, DefaultFileRegion src, long baseOffset, long offset, long length) throws IOException {
        // Open the file-region as it may be created via the lazy constructor. This is needed as we directly access
        // the FileChannel field directly via JNI
        src.open();

        long res = sendfile0(dest, src, baseOffset, offset, length);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendfile", (int) res, CONNECTION_RESET_EXCEPTION_SENDFILE);
    }

    private static native long sendfile0(
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
        int res = sendTo0(fd, buf, pos, limit, address, scopeId, port);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendTo", res, CONNECTION_RESET_EXCEPTION_SENDTO);
    }

    private static native int sendTo0(
            int fd, ByteBuffer buf, int pos, int limit, byte[] address, int scopeId, int port);

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
        int res = sendToAddress0(fd, memoryAddress, pos, limit, address, scopeId, port);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendToAddress", res, CONNECTION_RESET_EXCEPTION_SENDTO);
    }

    private static native int sendToAddress0(
            int fd, long memoryAddress, int pos, int limit, byte[] address, int scopeId, int port);

    public static int sendToAddresses(
            int fd, long memoryAddress, int length, InetAddress addr, int port) throws IOException {
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
        int res = sendToAddresses(fd, memoryAddress, length, address, scopeId, port);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendToAddresses", res, CONNECTION_RESET_EXCEPTION_SENDMSG);
    }

    private static native int sendToAddresses(
            int fd, long memoryAddress, int length, byte[] address, int scopeId, int port);

    public static native EpollDatagramChannel.DatagramSocketAddress recvFrom(
            int fd, ByteBuffer buf, int pos, int limit) throws IOException;

    public static native EpollDatagramChannel.DatagramSocketAddress recvFromAddress(
            int fd, long memoryAddress, int pos, int limit) throws IOException;

    public static int sendmmsg(
            int fd, NativeDatagramPacketArray.NativeDatagramPacket[] msgs, int offset, int len) throws IOException {
        int res = sendmmsg0(fd, msgs, offset, len);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendmmsg", res, CONNECTION_RESET_EXCEPTION_SENDMMSG);
    }

    private static native int sendmmsg0(
            int fd, NativeDatagramPacketArray.NativeDatagramPacket[] msgs, int offset, int len);

    private static native boolean isSupportingSendmmsg();

    // socket operations
    public static int socketStreamFd() {
        int res = socketStream();
        if (res < 0) {
            throw new ChannelException(newIOException("socketStreamFd", res));
        }
        return res;
    }

    public static int socketDgramFd() {
        int res = socketDgram();
        if (res < 0) {
            throw new ChannelException(newIOException("socketDgramFd", res));
        }
        return res;
    }

    public static int socketDomainFd() {
        int res = socketDomain();
        if (res < 0) {
            throw new ChannelException(newIOException("socketDomain", res));
        }
        return res;
    }

    private static native int socketStream();
    private static native int socketDgram();
    private static native int socketDomain();

    public static void bind(int fd, SocketAddress socketAddress) throws IOException {
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress addr = (InetSocketAddress) socketAddress;
            NativeInetAddress address = toNativeInetAddress(addr.getAddress());
            int res = bind(fd, address.address, address.scopeId, addr.getPort());
            if (res < 0) {
                throw newIOException("bind", res);
            }
        } else if (socketAddress instanceof DomainSocketAddress) {
            DomainSocketAddress addr = (DomainSocketAddress) socketAddress;
            int res = bindDomainSocket(fd, addr.path());
            if (res < 0) {
                throw newIOException("bind", res);
            }
        } else {
            throw new Error("Unexpected SocketAddress implementation " + socketAddress);
        }
    }

    private static native int bind(int fd, byte[] address, int scopeId, int port);
    private static native int bindDomainSocket(int fd, String path);

    public static void listen(int fd, int backlog) throws IOException {
        int res = listen0(fd, backlog);
        if (res < 0) {
            throw newIOException("listen", res);
        }
    }

    private static native int listen0(int fd, int backlog);

    public static boolean connect(int fd, SocketAddress socketAddress) throws IOException {
        int res;
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
            NativeInetAddress address = toNativeInetAddress(inetSocketAddress.getAddress());
            res = connect(fd, address.address, address.scopeId, inetSocketAddress.getPort());
        } else if (socketAddress instanceof DomainSocketAddress) {
            DomainSocketAddress unixDomainSocketAddress = (DomainSocketAddress) socketAddress;
            res = connectDomainSocket(fd, unixDomainSocketAddress.path());
        } else {
            throw new Error("Unexpected SocketAddress implementation " + socketAddress);
        }
        if (res < 0) {
            if (res == ERRNO_EINPROGRESS_NEGATIVE) {
                // connect not complete yet need to wait for EPOLLOUT event
                return false;
            }
            throw newIOException("connect", res);
        }
        return true;
    }

    private static native int connect(int fd, byte[] address, int scopeId, int port);
    private static native int connectDomainSocket(int fd, String path);

    public static boolean finishConnect(int fd) throws IOException {
        int res = finishConnect0(fd);
        if (res < 0) {
            if (res == ERRNO_EINPROGRESS_NEGATIVE) {
                // connect still in progress
                return false;
            }
            throw newIOException("finishConnect", res);
        }
        return true;
    }

    private static native int finishConnect0(int fd);

    public static InetSocketAddress remoteAddress(int fd) {
        byte[] addr = remoteAddress0(fd);
        return address(addr);
    }

    public static InetSocketAddress localAddress(int fd) {
        byte[] addr = localAddress0(fd);
        return address(addr);
    }

    static InetSocketAddress address(byte[] addr) {
        int len = addr.length;
        // The last 4 bytes are always the port
        final int port = decodeInt(addr, len - 4);
        final InetAddress address;

        try {
            switch (len) {
                // 8 bytes:
                // - 4  == ipaddress
                // - 4  == port
                case 8:
                    byte[] ipv4 = new byte[4];
                    System.arraycopy(addr, 0, ipv4, 0, 4);
                    address = InetAddress.getByAddress(ipv4);
                    break;

                // 24 bytes:
                // - 16  == ipaddress
                // - 4   == scopeId
                // - 4   == port
                case 24:
                    byte[] ipv6 = new byte[16];
                    System.arraycopy(addr, 0, ipv6, 0, 16);
                    int scopeId = decodeInt(addr, len  - 8);
                    address = Inet6Address.getByAddress(null, ipv6, scopeId);
                    break;
                default:
                    throw new Error();
            }
            return new InetSocketAddress(address, port);
        } catch (UnknownHostException e) {
            throw new Error("Should never happen", e);
        }
    }

    private static int decodeInt(byte[] addr, int index) {
        return  (addr[index]     & 0xff) << 24 |
                (addr[index + 1] & 0xff) << 16 |
                (addr[index + 2] & 0xff) <<  8 |
                addr[index + 3] & 0xff;
    }

    private static native byte[] remoteAddress0(int fd);
    private static native byte[] localAddress0(int fd);

    public static int accept(int fd) throws IOException {
        int res = accept0(fd);
        if (res >= 0) {
            return res;
        }
        if (res == ERRNO_EAGAIN_NEGATIVE || res == ERRNO_EWOULDBLOCK_NEGATIVE) {
            // Everything consumed so just return -1 here.
            return -1;
        }
        throw newIOException("accept", res);
    }

    private static native int accept0(int fd);

    public static int recvFd(int fd) throws IOException {
        int res = recvFd0(fd);
        if (res > 0) {
            return res;
        }
        if (res == 0) {
            return -1;
        }

        if (res == ERRNO_EAGAIN_NEGATIVE || res == ERRNO_EWOULDBLOCK_NEGATIVE) {
            // Everything consumed so just return -1 here.
            return 0;
        }
        throw newIOException("recvFd", res);
    }

    private static native int recvFd0(int fd);

    public static int sendFd(int socketFd, int fd) throws IOException {
        int res = sendFd0(socketFd, fd);
        if (res >= 0) {
            return res;
        }
        if (res == ERRNO_EAGAIN_NEGATIVE || res == ERRNO_EWOULDBLOCK_NEGATIVE) {
            // Everything consumed so just return -1 here.
            return -1;
        }
        throw newIOException("sendFd", res);
    }

    private static native int sendFd0(int socketFd, int fd);

    public static void shutdown(int fd, boolean read, boolean write) throws IOException {
        int res = shutdown0(fd, read, write);
        if (res < 0) {
            throw newIOException("shutdown", res);
        }
    }

    private static native int shutdown0(int fd, boolean read, boolean write);

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

    static byte[] ipv4MappedIpv6Address(byte[] ipv4) {
        byte[] address = new byte[16];
        System.arraycopy(IPV4_MAPPED_IPV6_PREFIX, 0, address, 0, IPV4_MAPPED_IPV6_PREFIX.length);
        System.arraycopy(ipv4, 0, address, 12, ipv4.length);
        return address;
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

    private static native int uioMaxIov();

    private Native() {
        // utility
    }
}
