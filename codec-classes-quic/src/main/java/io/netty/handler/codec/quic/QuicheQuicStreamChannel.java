/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelShutdownDirection;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.IoTransport;
import io.netty.channel.PendingWriteQueue;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.ChannelOutputShutdownException;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jetbrains.annotations.Nullable;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.RejectedExecutionException;

/**
 * {@link QuicStreamChannel} implementation that uses <a href="https://github.com/cloudflare/quiche">quiche</a>.
 */
final class QuicheQuicStreamChannel extends DefaultAttributeMap implements QuicStreamChannel {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicheQuicStreamChannel.class);
    private final QuicheQuicChannel parent;
    private final ChannelId id;
    private final ChannelPipeline pipeline;
    private final QuicStreamIoTransport ioTransport;
    private final Promise<Void> closePromise;
    private final PendingWriteQueue queue;

    private final QuicheQuicStreamChannelConfig config;
    private final QuicStreamAddress address;

    private boolean readable;
    private boolean readPending;
    private boolean inRecv;
    private boolean inWriteQueued;
    private boolean finReceived;
    private boolean finSent;

    private volatile boolean registered;
    private volatile boolean writable = true;
    private volatile boolean active = true;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;
    private volatile QuicStreamPriority priority;
    private volatile long capacity;
    private volatile boolean hasPending;

    QuicheQuicStreamChannel(QuicheQuicChannel parent, long streamId) {
        this.parent = parent;
        this.id = DefaultChannelId.newInstance();
        ioTransport = new QuicStreamIoTransport();
        this.pipeline = new DefaultChannelPipeline(this, false, ioTransport) {
            // TODO: add some overrides maybe ?
        };
        config = new QuicheQuicStreamChannelConfig(this);
        this.address = new QuicStreamAddress(streamId);
        this.closePromise = newPromise();
        queue = new PendingWriteQueue(parent().executor(), config.getMessageSizeEstimator().newHandle());
        // Local created unidirectional streams have the input shutdown by spec. There will never be any data for
        // these to be read.
        if (parent.streamType(streamId) == QuicStreamType.UNIDIRECTIONAL && parent.isStreamLocalCreated(streamId)) {
            inputShutdown = true;
        }
    }

    @Override
    public boolean hasPendingBytes() {
        return hasPending;
    }

    @Override
    public QuicStreamAddress localAddress() {
        return address;
    }

    @Override
    public QuicStreamAddress remoteAddress() {
        return address;
    }

    @Override
    public boolean isLocalCreated() {
        return parent().isStreamLocalCreated(streamId());
    }

    @Override
    public QuicStreamType type() {
        return parent().streamType(streamId());
    }

    @Override
    public long streamId() {
        return address.streamId();
    }

    @Override
    public QuicStreamPriority priority() {
        return priority;
    }

    @Override
    public void updatePriority(QuicStreamPriority priority, Promise<Void> promise) {
        if (executor().inEventLoop()) {
            updatePriority0(priority, promise);
        } else {
            executor().execute(() -> updatePriority0(priority, promise));
        }
    }

    private void updatePriority0(QuicStreamPriority priority, Promise<Void> promise) {
        assert executor().inEventLoop();
        try {
            parent().streamPriority(streamId(), (byte) priority.urgency(), priority.isIncremental());
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        this.priority = priority;
        promise.setSuccess(null);
    }

    @Override
    public QuicheQuicChannel parent() {
        return parent;
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        if (!isActive()) {
            return true;
        }
        switch (direction) {
            case Outbound:
                return outputShutdown;
            case Inbound:
                return inputShutdown;
            default:
                return false;
        }
    }

    private void sendFinIfNeeded() throws Exception {
        if (!finSent) {
            finSent = true;
            parent().streamSendFin(streamId());
        }
    }

    private void closeIfDone() {
        if (finSent && (finReceived || type() == QuicStreamType.UNIDIRECTIONAL && isLocalCreated())) {
            ioTransport().close(newPromise());
        }
    }

    private void removeStreamFromParent() {
        if (!active && finReceived) {
            parent().streamClosed(streamId());
            inputShutdown = true;
            outputShutdown = true;
        }
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return active;
    }

    @Override
    public boolean isActive() {
        return isOpen();
    }

    @Override
    public ChannelId id() {
        return id;
    }

    @Override
    public EventLoop executor() {
        return parent.executor();
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public Future<Void> closeFuture() {
        return closePromise;
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    @Override
    public long bytesBeforeUnwritable() {
        // Capacity might be negative if the stream was closed.
        return Math.max(capacity, 0);
    }

    @Override
    public long bytesBeforeWritable() {
        if (writable) {
            return 0;
        }
        // Just return something positive for now
        return 8;
    }

    QuicStreamIoTransport ioTransport() {
        return ioTransport;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public ByteBufAllocator alloc() {
        return config.getAllocator();
    }

    @Override
    public int compareTo(Channel o) {
        return id.compareTo(o.id());
    }

    /**
     * Returns the ID of this channel.
     */
    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Returns {@code true} if and only if the specified object is identical
     * with this channel (i.e: {@code this == o}).
     */
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public String toString() {
        return "[id: 0x" + id.asShortText() + ", " + address + "]";
    }

    /**
     * Stream writability changed.
     */
    boolean writable(long capacity) {
        assert executor().inEventLoop();
        if (capacity < 0) {
            // If the value is negative its a quiche error.
            if (capacity != Quiche.QUICHE_ERR_DONE) {
                if (!queue.isEmpty()) {
                    if (capacity == Quiche.QUICHE_ERR_STREAM_STOPPED) {
                        queue.removeAndFailAll(new ChannelOutputShutdownException("STOP_SENDING frame received"));
                        // If STOP_SENDING is received we should not close the channel but just fail all queued writes.
                        return false;
                    } else {
                        queue.removeAndFailAll(Quiche.convertToException((int) capacity));
                    }
                } else if (capacity == Quiche.QUICHE_ERR_STREAM_STOPPED) {
                    // If STOP_SENDING is received we should not close the channel
                    return false;
                }
                // IF this error was not QUICHE_ERR_STREAM_STOPPED we should close the channel.
                finSent = true;
                ioTransport().close(newPromise());
            }
            return false;
        }
        this.capacity = capacity;
        boolean mayNeedWrite = ioTransport().writeQueued();
        // we need to re-read this.capacity as writeQueued() may update the capacity.
        updateWritabilityIfNeeded(this.capacity > 0);
        return mayNeedWrite;
    }

    private void updateWritabilityIfNeeded(boolean newWritable) {
        if (writable != newWritable) {
            writable = newWritable;
            pipeline.fireChannelWritabilityChanged();
        }
    }

    /**
     * Stream is readable.
     */
    void readable() {
        assert executor().inEventLoop();
        // Mark as readable and if a read is pending execute it.
        readable = true;
        if (readPending) {
            ioTransport().recv();
        }
    }

    final class QuicStreamIoTransport implements IoTransport {

        @SuppressWarnings("deprecation")
        private RecvByteBufAllocator.Handle recvHandle;

        @Override
        public void connect(SocketAddress remote, SocketAddress local, Promise<Void> promise) {
            assert executor().inEventLoop();
            promise.setFailure(new UnsupportedOperationException());
        }

        @SuppressWarnings("deprecation")
        public RecvByteBufAllocator.Handle recvBufAllocHandle() {
            if (recvHandle == null) {
                recvHandle = config.getRecvByteBufAllocator().newHandle();
            }
            return recvHandle;
        }

        @Override
        public void shutdown(ChannelShutdownType type, Promise<Void> promise) {
            if (type.data() != null && !(type.data() instanceof Integer)) {
                promise.setFailure(new IllegalArgumentException(
                        "ChannelShutdownType with data if non integer type is allowed: " + type));
                return;
            }
            final boolean read;
            final boolean write;
            switch (type.direction()) {
                case Outbound:
                    write = true;
                    read = false;
                    break;
                case Inbound:
                    read = true;
                    write = false;
                    break;
                default:
                    promise.setFailure(new UnsupportedOperationException());
                    return;
            }

            if (write) {
                if (outputShutdown) {
                    promise.setSuccess(null);
                    return;
                }
                ioTransport.writeWithoutCheckChannelState(QuicStreamFrame.EMPTY_FIN,
                        QuicheQuicStreamChannel.this.<Void>newPromise().addListener(f -> {
                            if (f.isSuccess()) {
                                if (type.data() == null) {
                                    promise.setSuccess(null);
                                } else {
                                    shutdown0(read, write, (Integer) type.data(), promise);
                                }
                            } else {
                                promise.setFailure(f.cause());
                            }
                        }));
                ioTransport.flush();
            } else {
                if (inputShutdown) {
                    promise.setSuccess(null);
                    return;
                }
                int error;
                if (type.data() == null) {
                    error = 0;
                } else {
                    error = (Integer) type.data();
                }
                shutdown0(read, write, error, promise);
            }
        }

        private void shutdown0(boolean read, boolean write, int error, Promise<Void> promise) {
            parent().streamShutdown(streamId(), read, write, error,
                    QuicheQuicStreamChannel.this.<Void>newPromise().addListener(f -> {
                if (f.isSuccess()) {
                    // Update fields before notify promise
                    if (write) {
                        outputShutdown = true;
                    }
                    if (read) {
                        inputShutdown = true;
                    }
                    promise.setSuccess(null);
                } else {
                    promise.setFailure(f.cause());
                }
                closeIfDone();
            }));
        }

        @Override
        public void register(Promise<Void> promise) {
            if (registered) {
                promise.setFailure(new IllegalStateException());
                return;
            }
            registered = true;
            promise.setSuccess(null);
            pipeline.fireChannelRegistered();
            pipeline.fireChannelActive();
        }

        @Override
        public void bind(SocketAddress localAddress, Promise<Void> promise) {
            assert executor().inEventLoop();
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        public void disconnect(Promise<Void> promise) {
            assert executor().inEventLoop();
            close(promise);
        }

        @Override
        public void close(Promise<Void> promise) {
            close(null, promise);
        }

        void close(@Nullable ClosedChannelException writeFailCause, Promise<Void> promise) {
            assert executor().inEventLoop();
            if (!active || closePromise.isDone()) {
                closePromise.addListener(new PromiseNotifier<>(promise));
                return;
            }
            active = false;
            try {
                // Close the channel and fail the queued messages in all cases.
                sendFinIfNeeded();
            } catch (Exception ignore) {
                // Just ignore
            } finally {
                if (!queue.isEmpty()) {
                    // Only fail if the queue is non-empty.
                    if (writeFailCause == null) {
                        writeFailCause = new ClosedChannelException();
                    }
                    queue.removeAndFailAll(writeFailCause);
                }

                promise.trySuccess(null);
                closePromise.trySuccess(null);
                if (type() == QuicStreamType.UNIDIRECTIONAL && isLocalCreated()) {
                    inputShutdown = true;
                    outputShutdown = true;
                    // If its an unidirectional stream and was created locally it is safe to close the stream now as
                    // we will never receive data from the other side.
                    parent().streamClosed(streamId());
                } else {
                    removeStreamFromParent();
                }
            }
            if (inWriteQueued) {
                invokeLater(() -> deregister(newPromise(), true));
            } else {
                deregister(newPromise(), true);
            }
        }

        private void deregister(final Promise<Void> promise, final boolean fireChannelInactive) {
            assert executor().inEventLoop();
            if (!registered) {
                promise.trySuccess(null);
                return;
            }

            // As a user may call deregister() from within any method while doing processing in the ChannelPipeline,
            // we need to ensure we do the actual deregister operation later. This is needed as for example,
            // we may be in the ByteToMessageDecoder.callDecode(...) method and so still try to do processing in
            // the old EventLoop while the user already registered the Channel to a new EventLoop. Without delay,
            // the deregister operation this could lead to have a handler invoked by different EventLoop and so
            // threads.
            //
            // See:
            // https://github.com/netty/netty/issues/4435
            invokeLater(() -> {
                if (fireChannelInactive) {
                    pipeline.fireChannelInactive();
                }
                // Some transports like local and AIO does not allow the deregistration of
                // an open channel.  Their doDeregister() calls close(). Consequently,
                // close() calls deregister() again - no need to fire channelUnregistered, so check
                // if it was registered.
                if (registered) {
                    registered = false;
                    pipeline.fireChannelUnregistered();
                }
                promise.setSuccess(null);
            });
        }

        private void invokeLater(Runnable task) {
            try {
                // This method is used by outbound operation implementations to trigger an inbound event later.
                // They do not trigger an inbound event immediately because an outbound operation might have been
                // triggered by another inbound event handler method.  If fired immediately, the call stack
                // will look like this for example:
                //
                //   handlerA.inboundBufferUpdated() - (1) an inbound handler method closes a connection.
                //   -> handlerA.ctx.close()
                //      -> channel.unsafe.close()
                //         -> handlerA.channelInactive() - (2) another inbound handler method called while in (1) yet
                //
                // which means the execution of two inbound handler methods of the same handler overlap undesirably.
                executor().execute(task);
            } catch (RejectedExecutionException e) {
                LOGGER.warn("Can't invoke task later as EventLoop rejected it", e);
            }
        }

        @Override
        public void deregister(Promise<Void> promise) {
            assert executor().inEventLoop();
            deregister(promise, false);
        }

        @Override
        public void read() {
            assert executor().inEventLoop();
            readPending = true;
            if (readable) {
                ioTransport().recv();

                // As the stream was readable, and we called recv() ourselves we also need to call
                // connectionSendAndFlush(). This is needed as recv() might consume data and so a window update
                // frame might be produced. If we miss to call connectionSendAndFlush() we might never send the update
                // to the remote peer and so the remote peer might never attempt to send more data.
                // See also https://docs.rs/quiche/latest/quiche/struct.Connection.html#method.send.
                parent().connectionSendAndFlush();
            }
        }

        private void closeIfNeeded(boolean wasFinSent) {
            // Let's check if we should close the channel now.
            // If it's a unidirectional channel we can close it as there will be no fin that we can read
            // from the remote peer. If its an bidirectional channel we should only close the channel if we
            // also received the fin from the remote peer.
            if (!wasFinSent && QuicheQuicStreamChannel.this.finSent
                    && (type() == QuicStreamType.UNIDIRECTIONAL || finReceived)) {
                // close the channel now
                close(newPromise());
            }
        }

        boolean writeQueued() {
            assert executor().inEventLoop();
            boolean wasFinSent = QuicheQuicStreamChannel.this.finSent;
            inWriteQueued = true;
            try {
                if (queue.isEmpty()) {
                    return false;
                }
                boolean written = false;
                for (;;) {
                    Object msg = queue.current();
                    if (msg == null) {
                        break;
                    }
                    try {
                        int res = write0(msg);
                        if (res == 1) {
                            queue.remove().setSuccess(null);
                            written = true;
                        } else if (res == 0 || res == Quiche.QUICHE_ERR_DONE) {
                            break;
                        } else if (res == Quiche.QUICHE_ERR_STREAM_STOPPED) {
                            // Once its signaled that the stream is stopped we can just fail everything.
                            // That said we should not close the channel yet as there might be some data that is
                            // not read yet by the user.
                            queue.removeAndFailAll(
                                    new ChannelOutputShutdownException("STOP_SENDING frame received"));
                            break;
                        } else {
                            queue.remove().setFailure(Quiche.convertToException(res));
                        }
                    } catch (Exception e) {
                        queue.remove().setFailure(e);
                    }
                }
                if (written) {
                    updateWritabilityIfNeeded(true);
                }
                return written;
            } finally {
                updateHasPending();
                closeIfNeeded(wasFinSent);
                inWriteQueued = false;
            }
        }

        @Override
        public void write(Object msg, Promise<Void> promise) {
            assert executor().inEventLoop();
            // Check first if the Channel is in a state in which it will accept writes, if not fail everything
            // with the right exception
            if (!isOpen()) {
                queueAndFailAll(msg, promise, new ClosedChannelException());
            } else if (finSent) {
                queueAndFailAll(msg, promise, new ChannelOutputShutdownException("Fin was sent already"));
            } else if (!queue.isEmpty()) {
                // If the queue is not empty we should just add the message to the queue as we will drain
                // it later once the stream becomes writable again.
                try {
                    msg = filterMsg(msg);
                } catch (UnsupportedOperationException e) {
                    ReferenceCountUtil.release(msg);
                    promise.setFailure(e);
                    return;
                }

                // Touch the message to make things easier in terms of debugging buffer leaks.
                ReferenceCountUtil.touch(msg);
                queue.add(msg, promise);
                updateHasPending();
                // Try again to write queued messages.
                writeQueued();
            } else {
                assert queue.isEmpty();
                writeWithoutCheckChannelState(msg, promise);
            }
        }

        private void queueAndFailAll(Object msg, Promise<Void> promise, Throwable cause) {
            // Touch the message to make things easier in terms of debugging buffer leaks.
            ReferenceCountUtil.touch(msg);

            queue.add(msg, promise);
            updateHasPending();
            queue.removeAndFailAll(cause);
            updateHasPending();
        }

        private void updateHasPending() {
            hasPending = queue.bytes() > 0;
        }

        private Object filterMsg(Object msg) {
            if (msg instanceof ByteBuf) {
                ByteBuf buffer = (ByteBuf)  msg;
                if (!buffer.isDirect()) {
                    ByteBuf tmpBuffer = alloc().directBuffer(buffer.readableBytes());
                    tmpBuffer.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
                    buffer.release();
                    return tmpBuffer;
                }
            } else if (msg instanceof QuicStreamFrame) {
                QuicStreamFrame frame = (QuicStreamFrame) msg;
                ByteBuf buffer = frame.content();
                if (!buffer.isDirect()) {
                    ByteBuf tmpBuffer = alloc().directBuffer(buffer.readableBytes());
                    tmpBuffer.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
                    QuicStreamFrame tmpFrame = frame.replace(tmpBuffer);
                    frame.release();
                    return tmpFrame;
                }
            } else {
                throw new UnsupportedOperationException(
                        "unsupported message type: " + StringUtil.simpleClassName(msg));
            }
            return msg;
        }

        void writeWithoutCheckChannelState(Object msg, Promise<Void> promise) {
            try {
                msg = filterMsg(msg);
            } catch (UnsupportedOperationException e) {
                ReferenceCountUtil.release(msg);
                promise.setFailure(e);
            }

            boolean wasFinSent = QuicheQuicStreamChannel.this.finSent;
            boolean mayNeedWritabilityUpdate = false;
            try {
                int res = write0(msg);
                if (res > 0) {
                    ReferenceCountUtil.release(msg);
                    promise.setSuccess(null);
                    mayNeedWritabilityUpdate = capacity == 0;
                } else if (res == 0 || res == Quiche.QUICHE_ERR_DONE) {
                    // Touch the message to make things easier in terms of debugging buffer leaks.
                    ReferenceCountUtil.touch(msg);
                    queue.add(msg, promise);
                    mayNeedWritabilityUpdate = true;
                } else if (res == Quiche.QUICHE_ERR_STREAM_STOPPED) {
                    throw new ChannelOutputShutdownException("STOP_SENDING frame received");
                } else {
                    throw Quiche.convertToException(res);
                }
            } catch (Exception e) {
                ReferenceCountUtil.release(msg);
                promise.setFailure(e);
                mayNeedWritabilityUpdate = capacity == 0;
            } finally {
                if (mayNeedWritabilityUpdate) {
                    updateWritabilityIfNeeded(false);
                }
                closeIfNeeded(wasFinSent);
            }
        }

        private int write0(Object msg) throws Exception {
            if (type() == QuicStreamType.UNIDIRECTIONAL && !isLocalCreated()) {
                throw new UnsupportedOperationException(
                        "Writes on non-local created streams that are unidirectional are not supported");
            }
            if (finSent) {
                throw new ChannelOutputShutdownException("Fin was sent already");
            }

            final boolean fin;
            ByteBuf buffer;
            if (msg instanceof ByteBuf) {
                fin = false;
                buffer = (ByteBuf) msg;
            } else {
                QuicStreamFrame frame = (QuicStreamFrame) msg;
                fin = frame.hasFin();
                buffer = frame.content();
            }

            boolean readable = buffer.isReadable();
            if (!fin && !readable) {
                return 1;
            }

            boolean sendSomething = false;
            try {
                do {
                    int res = parent().streamSend(streamId(), buffer, fin);

                    // Update the capacity as well.
                    long cap = parent.streamCapacity(streamId());
                    if (cap >= 0) {
                        capacity = cap;
                    }
                    if (res < 0) {
                        return res;
                    }
                    if (readable && res == 0) {
                        return 0;
                    }
                    sendSomething = true;
                    buffer.skipBytes(res);
                } while (buffer.isReadable());

                if (fin) {
                    finSent = true;
                    outputShutdown = true;
                }
                return 1;
            } finally {
                // As we called quiche_conn_stream_send(...) we need to ensure we will call quiche_conn_send(...) either
                // now or we will do so once we see the channelReadComplete event.
                //
                // See https://docs.rs/quiche/0.6.0/quiche/struct.Connection.html#method.send
                if (sendSomething) {
                    parent.connectionSendAndFlush();
                }
            }
        }

        @Override
        public void flush() {
            assert executor().inEventLoop();
            // NOOP.
        }

        private void closeOnRead(ChannelPipeline pipeline, boolean readFrames) {
            if (readFrames && finReceived && finSent) {
                close(newPromise());
            } else if (config.isAllowHalfClosure()) {
                if (finReceived) {
                    // If we receive a fin there will be no more data to read so we need to fire both events
                    // to be consistent with other transports.
                    pipeline.fireChannelShutdown(ChannelShutdownType.newInbound());
                    if (finSent) {
                        // This was an unidirectional stream which means as soon as we received FIN and sent a FIN
                        // we need close the connection.
                        close(newPromise());
                    }
                }
            } else {
                // This was an unidirectional stream which means as soon as we received FIN we need
                // close the connection.
                close(newPromise());
            }
        }

        private void handleReadException(ChannelPipeline pipeline, @Nullable ByteBuf byteBuf, Throwable cause,
                                         @SuppressWarnings("deprecation") RecvByteBufAllocator.Handle allocHandle,
                                         boolean readFrames) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    byteBuf.release();
                }
            }

            readComplete(allocHandle, pipeline);
            pipeline.fireExceptionCaught(cause);
            if (finReceived) {
                closeOnRead(pipeline, readFrames);
            }
        }

        void recv() {
            assert executor().inEventLoop();
            if (inRecv) {
                // As the use may call read() we need to guard against reentrancy here as otherwise it could
                // be possible that we re-enter this method while still processing it.
                return;
            }

            inRecv = true;
            try {
                ChannelPipeline pipeline = pipeline();
                QuicheQuicStreamChannelConfig config = (QuicheQuicStreamChannelConfig) config();
                // Directly access the DirectIoByteBufAllocator as we need an direct buffer to read into in all cases
                // even if there is no Unsafe present and the direct buffer is not pooled.
                DirectIoByteBufAllocator allocator = config.allocator;
                @SuppressWarnings("deprecation")
                RecvByteBufAllocator.Handle allocHandle = this.recvBufAllocHandle();
                boolean readFrames = config.isReadFrames();

                // We should loop as long as a read() was requested and there is anything left to read, which means the
                // stream was marked as readable before.
                while (active && readPending && readable) {
                    allocHandle.reset(config);
                    ByteBuf byteBuf = null;
                    QuicheQuicChannel parent = parent();
                    // It's possible that the stream was marked as finish while we iterated over the readable streams
                    // or while we did have auto read disabled. If so we need to ensure we not try to read from it as it
                    // would produce an error.
                    boolean readCompleteNeeded = false;
                    boolean continueReading = true;
                    try {
                        while (!finReceived && continueReading) {
                            byteBuf = allocHandle.allocate(allocator);
                            allocHandle.attemptedBytesRead(byteBuf.writableBytes());
                            QuicheQuicChannel.StreamRecvResult result = parent.streamRecv(streamId(), byteBuf);
                            switch (result) {
                                case DONE:
                                    // Nothing left to read;
                                    readable = false;
                                    break;
                                case FIN:
                                    // If we received a FIN we also should mark the channel as non-readable as
                                    // there is nothing left to read really.
                                    readable = false;
                                    finReceived = true;
                                    inputShutdown = true;
                                    break;
                                case OK:
                                    break;
                                default:
                                    throw new Error("Unexpected StreamRecvResult: " + result);
                            }
                            allocHandle.lastBytesRead(byteBuf.readableBytes());
                            if (allocHandle.lastBytesRead() <= 0) {
                                byteBuf.release();
                                if (finReceived && readFrames) {
                                    // If we read QuicStreamFrames we should fire an frame through the pipeline
                                    // with an empty buffer but the fin flag set to true.
                                    byteBuf = Unpooled.EMPTY_BUFFER;
                                } else {
                                    byteBuf = null;
                                    break;
                                }
                            }
                            // We did read one message.
                            allocHandle.incMessagesRead(1);
                            readCompleteNeeded = true;

                            // It's important that we reset this to false before we call fireChannelRead(...)
                            // as the user may request another read() from channelRead(...) callback.
                            readPending = false;

                            if (readFrames) {
                                pipeline.fireChannelRead(new DefaultQuicStreamFrame(byteBuf, finReceived));
                            } else {
                                pipeline.fireChannelRead(byteBuf);
                            }
                            byteBuf = null;
                            continueReading = allocHandle.continueReading();
                        }

                        if (readCompleteNeeded) {
                            readComplete(allocHandle, pipeline);
                        }
                        if (finReceived) {
                            readable = false;
                            closeOnRead(pipeline, readFrames);
                        }
                    } catch (Throwable cause) {
                        readable = false;
                        handleReadException(pipeline, byteBuf, cause, allocHandle, readFrames);
                    }
                }
            } finally {
                // About to leave the method lets reset so we can enter it again.
                inRecv = false;
                removeStreamFromParent();
            }
        }

        // Read was complete and something was read, so we we need to reset the readPending flags, the allocHandle
        // and call fireChannelReadComplete(). The user may schedule another read now.
        private void readComplete(@SuppressWarnings("deprecation") RecvByteBufAllocator.Handle allocHandle,
                                  ChannelPipeline pipeline) {
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
        }
    }
}
