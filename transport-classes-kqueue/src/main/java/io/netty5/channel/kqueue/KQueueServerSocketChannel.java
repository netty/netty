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
package io.netty5.channel.kqueue;

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
import io.netty5.util.NetUtil;
import io.netty5.util.internal.UnstableApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.util.Set;

import static io.netty5.channel.ChannelOption.SO_BACKLOG;
import static io.netty5.channel.ChannelOption.SO_RCVBUF;
import static io.netty5.channel.ChannelOption.SO_REUSEADDR;
import static io.netty5.channel.ChannelOption.TCP_FASTOPEN;
import static io.netty5.channel.kqueue.BsdSocket.newSocketStream;
import static io.netty5.channel.kqueue.KQueueChannelOption.SO_ACCEPTFILTER;
import static io.netty5.channel.unix.NativeInetAddress.address;
import static io.netty5.channel.unix.UnixChannelOption.SO_REUSEPORT;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * {@link ServerSocketChannel} implementation that uses KQueue.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ServerSocketChannel} and {@link UnixChannel},
 * {@link KQueueServerSocketChannel} allows the following options in the option map:
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
 * <td>{@link ChannelOption#TCP_FASTOPEN}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link KQueueChannelOption#SO_ACCEPTFILTER}</td><td>X</td><td>X</td><td>X</td>
 * </tr><tr>
 * <td>{@link io.netty5.channel.unix.UnixChannelOption#SO_REUSEPORT}</td><td>X</td><td>X</td><td>-</td>
 * </tr>
 * </table>
 */
@UnstableApi
public final class KQueueServerSocketChannel extends
        AbstractKQueueChannel<UnixChannel> implements ServerSocketChannel {
    private static final Logger logger = LoggerFactory.getLogger(KQueueServerSocketChannel.class);

    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS = supportedOptions();

    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS_DOMAIN = supportedOptionsDomainSocket();

    private final EventLoopGroup childEventLoopGroup;

    // Will hold the remote address after accept(...) was successful.
    // We need 24 bytes for the address as maximum + 1 byte for storing the capacity.
    // So use 26 bytes as it's a power of two.
    private final byte[] acceptedAddress = new byte[26];

    private volatile int backlog = NetUtil.SOMAXCONN;
    private volatile boolean enableTcpFastOpen;

    public KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        super(null, eventLoop, false, new ServerChannelReadHandleFactory(), new ServerChannelWriteHandleFactory(),
                newSocketStream(), false);
        this.childEventLoopGroup = validateEventLoopGroup(childEventLoopGroup, "childEventLoopGroup",
                KQueueIoHandle.class);
    }

    public KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                                     ProtocolFamily protocolFamily) {
        super(null, eventLoop, false, new ServerChannelReadHandleFactory(), new ServerChannelWriteHandleFactory(),
                BsdSocket.newSocket(protocolFamily), false);
        this.childEventLoopGroup = validateEventLoopGroup(childEventLoopGroup, "childEventLoopGroup",
                KQueueIoHandle.class);
    }

    public KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                                     int fd, ProtocolFamily protocolFamily) {
        // Must call this constructor to ensure this object's local address is configured correctly.
        // The local address can only be obtained from a Socket object.
        this(eventLoop, childEventLoopGroup, new BsdSocket(fd, SocketProtocolFamily.of(protocolFamily)));
    }

    private KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, BsdSocket socket) {
        // Must call this constructor to ensure this object's local address is configured correctly.
        // The local address can only be obtained from a Socket object.
        super(null, eventLoop, false, new ServerChannelReadHandleFactory(), new ServerChannelWriteHandleFactory(),
                socket, isSoErrorZero(socket));
        this.childEventLoopGroup = validateEventLoopGroup(childEventLoopGroup, "childEventLoopGroup",
                KQueueIoHandle.class);
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        socket.listen(getBacklog());
        if (socket.protocolFamily() != SocketProtocolFamily.UNIX && isTcpFastOpen()) {
            socket.setTcpFastOpen(true);
        }
        active = true;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (isSupported(socket.protocolFamily(), option)) {
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
                return (T) (isTcpFastOpen() ? Integer.valueOf(1) : Integer.valueOf(0));
            }
            if (option == SO_REUSEPORT) {
                return (T) Boolean.valueOf(isReusePort());
            }
            if (option == SO_ACCEPTFILTER) {
                return (T) getAcceptFilter();
            }
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected  <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (isSupported(socket.protocolFamily(), option)) {
            if (option == SO_RCVBUF) {
                setReceiveBufferSize((Integer) value);
            } else if (option == SO_REUSEADDR) {
                setReuseAddress((Boolean) value);
            } else if (option == SO_BACKLOG) {
                setBacklog((Integer) value);
            } else if (option == TCP_FASTOPEN) {
                setTcpFastOpen((Integer) value > 0);
            } else if (option == SO_REUSEPORT) {
                setReusePort((Boolean) value);
            } else if (option == SO_ACCEPTFILTER) {
                setAcceptFilter((AcceptFilter) value);
            }
        } else {
            super.setExtendedOption(option, value);
        }
    }

    private boolean isSupported(SocketProtocolFamily protocolFamily, ChannelOption<?> option) {
        if (protocolFamily == SocketProtocolFamily.UNIX) {
            return SUPPORTED_OPTIONS_DOMAIN.contains(option);
        }
        return SUPPORTED_OPTIONS.contains(option);
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        return isSupported(socket.protocolFamily(), option) || super.isExtendedOptionSupported(option);
    }

    private static Set<ChannelOption<?>> supportedOptions() {
        return newSupportedIdentityOptionsSet(SO_RCVBUF, SO_REUSEADDR, SO_BACKLOG,
                TCP_FASTOPEN, SO_REUSEPORT, SO_ACCEPTFILTER);
    }

    private static Set<ChannelOption<?>> supportedOptionsDomainSocket() {
        return newSupportedIdentityOptionsSet(SO_RCVBUF, SO_REUSEADDR, SO_BACKLOG, SO_ACCEPTFILTER);
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

    /**
     * Returns {@code true} if TCP FastOpen is enabled.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7413#appendix-A.2">RFC 7413 Passive Open</a>
     */
    private boolean isTcpFastOpen() {
        return enableTcpFastOpen;
    }

    /**
     * Enables TCP FastOpen on the server channel. If the underlying os doesn't support TCP_FASTOPEN setting this has no
     * effect. This has to be set before doing listen on the socket otherwise this takes no effect.
     *
     * @param enableTcpFastOpen {@code true} if TCP FastOpen should be enabled for incomming connections.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7413#appendix-A.2">RFC 7413 Passive Open</a>
     */
    private void setTcpFastOpen(boolean enableTcpFastOpen) {
        this.enableTcpFastOpen = enableTcpFastOpen;
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

    private void setAcceptFilter(AcceptFilter acceptFilter) {
        try {
            socket.setAcceptFilter(acceptFilter);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private AcceptFilter getAcceptFilter() {
        try {
            return socket.getAcceptFilter();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private Channel newChildChannel(int fd, byte[] address, int offset, int len) throws Exception {
        final SocketAddress remote;
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            remote = null;
        } else {
            remote = address(address, offset, len);
        }
        return new KQueueSocketChannel(this, childEventLoopGroup().next(),
                new BsdSocket(fd, socket.protocolFamily()), remote);
    }

    @Override
    protected void doClose() throws Exception {
        SocketAddress local = localAddress();
        try {
            super.doClose();
        } finally {
            if (local != null && socket.protocolFamily() == SocketProtocolFamily.UNIX) {
                String path = ((DomainSocketAddress) local).path();
                // Delete the socket file if possible.
                File socketFile = new File(path);
                boolean success = socketFile.delete();
                if (!success && logger.isDebugEnabled()) {
                    logger.debug("Failed to delete a domain socket file: {}", path);
                }
            }
        }
    }

    @Override
    public EventLoopGroup childEventLoopGroup() {
        return childEventLoopGroup;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
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
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData) {
        throw new UnsupportedOperationException();
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
    int readReady(ReadSink readSink) throws Exception {
        int acceptFd = socket.accept(acceptedAddress);
        if (acceptFd == -1) {
            // this means everything was handled for now
            readSink.processRead(0, 0, null);
            return 0;
        }
        readSink.processRead(0, 0,
                newChildChannel(acceptFd, acceptedAddress, 1, acceptedAddress[0]));
        return 1;
    }
}
