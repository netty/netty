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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.unix.IovArray;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;

import static io.netty.channel.epoll.LinuxSocket.newSocketStream;
import static io.netty.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;

/**
 * {@link SocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 */
public final class EpollSocketChannel extends AbstractEpollStreamChannel implements SocketChannel {

    private final EpollSocketChannelConfig config;

    private volatile Collection<InetAddress> tcpMd5SigAddresses = Collections.emptyList();
    private boolean pendingFastOpenWrite;

    public EpollSocketChannel() {
        super(newSocketStream(), false);
        config = new EpollSocketChannelConfig(this);
    }

    public EpollSocketChannel(int fd) {
        super(fd);
        config = new EpollSocketChannelConfig(this);
    }

    EpollSocketChannel(LinuxSocket fd, boolean active) {
        super(fd, active);
        config = new EpollSocketChannelConfig(this);
    }

    EpollSocketChannel(Channel parent, LinuxSocket fd, InetSocketAddress remoteAddress) {
        super(parent, fd, remoteAddress);
        config = new EpollSocketChannelConfig(this);

        if (parent instanceof EpollServerSocketChannel) {
            tcpMd5SigAddresses = ((EpollServerSocketChannel) parent).tcpMd5SigAddresses();
        }
    }

    /**
     * Returns the {@code TCP_INFO} for the current socket.
     * See <a href="https://linux.die.net//man/7/tcp">man 7 tcp</a>.
     */
    public EpollTcpInfo tcpInfo() {
        return tcpInfo(new EpollTcpInfo());
    }

    /**
     * Updates and returns the {@code TCP_INFO} for the current socket.
     * See <a href="https://linux.die.net//man/7/tcp">man 7 tcp</a>.
     */
    public EpollTcpInfo tcpInfo(EpollTcpInfo info) {
        try {
            socket.getTcpInfo(info);
            return info;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
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
    public EpollSocketChannelConfig config() {
        return config;
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
    boolean doConnect0(SocketAddress remote) throws Exception {
        if (Native.IS_SUPPORTING_TCP_FASTOPEN && config.isTcpFastOpenConnect()) {
            pendingFastOpenWrite = true;
            return true;
        }
        return super.doConnect0(remote);
    }

    private long doWriteBytesFastOpen(ByteBuf data) throws Exception {
        final long writtenBytes;
        if (data.hasMemoryAddress()) {
            long memoryAddress = data.memoryAddress();
            writtenBytes = socket.sendToAddress(memoryAddress, data.readerIndex(), data.writerIndex(),
                                                remoteAddress().getAddress(), remoteAddress().getPort(), true);
        } else if (data.nioBufferCount() > 1) {
            IovArray array = ((EpollEventLoop) eventLoop()).cleanIovArray();
            array.add(data, data.readerIndex(), data.readableBytes());
            int cnt = array.count();
            assert cnt != 0;

            writtenBytes = socket.sendToAddresses(array.memoryAddress(0), cnt, remoteAddress().getAddress(),
                                                  remoteAddress().getPort(), true);
        } else {
            ByteBuffer nioData = data.internalNioBuffer(data.readerIndex(), data.readableBytes());
            writtenBytes = socket.sendTo(nioData, nioData.position(), nioData.limit(), remoteAddress().getAddress(),
                                         remoteAddress().getPort(), true);
        }

        return writtenBytes;
    }

    @Override
    int writeBytes(ChannelOutboundBuffer in, ByteBuf buf) throws Exception {
        if (buf.isReadable() && pendingFastOpenWrite) {
            long localFlushedAmount = doWriteBytesFastOpen(buf);
            if (localFlushedAmount > 0) {
                pendingFastOpenWrite = false;
                in.removeBytes(localFlushedAmount);
                return 1;
            }
            return WRITE_STATUS_SNDBUF_FULL;
        }

        return super.writeBytes(in, buf);
    }

    private final class EpollSocketChannelUnsafe extends EpollStreamUnsafe {
        @Override
        protected Executor prepareToClose() {
            try {
                // Check isOpen() first as otherwise it will throw a RuntimeException
                // when call getSoLinger() as the fd is not valid anymore.
                if (isOpen() && config().getSoLinger() > 0) {
                    // We need to cancel this key of the channel so we may not end up in a eventloop spin
                    // because we try to read or write until the actual close happens which may be later due
                    // SO_LINGER handling.
                    // See https://github.com/netty/netty/issues/4449
                    ((EpollEventLoop) eventLoop()).remove(EpollSocketChannel.this);
                    return GlobalEventExecutor.INSTANCE;
                }
            } catch (Throwable ignore) {
                // Ignore the error as the underlying channel may be closed in the meantime and so
                // getSoLinger() may produce an exception. In this case we just return null.
                // See https://github.com/netty/netty/issues/4449
            }
            return null;
        }
    }

    void setTcpMd5Sig(Map<InetAddress, byte[]> keys) throws IOException {
        tcpMd5SigAddresses = TcpMd5Util.newTcpMd5Sigs(this, tcpMd5SigAddresses, keys);
    }
}
