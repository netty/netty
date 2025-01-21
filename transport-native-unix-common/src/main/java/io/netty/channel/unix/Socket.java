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
import io.netty.channel.socket.InternetProtocolFamily;
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

import static io.netty.channel.unix.Errors.ERRNO_EAGAIN_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EINPROGRESS_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EWOULDBLOCK_NEGATIVE;
import static io.netty.channel.unix.Errors.ERROR_ECONNREFUSED_NEGATIVE;
import static io.netty.channel.unix.Errors.handleConnectErrno;
import static io.netty.channel.unix.Errors.ioResult;
import static io.netty.channel.unix.Errors.newIOException;
import static io.netty.channel.unix.NativeInetAddress.address;
import static io.netty.channel.unix.NativeInetAddress.ipv4MappedIpv6Address;

/**
 * Provides a JNI bridge to native socket operations.
 * <strong>Internal usage only!</strong>
 */
public class Socket extends FileDescriptor {

    private static volatile boolean isIpv6Preferred;

    @Deprecated
    public static final int UDS_SUN_PATH_SIZE = 100;

    protected final boolean ipv6;

    public Socket(int fd, boolean ipv6) {
        super(fd);
        this.ipv6 = ipv6;
    }

    public Socket(int fd) {
        this(fd, isIPv6(fd));
    }

    /**
     * Returns {@code true} if we should use IPv6 internally, {@code false} otherwise.
     */
    private boolean useIpv6(InetAddress address) {
        return useIpv6(this, address);
    }

    /**
     * Returns {@code true} if the given socket and address combination should use IPv6 internally,
     * {@code false} otherwise.
     */
    protected static boolean useIpv6(Socket socket, InetAddress address) {
        return socket.ipv6 || address instanceof Inet6Address;
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
            final int oldState = state;
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
        return sendTo(buf, pos, limit, addr, port, false);
    }

