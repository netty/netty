/*
 * Copyright 2014 The Netty Project
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.OneTimeTask;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executor;

/**
 * {@link SocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollSocketChannel extends AbstractEpollStreamChannel implements SocketChannel {

    private final EpollSocketChannelConfig config;

    private volatile InetSocketAddress local;
    private volatile InetSocketAddress remote;

    EpollSocketChannel(Channel parent, int fd, InetSocketAddress remote) {
        super(parent, fd);
        config = new EpollSocketChannelConfig(this);
        // Directly cache the remote and local addresses
        // See https://github.com/netty/netty/issues/2359
        this.remote = remote;
        local = Native.localAddress(fd);
    }

    public EpollSocketChannel() {
        super(Native.socketStreamFd());
        config = new EpollSocketChannelConfig(this);
    }

    /**
     * Creates a new {@link EpollSocketChannel} from an existing {@link FileDescriptor}.
     */
    public EpollSocketChannel(FileDescriptor fd) {
        super(fd);
        config = new EpollSocketChannelConfig(this);

        // As we create an EpollSocketChannel from a FileDescriptor we should try to obtain the remote and local
        // address from it. This is needed as the FileDescriptor may be bound/connected already.
        remote = Native.remoteAddress(fd.intValue());
        local = Native.localAddress(fd.intValue());
    }

    /**
     * Returns the {@code TCP_INFO} for the current socket. See <a href="http://linux.die.net/man/7/tcp">man 7 tcp</a>.
     */
    public EpollTcpInfo tcpInfo() {
        return tcpInfo(new EpollTcpInfo());
    }

    /**
     * Updates and returns the {@code TCP_INFO} for the current socket.
     * See <a href="http://linux.die.net/man/7/tcp">man 7 tcp</a>.
     */
    public EpollTcpInfo tcpInfo(EpollTcpInfo info) {
        Native.tcpInfo(fd().intValue(), info);
        return info;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    protected SocketAddress localAddress0() {
        return local;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        if (remote == null) {
            // Remote address not know, try to get it now.
            InetSocketAddress address = Native.remoteAddress(fd().intValue());
            if (address != null) {
                remote = address;
            }
            return address;
        }
        return remote;
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        InetSocketAddress localAddress = (InetSocketAddress) local;
        int fd = fd().intValue();
        Native.bind(fd, localAddress);
        this.local = Native.localAddress(fd);
    }

    @Override
    public EpollSocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isInputShutdown() {
        return isInputShutdown0();
    }

    @Override
    public boolean isOutputShutdown() {
        return isOutputShutdown0();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise promise) {
        Executor closeExecutor = ((EpollSocketChannelUnsafe) unsafe()).closeExecutor();
        if (closeExecutor != null) {
            closeExecutor.execute(new OneTimeTask() {
                @Override
                public void run() {
                    shutdownOutput0(promise);
                }
            });
        } else {
            EventLoop loop = eventLoop();
            if (loop.inEventLoop()) {
                shutdownOutput0(promise);
            } else {
                loop.execute(new OneTimeTask() {
                    @Override
                    public void run() {
                        shutdownOutput0(promise);
                    }
                });
            }
        }
        return promise;
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollSocketChannelUnsafe();
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            checkResolvable((InetSocketAddress) localAddress);
        }
        checkResolvable((InetSocketAddress) remoteAddress);
        int fd = fd().intValue();
        boolean connected = super.doConnect(remoteAddress, localAddress);
        if (connected) {
            remote = (InetSocketAddress) remoteAddress;
            return true;
        }
        // We always need to set the localAddress even if not connected yet
        //
        // See https://github.com/netty/netty/issues/3463
        local = Native.localAddress(fd);
        return connected;
    }

    private final class EpollSocketChannelUnsafe extends EpollStreamUnsafe {
        @Override
        protected Executor closeExecutor() {
            if (config().getSoLinger() > 0) {
                return GlobalEventExecutor.INSTANCE;
            }
            return null;
        }
    }
}
