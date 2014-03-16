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

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.EventLoop;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.UnresolvedAddressException;

abstract class AbstractEpollChannel extends AbstractChannel {
    private static final ChannelMetadata DATA = new ChannelMetadata(false);
    private final int readFlag;
    protected int flags;
    protected volatile boolean active;
    volatile int fd;
    int id;

    AbstractEpollChannel(int flag) {
        this(null, socketFd(), flag, false);
    }

    AbstractEpollChannel(Channel parent, int fd, int flag, boolean active) {
        super(parent);
        this.fd = fd;
        readFlag = flag;
        flags |= flag;
        this.active = active;
    }

    private static int socketFd() {
        try {
            return Native.socket();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public ChannelMetadata metadata() {
        return DATA;
    }

    @Override
    protected void doClose() throws Exception {
        active = false;

        // deregister from epoll now
        doDeregister();

        int fd = this.fd;
        this.fd = -1;
        Native.close(fd);
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
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof EpollEventLoop;
    }

    @Override
    public boolean isOpen() {
        return fd != -1;
    }

    @Override
    protected void doDeregister() throws Exception {
        ((EpollEventLoop) eventLoop()).remove(this);
    }

    @Override
    protected void doBeginRead() throws Exception {
        if ((flags & readFlag) == 0) {
            flags |= readFlag;
            ((EpollEventLoop) eventLoop()).modify(this);
        }
    }

    protected final void clearEpollIn() {
        if ((flags & readFlag) != 0) {
            flags &= ~readFlag;
            ((EpollEventLoop) eventLoop()).modify(this);
        }
    }

    @Override
    protected void doRegister() throws Exception {
        EpollEventLoop loop = (EpollEventLoop) eventLoop();
        loop.add(this);
    }

    @Override
    protected abstract AbstractEpollUnsafe newUnsafe();

    protected static void checkResolvable(InetSocketAddress addr) {
        if (addr.isUnresolved()) {
            throw new UnresolvedAddressException();
        }
    }

    protected abstract class AbstractEpollUnsafe extends AbstractUnsafe {
        protected boolean readPending;

        /**
         * Called once EPOLLIN event is ready to be processed
         */
        abstract void epollInReady();

        /**
         * Called once EPOLLRDHUP event is ready to be processed
         */
        void epollRdHupReady() {
            // NOOP
        }

        @Override
        public void beginRead() {
            // Channel.read() or ChannelHandlerContext.read() was called
            readPending = true;
            super.beginRead();
        }

        @Override
        protected void flush0() {
            // Flush immediately only when there's no pending flush.
            // If there's a pending flush operation, event loop will call forceFlush() later,
            // and thus there's no need to call it now.
            if (isFlushPending()) {
                return;
            }
            super.flush0();
        }

        /**
         * Called once a EPOLLOUT event is ready to be processed
         */
        void epollOutReady() {
            // directly call super.flush0() to force a flush now
            super.flush0();
        }

        private boolean isFlushPending() {
            return (flags & Native.EPOLLOUT) != 0;
        }
    }
}
