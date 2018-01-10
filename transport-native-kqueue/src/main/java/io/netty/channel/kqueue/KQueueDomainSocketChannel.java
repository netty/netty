/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.PeerCredentials;
import io.netty.util.internal.UnstableApi;

import java.io.IOException;
import java.net.SocketAddress;

import static io.netty.channel.kqueue.BsdSocket.newSocketDomain;

@UnstableApi
public final class KQueueDomainSocketChannel extends AbstractKQueueStreamChannel implements DomainSocketChannel {
    private final KQueueDomainSocketChannelConfig config = new KQueueDomainSocketChannelConfig(this);

    private volatile DomainSocketAddress local;
    private volatile DomainSocketAddress remote;

    public KQueueDomainSocketChannel() {
        super(null, newSocketDomain(), false);
    }

    public KQueueDomainSocketChannel(int fd) {
        this(null, new BsdSocket(fd));
    }

    KQueueDomainSocketChannel(Channel parent, BsdSocket fd) {
        super(parent, fd, true);
    }

    @Override
    protected AbstractKQueueUnsafe newUnsafe() {
        return new KQueueDomainUnsafe();
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
    public KQueueDomainSocketChannelConfig config() {
        return config;
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (super.doConnect(remoteAddress, localAddress)) {
            local = (DomainSocketAddress) localAddress;
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
     * <a href=http://man7.org/linux/man-pages/man7/socket.7.html>SO_PEERCRED</a>
     */
    @UnstableApi
    public PeerCredentials peerCredentials() throws IOException {
        return socket.getPeerCredentials();
    }

    private final class KQueueDomainUnsafe extends KQueueStreamUnsafe {
        @Override
        void readReady(KQueueRecvByteAllocatorHandle allocHandle) {
            switch (config().getReadMode()) {
                case BYTES:
                    super.readReady(allocHandle);
                    break;
                case FILE_DESCRIPTORS:
                    readReadyFd();
                    break;
                default:
                    throw new Error();
            }
        }

        private void readReadyFd() {
            if (socket.isInputShutdown()) {
                super.clearReadFilter0();
                return;
            }
            final ChannelConfig config = config();
            final KQueueRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();

            final ChannelPipeline pipeline = pipeline();
            allocHandle.reset(config);
            readReadyBefore();

            try {
                readLoop: do {
                    // lastBytesRead represents the fd. We use lastBytesRead because it must be set so that the
                    // KQueueRecvByteAllocatorHandle knows if it should try to read again or not when autoRead is
                    // enabled.
                    int recvFd = socket.recvFd();
                    switch(recvFd) {
                        case 0:
                            allocHandle.lastBytesRead(0);
                            break readLoop;
                        case -1:
                            allocHandle.lastBytesRead(-1);
                            close(voidPromise());
                            return;
                        default:
                            allocHandle.lastBytesRead(1);
                            allocHandle.incMessagesRead(1);
                            readPending = false;
                            pipeline.fireChannelRead(new FileDescriptor(recvFd));
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
                readReadyFinally(config);
            }
        }
    }
}
