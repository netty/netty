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
package io.netty5.channel.local;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.IoEvent;
import io.netty5.channel.IoHandle;
import io.netty5.channel.IoRegistration;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.ReferenceCounted;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.FastThreadLocal;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A {@link Channel} for the local transport.
 */
public class LocalChannel extends AbstractChannel<LocalServerChannel, LocalAddress, LocalAddress> {
    private static final Logger logger = LoggerFactory.getLogger(LocalChannel.class);
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<LocalChannel, Future> FINISH_READ_FUTURE_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(LocalChannel.class, Future.class, "finishReadFuture");
    private static final int MAX_READER_STACK_DEPTH = 8;

    private enum State { OPEN, BOUND, CONNECTED, CLOSED }

    final Queue<Object> inboundBuffer = PlatformDependent.newSpscQueue();
    private final Runnable readNowTask = () -> {
        // Ensure the inboundBuffer is not empty as readInbound() will always call fireChannelReadComplete()
        if (!inboundBuffer.isEmpty()) {
            readNow();
        }
    };

    private final LocalIoHandle handle = new LocalIoHandle() {

        @Override
        public void registerTransportNow() {
            // Store the peer in a local variable as it may be set to null if doClose() is called.
            // See https://github.com/netty/netty/issues/2144
            LocalChannel peer = LocalChannel.this.peer;
            if (parent() != null && peer != null) {
                // Mark this Channel as active before finish the connect on the remote peer.
                state = State.CONNECTED;
                peer.finishConnectAsync();
            }
        }

        @Override
        public void deregisterTransportNow() {
            // Noop
        }

        @Override
        public void closeTransportNow() {
            closeTransport(newPromise());
        }

        @Override
        public void handle(IoRegistration registration, IoEvent ioEvent) {
            // NOOP.
        }

        @Override
        public void close() {
            closeTransport(newPromise());
        }
    };

    private volatile State state;
    private volatile LocalChannel peer;
    private volatile LocalAddress localAddress;
    private volatile LocalAddress remoteAddress;
    private volatile boolean readInProgress;
    private volatile boolean writeInProgress;
    private volatile Future<?> finishReadFuture;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    public LocalChannel(EventLoop eventLoop) {
        this(null, eventLoop, null);
    }

