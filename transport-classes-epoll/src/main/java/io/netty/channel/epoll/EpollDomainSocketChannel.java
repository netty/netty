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
package io.netty.channel.epoll;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.PeerCredentials;

import java.io.IOException;
import java.net.SocketAddress;

import static io.netty.channel.epoll.LinuxSocket.newSocketDomain;

public final class EpollDomainSocketChannel extends AbstractEpollStreamChannel implements DomainSocketChannel {
    private final EpollDomainSocketChannelConfig config = new EpollDomainSocketChannelConfig(this);

    private volatile DomainSocketAddress local;
    private volatile DomainSocketAddress remote;

    public EpollDomainSocketChannel() {
        super(newSocketDomain(), false);
    }

    EpollDomainSocketChannel(Channel parent, FileDescriptor fd) {
        this(parent, new LinuxSocket(fd.intValue()));
    }

    public EpollDomainSocketChannel(int fd) {
        super(fd);
    }

    public EpollDomainSocketChannel(Channel parent, LinuxSocket fd) {
        super(parent, fd);
        local = fd.localDomainSocketAddress();
        remote = fd.remoteDomainSocketAddress();
    }

    public EpollDomainSocketChannel(int fd, boolean active) {
        super(new LinuxSocket(fd), active);
    }

    @Override
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollDomainUnsafe();
    }

    @Override
    protected DomainSocketAddress localAddress0() {
        return local;
    }

    @Override
    protected DomainSocketAddress remoteAddress0() {
        return remote;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        socket.bind(localAddress);
        local = (DomainSocketAddress) localAddress;
    }

    @Override
    public EpollDomainSocketChannelConfig config() {
        return config;
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (super.doConnect(remoteAddress, localAddress)) {
            local = localAddress != null ? (DomainSocketAddress) localAddress : socket.localDomainSocketAddress();
            remote = (DomainSocketAddress) remoteAddress;
            return true;
        }
        return false;
    }

    @Override
    public DomainSocketAddress remoteAddress() {
        return (DomainSocketAddress) super.remoteAddress();
    }

    @Override
    public DomainSocketAddress localAddress() {
        return (DomainSocketAddress) super.localAddress();
    }

    @Override
    protected int doWriteSingle(ChannelOutboundBuffer in) throws Exception {
        Object msg = in.current();
        if (msg instanceof FileDescriptor && socket.sendFd(((FileDescriptor) msg).intValue()) > 0) {
            // File descriptor was written, so remove it.
            in.remove();
            return 1;
        }
        return super.doWriteSingle(in);
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof FileDescriptor) {
            return msg;
        }
        return super.filterOutboundMessage(msg);
    }

    /**
     * Returns the unix credentials (uid, gid, pid) of the peer
     * <a href=https://man7.org/linux/man-pages/man7/socket.7.html>SO_PEERCRED</a>
     */
    public PeerCredentials peerCredentials() throws IOException {
        return socket.getPeerCredentials();
    }

    private final class EpollDomainUnsafe extends EpollStreamUnsafe {
        @Override
        void epollInReady() {
            switch (config().getReadMode()) {
                case BYTES:
                    super.epollInReady();
                    break;
                case FILE_DESCRIPTORS:
                    epollInReadFd();
                    break;
                default:
                    throw new Error();
            }
        }

        private void epollInReadFd() {
            if (socket.isInputShutdown()) {
                clearEpollIn0();
                return;
            }
            final ChannelConfig config = config();
            final EpollRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();

            final ChannelPipeline pipeline = pipeline();
            allocHandle.reset(config);

            try {
                readLoop: do {
                    // lastBytesRead represents the fd. We use lastBytesRead because it must be set so that the
                    // EpollRecvByteAllocatorHandle knows if it should try to read again or not when autoRead is
                    // enabled.
                    allocHandle.lastBytesRead(socket.recvFd());
                    switch(allocHandle.lastBytesRead()) {
                    case 0:
                        break readLoop;
                    case -1:
                        close(voidPromise());
                        return;
                    default:
                        allocHandle.incMessagesRead(1);
                        readPending = false;
                        pipeline.fireChannelRead(new FileDescriptor(allocHandle.lastBytesRead()));
                        break;
                    }
                } while (allocHandle.continueReading());

                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();
            } catch (Throwable t) {
                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();
                pipeline.fireExceptionCaught(t);
            } finally {
                if (shouldStopReading(config)) {
                    clearEpollIn();
                }
            }
        }
    }
}