    public final int sendTo(ByteBuffer buf, int pos, int limit, InetAddress addr, int port, boolean fastOpen)
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
        int flags = fastOpen ? msgFastopen() : 0;
        int res = sendTo(fd, useIpv6(addr), buf, pos, limit, address, scopeId, port, flags);
        if (res >= 0) {
            return res;
        }
        if (res == ERRNO_EINPROGRESS_NEGATIVE && fastOpen) {
            // This happens when we (as a client) have no pre-existing cookie for doing a fast-open connection.
            // In this case, our TCP connection will be established normally, but no data was transmitted at this time.
            // We'll just transmit the data with normal writes later.
            return 0;
        }
        if (res == ERROR_ECONNREFUSED_NEGATIVE) {
            throw new PortUnreachableException("sendTo failed");
        }
        return ioResult("sendTo", res);
    }

    public final int sendToDomainSocket(ByteBuffer buf, int pos, int limit, byte[] path) throws IOException {
        int res = sendToDomainSocket(fd, buf, pos, limit, path);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendToDomainSocket", res);
    }

    public final int sendToAddress(long memoryAddress, int pos, int limit, InetAddress addr, int port)
            throws IOException {
        return sendToAddress(memoryAddress, pos, limit, addr, port, false);
    }

    public final int sendToAddress(long memoryAddress, int pos, int limit, InetAddress addr, int port,
                                   boolean fastOpen) throws IOException {
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
        int flags = fastOpen ? msgFastopen() : 0;
        int res = sendToAddress(fd, useIpv6(addr), memoryAddress, pos, limit, address, scopeId, port, flags);
        if (res >= 0) {
            return res;
        }
        if (res == ERRNO_EINPROGRESS_NEGATIVE && fastOpen) {
            // This happens when we (as a client) have no pre-existing cookie for doing a fast-open connection.
            // In this case, our TCP connection will be established normally, but no data was transmitted at this time.
            // We'll just transmit the data with normal writes later.
            return 0;
        }
        if (res == ERROR_ECONNREFUSED_NEGATIVE) {
            throw new PortUnreachableException("sendToAddress failed");
        }
        return ioResult("sendToAddress", res);
    }

    public final int sendToAddressDomainSocket(long memoryAddress, int pos, int limit, byte[] path) throws IOException {
        int res = sendToAddressDomainSocket(fd, memoryAddress, pos, limit, path);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendToAddressDomainSocket", res);
    }

    public final int sendToAddresses(long memoryAddress, int length, InetAddress addr, int port) throws IOException {
        return sendToAddresses(memoryAddress, length, addr, port, false);
    }

    public final int sendToAddresses(long memoryAddress, int length, InetAddress addr, int port, boolean fastOpen)
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
        int flags = fastOpen ? msgFastopen() : 0;
        int res = sendToAddresses(fd, useIpv6(addr), memoryAddress, length, address, scopeId, port, flags);
        if (res >= 0) {
            return res;
        }
        if (res == ERRNO_EINPROGRESS_NEGATIVE && fastOpen) {
            // This happens when we (as a client) have no pre-existing cookie for doing a fast-open connection.
            // In this case, our TCP connection will be established normally, but no data was transmitted at this time.
            // We'll just transmit the data with normal writes later.
            return 0;
        }
        if (res == ERROR_ECONNREFUSED_NEGATIVE) {
            throw new PortUnreachableException("sendToAddresses failed");
        }
        return ioResult("sendToAddresses", res);
    }

    public final int sendToAddressesDomainSocket(long memoryAddress, int length, byte[] path) throws IOException {
        int res = sendToAddressesDomainSocket(fd, memoryAddress, length, path);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendToAddressesDomainSocket", res);
    }

    public final DatagramSocketAddress recvFrom(ByteBuffer buf, int pos, int limit) throws IOException {
        return recvFrom(fd, buf, pos, limit);
    }

    public final DatagramSocketAddress recvFromAddress(long memoryAddress, int pos, int limit) throws IOException {
        return recvFromAddress(fd, memoryAddress, pos, limit);
    }

    public final DomainDatagramSocketAddress recvFromDomainSocket(ByteBuffer buf, int pos, int limit)
            throws IOException {
        return recvFromDomainSocket(fd, buf, pos, limit);
    }

    public final DomainDatagramSocketAddress recvFromAddressDomainSocket(long memoryAddress, int pos, int limit)
            throws IOException {
        return recvFromAddressDomainSocket(fd, memoryAddress, pos, limit);
    }

    public int recv(ByteBuffer buf, int pos, int limit) throws IOException {
        int res = recv(intValue(), buf, pos, limit);
        if (res > 0) {
            return res;
        }
        if (res == 0) {
            return -1;
        }
        return ioResult("recv", res);
    }

    public int recvAddress(long address, int pos, int limit) throws IOException {
        int res = recvAddress(intValue(), address, pos, limit);
        if (res > 0) {
            return res;
        }
        if (res == 0) {
            return -1;
        }
        return ioResult("recvAddress", res);
    }

    public int send(ByteBuffer buf, int pos, int limit) throws IOException {
        int res = send(intValue(), buf, pos, limit);
        if (res >= 0) {
            return res;
        }
        return ioResult("send", res);
    }

    public int sendAddress(long address, int pos, int limit) throws IOException {
        int res = sendAddress(intValue(), address, pos, limit);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendAddress", res);
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
            return handleConnectErrno("connect", res);
        }
        return true;
    }

    public final boolean finishConnect() throws IOException {
        int res = finishConnect(fd);
        if (res < 0) {
            return handleConnectErrno("finishConnect", res);
        }
        return true;
    }

    public final void disconnect() throws IOException {
        int res = disconnect(fd, ipv6);
        if (res < 0) {
            handleConnectErrno("disconnect", res);
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

    public final DomainSocketAddress remoteDomainSocketAddress() {
        byte[] addr = remoteDomainSocketAddress(fd);
        return addr == null ? null : new DomainSocketAddress(new String(addr));
    }

    public final InetSocketAddress localAddress() {
        byte[] addr = localAddress(fd);
        // addr may be null if getpeername failed.
        // See https://github.com/netty/netty/issues/3328
        return addr == null ? null : address(addr, 0, addr.length);
    }

    public final DomainSocketAddress localDomainSocketAddress() {
        byte[] addr = localDomainSocketAddress(fd);
        return addr == null ? null : new DomainSocketAddress(new String(addr));
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

    public void setIntOpt(int level, int optname, int optvalue) throws IOException {
        setIntOpt(fd, level, optname, optvalue);
    }

    public void setRawOpt(int level, int optname, ByteBuffer optvalue) throws IOException {
        int limit = optvalue.limit();
        if (optvalue.isDirect()) {
            setRawOptAddress(fd, level, optname,
                    Buffer.memoryAddress(optvalue) + optvalue.position(), optvalue.remaining());
        } else if (optvalue.hasArray()) {
            setRawOptArray(fd, level, optname,
                    optvalue.array(), optvalue.arrayOffset() + optvalue.position(), optvalue.remaining());
        } else {
            byte[] bytes = new byte[optvalue.remaining()];
            optvalue.duplicate().get(bytes);
            setRawOptArray(fd, level, optname, bytes, 0, bytes.length);
        }
        optvalue.position(limit);
    }

    public int getIntOpt(int level, int optname) throws IOException {
        return getIntOpt(fd, level, optname);
    }

    public void getRawOpt(int level, int optname, ByteBuffer out) throws IOException {
        if (out.isDirect()) {
            getRawOptAddress(fd, level, optname, Buffer.memoryAddress(out) + out.position() , out.remaining());
        } else if (out.hasArray()) {
            getRawOptArray(fd, level, optname, out.array(), out.position() + out.arrayOffset(), out.remaining());
        } else {
            byte[] outArray = new byte[out.remaining()];
            getRawOptArray(fd, level, optname, outArray, 0, outArray.length);
            out.put(outArray);
        }
        out.position(out.limit());
    }

    public static boolean isIPv6Preferred() {
        return isIpv6Preferred;
    }

    public static boolean shouldUseIpv6(InternetProtocolFamily family) {
        return family == null ? isIPv6Preferred() :
                        family == InternetProtocolFamily.IPv6;
    }

    private static native boolean isIPv6Preferred0(boolean ipv4Preferred);

    private static native boolean isIPv6(int fd);

    @Override
    public String toString() {
        return "Socket{" +
                "fd=" + fd +
                '}';
    }

    public static Socket newSocketStream() {
        return new Socket(newSocketStream0());
    }

    public static Socket newSocketDgram() {
        return new Socket(newSocketDgram0());
    }

    public static Socket newSocketDomain() {
        return new Socket(newSocketDomain0());
    }

    public static Socket newSocketDomainDgram() {
        return new Socket(newSocketDomainDgram0());
    }

    public static void initialize() {
        isIpv6Preferred = isIPv6Preferred0(NetUtil.isIpV4StackPreferred());
    }

    protected static int newSocketStream0() {
        return newSocketStream0(isIPv6Preferred());
    }

    protected static int newSocketStream0(InternetProtocolFamily protocol) {
        return newSocketStream0(shouldUseIpv6(protocol));
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

    protected static int newSocketDgram0(InternetProtocolFamily family) {
        return newSocketDgram0(shouldUseIpv6(family));
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

    protected static int newSocketDomainDgram0() {
        int res = newSocketDomainDgramFd();
        if (res < 0) {
            throw new ChannelException(newIOException("newSocketDomainDgram", res));
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
    private static native byte[] remoteDomainSocketAddress(int fd);
    private static native byte[] localAddress(int fd);
    private static native byte[] localDomainSocketAddress(int fd);

    private static native int send(int fd, ByteBuffer buf, int pos, int limit);
    private static native int sendAddress(int fd, long address, int pos, int limit);
    private static native int recv(int fd, ByteBuffer buf, int pos, int limit);

    private static native int recvAddress(int fd, long address, int pos, int limit);

    private static native int sendTo(
            int fd, boolean ipv6, ByteBuffer buf, int pos, int limit, byte[] address, int scopeId, int port,
            int flags);

    private static native int sendToAddress(
            int fd, boolean ipv6, long memoryAddress, int pos, int limit, byte[] address, int scopeId, int port,
            int flags);

    private static native int sendToAddresses(
            int fd, boolean ipv6, long memoryAddress, int length, byte[] address, int scopeId, int port,
            int flags);

    private static native int sendToDomainSocket(int fd, ByteBuffer buf, int pos, int limit, byte[] path);
    private static native int sendToAddressDomainSocket(int fd, long memoryAddress, int pos, int limit, byte[] path);
    private static native int sendToAddressesDomainSocket(int fd, long memoryAddress, int length, byte[] path);

    private static native DatagramSocketAddress recvFrom(
            int fd, ByteBuffer buf, int pos, int limit) throws IOException;
    private static native DatagramSocketAddress recvFromAddress(
            int fd, long memoryAddress, int pos, int limit) throws IOException;
    private static native DomainDatagramSocketAddress recvFromDomainSocket(
            int fd, ByteBuffer buf, int pos, int limit) throws IOException;
    private static native DomainDatagramSocketAddress recvFromAddressDomainSocket(
            int fd, long memoryAddress, int pos, int limit) throws IOException;
    private static native int recvFd(int fd);
    private static native int sendFd(int socketFd, int fd);
    private static native int msgFastopen();

    private static native int newSocketStreamFd(boolean ipv6);
    private static native int newSocketDgramFd(boolean ipv6);
    private static native int newSocketDomainFd();
    private static native int newSocketDomainDgramFd();

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

    private static native void setIntOpt(int fd, int level, int optname, int optvalue) throws IOException;
    private static native void setRawOptArray(int fd, int level, int optname, byte[] optvalue, int offset, int length)
            throws IOException;
    private static native void setRawOptAddress(int fd, int level, int optname, long optvalueMemoryAddress, int length)
            throws IOException;
    private static native int getIntOpt(int fd, int level, int optname) throws IOException;
    private static native void getRawOptArray(int fd, int level, int optname, byte[] out, int offset, int length)
            throws IOException;
    private static native void getRawOptAddress(int fd, int level, int optname, long outMemoryAddress, int length)
            throws IOException;
}
