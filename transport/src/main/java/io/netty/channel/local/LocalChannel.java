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

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.FastThreadLocal;

import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;

/**
 * A {@link Channel} for the local transport.
 */
public class LocalChannel extends AbstractChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private static final int MAX_READER_STACK_DEPTH = 8;
    private static final ThreadLocal<Integer> READER_STACK_DEPTH = new FastThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final Queue<Object> inboundBuffer = new ArrayDeque<Object>();
    private final Runnable readTask = new Runnable() {
        @Override
        public void run() {
            ChannelPipeline pipeline = pipeline();
            for (;;) {
                Object m = inboundBuffer.poll();
                if (m == null) {
                    break;
                }
                pipeline.fireChannelRead(m);
            }
            pipeline.fireChannelReadComplete();
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
    private volatile boolean registerInProgress;

    public LocalChannel() {
        super(null);
    }

    LocalChannel(LocalServerChannel parent, LocalChannel peer) {
        super(parent);
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
    protected void doRegister() throws Exception {
        // Check if both peer and parent are non-null because this channel was created by a LocalServerChannel.
        // This is needed as a peer may not be null also if a LocalChannel was connected before and
        // deregistered / registered later again.
        //
        // See https://github.com/netty/netty/issues/2400
        if (peer != null && parent() != null) {
            // Store the peer in a local variable as it may be set to null if doClose() is called.
            // Because of this we also set registerInProgress to true as we check for this in doClose() and make sure
            // we delay the fireChannelInactive() to be fired after the fireChannelActive() and so keep the correct
            // order of events.
            //
            // See https://github.com/netty/netty/issues/2144
            final LocalChannel peer = this.peer;
            registerInProgress = true;
            state = 2;

            peer.remoteAddress = parent().localAddress();
            peer.state = 2;

            // Always call peer.eventLoop().execute() even if peer.eventLoop().inEventLoop() is true.
            // This ensures that if both channels are on the same event loop, the peer's channelActive
            // event is triggered *after* this channel's channelRegistered event, so that this channel's
            // pipeline is fully initialized by ChannelInitializer before any channelRead events.
            peer.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    registerInProgress = false;
                    peer.pipeline().fireChannelActive();
                    peer.connectPromise.setSuccess();
                }
            });
        }
        ((SingleThreadEventExecutor) eventLoop()).addShutdownHook(shutdownHook);
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
    protected void doClose() throws Exception {
        if (state <= 2) {
            // Update all internal state before the closeFuture is notified.
            if (localAddress != null) {
                if (parent() == null) {
                    LocalChannelRegistry.unregister(localAddress);
                }
                localAddress = null;
            }
            state = 3;
        }

        final LocalChannel peer = this.peer;
        if (peer != null && peer.isActive()) {
            // Need to execute the close in the correct EventLoop
            // See https://github.com/netty/netty/issues/1777
            EventLoop eventLoop = peer.eventLoop();

            // Also check if the registration was not done yet. In this case we submit the close to the EventLoop
            // to make sure it is run after the registration completes.
            //
            // See https://github.com/netty/netty/issues/2144
            if (eventLoop.inEventLoop() && !registerInProgress) {
                peer.unsafe().close(unsafe().voidPromise());
            } else {
                peer.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        peer.unsafe().close(unsafe().voidPromise());
                    }
                });
            }
            this.peer = null;
        }
    }

    @Override
    protected void doDeregister() throws Exception {
        // Just remove the shutdownHook as this Channel may be closed later or registered to another EventLoop
        ((SingleThreadEventExecutor) eventLoop()).removeShutdownHook(shutdownHook);
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (readInProgress) {
            return;
        }

        ChannelPipeline pipeline = pipeline();
        Queue<Object> inboundBuffer = this.inboundBuffer;
        if (inboundBuffer.isEmpty()) {
            readInProgress = true;
            return;
        }

        final Integer stackDepth = READER_STACK_DEPTH.get();
        if (stackDepth < MAX_READER_STACK_DEPTH) {
            READER_STACK_DEPTH.set(stackDepth + 1);
            try {
                for (;;) {
                    Object received = inboundBuffer.poll();
                    if (received == null) {
                        break;
                    }
                    pipeline.fireChannelRead(received);
                }
                pipeline.fireChannelReadComplete();
            } finally {
                READER_STACK_DEPTH.set(stackDepth);
            }
        } else {
            eventLoop().execute(readTask);
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
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
            for (;;) {
                Object msg = in.current();
                if (msg == null) {
                    break;
                }
                peer.inboundBuffer.add(msg);
                ReferenceCountUtil.retain(msg);
                in.remove();
            }
            finishPeerRead(peer, peerPipeline);
        } else {
            // Use a copy because the original msgs will be recycled by AbstractChannel.
            final Object[] msgsCopy = new Object[in.size()];
            for (int i = 0; i < msgsCopy.length; i ++) {
                msgsCopy[i] = ReferenceCountUtil.retain(in.current());
                in.remove();
            }

            peerLoop.execute(new Runnable() {
                @Override
                public void run() {
                    Collections.addAll(peer.inboundBuffer, msgsCopy);
                    finishPeerRead(peer, peerPipeline);
                }
            });
        }
    }

    private static void finishPeerRead(LocalChannel peer, ChannelPipeline peerPipeline) {
        if (peer.readInProgress) {
            peer.readInProgress = false;
            for (;;) {
                Object received = peer.inboundBuffer.poll();
                if (received == null) {
                    break;
                }
                peerPipeline.fireChannelRead(received);
            }
            peerPipeline.fireChannelReadComplete();
        }
    }

    private class LocalUnsafe extends AbstractUnsafe {

        @Override
        public void connect(final SocketAddress remoteAddress,
                SocketAddress localAddress, final ChannelPromise promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            if (state == 2) {
                Exception cause = new AlreadyConnectedException();
                safeSetFailure(promise, cause);
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
                    safeSetFailure(promise, t);
                    close(voidPromise());
                    return;
                }
            }

            Channel boundChannel = LocalChannelRegistry.get(remoteAddress);
            if (!(boundChannel instanceof LocalServerChannel)) {
                Exception cause = new ChannelException("connection refused");
                safeSetFailure(promise, cause);
                close(voidPromise());
                return;
            }

            LocalServerChannel serverChannel = (LocalServerChannel) boundChannel;
            peer = serverChannel.serve(LocalChannel.this);
        }
    }
}
