/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.local;

import io.netty.buffer.BufType;
import io.netty.buffer.MessageBuf;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.concurrent.SingleThreadEventExecutor;

import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.util.Collections;

/**
 * A {@link Channel} for the local transport.
 */
public class LocalChannel extends AbstractChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(BufType.MESSAGE, false);

    private static final int MAX_READER_STACK_DEPTH = 8;
    private static final ThreadLocal<Integer> READER_STACK_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final Runnable readTask = new Runnable() {
        @Override
        public void run() {
            ChannelPipeline pipeline = pipeline();
            pipeline.fireInboundBufferUpdated();
            pipeline.fireChannelReadSuspended();
        }
    };

    private final Runnable shutdownHook = new Runnable() {
        @Override
        public void run() {
            unsafe().close(unsafe().voidPromise());
        }
    };

    private volatile int state; // 0 - open, 1 - bound, 2 - connected, 3 - closed
    private volatile LocalChannel peer;
    private volatile LocalAddress localAddress;
    private volatile LocalAddress remoteAddress;
    private volatile ChannelPromise connectPromise;
    private volatile boolean readInProgress;

    public LocalChannel() {
        this(null);
    }

    public LocalChannel(Integer id) {
        super(null, id);
    }

    LocalChannel(LocalServerChannel parent, LocalChannel peer) {
        super(parent, null);
        this.peer = peer;
        localAddress = parent.localAddress();
        remoteAddress = peer.localAddress();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public LocalServerChannel parent() {
        return (LocalServerChannel) super.parent();
    }

    @Override
    public LocalAddress localAddress() {
        return (LocalAddress) super.localAddress();
    }

    @Override
    public LocalAddress remoteAddress() {
        return (LocalAddress) super.remoteAddress();
    }

    @Override
    public boolean isOpen() {
        return state < 3;
    }

    @Override
    public boolean isActive() {
        return state == 2;
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new LocalUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof SingleThreadEventLoop;
    }

    @Override
    protected SocketAddress localAddress0() {
        return localAddress;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remoteAddress;
    }

    @Override
    protected Runnable doRegister() throws Exception {
        final LocalChannel peer = this.peer;
        Runnable postRegisterTask;

        if (peer != null) {
            state = 2;

            peer.remoteAddress = parent().localAddress();
            peer.state = 2;

            // Ensure the peer's channelActive event is triggered *after* this channel's
            // channelRegistered event is triggered, so that this channel's pipeline is fully
            // initialized by ChannelInitializer.
            final EventLoop peerEventLoop = peer.eventLoop();
            postRegisterTask = new Runnable() {
                @Override
                public void run() {
                    peerEventLoop.execute(new Runnable() {
                        @Override
                        public void run() {
                            peer.connectPromise.setSuccess();
                            peer.pipeline().fireChannelActive();
                        }
                    });
                }
            };
        } else {
            postRegisterTask = null;
        }

        ((SingleThreadEventExecutor) eventLoop()).addShutdownHook(shutdownHook);

        return postRegisterTask;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        this.localAddress =
                LocalChannelRegistry.register(this, this.localAddress,
                        localAddress);
        state = 1;
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doPreClose() throws Exception {
        if (state > 2) {
            // Closed already
            return;
        }

        // Update all internal state before the closeFuture is notified.
        if (localAddress != null) {
            if (parent() == null) {
                LocalChannelRegistry.unregister(localAddress);
            }
            localAddress = null;
        }
        state = 3;
    }

    @Override
    protected void doClose() throws Exception {
        LocalChannel peer = this.peer;
        if (peer != null && peer.isActive()) {
            peer.unsafe().close(unsafe().voidPromise());
            this.peer = null;
        }
    }

    @Override
    protected Runnable doDeregister() throws Exception {
        if (isOpen()) {
            unsafe().close(unsafe().voidPromise());
        }
        ((SingleThreadEventExecutor) eventLoop()).removeShutdownHook(shutdownHook);
        return null;
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (readInProgress) {
            return;
        }

        ChannelPipeline pipeline = pipeline();
        MessageBuf<Object> buf = pipeline.inboundMessageBuffer();
        if (buf.isEmpty()) {
            readInProgress = true;
            return;
        }

        final Integer stackDepth = READER_STACK_DEPTH.get();
        if (stackDepth < MAX_READER_STACK_DEPTH) {
            READER_STACK_DEPTH.set(stackDepth + 1);
            try {
                pipeline.fireInboundBufferUpdated();
                pipeline.fireChannelReadSuspended();
            } finally {
                READER_STACK_DEPTH.set(stackDepth);
            }
        } else {
            eventLoop().execute(readTask);
        }
    }

    @Override
    protected void doFlushMessageBuffer(MessageBuf<Object> buf) throws Exception {
        if (state < 2) {
            throw new NotYetConnectedException();
        }
        if (state > 2) {
            throw new ClosedChannelException();
        }

        final LocalChannel peer = this.peer;
        final ChannelPipeline peerPipeline = peer.pipeline();
        final EventLoop peerLoop = peer.eventLoop();

        if (peerLoop == eventLoop()) {
            buf.drainTo(peerPipeline.inboundMessageBuffer());
            finishPeerRead(peer, peerPipeline);
        } else {
            final Object[] msgs = buf.toArray();
            buf.clear();
            peerLoop.execute(new Runnable() {
                @Override
                public void run() {
                    MessageBuf<Object> buf = peerPipeline.inboundMessageBuffer();
                    Collections.addAll(buf, msgs);
                    finishPeerRead(peer, peerPipeline);
                }
            });
        }
    }

    private static void finishPeerRead(LocalChannel peer, ChannelPipeline peerPipeline) {
        if (peer.readInProgress) {
            peer.readInProgress = false;
            peerPipeline.fireInboundBufferUpdated();
            peerPipeline.fireChannelReadSuspended();
        }
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    private class LocalUnsafe extends AbstractUnsafe {

        @Override
        public void connect(final SocketAddress remoteAddress,
                SocketAddress localAddress, final ChannelPromise promise) {
            if (!ensureOpen(promise)) {
                return;
            }

            if (state == 2) {
                Exception cause = new AlreadyConnectedException();
                promise.setFailure(cause);
                pipeline().fireExceptionCaught(cause);
                return;
            }

            if (connectPromise != null) {
                throw new ConnectionPendingException();
            }

            connectPromise = promise;

            if (state != 1) {
                // Not bound yet and no localAddress specified - get one.
                if (localAddress == null) {
                    localAddress = new LocalAddress(LocalChannel.this);
                }
            }

            if (localAddress != null) {
                try {
                    doBind(localAddress);
                } catch (Throwable t) {
                    promise.setFailure(t);
                    pipeline().fireExceptionCaught(t);
                    close(voidPromise());
                    return;
                }
            }

            Channel boundChannel = LocalChannelRegistry.get(remoteAddress);
            if (!(boundChannel instanceof LocalServerChannel)) {
                Exception cause = new ChannelException("connection refused");
                promise.setFailure(cause);
                close(voidPromise());
                return;
            }

            LocalServerChannel serverChannel = (LocalServerChannel) boundChannel;
            peer = serverChannel.serve(LocalChannel.this);
        }
    }
}
