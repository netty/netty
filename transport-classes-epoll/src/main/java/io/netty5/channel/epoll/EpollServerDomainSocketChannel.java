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
package io.netty5.channel.epoll;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.channel.unix.Socket;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.channel.unix.UnixServerSocketChannel;
import io.netty5.util.NetUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;

import static io.netty5.channel.ChannelOption.SO_BACKLOG;
import static io.netty5.channel.ChannelOption.SO_RCVBUF;
import static io.netty5.channel.ChannelOption.SO_REUSEADDR;
import static io.netty5.channel.epoll.LinuxSocket.newSocketDomain;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * {@link UnixServerSocketChannel} implementation for Unix Domain Sockets that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link UnixServerSocketChannel},
 * {@link EpollServerDomainSocketChannel} allows the following options in the option map:
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th>
 * </tr><tr>
 * <td>{@link io.netty5.channel.unix.RawUnixChannelOption}</td>
 * </tr><tr>
 * <td>{@link io.netty5.channel.unix.IntegerUnixChannelOption}</td>
 * </tr>
 * </table>
 */
public final class EpollServerDomainSocketChannel
        extends AbstractEpollServerChannel<UnixChannel, DomainSocketAddress, DomainSocketAddress>
        implements UnixServerSocketChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(
            EpollServerDomainSocketChannel.class);

    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS = supportedOptions();
    private volatile int backlog = NetUtil.SOMAXCONN;
    private volatile DomainSocketAddress local;

    public EpollServerDomainSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        super(eventLoop, childEventLoopGroup, EpollDomainSocketChannel.class,  newSocketDomain(), false);
    }

    public EpollServerDomainSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup, int fd) {
        super(eventLoop, childEventLoopGroup, EpollDomainSocketChannel.class, fd);
    }

    @Override
    protected Channel newChildChannel(int fd, byte[] addr, int offset, int len) {
        return new EpollDomainSocketChannel(this, childEventLoopGroup().next(), new Socket(fd));
    }

    @Override
    protected DomainSocketAddress localAddress0() {
        return local;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        socket.bind(localAddress);
        socket.listen(getBacklog());
        local = (DomainSocketAddress) localAddress;
        active = true;
    }

    @Override
    protected void doClose() throws Exception {
        try {
            super.doClose();
        } finally {
            DomainSocketAddress local = this.local;
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
        return super.getExtendedOption(option);
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (option == SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else if (option == SO_REUSEADDR) {
            setReuseAddress((Boolean) value);
        } else if (option == SO_BACKLOG) {
            setBacklog((Integer) value);
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
}
