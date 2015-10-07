/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.unix;

import io.netty.channel.ChannelException;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty.channel.unix.Errors.CONNECTION_NOT_CONNECTED_SHUTDOWN_EXCEPTION;
import static io.netty.channel.unix.Errors.CONNECTION_RESET_EXCEPTION_SENDMSG;
import static io.netty.channel.unix.Errors.CONNECTION_RESET_EXCEPTION_SENDTO;
import static io.netty.channel.unix.Errors.ERRNO_EAGAIN_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EINPROGRESS_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EWOULDBLOCK_NEGATIVE;
import static io.netty.channel.unix.Errors.ioResult;
import static io.netty.channel.unix.Errors.newConnectException;
import static io.netty.channel.unix.Errors.newIOException;
import static io.netty.channel.unix.NativeInetAddress.address;
import static io.netty.channel.unix.NativeInetAddress.ipv4MappedIpv6Address;

/**
 * Provides a JNI bridge to native socket operations.
 * <strong>Internal usage only!</strong>
 */
public final class Socket extends FileDescriptor {
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    public Socket(int fd) {
        super(fd);
    }

    public void shutdown(boolean read, boolean write) throws IOException {
        inputShutdown = read || inputShutdown;
        outputShutdown = write || outputShutdown;
        int res = shutdown(intValue(), read, write);
        if (res < 0) {
            ioResult("shutdown", res, CONNECTION_NOT_CONNECTED_SHUTDOWN_EXCEPTION);
        }
    }

    public void shutdown() throws IOException {
        shutdown(true, true);
    }

    public boolean isShutdown() {
        return isInputShutdown() && isOutputShutdown();
    }

    public boolean isInputShutdown() {
        return inputShutdown;
    }

    public boolean isOutputShutdown() {
        return outputShutdown;
    }

    public int sendTo(ByteBuffer buf, int pos, int limit, InetAddress addr, int port) throws IOException {
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
        int res = sendTo(intValue(), buf, pos, limit, address, scopeId, port);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendTo", res, CONNECTION_RESET_EXCEPTION_SENDTO);
    }

