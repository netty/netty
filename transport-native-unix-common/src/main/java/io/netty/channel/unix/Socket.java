/*
 * Copyright 2015 The Netty Project
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

import io.netty.channel.ChannelException;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.channel.unix.Errors.ERRNO_EAGAIN_NEGATIVE;
import static io.netty.channel.unix.Errors.ERROR_ECONNREFUSED_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EINPROGRESS_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EWOULDBLOCK_NEGATIVE;
import static io.netty.channel.unix.Errors.ioResult;
import static io.netty.channel.unix.Errors.newIOException;
import static io.netty.channel.unix.Errors.throwConnectException;
import static io.netty.channel.unix.LimitsStaticallyReferencedJniMethods.udsSunPathSize;
import static io.netty.channel.unix.NativeInetAddress.address;
import static io.netty.channel.unix.NativeInetAddress.ipv4MappedIpv6Address;

/**
 * Provides a JNI bridge to native socket operations.
 * <strong>Internal usage only!</strong>
 */
public class Socket extends FileDescriptor {

    public static final int UDS_SUN_PATH_SIZE = udsSunPathSize();

    protected final boolean ipv6;

    public Socket(int fd) {
        super(fd);
        this.ipv6 = isIPv6(fd);
    }

    /**
     * Returns {@code true} if we should use IPv6 internally, {@code false} otherwise.
     */
    private boolean useIpv6(InetAddress address) {
        return ipv6 || address instanceof Inet6Address;
    }

    public final void shutdown() throws IOException {
        shutdown(true, true);
    }

    public final void shutdown(boolean read, boolean write) throws IOException {
        for (;;) {
            // We need to only shutdown what has not been shutdown yet, and if there is no change we should not
            // shutdown anything. This is because if the underlying FD is reused and we still have an object which
            // represents the previous incarnation of the FD we need to be sure we don't inadvertently shutdown the
            // "new" FD without explicitly having a change.
            final int oldState = this.state;
            if (isClosed(oldState)) {
                throw new ClosedChannelException();
            }
            int newState = oldState;
            if (read && !isInputShutdown(newState)) {
                newState = inputShutdown(newState);
            }
            if (write && !isOutputShutdown(newState)) {
                newState = outputShutdown(newState);
            }

            // If there is no change in state, then we should not take any action.
            if (newState == oldState) {
                return;
            }
            if (casState(oldState, newState)) {
                break;
            }
        }
        int res = shutdown(fd, read, write);
        if (res < 0) {
            ioResult("shutdown", res);
        }
    }

    public final boolean isShutdown() {
        int state = this.state;
        return isInputShutdown(state) && isOutputShutdown(state);
    }

    public final boolean isInputShutdown() {
        return isInputShutdown(state);
    }

    public final boolean isOutputShutdown() {
        return isOutputShutdown(state);
    }

    public final int sendTo(ByteBuffer buf, int pos, int limit, InetAddress addr, int port) throws IOException {
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
        int res = sendTo(fd, useIpv6(addr), buf, pos, limit, address, scopeId, port);
        if (res >= 0) {
            return res;
        }
        if (res == ERROR_ECONNREFUSED_NEGATIVE) {
            throw new PortUnreachableException("sendTo failed");
        }
        return ioResult("sendTo", res);
    }

    public final int sendToAddress(long memoryAddress, int pos, int limit, InetAddress addr, int port)
            throws IOException {
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
        int res = sendToAddress(fd, useIpv6(addr), memoryAddress, pos, limit, address, scopeId, port);
        if (res >= 0) {
            return res;
        }
        if (res == ERROR_ECONNREFUSED_NEGATIVE) {
            throw new PortUnreachableException("sendToAddress failed");
        }
        return ioResult("sendToAddress", res);
    }

    public final int sendToAddresses(long memoryAddress, int length, InetAddress addr, int port) throws IOException {
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
        int res = sendToAddresses(fd, useIpv6(addr), memoryAddress, length, address, scopeId, port);
        if (res >= 0) {
            return res;
        }

        if (res == ERROR_ECONNREFUSED_NEGATIVE) {
            throw new PortUnreachableException("sendToAddresses failed");
        }
        return ioResult("sendToAddresses", res);
    }

    public final DatagramSocketAddress recvFrom(ByteBuffer buf, int pos, int limit) throws IOException {
        return recvFrom(fd, buf, pos, limit);
    }

    public final DatagramSocketAddress recvFromAddress(long memoryAddress, int pos, int limit) throws IOException {
        return recvFromAddress(fd, memoryAddress, pos, limit);
    }

