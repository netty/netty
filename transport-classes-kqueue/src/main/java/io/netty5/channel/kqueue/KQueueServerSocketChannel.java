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

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.socket.ServerSocketChannel;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.util.NetUtil;
import io.netty5.util.internal.UnstableApi;

import java.io.IOException;
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
 * <th>Name</th>
 * </tr><tr>
 * <td>{@link ChannelOption#TCP_FASTOPEN}</td>
 * </tr><tr>
 * <td>{@link KQueueChannelOption#RCV_ALLOC_TRANSPORT_PROVIDES_GUESS}</td>
 * </tr><tr>
 * <td>{@link KQueueChannelOption#SO_ACCEPTFILTER}</td>
 * </tr><tr>
 * <td>{@link io.netty5.channel.unix.UnixChannelOption#SO_REUSEPORT}</td>
 * </tr>
 * </table>
 */
@UnstableApi
public final class KQueueServerSocketChannel extends
        AbstractKQueueServerChannel<UnixChannel, SocketAddress, SocketAddress> implements ServerSocketChannel {
    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS = supportedOptions();
    private volatile int backlog = NetUtil.SOMAXCONN;
    private volatile boolean enableTcpFastOpen;
    public KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        super(eventLoop, childEventLoopGroup, KQueueSocketChannel.class, newSocketStream(), false);
    }

    public KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, int fd) {
        // Must call this constructor to ensure this object's local address is configured correctly.
        // The local address can only be obtained from a Socket object.
        this(eventLoop, childEventLoopGroup, new BsdSocket(fd));
    }

    KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, BsdSocket fd) {
        super(eventLoop, childEventLoopGroup, KQueueSocketChannel.class, fd);
    }

    KQueueServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, BsdSocket fd, boolean active) {
        super(eventLoop, childEventLoopGroup, KQueueSocketChannel.class, fd, active);
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        socket.listen(getBacklog());
        if (isTcpFastOpen()) {
            socket.setTcpFastOpen(true);
        }
        active = true;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
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
        return super.getExtendedOption(option);
    }

    @Override
    protected  <T> void setExtendedOption(ChannelOption<T> option, T value) {
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
        } else {
            super.setExtendedOption(option, value);
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        if (SUPPORTED_OPTIONS.contains(option)) {
            return true;
        }
        return super.isExtendedOptionSupported(option);
    }

    private static Set<ChannelOption<?>> supportedOptions() {
        return newSupportedIdentityOptionsSet(SO_RCVBUF, SO_REUSEADDR, SO_BACKLOG,
                TCP_FASTOPEN, SO_REUSEPORT, SO_ACCEPTFILTER);
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

    @Override
    protected Channel newChildChannel(int fd, byte[] address, int offset, int len) throws Exception {
        return new KQueueSocketChannel(this, childEventLoopGroup().next(),
                                       new BsdSocket(fd), address(address, offset, len));
    }
}
