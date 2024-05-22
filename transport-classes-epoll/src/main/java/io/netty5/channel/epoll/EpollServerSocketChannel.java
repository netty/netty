/*
 * Copyright 2014 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.ServerChannelReadHandleFactory;
import io.netty5.channel.ServerChannelWriteHandleFactory;
import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.channel.socket.ServerSocketChannel;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.IntegerUnixChannelOption;
import io.netty5.channel.unix.RawUnixChannelOption;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.channel.unix.UnixChannelOption;
import io.netty5.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static io.netty5.channel.ChannelOption.SO_BACKLOG;
import static io.netty5.channel.ChannelOption.SO_RCVBUF;
import static io.netty5.channel.ChannelOption.SO_REUSEADDR;
import static io.netty5.channel.ChannelOption.TCP_FASTOPEN;
import static io.netty5.channel.epoll.Native.IS_SUPPORTING_TCP_FASTOPEN_SERVER;
import static io.netty5.channel.unix.NativeInetAddress.address;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * {@link ServerSocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ServerSocketChannel} and {@link UnixChannel},
 * {@link EpollServerSocketChannel} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>{@link ChannelOption}</th>
 * <th>{@code INET}</th>
 * <th>{@code INET6}</th>
 * <th>{@code UNIX}</th>
 * </tr><tr>
 * <td>{@link IntegerUnixChannelOption}</td><td>X</td><td>X</td><td>X</td>
 * </tr><tr>
 * <td>{@link RawUnixChannelOption}</td><td>X</td><td>X</td><td>X</td>
 * </tr><tr>
 * <td>{@link UnixChannelOption#SO_REUSEPORT}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#IP_FREEBIND}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#TCP_DEFER_ACCEPT}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link ChannelOption#TCP_FASTOPEN}</td><td>X</td><td>X</td><td>-</td>
 * </tr>
 * </table>
 */