    public final int recvFd() throws IOException {
        int res = recvFd(fd);
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

    public final int sendFd(int fdToSend) throws IOException {
        int res = sendFd(fd, fdToSend);
        if (res >= 0) {
            return res;
        }
        if (res == ERRNO_EAGAIN_NEGATIVE || res == ERRNO_EWOULDBLOCK_NEGATIVE) {
            // Everything consumed so just return -1 here.
            return -1;
        }
        throw newIOException("sendFd", res);
    }

    public final boolean connect(SocketAddress socketAddress) throws IOException {
        int res;
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
            InetAddress inetAddress = inetSocketAddress.getAddress();
            NativeInetAddress address = NativeInetAddress.newInstance(inetAddress);
            res = connect(fd, useIpv6(inetAddress), address.address, address.scopeId, inetSocketAddress.getPort());
        } else if (socketAddress instanceof DomainSocketAddress) {
            DomainSocketAddress unixDomainSocketAddress = (DomainSocketAddress) socketAddress;
            res = connectDomainSocket(fd, unixDomainSocketAddress.path().getBytes(CharsetUtil.UTF_8));
        } else {
            throw new Error("Unexpected SocketAddress implementation " + socketAddress);
        }
        if (res < 0) {
            if (res == ERRNO_EINPROGRESS_NEGATIVE) {
                // connect not complete yet need to wait for EPOLLOUT event
                return false;
            }
            throwConnectException("connect", res);
        }
        return true;
    }

    public final boolean finishConnect() throws IOException {
        int res = finishConnect(fd);
        if (res < 0) {
            if (res == ERRNO_EINPROGRESS_NEGATIVE) {
                // connect still in progress
                return false;
            }
            throwConnectException("finishConnect", res);
        }
        return true;
    }

    public final void disconnect() throws IOException {
        int res = disconnect(fd, ipv6);
        if (res < 0) {
            throwConnectException("disconnect", res);
        }
    }

