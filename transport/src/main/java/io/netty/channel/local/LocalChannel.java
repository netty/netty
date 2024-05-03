/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.local;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.IoEvent;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoRegistration;
import io.netty.channel.PreferHeapByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.ConnectException;
import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A {@link Channel} for the local transport.
 */
public class LocalChannel extends AbstractChannel {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(LocalChannel.class);
    @SuppressWarnings({ "rawtypes" })
    private static final AtomicReferenceFieldUpdater<LocalChannel, Future> FINISH_READ_FUTURE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(LocalChannel.class, Future.class, "finishReadFuture");
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private static final int MAX_READER_STACK_DEPTH = 8;

    private enum State { OPEN, BOUND, CONNECTED, CLOSED }

    private final ChannelConfig config = new DefaultChannelConfig(this);
    // To further optimize this we could write our own SPSC queue.
    final Queue<Object> inboundBuffer = PlatformDependent.newSpscQueue();
    private final Runnable readTask = new Runnable() {
        @Override
        public void run() {
            // Ensure the inboundBuffer is not empty as readInbound() will always call fireChannelReadComplete()
            if (!inboundBuffer.isEmpty()) {
                readInbound();
            }
        }
    };

    private final Runnable shutdownHook = new Runnable() {
        @Override
        public void run() {
            unsafe().close(unsafe().voidPromise());
        }
    };

    private IoRegistration registration;

    private volatile State state;
    private volatile LocalChannel peer;
    private volatile LocalAddress localAddress;
    private volatile LocalAddress remoteAddress;
    private volatile ChannelPromise connectPromise;
    private volatile boolean readInProgress;
    private volatile boolean writeInProgress;
    private volatile Future<?> finishReadFuture;

    public LocalChannel() {
        super(null);
        config().setAllocator(new PreferHeapByteBufAllocator(config.getAllocator()));
    }

    protected LocalChannel(LocalServerChannel parent, LocalChannel peer) {
        super(parent);
        config().setAllocator(new PreferHeapByteBufAllocator(config.getAllocator()));
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
        return state != State.CLOSED;
    }