public final class EpollServerSocketChannel
        extends AbstractEpollChannel<UnixChannel>
        implements ServerSocketChannel {

    private static final Logger logger = LoggerFactory.getLogger(EpollServerSocketChannel.class);
    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS = supportedOptions();
    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS_DOMAIN_SOCKET = supportedOptionsDomainSocket();

    private final EventLoopGroup childEventLoopGroup;
    // Will hold the remote address after accept(...) was successful.
    // We need 24 bytes for the address as maximum + 1 byte for storing the length.
    // So use 26 bytes as it's a power of two.
    private final byte[] acceptedAddress = new byte[26];

    private volatile int backlog = NetUtil.SOMAXCONN;
    private volatile int pendingFastOpenRequestsThreshold;

    private volatile Collection<InetAddress> tcpMd5SigAddresses = Collections.emptyList();

    public EpollServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        super(eventLoop, false, EpollIoOps.valueOf(0), new ServerChannelReadHandleFactory(),
                new ServerChannelWriteHandleFactory(), LinuxSocket.newSocketStream());
        this.childEventLoopGroup = validateEventLoopGroup(
                childEventLoopGroup, "childEventLoopGroup", EpollIoHandle.class);
    }

    public EpollServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                                    ProtocolFamily protocolFamily) {
        super(null, eventLoop, false, EpollIoOps.valueOf(0), new ServerChannelReadHandleFactory(),
                new ServerChannelWriteHandleFactory(), LinuxSocket.newSocket(protocolFamily), false);
        this.childEventLoopGroup = validateEventLoopGroup(
                childEventLoopGroup, "childEventLoopGroup", EpollIoHandle.class);
    }

    public EpollServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, int fd,
                                    ProtocolFamily protocolFamily) {
        this(eventLoop, childEventLoopGroup, new LinuxSocket(fd, SocketProtocolFamily.of(protocolFamily)));
    }

    private EpollServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, LinuxSocket socket) {
        // Must call this constructor to ensure this object's local address is configured correctly.
        // The local address can only be obtained from a Socket object.
        super(null, eventLoop, false, EpollIoOps.valueOf(0), new ServerChannelReadHandleFactory(),
                new ServerChannelWriteHandleFactory(), socket, isSoErrorZero(socket));
        this.childEventLoopGroup = validateEventLoopGroup(childEventLoopGroup, "childEventLoopGroup",
                EpollIoHandle.class);
    }

    @Override
    public EventLoopGroup childEventLoopGroup() {
        return childEventLoopGroup;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (isOptionSupported(socket.protocolFamily(), option)) {
            if (option == SO_RCVBUF) {
                return (T) Integer.valueOf(getReceiveBufferSize());
            }
            if (option == SO_REUSEADDR) {
                return (T) Boolean.valueOf(isReuseAddress());
            }
            if (option == SO_BACKLOG) {
                return (T) Integer.valueOf(getBacklog());
            }
            if (option == TCP_FASTOPEN) {
                return (T) Integer.valueOf(getTcpFastopen());
            }
            if (option == EpollChannelOption.IP_FREEBIND) {
                return (T) Boolean.valueOf(isIpFreebind());
            }
            if (option == EpollChannelOption.TCP_DEFER_ACCEPT) {
                return (T) Integer.valueOf(getTcpDeferAccept());
            }
            if (option == UnixChannelOption.SO_REUSEPORT) {
                return (T) Boolean.valueOf(isReusePort());
            }
            if (option == EpollChannelOption.TCP_MD5SIG) {
                return null;
            }
        }

        return super.getExtendedOption(option);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (isOptionSupported(socket.protocolFamily(), option)) {
            if (option == SO_RCVBUF) {
                setReceiveBufferSize((Integer) value);
            } else if (option == SO_REUSEADDR) {
                setReuseAddress((Boolean) value);
            } else if (option == SO_BACKLOG) {
                setBacklog((Integer) value);
            } else if (option == TCP_FASTOPEN) {
                setTcpFastopen((Integer) value);
            } else if (option == EpollChannelOption.IP_FREEBIND) {
                setIpFreebind((Boolean) value);
            } else if (option == EpollChannelOption.TCP_DEFER_ACCEPT) {
                setTcpDeferAccept((Integer) value);
            } else if (option == UnixChannelOption.SO_REUSEPORT) {
                setReusePort((Boolean) value);
            } else if (option == EpollChannelOption.TCP_MD5SIG) {
                setTcpMd5Sig((Map<InetAddress, byte[]>) value);
            }
        } else {
            super.setExtendedOption(option, value);
        }
    }

    private static boolean isOptionSupported(SocketProtocolFamily family, ChannelOption<?> option) {
        if (family == SocketProtocolFamily.UNIX) {
            return SUPPORTED_OPTIONS_DOMAIN_SOCKET.contains(option);
        }
        return SUPPORTED_OPTIONS.contains(option);
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        return isOptionSupported(socket.protocolFamily(), option) || super.isExtendedOptionSupported(option);
    }

    private static Set<ChannelOption<?>> supportedOptions() {
        return newSupportedIdentityOptionsSet(SO_RCVBUF, SO_REUSEADDR, SO_BACKLOG, TCP_FASTOPEN,
                EpollChannelOption.TCP_MD5SIG, EpollChannelOption.SO_REUSEPORT, EpollChannelOption.IP_FREEBIND,
                EpollChannelOption.TCP_DEFER_ACCEPT);
    }

    private static Set<ChannelOption<?>> supportedOptionsDomainSocket() {
        return newSupportedIdentityOptionsSet(SO_RCVBUF, SO_REUSEADDR, SO_BACKLOG);
    }

    private boolean isReuseAddress() {
        try {
            return socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReuseAddress(boolean reuseAddress) {
        try {
            socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReusePort(boolean reusePort) {
        try {
            socket.setReusePort(reusePort);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isReusePort() {
        try {
            return socket.isReusePort();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setIpFreebind(boolean reusePort) {
        try {
            socket.setIpFreeBind(reusePort);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isIpFreebind() {
        try {
            return socket.isIpFreeBind();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getReceiveBufferSize() {
        try {
            return socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReceiveBufferSize(int receiveBufferSize) {
        try {
            socket.setReceiveBufferSize(receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getBacklog() {
        return backlog;
    }

    private void setBacklog(int backlog) {
        checkPositiveOrZero(backlog, "backlog");
        this.backlog = backlog;
    }

    private int getTcpDeferAccept() {
        try {
            return socket.getTcpDeferAccept();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTcpDeferAccept(int deferAccept) {
        try {
            socket.setTcpDeferAccept(deferAccept);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns threshold value of number of pending for fast open connect.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7413#appendix-A.2">RFC 7413 Passive Open</a>
     */
    private int getTcpFastopen() {
        return pendingFastOpenRequestsThreshold;
    }

    /**
     * Enables tcpFastOpen on the server channel. If the underlying os doesn't support TCP_FASTOPEN setting this has no
     * effect. This has to be set before doing listen on the socket otherwise this takes no effect.
     *
     * @param pendingFastOpenRequestsThreshold number of requests to be pending for fastopen at a given point in time
     * for security.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7413#appendix-A.2">RFC 7413 Passive Open</a>
     */
    private void setTcpFastopen(int pendingFastOpenRequestsThreshold) {
        checkPositiveOrZero(pendingFastOpenRequestsThreshold, "pendingFastOpenRequestsThreshold");
        this.pendingFastOpenRequestsThreshold = pendingFastOpenRequestsThreshold;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        final int tcpFastopen;
        if (socket.protocolFamily() != SocketProtocolFamily.UNIX &&
                IS_SUPPORTING_TCP_FASTOPEN_SERVER && (tcpFastopen = getTcpFastopen()) > 0) {
            socket.setTcpFastOpen(tcpFastopen);
        }
        socket.listen(getBacklog());
        active = true;
    }

    @Override
    protected void doWriteNow(WriteSink writeHandle) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ReadState epollInReady(ReadSink readSink) throws Exception {
        int acceptedFd = socket.accept(acceptedAddress);
        if (acceptedFd == -1) {
            readSink.processRead(0, 0, null);
            // this means everything was handled for now
            return ReadState.All;
        }
        readSink.processRead(0, 0,
                newChildChannel(acceptedFd, acceptedAddress, 1, acceptedAddress[0]));
        return ReadState.Partial;
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        return !isActive();
    }

    @Override
    protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) {
        // Connect not supported by ServerChannel implementations
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData) {
        throw new UnsupportedOperationException();
    }

    private Channel newChildChannel(int fd, byte[] address, int offset, int len) {
        final SocketAddress remote;
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            remote = null;
        } else {
            remote = address(address, offset, len);
        }
        return new EpollSocketChannel(this, childEventLoopGroup().next(),
                new LinuxSocket(fd, socket.protocolFamily()), remote);
    }

    @Override
    protected void doClose() throws Exception {
        try {
            super.doClose();
        } finally {
            if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
                DomainSocketAddress local = (DomainSocketAddress) localAddress();
                if (local != null) {
                    // Delete the socket file if possible.
                    File socketFile = new File(local.path());
                    boolean success = socketFile.delete();
                    if (!success && logger.isDebugEnabled()) {
                        logger.debug("Failed to delete a domain socket file: {}", local.path());
                    }
                }
            }
        }
    }

    Collection<InetAddress> tcpMd5SigAddresses() {
        return tcpMd5SigAddresses;
    }

    private void setTcpMd5Sig(Map<InetAddress, byte[]> keys) {
        // Add synchronized as newTcpMp5Sigs might do multiple operations on the socket itself.
        synchronized (this) {
            try {
                tcpMd5SigAddresses = TcpMd5Util.newTcpMd5Sigs(this, tcpMd5SigAddresses, keys);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
    }
}
