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
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseNotifier;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.PendingWriteQueue;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.VoidChannelPromise;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownException;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.RejectedExecutionException;

/**
 * {@link QuicStreamChannel} implementation that uses <a href="https://github.com/cloudflare/quiche">quiche</a>.
 */
final class QuicheQuicStreamChannel extends DefaultAttributeMap implements QuicStreamChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicheQuicStreamChannel.class);
    private final QuicheQuicChannel parent;
    private final ChannelId id;
    private final ChannelPipeline pipeline;
    private final QuicStreamChannelUnsafe unsafe;
    private final ChannelPromise closePromise;
    private final PendingWriteQueue queue;

    private final QuicStreamChannelConfig config;
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

    QuicheQuicStreamChannel(QuicheQuicChannel parent, long streamId) {
        this.parent = parent;
        this.id = DefaultChannelId.newInstance();
        unsafe = new QuicStreamChannelUnsafe();
        this.pipeline = new DefaultChannelPipeline(this) {
            // TODO: add some overrides maybe ?
        };
        config = new QuicheQuicStreamChannelConfig(this);
        this.address = new QuicStreamAddress(streamId);
        this.closePromise = newPromise();
        queue = new PendingWriteQueue(this);
        // Local created unidirectional streams have the input shutdown by spec. There will never be any data for
        // these to be read.
        if (parent.streamType(streamId) == QuicStreamType.UNIDIRECTIONAL && parent.isStreamLocalCreated(streamId)) {
            inputShutdown = true;
        }
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
    public ChannelFuture updatePriority(QuicStreamPriority priority, ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            updatePriority0(priority, promise);
        } else {
            eventLoop().execute(() -> updatePriority0(priority, promise));
        }
        return promise;
    }

    private void updatePriority0(QuicStreamPriority priority, ChannelPromise promise) {
        try {
            parent().streamPriority(streamId(), (byte) priority.urgency(), priority.isIncremental());
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        this.priority = priority;
        promise.setSuccess();
    }

    @Override
    public boolean isInputShutdown() {
        return inputShutdown;
    }

    @Override
    public ChannelFuture shutdownOutput(ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            shutdownOutput0(promise);
        } else {
            eventLoop().execute(() -> shutdownOutput0(promise));
        }
        return promise;
    }

    private void shutdownOutput0(ChannelPromise promise) {
        outputShutdown = true;
        unsafe.write(QuicStreamFrame.EMPTY_FIN, promise);
        unsafe.flush();
    }

    @Override
    public ChannelFuture shutdownInput(int error, ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            shutdownInput0(error, promise);
        } else {
            eventLoop().execute(() -> shutdownInput0(error, promise));
        }
        return promise;
    }

    @Override
    public ChannelFuture shutdownOutput(int error, ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            shutdownOutput0(error, promise);
        } else {
            eventLoop().execute(() -> shutdownOutput0(error, promise));
        }
        return promise;
    }

    @Override
    public QuicheQuicChannel parent() {
        return parent;
    }

    private void shutdownInput0(int err, ChannelPromise channelPromise) {
        inputShutdown = true;
        parent().streamShutdown(streamId(), true, false, err, channelPromise);
        closeIfDone();
    }

    @Override
    public boolean isOutputShutdown() {
        return outputShutdown;
    }

    private void shutdownOutput0(int error, ChannelPromise channelPromise) {
        parent().streamShutdown(streamId(), false, true, error, channelPromise);
        outputShutdown = true;
        closeIfDone();
    }

    @Override
    public boolean isShutdown() {
        return outputShutdown && inputShutdown;
    }

    @Override
    public ChannelFuture shutdown(ChannelPromise channelPromise) {
        if (eventLoop().inEventLoop()) {
            shutdown0(channelPromise);
        } else {
            eventLoop().execute(() -> shutdown0(channelPromise));
        }
        return channelPromise;
    }

    private void shutdown0(ChannelPromise promise) {
        inputShutdown = true;
        outputShutdown = true;
        unsafe.write(QuicStreamFrame.EMPTY_FIN, unsafe.voidPromise());
        unsafe.flush();
        parent().streamShutdown(streamId(), true, false, 0, promise);
        closeIfDone();
    }

    @Override
    public ChannelFuture shutdown(int error, ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            shutdown0(error, promise);
        } else {
            eventLoop().execute(() -> shutdown0(error, promise));
        }
        return promise;
    }

    private void shutdown0(int error, ChannelPromise channelPromise) {
        inputShutdown = true;
        outputShutdown = true;
        parent().streamShutdown(streamId(), true, true, error, channelPromise);
        closeIfDone();
    }

    private void sendFinIfNeeded() throws Exception {
        if (!finSent) {
            finSent = true;
            parent().streamSendFin(streamId());
        }
    }

    private void closeIfDone() {
        if (finSent && (finReceived || type() == QuicStreamType.UNIDIRECTIONAL && isLocalCreated())) {
            unsafe().close(unsafe().voidPromise());
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
    public QuicStreamChannel flush() {
        pipeline.flush();
        return this;
    }

    @Override
    public QuicStreamChannel read() {
        pipeline.read();
        return this;
    }

    @Override
    public QuicStreamChannelConfig config() {
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
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public ChannelId id() {
        return id;
    }

    @Override
    public EventLoop eventLoop() {
        return parent.eventLoop();
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public ChannelFuture closeFuture() {
        return closePromise;
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    @Override
    public long bytesBeforeUnwritable() {
        return 0;
    }

    @Override
    public long bytesBeforeWritable() {
        return 0;
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
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
     * Stream is writable.
     */
    void writable(@SuppressWarnings("unused") int capacity) {
        ((QuicStreamChannelUnsafe) unsafe()).writeQueued();
    }

    /**
     * Stream is readable.
     */
    void readable() {
        // Mark as readable and if a read is pending execute it.
        readable = true;
        if (readPending) {
            ((QuicStreamChannelUnsafe) unsafe()).recv();
        }
    }

    void forceClose() {
        // Set received to true to ensure we will actual remove it from the internal map once we send the fin.
        finSent = true;
        unsafe().close(unsafe().voidPromise());
    }

    private final class QuicStreamChannelUnsafe implements Unsafe {
        private RecvByteBufAllocator.Handle recvHandle;

        private final ChannelPromise voidPromise = new VoidChannelPromise(
                QuicheQuicStreamChannel.this, false);
        @Override
        public void connect(SocketAddress remote, SocketAddress local, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @SuppressWarnings("deprecation")
        @Override
        public RecvByteBufAllocator.Handle recvBufAllocHandle() {
            if (recvHandle == null) {
                recvHandle = config.getRecvByteBufAllocator().newHandle();
            }
            return recvHandle;
        }

        @Override
        public SocketAddress localAddress() {
            return address;
        }

        @Override
        public SocketAddress remoteAddress() {
            return address;
        }

        @Override
        public void register(EventLoop eventLoop, ChannelPromise promise) {
            if (registered) {
                promise.setFailure(new IllegalStateException());
                return;
            }
            if (eventLoop != parent.eventLoop()) {
                promise.setFailure(new IllegalArgumentException());
                return;
            }
            registered = true;
            promise.setSuccess();
            pipeline.fireChannelRegistered();
            pipeline.fireChannelActive();
        }

        @Override
        public void bind(SocketAddress localAddress, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        public void disconnect(ChannelPromise promise) {
            close(promise);
        }

        @Override
        public void close(ChannelPromise promise) {
            if (!active || closePromise.isDone()) {
                if (promise.isVoid()) {
                    return;
                }
                closePromise.addListener(new ChannelPromiseNotifier(promise));
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
                    queue.removeAndFailAll(new ClosedChannelException());
                }

                promise.trySuccess();
                closePromise.trySuccess();
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
                invokeLater(() -> deregister(voidPromise(), true));
            } else {
                deregister(voidPromise(), true);
            }
        }

        private void deregister(final ChannelPromise promise, final boolean fireChannelInactive) {
            if (!promise.setUncancellable()) {
                return;
            }

            if (!registered) {
                promise.trySuccess();
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
                promise.setSuccess();
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
                eventLoop().execute(task);
            } catch (RejectedExecutionException e) {
                LOGGER.warn("Can't invoke task later as EventLoop rejected it", e);
            }
        }

        @Override
        public void closeForcibly() {
            close(unsafe().voidPromise());
        }

        @Override
        public void deregister(ChannelPromise promise) {
            deregister(promise, false);
        }

        @Override
        public void beginRead() {
            readPending = true;
            if (readable) {
                ((QuicStreamChannelUnsafe) unsafe()).recv();
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
                close(voidPromise());
            }
        }

        void writeQueued() {
            boolean wasFinSent = QuicheQuicStreamChannel.this.finSent;
            inWriteQueued = true;
            try {
                for (;;) {
                    Object msg = queue.current();
                    if (msg == null) {
                        break;
                    }
                    try {
                        if (!write0(msg)) {
                            return;
                        }
                    } catch (Exception e) {
                        queue.remove().setFailure(e);
                        continue;
                    }
                    queue.remove().setSuccess();
                }
                if (!writable) {
                    writable = true;
                    pipeline.fireChannelWritabilityChanged();
                }
            } finally {
                closeIfNeeded(wasFinSent);
                inWriteQueued = false;
            }
        }

        @Override
        public void write(Object msg, ChannelPromise promise) {
            if (msg instanceof ByteBuf) {
                ByteBuf buffer = (ByteBuf)  msg;
                if (!buffer.isDirect()) {
                    ByteBuf tmpBuffer = alloc().directBuffer(buffer.readableBytes());
                    tmpBuffer.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
                    buffer.release();
                    msg = tmpBuffer;
                }
            } else if (msg instanceof QuicStreamFrame) {
                QuicStreamFrame frame = (QuicStreamFrame) msg;
                ByteBuf buffer = frame.content();
                if (!buffer.isDirect()) {
                    ByteBuf tmpBuffer = alloc().directBuffer(buffer.readableBytes());
                    tmpBuffer.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
                    buffer.release();
                    msg = frame.replace(tmpBuffer);
                }
            } else {
                ReferenceCountUtil.release(msg);
                promise.setFailure(new UnsupportedOperationException(
                        "unsupported message type: " + StringUtil.simpleClassName(msg)));
                return;
            }

            if (!queue.isEmpty()) {
                // Something is queued already.
                queue.add(msg, promise);
                if (finSent) {
                    ChannelOutputShutdownException e = new ChannelOutputShutdownException(
                            "Fin was sent already");
                    queue.removeAndFail(e);
                }
                return;
            }

            boolean wasFinSent = QuicheQuicStreamChannel.this.finSent;
            try {
                if (write0(msg)) {
                    ReferenceCountUtil.release(msg);
                    promise.setSuccess();
                } else {
                    queue.add(msg, promise);
                    if (writable) {
                        writable = false;
                        pipeline.fireChannelWritabilityChanged();
                    }
                }
            } catch (Exception e) {
                ReferenceCountUtil.release(msg);
                promise.setFailure(e);
            } finally {
                closeIfNeeded(wasFinSent);
            }
        }

        private boolean write0(Object msg) throws Exception {
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

            if (!fin && !buffer.isReadable()) {
                return true;
            }

            boolean sendSomething = false;
            try {
                do {
                    int res = parent().streamSend(streamId(), buffer, fin);
                    if (Quiche.throwIfError(res) || res == 0) {
                        parent.streamHasPendingWrites(streamId());
                        return false;
                    }
                    sendSomething = true;
                    buffer.skipBytes(res);
                } while (buffer.isReadable());

                if (fin) {
                    finSent = true;
                    outputShutdown = true;
                }
                return true;
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
            // NOOP.
        }

        @Override
        public ChannelPromise voidPromise() {
            return voidPromise;
        }

        @Override
        public ChannelOutboundBuffer outboundBuffer() {
            return null;
        }

        private void closeOnRead(ChannelPipeline pipeline, boolean readFrames) {
            if (readFrames && finReceived && finSent) {
                close(voidPromise());
            } else if (config.isAllowHalfClosure()) {
                if (finReceived) {
                    // If we receive a fin there will be no more data to read so we need to fire both events
                    // to be consistent with other transports.
                    pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                    pipeline.fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
                    if (finSent) {
                        // This was an unidirectional stream which means as soon as we received FIN and sent a FIN
                        // we need close the connection.
                        close(voidPromise());
                    }
                }
            } else {
                // This was an unidirectional stream which means as soon as we received FIN we need
                // close the connection.
                close(voidPromise());
            }
        }

        private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf, Throwable cause,
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
            if (inRecv) {
                // As the use may call read() we need to guard against re-entrancy here as otherwise it could
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
                            switch (parent.streamRecv(streamId(), byteBuf)) {
                                case DONE:
                                    // Nothing left to read;
                                    readable = false;
                                    break;
                                case FIN:
                                    // If we received a FIN we also should mark the channel as non readable as
                                    // there is nothing left to read really.
                                    readable = false;
                                    finReceived = true;
                                    inputShutdown = true;
                                    break;
                                case OK:
                                    break;
                                default:
                                    throw new Error();
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