    protected LocalChannel(LocalServerChannel parent, EventLoop eventLoop, LocalChannel peer) {
        super(parent, eventLoop, false, LocalIoHandle.class);
        this.peer = peer;
        if (parent != null) {
            localAddress = parent.localAddress();
        }
        if (peer != null) {
            remoteAddress = peer.localAddress();
        }
        setOption(ChannelOption.BUFFER_ALLOCATOR, DefaultBufferAllocators.onHeapAllocator());
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
    protected LocalAddress localAddress0() {
        return localAddress;
    }

    @Override
    protected LocalAddress remoteAddress0() {
        return remoteAddress;
    }

    @Override
    protected IoHandle ioHandle() {
        return handle;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        this.localAddress = LocalChannelRegistry.register(this, this.localAddress, localAddress);
        state = State.BOUND;
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) {
        switch (direction) {
            case Inbound:
                inputShutdown = true;
                break;
            case Outbound:
                outputShutdown = true;
                break;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        if (!isActive()) {
            return true;
        }
        switch (direction) {
            case Inbound:
                return inputShutdown;
            case Outbound:
                return outputShutdown;
            default:
                throw new AssertionError();
        }
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
            }

            if (peer != null) {
                this.peer = null;
                // Always call peer.eventLoop().execute() even if peer.eventLoop().inEventLoop() is true.
                // This ensures that if both channels are on the same event loop, the peer's channelInActive
                // event is triggered *after* this peer's channelInActive event
                EventLoop peerEventLoop = peer.executor();
                final boolean peerIsActive = peer.isActive();
                try {
                    peerEventLoop.execute(() -> peer.tryClose(peerIsActive));
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
                    throw cause;
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
        if (!isActive) {
            releaseInboundBuffers();
        }
        closeTransport(newPromise());
    }

    @Override
    protected boolean doReadNow(ReadSink readSink) {
        Object received = inboundBuffer.poll();
        if (received instanceof Buffer && inboundBuffer.peek() instanceof Buffer) {
            Buffer msg = (Buffer) received;
            Buffer buffer = readSink.allocateBuffer();
            if (buffer != null) {
                if (buffer.writableBytes() >= msg.readableBytes()) {
                    buffer.writeBytes(msg);
                    msg.close();
                } else {
                    readSink.processRead(buffer.capacity(), msg.capacity(), msg);
                    buffer.close();
                    return false;
                }
                Object peeked;
                while ((peeked = inboundBuffer.peek()) instanceof Buffer &&
                        (msg = (Buffer) peeked).readableBytes() <= buffer.writableBytes()) {
                    inboundBuffer.poll();
                    buffer.writeBytes(msg);
                    msg.close();
                }

                readSink.processRead(buffer.capacity(), buffer.readableBytes(), buffer);
                return false;
            }
        }
        readSink.processRead(0, 0, received);
        return false;
    }

    private static final class ReaderStackDepth {
        private int stackDepth;

        boolean incrementIfPossible() {
            if (stackDepth == MAX_READER_STACK_DEPTH) {
                return false;
            }
            stackDepth++;
            return true;
        }

        void decrement() {
            stackDepth--;
        }
    }

    private static final FastThreadLocal<ReaderStackDepth> STACK_DEPTH = new FastThreadLocal<>() {
        @Override
        protected ReaderStackDepth initialValue() throws Exception {
            return new ReaderStackDepth();
        }
    };

    @Override
    protected void doRead(boolean wasReadPendingAlready) throws Exception {
        if (readInProgress) {
            return;
        }

        Queue<Object> inboundBuffer = this.inboundBuffer;
        if (inboundBuffer.isEmpty()) {
            readInProgress = true;
            return;
        }

        final ReaderStackDepth readerStackDepth = STACK_DEPTH.get();
        if (readerStackDepth.incrementIfPossible()) {
            try {
                readNow();
            } finally {
                readerStackDepth.decrement();
            }
        } else {
            try {
                executor().execute(readNowTask);
            } catch (Throwable cause) {
                logger.warn("Closing Local channels {}-{} because exception occurred!", this, peer, cause);
                close();
                peer.close();
                throw cause;
            }
        }
    }

    @Override
    protected void doWriteNow(WriteSink writeSink) throws Exception {
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

        Object msg = writeSink.currentFlushedMessage();
        // It is possible the peer could have closed while we are writing, and in this case we should
        // simulate real socket behavior and ensure the write operation is failed.
        if (peer.state == State.CONNECTED) {
            if (msg instanceof ReferenceCounted) {
                peer.inboundBuffer.add(ReferenceCountUtil.retain(msg));
            } else if (msg instanceof Resource) {
                peer.inboundBuffer.add(((Resource<?>) msg).send().receive());
            } else {
                peer.inboundBuffer.add(msg);
            }
            writeSink.complete(0, 0, 1, true);
        } else {
            writeSink.complete(0, 0, 0, false);
        }
    }

    @Override
    protected void writeLoopComplete(boolean allWritten) {
        try {
            // The following situation may cause trouble:
            // 1. Write (with promise X)
            // 2. promise X is completed when in.remove() is called, and a listener on this promise calls close()
            // 3. Then the close event will be executed for the peer before the write events, when the write events
            // actually happened before the close event.
            writeInProgress = false;

            finishPeerRead(peer);
        } finally {
            super.writeLoopComplete(allWritten);
        }
    }

    private void finishPeerRead(final LocalChannel peer) {
        // If the peer is also writing, then we must schedule the event on the event loop to preserve read order.
        if (peer.executor() == executor() && !peer.writeInProgress) {
            finishPeerRead0(peer);
        } else {
            runFinishPeerReadTask(peer);
        }
    }

    private void runFinishPeerReadTask(final LocalChannel peer) {
        // If the peer is writing, we must wait until after reads are completed for that peer before we can read. So
        // we keep track of the task, and coordinate later that our read can't happen until the peer is done.
        final Runnable finishPeerReadTask = () -> finishPeerRead0(peer);
        try {
            if (peer.writeInProgress) {
                peer.finishReadFuture = peer.executor().submit(finishPeerReadTask);
            } else {
                peer.executor().execute(finishPeerReadTask);
            }
        } catch (Throwable cause) {
            logger.warn("Closing Local channels {}-{} because exception occurred!", this, peer, cause);
            close();
            peer.close();
            throw cause;
        }
    }

    private void releaseInboundBuffers() {
        assert executor() == null || executor().inEventLoop();
        readInProgress = false;
        Queue<Object> inboundBuffer = this.inboundBuffer;
        Object msg;
        while ((msg = inboundBuffer.poll()) != null) {
            Resource.dispose(msg);
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
            peer.readNow();
        }
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData)
            throws Exception {
        if (state == State.CONNECTED) {
            throw new AlreadyConnectedException();
        }

        if (state != State.BOUND) {
            // Not bound yet and no localAddress specified - get one.
            if (localAddress == null) {
                localAddress = new LocalAddress(this);
            }
        }

        if (localAddress != null) {
            try {
                doBind(localAddress);
            } catch (Throwable t) {
                closeTransport(newPromise());
                throw t;
            }
        }

        Channel boundChannel = LocalChannelRegistry.get(remoteAddress);
        if (!(boundChannel instanceof LocalServerChannel)) {
            Exception cause = new ConnectException("connection refused: " + remoteAddress);
            closeTransport(newPromise());
            throw cause;
        }

        LocalServerChannel serverChannel = (LocalServerChannel) boundChannel;
        peer = serverChannel.serve(this);
        return false;
    }

    @Override
    protected boolean doFinishConnect(LocalAddress requestedRemoteAddress) throws Exception {
        final LocalChannel peer = this.peer;
        if (peer == null) {
            return false;
        }
        state = State.CONNECTED;
        remoteAddress = peer.parent().localAddress();

        // As we changed our state to connected now we also need to try to flush the previous queued messages by the
        // peer.
        peer.writeFlushedAsync();
        return true;
    }

    private void writeFlushedAsync() {
        executor().execute(this::writeFlushed);
    }

    private void finishConnectAsync() {
        // We always dispatch to also ensure correct ordering if the peer Channel is on the same EventLoop
        executor().execute(() -> {
            if (isConnectPending()) {
                finishConnect();
            }
        });
    }
}
