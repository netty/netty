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
import io.netty.channel.unix.Socket;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.OneTimeTask;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;

import static io.netty.channel.unix.Socket.newSocketStream;

/**
 * {@link SocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollSocketChannel extends AbstractEpollStreamChannel implements SocketChannel {

    private final EpollSocketChannelConfig config;

    private volatile InetSocketAddress local;
    private volatile InetSocketAddress remote;
    private volatile Collection<InetAddress> tcpMd5SigAddresses = Collections.emptyList();

    EpollSocketChannel(Channel parent, Socket fd, InetSocketAddress remote) {
        super(parent, fd);
        config = new EpollSocketChannelConfig(this);
        // Directly cache the remote and local addresses
        // See https://github.com/netty/netty/issues/2359
        this.remote = remote;
        local = fd.localAddress();

        if (parent instanceof EpollServerSocketChannel) {
            tcpMd5SigAddresses = ((EpollServerSocketChannel) parent).tcpMd5SigAddresses();
        }
    }

    public EpollSocketChannel() {
        super(newSocketStream(), false);
        config = new EpollSocketChannelConfig(this);
    }

    /**
     * @deprecated Use {@link #EpollSocketChannel(Socket, boolean)}.
     */
    @Deprecated
    public EpollSocketChannel(FileDescriptor fd) {
        super(fd);
        // As we create an EpollSocketChannel from a FileDescriptor we should try to obtain the remote and local
        // address from it. This is needed as the FileDescriptor may be bound/connected already.
        remote = fd().remoteAddress();
        local = fd().localAddress();
        config = new EpollSocketChannelConfig(this);
    }

    /**
     * Creates a new {@link EpollSocketChannel} from an existing {@link FileDescriptor}.
     */
    public EpollSocketChannel(Socket fd, boolean active) {
        super(fd, active);
        // As we create an EpollSocketChannel from a FileDescriptor we should try to obtain the remote and local
        // address from it. This is needed as the FileDescriptor may be bound/connected already.
        remote = fd.remoteAddress();
        local = fd.localAddress();
        config = new EpollSocketChannelConfig(this);
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
            InetSocketAddress address = fd().remoteAddress();
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
        fd().bind(localAddress);
        this.local = fd().localAddress();
    }

    @Override
    public EpollSocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isInputShutdown() {
        return fd().isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return fd().isOutputShutdown();
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
        boolean connected = super.doConnect(remoteAddress, localAddress);
        if (connected) {
            remote = (InetSocketAddress) remoteAddress;
            return true;
        }
        // We always need to set the localAddress even if not connected yet
        //
        // See https://github.com/netty/netty/issues/3463
        local = fd().localAddress();
        return connected;
    }

    private final class EpollSocketChannelUnsafe extends EpollStreamUnsafe {
        @Override
        protected Executor closeExecutor() {
            // Check isOpen() first as otherwise it will throw a RuntimeException
            // when call getSoLinger() as the fd is not valid anymore.
            if (isOpen() && config().getSoLinger() > 0) {
                return GlobalEventExecutor.INSTANCE;
            }
            return null;
        }
    }

    void setTcpMd5Sig(Map<InetAddress, byte[]> keys) {
        this.tcpMd5SigAddresses = TcpMd5Util.newTcpMd5Sigs(this, tcpMd5SigAddresses, keys);
    }
}