    @Override
    public boolean isActive() {
        return state == State.CONNECTED;
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new LocalUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof SingleThreadEventLoop ||
                (loop instanceof IoEventLoop && ((IoEventLoop) loop).isCompatible(LocalUnsafe.class));
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
    protected void doRegister(ChannelPromise promise) {
        EventLoop loop = eventLoop();
        if (loop instanceof IoEventLoop) {
            assert registration == null;
            ((IoEventLoop) loop).register((LocalUnsafe) unsafe()).addListener(f -> {
               if (f.isSuccess()) {
                   registration = (IoRegistration) f.getNow();
                   promise.setSuccess();
               } else {
                   promise.setFailure(f.cause());
               }
            });
        } else {
            try {
                ((LocalUnsafe) unsafe()).registerNow();
            } catch (Throwable cause) {
                promise.setFailure(cause);
            }
            promise.setSuccess();
        }
    }

    @Override
    protected void doDeregister() throws Exception {
        EventLoop loop = eventLoop();
        if (loop instanceof IoEventLoop) {
            IoRegistration registration = this.registration;
            if (registration != null) {
                this.registration = null;
                registration.cancel();
            }
        } else {
            ((LocalUnsafe) unsafe()).deregisterNow();
        }
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        this.localAddress =
                LocalChannelRegistry.register(this, this.localAddress,
                        localAddress);
        state = State.BOUND;
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        final LocalChannel peer = this.peer;
        State oldState = state;
        try {
            if (oldState != State.CLOSED) {
                // Update all internal state before the closeFuture is notified.
                if (localAddress != null) {
                    if (parent() == null) {
                        LocalChannelRegistry.unregister(localAddress);
                    }
                    localAddress = null;
                }

                // State change must happen before finishPeerRead to ensure writes are released either in doWrite or
                // channelRead.
                state = State.CLOSED;

                // Preserve order of event and force a read operation now before the close operation is processed.
                if (writeInProgress && peer != null) {
                    finishPeerRead(peer);
                }

                ChannelPromise promise = connectPromise;
                if (promise != null) {
                    // Use tryFailure() instead of setFailure() to avoid the race against cancel().
                    promise.tryFailure(new ClosedChannelException());
                    connectPromise = null;
                }
            }

            if (peer != null) {
                this.peer = null;
                // Always call peer.eventLoop().execute() even if peer.eventLoop().inEventLoop() is true.
                // This ensures that if both channels are on the same event loop, the peer's channelInActive
                // event is triggered *after* this peer's channelInActive event
                EventLoop peerEventLoop = peer.eventLoop();
                final boolean peerIsActive = peer.isActive();
                try {
                    peerEventLoop.execute(new Runnable() {
                        @Override
                        public void run() {
                            peer.tryClose(peerIsActive);
                        }
                    });
                } catch (Throwable cause) {
                    logger.warn("Releasing Inbound Queues for channels {}-{} because exception occurred!",
                            this, peer, cause);
                    if (peerEventLoop.inEventLoop()) {
                        peer.releaseInboundBuffers();
                    } else {
                        // inboundBuffers is a SPSC so we may leak if the event loop is shutdown prematurely or
                        // rejects the close Runnable but give a best effort.
                        peer.close();
                    }
                    PlatformDependent.throwException(cause);
                }
            }
        } finally {
            // Release all buffers if the Channel was already registered in the past and if it was not closed before.
            if (oldState != null && oldState != State.CLOSED) {
                // We need to release all the buffers that may be put into our inbound queue since we closed the Channel
                // to ensure we not leak any memory. This is fine as it basically gives the same guarantees as TCP which
                // means even if the promise was notified before its not really guaranteed that the "remote peer" will
                // see the buffer at all.
                releaseInboundBuffers();
            }
        }
    }

    private void tryClose(boolean isActive) {
        if (isActive) {
            unsafe().close(unsafe().voidPromise());
        } else {
            releaseInboundBuffers();
        }
    }

    private void readInbound() {
        RecvByteBufAllocator.Handle handle = unsafe().recvBufAllocHandle();
        handle.reset(config());
        ChannelPipeline pipeline = pipeline();
        do {
            Object received = inboundBuffer.poll();
            if (received == null) {
                break;
            }
            if (received instanceof ByteBuf && inboundBuffer.peek() instanceof ByteBuf) {
                ByteBuf msg = (ByteBuf) received;
                ByteBuf output = handle.allocate(alloc());
                if (msg.readableBytes() < output.writableBytes()) {
                    // We have an opportunity to coalesce buffers.
                    output.writeBytes(msg, msg.readerIndex(), msg.readableBytes());
                    msg.release();
                    while ((received = inboundBuffer.peek()) instanceof ByteBuf &&
                            ((ByteBuf) received).readableBytes() < output.writableBytes()) {
                        inboundBuffer.poll();
                        msg = (ByteBuf) received;
                        output.writeBytes(msg, msg.readerIndex(), msg.readableBytes());
                        msg.release();
                    }
                    handle.lastBytesRead(output.readableBytes());
                    received = output; // Send the coalesced buffer down the pipeline.
                } else {
                    // It won't be profitable to coalesce buffers this time around.
                    handle.lastBytesRead(output.capacity());
                    output.release();
                }
            }
            handle.incMessagesRead(1);
            pipeline.fireChannelRead(received);
        } while (handle.continueReading());
        handle.readComplete();
        pipeline.fireChannelReadComplete();
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (readInProgress) {
            return;
        }

        Queue<Object> inboundBuffer = this.inboundBuffer;
        if (inboundBuffer.isEmpty()) {
            readInProgress = true;
            return;
        }

        final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();
        final int stackDepth = threadLocals.localChannelReaderStackDepth();
        if (stackDepth < MAX_READER_STACK_DEPTH) {
            threadLocals.setLocalChannelReaderStackDepth(stackDepth + 1);
            try {
                readInbound();
            } finally {
                threadLocals.setLocalChannelReaderStackDepth(stackDepth);
            }
        } else {
            try {
                eventLoop().execute(readTask);
            } catch (Throwable cause) {
                logger.warn("Closing Local channels {}-{} because exception occurred!", this, peer, cause);
                close();
                peer.close();
                PlatformDependent.throwException(cause);
            }
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        switch (state) {
        case OPEN:
        case BOUND:
            throw new NotYetConnectedException();
        case CLOSED:
            throw new ClosedChannelException();
        case CONNECTED:
            break;
        }

        final LocalChannel peer = this.peer;

        writeInProgress = true;
        try {
            ClosedChannelException exception = null;
            for (;;) {
                Object msg = in.current();
                if (msg == null) {
                    break;
                }
                try {
                    // It is possible the peer could have closed while we are writing, and in this case we should
                    // simulate real socket behavior and ensure the write operation is failed.
                    if (peer.state == State.CONNECTED) {
                        peer.inboundBuffer.add(ReferenceCountUtil.retain(msg));
                        in.remove();
                    } else {
                        if (exception == null) {
                            exception = new ClosedChannelException();
                        }
                        in.remove(exception);
                    }
                } catch (Throwable cause) {
                    in.remove(cause);
                }
            }
        } finally {
            // The following situation may cause trouble:
            // 1. Write (with promise X)
            // 2. promise X is completed when in.remove() is called, and a listener on this promise calls close()
            // 3. Then the close event will be executed for the peer before the write events, when the write events
            // actually happened before the close event.
            writeInProgress = false;
        }

        finishPeerRead(peer);
    }

    private void finishPeerRead(final LocalChannel peer) {
        // If the peer is also writing, then we must schedule the event on the event loop to preserve read order.
        if (peer.eventLoop() == eventLoop() && !peer.writeInProgress) {
            finishPeerRead0(peer);
        } else {
            runFinishPeerReadTask(peer);
        }
    }

    private void runFinishPeerReadTask(final LocalChannel peer) {
        // If the peer is writing, we must wait until after reads are completed for that peer before we can read. So
        // we keep track of the task, and coordinate later that our read can't happen until the peer is done.
        final Runnable finishPeerReadTask = new Runnable() {
            @Override
            public void run() {
                finishPeerRead0(peer);
            }
        };
        try {
            if (peer.writeInProgress) {
                peer.finishReadFuture = peer.eventLoop().submit(finishPeerReadTask);
            } else {
                peer.eventLoop().execute(finishPeerReadTask);
            }
        } catch (Throwable cause) {
            logger.warn("Closing Local channels {}-{} because exception occurred!", this, peer, cause);
            close();
            peer.close();
            PlatformDependent.throwException(cause);
        }
    }

    private void releaseInboundBuffers() {
        assert eventLoop() == null || eventLoop().inEventLoop();
        readInProgress = false;
        Queue<Object> inboundBuffer = this.inboundBuffer;
        Object msg;
        while ((msg = inboundBuffer.poll()) != null) {
            ReferenceCountUtil.release(msg);
        }
    }

    private void finishPeerRead0(LocalChannel peer) {
        Future<?> peerFinishReadFuture = peer.finishReadFuture;
        if (peerFinishReadFuture != null) {
            if (!peerFinishReadFuture.isDone()) {
                runFinishPeerReadTask(peer);
                return;
            } else {
                // Lazy unset to make sure we don't prematurely unset it while scheduling a new task.
                FINISH_READ_FUTURE_UPDATER.compareAndSet(peer, peerFinishReadFuture, null);
            }
        }
        // We should only set readInProgress to false if there is any data that was read as otherwise we may miss to
        // forward data later on.
        if (peer.readInProgress && !peer.inboundBuffer.isEmpty()) {
            peer.readInProgress = false;
            peer.readInbound();
        }
    }

    private class LocalUnsafe extends AbstractUnsafe implements LocalIoHandle {

        @Override
        public void close() {
            close(voidPromise());
        }

        @Override
        public void handle(IoRegistration registration, IoEvent event) {
            // NOOP
        }

        @Override
        public void registerNow() {
            // Check if both peer and parent are non-null because this channel was created by a LocalServerChannel.
            // This is needed as a peer may not be null also if a LocalChannel was connected before and
            // deregistered / registered later again.
            //
            // See https://github.com/netty/netty/issues/2400
            if (peer != null && parent() != null) {
                // Store the peer in a local variable as it may be set to null if doClose() is called.
                // See https://github.com/netty/netty/issues/2144
                final LocalChannel peer = LocalChannel.this.peer;
                state = State.CONNECTED;

                peer.remoteAddress = parent() == null ? null : parent().localAddress();
                peer.state = State.CONNECTED;

                // Always call peer.eventLoop().execute() even if peer.eventLoop().inEventLoop() is true.
                // This ensures that if both channels are on the same event loop, the peer's channelActive
                // event is triggered *after* this channel's channelRegistered event, so that this channel's
                // pipeline is fully initialized by ChannelInitializer before any channelRead events.
                peer.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        ChannelPromise promise = peer.connectPromise;

                        // Only trigger fireChannelActive() if the promise was not null and was not completed yet.
                        // connectPromise may be set to null if doClose() was called in the meantime.
                        if (promise != null && promise.trySuccess()) {
                            peer.pipeline().fireChannelActive();
                        }
                    }
                });
            }
            ((SingleThreadEventExecutor) eventLoop()).addShutdownHook(shutdownHook);
        }

        @Override
        public void deregisterNow() {
            // Just remove the shutdownHook as this Channel may be closed later or registered to another EventLoop
            ((SingleThreadEventExecutor) eventLoop()).removeShutdownHook(shutdownHook);
        }

        @Override
        public void closeNow() {
            close(voidPromise());
        }

        @Override
        public void connect(final SocketAddress remoteAddress,
                SocketAddress localAddress, final ChannelPromise promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            if (state == State.CONNECTED) {
                Exception cause = new AlreadyConnectedException();
                safeSetFailure(promise, cause);
                pipeline().fireExceptionCaught(cause);
                return;
            }

            if (connectPromise != null) {
                throw new ConnectionPendingException();
            }

            connectPromise = promise;

            if (state != State.BOUND) {
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
                Exception cause = new ConnectException("connection refused: " + remoteAddress);
                safeSetFailure(promise, cause);
                close(voidPromise());
                return;
            }

            LocalServerChannel serverChannel = (LocalServerChannel) boundChannel;
            peer = serverChannel.serve(LocalChannel.this);
        }
    }
}