    public int sendToAddress(long memoryAddress, int pos, int limit, InetAddress addr, int port)
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
        int res = sendToAddress(intValue(), memoryAddress, pos, limit, address, scopeId, port);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendToAddress", res, CONNECTION_RESET_EXCEPTION_SENDTO);
    }

    public int sendToAddresses(long memoryAddress, int length, InetAddress addr, int port) throws IOException {
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
        int res = sendToAddresses(intValue(), memoryAddress, length, address, scopeId, port);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendToAddresses", res, CONNECTION_RESET_EXCEPTION_SENDMSG);
    }

    public DatagramSocketAddress recvFrom(ByteBuffer buf, int pos, int limit) throws IOException {
        return recvFrom(intValue(), buf, pos, limit);
    }

    public DatagramSocketAddress recvFromAddress(long memoryAddress, int pos, int limit) throws IOException {
        return recvFromAddress(intValue(), memoryAddress, pos, limit);
    }

    public boolean connect(SocketAddress socketAddress) throws IOException {
        int res;
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
            NativeInetAddress address = NativeInetAddress.newInstance(inetSocketAddress.getAddress());
            res = connect(intValue(), address.address, address.scopeId, inetSocketAddress.getPort());
        } else if (socketAddress instanceof DomainSocketAddress) {
            DomainSocketAddress unixDomainSocketAddress = (DomainSocketAddress) socketAddress;
            res = connectDomainSocket(intValue(), unixDomainSocketAddress.path().getBytes(CharsetUtil.UTF_8));
        } else {
            throw new Error("Unexpected SocketAddress implementation " + socketAddress);
        }
        if (res < 0) {
            if (res == ERRNO_EINPROGRESS_NEGATIVE) {
                // connect not complete yet need to wait for EPOLLOUT event
                return false;
            }
            throw newConnectException("connect", res);
        }
        return true;
    }

    public boolean finishConnect() throws IOException {
        int res = finishConnect(intValue());
        if (res < 0) {
            if (res == ERRNO_EINPROGRESS_NEGATIVE) {
                // connect still in progress
                return false;
            }
            throw newConnectException("finishConnect", res);
        }
        return true;
    }

    public void bind(SocketAddress socketAddress) throws IOException {
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress addr = (InetSocketAddress) socketAddress;
            NativeInetAddress address = NativeInetAddress.newInstance(addr.getAddress());
            int res = bind(intValue(), address.address, address.scopeId, addr.getPort());
            if (res < 0) {
                throw newIOException("bind", res);
            }
        } else if (socketAddress instanceof DomainSocketAddress) {
            DomainSocketAddress addr = (DomainSocketAddress) socketAddress;
            int res = bindDomainSocket(intValue(), addr.path().getBytes(CharsetUtil.UTF_8));
            if (res < 0) {
                throw newIOException("bind", res);
            }
        } else {
            throw new Error("Unexpected SocketAddress implementation " + socketAddress);
        }
    }

    public void listen(int backlog) throws IOException {
        int res = listen(intValue(), backlog);
        if (res < 0) {
            throw newIOException("listen", res);
        }
    }

    public int accept(byte[] addr) throws IOException {
        int res = accept(intValue(), addr);
        if (res >= 0) {
            return res;
        }
        if (res == ERRNO_EAGAIN_NEGATIVE || res == ERRNO_EWOULDBLOCK_NEGATIVE) {
            // Everything consumed so just return -1 here.
            return -1;
        }
        throw newIOException("accept", res);
    }

    public InetSocketAddress remoteAddress() {
        byte[] addr = remoteAddress(intValue());
        // addr may be null if getpeername failed.
        // See https://github.com/netty/netty/issues/3328
        if (addr == null) {
            return null;
        }
        return address(addr, 0, addr.length);
    }

    public InetSocketAddress localAddress() {
        byte[] addr = localAddress(intValue());
        // addr may be null if getpeername failed.
        // See https://github.com/netty/netty/issues/3328
        if (addr == null) {
            return null;
        }
        return address(addr, 0, addr.length);
    }

    public int getReceiveBufferSize() {
        return getReceiveBufferSize(intValue());
    }

    public int getSendBufferSize() {
        return getSendBufferSize(intValue());
    }

    public boolean isKeepAlive() {
        return isKeepAlive(intValue()) != 0;
    }

    public boolean isTcpNoDelay() {
        return isTcpNoDelay(intValue()) != 0;
    }

    public boolean isTcpCork() {
        return isTcpCork(intValue()) != 0;
    }

    public int getSoLinger() {
        return getSoLinger(intValue());
    }

    public int getSoError() {
        return getSoError(intValue());
    }

    public void setKeepAlive(boolean keepAlive) {
        setKeepAlive(intValue(), keepAlive ? 1 : 0);
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        setReceiveBufferSize(intValue(), receiveBufferSize);
    }

    public void setSendBufferSize(int sendBufferSize) {
        setSendBufferSize(intValue(), sendBufferSize);
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        setTcpNoDelay(intValue(), tcpNoDelay ? 1 : 0);
    }

    public void setTcpCork(boolean tcpCork) {
        setTcpCork(intValue(), tcpCork ? 1 : 0);
    }

    public void setSoLinger(int soLinger) {
        setSoLinger(intValue(), soLinger);
    }

    @Override
    public String toString() {
        return "Socket{" +
                "fd=" + intValue() +
                '}';
    }

    public static Socket newSocketStream() {
        int res = newSocketStreamFd();
        if (res < 0) {
            throw new ChannelException(newIOException("newSocketStream", res));
        }
        return new Socket(res);
    }

    public static Socket newSocketDgram() {
        int res = newSocketDgramFd();
        if (res < 0) {
            throw new ChannelException(newIOException("newSocketDgram", res));
        }
        return new Socket(res);
    }

    public static Socket newSocketDomain() {
        int res = newSocketDomainFd();
        if (res < 0) {
            throw new ChannelException(newIOException("newSocketDomain", res));
        }
        return new Socket(res);
    }

    private static native int shutdown(int fd, boolean read, boolean write);
    private static native int connect(int fd, byte[] address, int scopeId, int port);
    private static native int connectDomainSocket(int fd, byte[] path);
    private static native int finishConnect(int fd);
    private static native int bind(int fd, byte[] address, int scopeId, int port);
    private static native int bindDomainSocket(int fd, byte[] path);
    private static native int listen(int fd, int backlog);
    private static native int accept(int fd, byte[] addr);

    private static native byte[] remoteAddress(int fd);
    private static native byte[] localAddress(int fd);

    private static native int sendTo(
            int fd, ByteBuffer buf, int pos, int limit, byte[] address, int scopeId, int port);
    private static native int sendToAddress(
            int fd, long memoryAddress, int pos, int limit, byte[] address, int scopeId, int port);
    private static native int sendToAddresses(
            int fd, long memoryAddress, int length, byte[] address, int scopeId, int port);

    private static native DatagramSocketAddress recvFrom(
            int fd, ByteBuffer buf, int pos, int limit) throws IOException;
    private static native DatagramSocketAddress recvFromAddress(
            int fd, long memoryAddress, int pos, int limit) throws IOException;

    private static native int newSocketStreamFd();
    private static native int newSocketDgramFd();
    private static native int newSocketDomainFd();

    private static native int getReceiveBufferSize(int fd);
    private static native int getSendBufferSize(int fd);
    private static native int isKeepAlive(int fd);
    private static native int isTcpNoDelay(int fd);
    private static native int isTcpCork(int fd);
    private static native int getSoLinger(int fd);
    private static native int getSoError(int fd);

    private static native void setKeepAlive(int fd, int keepAlive);
    private static native void setReceiveBufferSize(int fd, int receiveBufferSize);
    private static native void setSendBufferSize(int fd, int sendBufferSize);
    private static native void setTcpNoDelay(int fd, int tcpNoDelay);
    private static native void setTcpCork(int fd, int tcpCork);
    private static native void setSoLinger(int fd, int soLinger);
}