    public final void bind(SocketAddress socketAddress) throws IOException {
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress addr = (InetSocketAddress) socketAddress;
            InetAddress inetAddress = addr.getAddress();
            NativeInetAddress address = NativeInetAddress.newInstance(inetAddress);
            int res = bind(fd, useIpv6(inetAddress), address.address, address.scopeId, addr.getPort());
            if (res < 0) {
                throw newIOException("bind", res);
            }
        } else if (socketAddress instanceof DomainSocketAddress) {
            DomainSocketAddress addr = (DomainSocketAddress) socketAddress;
            int res = bindDomainSocket(fd, addr.path().getBytes(CharsetUtil.UTF_8));
            if (res < 0) {
                throw newIOException("bind", res);
            }
        } else {
            throw new Error("Unexpected SocketAddress implementation " + socketAddress);
        }
    }

    public final void listen(int backlog) throws IOException {
        int res = listen(fd, backlog);
        if (res < 0) {
            throw newIOException("listen", res);
        }
    }

    public final int accept(byte[] addr) throws IOException {
        int res = accept(fd, addr);
        if (res >= 0) {
            return res;
        }
        if (res == ERRNO_EAGAIN_NEGATIVE || res == ERRNO_EWOULDBLOCK_NEGATIVE) {
            // Everything consumed so just return -1 here.
            return -1;
        }
        throw newIOException("accept", res);
    }

    public final InetSocketAddress remoteAddress() {
        byte[] addr = remoteAddress(fd);
        // addr may be null if getpeername failed.
        // See https://github.com/netty/netty/issues/3328
        return addr == null ? null : address(addr, 0, addr.length);
    }

    public final InetSocketAddress localAddress() {
        byte[] addr = localAddress(fd);
        // addr may be null if getpeername failed.
        // See https://github.com/netty/netty/issues/3328
        return addr == null ? null : address(addr, 0, addr.length);
    }

    public final int getReceiveBufferSize() throws IOException {
        return getReceiveBufferSize(fd);
    }

    public final int getSendBufferSize() throws IOException {
        return getSendBufferSize(fd);
    }

    public final boolean isKeepAlive() throws IOException {
        return isKeepAlive(fd) != 0;
    }

    public final boolean isTcpNoDelay() throws IOException {
        return isTcpNoDelay(fd) != 0;
    }

    public final boolean isReuseAddress() throws IOException {
        return isReuseAddress(fd) != 0;
    }

    public final boolean isReusePort() throws IOException {
        return isReusePort(fd) != 0;
    }

    public final boolean isBroadcast() throws IOException {
        return isBroadcast(fd) != 0;
    }

    public final int getSoLinger() throws IOException {
        return getSoLinger(fd);
    }

    public final int getSoError() throws IOException {
        return getSoError(fd);
    }

    public final int getTrafficClass() throws IOException {
        return getTrafficClass(fd, ipv6);
    }

    public final void setKeepAlive(boolean keepAlive) throws IOException {
        setKeepAlive(fd, keepAlive ? 1 : 0);
    }

    public final void setReceiveBufferSize(int receiveBufferSize) throws IOException  {
        setReceiveBufferSize(fd, receiveBufferSize);
    }

    public final void setSendBufferSize(int sendBufferSize) throws IOException {
        setSendBufferSize(fd, sendBufferSize);
    }

    public final void setTcpNoDelay(boolean tcpNoDelay) throws IOException  {
        setTcpNoDelay(fd, tcpNoDelay ? 1 : 0);
    }

    public final void setSoLinger(int soLinger) throws IOException {
        setSoLinger(fd, soLinger);
    }

    public final void setReuseAddress(boolean reuseAddress) throws IOException {
        setReuseAddress(fd, reuseAddress ? 1 : 0);
    }

    public final void setReusePort(boolean reusePort) throws IOException {
        setReusePort(fd, reusePort ? 1 : 0);
    }

    public final void setBroadcast(boolean broadcast) throws IOException {
        setBroadcast(fd, broadcast ? 1 : 0);
    }

    public final void setTrafficClass(int trafficClass) throws IOException {
        setTrafficClass(fd, ipv6, trafficClass);
    }

    public static native boolean isIPv6Preferred();

    private static native boolean isIPv6(int fd);

    @Override
    public String toString() {
        return "Socket{" +
                "fd=" + fd +
                '}';
    }

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    public static Socket newSocketStream() {
        return new Socket(newSocketStream0());
    }

    public static Socket newSocketDgram() {
        return new Socket(newSocketDgram0());
    }

    public static Socket newSocketDomain() {
        return new Socket(newSocketDomain0());
    }

    public static void initialize() {
        if (INITIALIZED.compareAndSet(false, true)) {
            initialize(NetUtil.isIpV4StackPreferred());
        }
    }

    protected static int newSocketStream0() {
        return newSocketStream0(isIPv6Preferred());
    }

    protected static int newSocketStream0(boolean ipv6) {
        int res = newSocketStreamFd(ipv6);
        if (res < 0) {
            throw new ChannelException(newIOException("newSocketStream", res));
        }
        return res;
    }

    protected static int newSocketDgram0() {
        return newSocketDgram0(isIPv6Preferred());
    }

    protected static int newSocketDgram0(boolean ipv6) {
        int res = newSocketDgramFd(ipv6);
        if (res < 0) {
            throw new ChannelException(newIOException("newSocketDgram", res));
        }
        return res;
    }

    protected static int newSocketDomain0() {
        int res = newSocketDomainFd();
        if (res < 0) {
            throw new ChannelException(newIOException("newSocketDomain", res));
        }
        return res;
    }

    private static native int shutdown(int fd, boolean read, boolean write);
    private static native int connect(int fd, boolean ipv6, byte[] address, int scopeId, int port);
    private static native int connectDomainSocket(int fd, byte[] path);
    private static native int finishConnect(int fd);
    private static native int disconnect(int fd, boolean ipv6);
    private static native int bind(int fd, boolean ipv6, byte[] address, int scopeId, int port);
    private static native int bindDomainSocket(int fd, byte[] path);
    private static native int listen(int fd, int backlog);
    private static native int accept(int fd, byte[] addr);

    private static native byte[] remoteAddress(int fd);
    private static native byte[] localAddress(int fd);

    private static native int sendTo(
            int fd, boolean ipv6, ByteBuffer buf, int pos, int limit, byte[] address, int scopeId, int port);
    private static native int sendToAddress(
            int fd, boolean ipv6, long memoryAddress, int pos, int limit, byte[] address, int scopeId, int port);
    private static native int sendToAddresses(
            int fd, boolean ipv6, long memoryAddress, int length, byte[] address, int scopeId, int port);

    private static native DatagramSocketAddress recvFrom(
            int fd, ByteBuffer buf, int pos, int limit) throws IOException;
    private static native DatagramSocketAddress recvFromAddress(
            int fd, long memoryAddress, int pos, int limit) throws IOException;
    private static native int recvFd(int fd);
    private static native int sendFd(int socketFd, int fd);

    private static native int newSocketStreamFd(boolean ipv6);
    private static native int newSocketDgramFd(boolean ipv6);
    private static native int newSocketDomainFd();

    private static native int isReuseAddress(int fd) throws IOException;
    private static native int isReusePort(int fd) throws IOException;
    private static native int getReceiveBufferSize(int fd) throws IOException;
    private static native int getSendBufferSize(int fd) throws IOException;
    private static native int isKeepAlive(int fd) throws IOException;
    private static native int isTcpNoDelay(int fd) throws IOException;
    private static native int isBroadcast(int fd) throws IOException;
    private static native int getSoLinger(int fd) throws IOException;
    private static native int getSoError(int fd) throws IOException;
    private static native int getTrafficClass(int fd, boolean ipv6) throws IOException;

    private static native void setReuseAddress(int fd, int reuseAddress) throws IOException;
    private static native void setReusePort(int fd, int reuseAddress) throws IOException;
    private static native void setKeepAlive(int fd, int keepAlive) throws IOException;
    private static native void setReceiveBufferSize(int fd, int receiveBufferSize) throws IOException;
    private static native void setSendBufferSize(int fd, int sendBufferSize) throws IOException;
    private static native void setTcpNoDelay(int fd, int tcpNoDelay) throws IOException;
    private static native void setSoLinger(int fd, int soLinger) throws IOException;
    private static native void setBroadcast(int fd, int broadcast) throws IOException;
    private static native void setTrafficClass(int fd, boolean ipv6, int trafficClass) throws IOException;
    private static native void initialize(boolean ipv4Preferred);
}
