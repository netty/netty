/*
 * Copyright 2019 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelId;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.DefaultChannelConfig;
import io.netty5.channel.DefaultChannelPipeline;
import io.netty5.channel.EventLoop;
import io.netty5.channel.MessageSizeEstimator;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.channel.WriteBufferWaterMark;
import io.netty5.handler.codec.http2.Http2FrameCodec.DefaultHttp2FrameStream;
import io.netty5.util.DefaultAttributeMap;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import static io.netty5.handler.codec.http2.Http2CodecUtil.isStreamIdValid;
import static java.lang.Math.min;

abstract class AbstractHttp2StreamChannel extends DefaultAttributeMap implements Http2StreamChannel {

    static final Http2FrameStreamVisitor WRITABLE_VISITOR = stream -> {
        final AbstractHttp2StreamChannel childChannel = (AbstractHttp2StreamChannel)
                ((DefaultHttp2FrameStream) stream).attachment;
        childChannel.trySetWritable();
        return true;
    };

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractHttp2StreamChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);

    /**
     * Number of bytes to consider non-payload messages. 9 is arbitrary, but also the minimum size of an HTTP/2 frame.
     * Primarily is non-zero.
     */
    private static final int MIN_HTTP2_FRAME_SIZE = 9;

    /**
     * Returns the flow-control size for DATA frames, and {@value MIN_HTTP2_FRAME_SIZE} for all other frames.
     */
    private static final class FlowControlledFrameSizeEstimator implements MessageSizeEstimator {

        static final FlowControlledFrameSizeEstimator INSTANCE = new FlowControlledFrameSizeEstimator();

    private static final Handle HANDLE_INSTANCE = msg -> {
        return msg instanceof Http2DataFrame ?
                // Guard against overflow.
                (int) min(Integer.MAX_VALUE, ((Http2DataFrame) msg).initialFlowControlledBytes() +
                        (long) MIN_HTTP2_FRAME_SIZE) : MIN_HTTP2_FRAME_SIZE;
        };

        @Override
        public Handle newHandle() {
            return HANDLE_INSTANCE;
        }
    }

    private static final AtomicLongFieldUpdater<AbstractHttp2StreamChannel> TOTAL_PENDING_SIZE_UPDATER =
            AtomicLongFieldUpdater.newUpdater(AbstractHttp2StreamChannel.class, "totalPendingSize");

    private static final AtomicIntegerFieldUpdater<AbstractHttp2StreamChannel> UNWRITABLE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractHttp2StreamChannel.class, "unwritable");

    private static void windowUpdateFrameWriteComplete(Channel streamChannel, Future<?> future) {
        Throwable cause = future.cause();
        if (cause != null) {
            Throwable unwrappedCause;
            // Unwrap if needed
            if (cause instanceof Http2FrameStreamException && (unwrappedCause = cause.getCause()) != null) {
                cause = unwrappedCause;
            }

            // Notify the child-channel and close it.
            streamChannel.pipeline().fireExceptionCaught(cause);
            streamChannel.unsafe().close(streamChannel.newPromise());
        }
    }

    /**
     * The current status of the read-processing for a {@link AbstractHttp2StreamChannel}.
     */
    private enum ReadStatus {
        /**
         * No read in progress and no read was requested (yet)
         */
        IDLE,

        /**
         * Reading in progress
         */
        IN_PROGRESS,

        /**
         * A read operation was requested.
         */
        REQUESTED
    }

    private final Http2StreamChannelConfig config = new Http2StreamChannelConfig(this);
    private final Http2ChannelUnsafe unsafe = new Http2ChannelUnsafe();
    private final ChannelId channelId;
    private final ChannelPipeline pipeline;
    private final DefaultHttp2FrameStream stream;
    private final Promise<Void> closePromise;

    private volatile boolean registered;

    private volatile long totalPendingSize;
    private volatile int unwritable;

    // Cached to reduce GC
    private Runnable fireChannelWritabilityChangedTask;

    private boolean outboundClosed;
    private int flowControlledBytes;

    /**
     * This variable represents if a read is in progress for the current channel or was requested.
     * Note that depending upon the {@link RecvBufferAllocator} behavior a read may extend beyond the
     * {@link Http2ChannelUnsafe#beginRead()} method scope. The {@link Http2ChannelUnsafe#beginRead()} loop may
     * drain all pending data, and then if the parent channel is reading this channel may still accept frames.
     */
    private ReadStatus readStatus = ReadStatus.IDLE;

    private Queue<Object> inboundBuffer;

    /** {@code true} after the first HEADERS frame has been written **/
    private boolean firstFrameWritten;
    private boolean readCompletePending;

    AbstractHttp2StreamChannel(DefaultHttp2FrameStream stream, int id, ChannelHandler inboundHandler) {
        this.stream = stream;
        stream.attachment = this;
        pipeline = new DefaultChannelPipeline(this) {
            @Override
            protected void incrementPendingOutboundBytes(long size) {
                AbstractHttp2StreamChannel.this.incrementPendingOutboundBytes(size, true);
            }

            @Override
            protected void decrementPendingOutboundBytes(long size) {
                AbstractHttp2StreamChannel.this.decrementPendingOutboundBytes(size, true);
            }
        };

        closePromise = pipeline.newPromise();
        channelId = new Http2StreamChannelId(parent().id(), id);

        if (inboundHandler != null) {
            // Add the handler to the pipeline now that we are registered.
            pipeline.addLast(inboundHandler);
        }
    }

    private void incrementPendingOutboundBytes(long size, boolean invokeLater) {
        if (size == 0) {
            return;
        }

        long newWriteBufferSize = TOTAL_PENDING_SIZE_UPDATER.addAndGet(this, size);
        if (newWriteBufferSize > config().getWriteBufferHighWaterMark()) {
            setUnwritable(invokeLater);
        }
    }

    private void decrementPendingOutboundBytes(long size, boolean invokeLater) {
        if (size == 0) {
            return;
        }

        long newWriteBufferSize = TOTAL_PENDING_SIZE_UPDATER.addAndGet(this, -size);
        // Once the totalPendingSize dropped below the low water-mark we can mark the child channel
        // as writable again. Before doing so we also need to ensure the parent channel is writable to
        // prevent excessive buffering in the parent outbound buffer. If the parent is not writable
        // we will mark the child channel as writable once the parent becomes writable by calling
        // trySetWritable() later.
        if (newWriteBufferSize < config().getWriteBufferLowWaterMark() && parent().isWritable()) {
            setWritable(invokeLater);
        }
    }

    final void trySetWritable() {
        // The parent is writable again but the child channel itself may still not be writable.
        // Lets try to set the child channel writable to match the state of the parent channel
        // if (and only if) the totalPendingSize is smaller then the low water-mark.
        // If this is not the case we will try again later once we drop under it.
        if (totalPendingSize < config().getWriteBufferLowWaterMark()) {
            setWritable(false);
        }
    }

    private void setWritable(boolean invokeLater) {
        for (;;) {
            final int oldValue = unwritable;
            final int newValue = oldValue & ~1;
            if (UNWRITABLE_UPDATER.compareAndSet(this, oldValue, newValue)) {
                if (oldValue != 0 && newValue == 0) {
                    fireChannelWritabilityChanged(invokeLater);
                }
                break;
            }
        }
    }

    private void setUnwritable(boolean invokeLater) {
        for (;;) {
            final int oldValue = unwritable;
            final int newValue = oldValue | 1;
            if (UNWRITABLE_UPDATER.compareAndSet(this, oldValue, newValue)) {
                if (oldValue == 0) {
                    fireChannelWritabilityChanged(invokeLater);
                }
                break;
            }
        }
    }

    private void fireChannelWritabilityChanged(boolean invokeLater) {
        final ChannelPipeline pipeline = pipeline();
        if (invokeLater) {
            Runnable task = fireChannelWritabilityChangedTask;
            if (task == null) {
                fireChannelWritabilityChangedTask = task = pipeline::fireChannelWritabilityChanged;
            }
            executor().execute(task);
        } else {
            pipeline.fireChannelWritabilityChanged();
        }
    }
    @Override
    public Http2FrameStream stream() {
        return stream;
    }

    void closeOutbound() {
        outboundClosed = true;
    }

    void streamClosed() {
        unsafe.readEOS();
        // Attempt to drain any queued data from the queue and deliver it to the application before closing this
        // channel.
        unsafe.doBeginRead();
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
    public boolean isOpen() {
        return !closePromise.isDone();
    }

    @Override
    public boolean isActive() {
        return isOpen();
    }

    @Override
    public boolean isWritable() {
        return unwritable == 0;
    }

    @Override
    public ChannelId id() {
        return channelId;
    }

    @Override
    public EventLoop executor() {
        return parent().executor();
    }

    @Override
    public Channel parent() {
        return parentContext().channel();
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public SocketAddress localAddress() {
        return parent().localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return parent().remoteAddress();
    }

    @Override
    public Future<Void> closeFuture() {
        return closePromise.asFuture();
    }

    @Override
    public long bytesBeforeUnwritable() {
        long bytes = config().getWriteBufferHighWaterMark() - totalPendingSize;
        // If bytes is negative we know we are not writable, but if bytes is non-negative we have to check
        // writability. Note that totalPendingSize and isWritable() use different volatile variables that are not
        // synchronized together. totalPendingSize will be updated before isWritable().
        if (bytes > 0) {
            return isWritable() ? bytes : 0;
        }
        return 0;
    }

    @Override
    public long bytesBeforeWritable() {
        long bytes = totalPendingSize - config().getWriteBufferLowWaterMark();
        // If bytes is negative we know we are writable, but if bytes is non-negative we have to check writability.
        // Note that totalPendingSize and isWritable() use different volatile variables that are not synchronized
        // together. totalPendingSize will be updated before isWritable().
        if (bytes > 0) {
            return isWritable() ? 0 : bytes;
        }
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
    public int hashCode() {
        return id().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int compareTo(Channel o) {
        if (this == o) {
            return 0;
        }

        return id().compareTo(o.id());
    }

    @Override
    public String toString() {
        return parent().toString() + "(H2 - " + stream + ')';
    }

    /**
     * Receive a read message. This does not notify handlers unless a read is in progress on the
     * channel.
     */
    void fireChildRead(Http2Frame frame) {
        assert executor().inEventLoop();
        if (!isActive()) {
            ReferenceCountUtil.release(frame);
        } else if (readStatus != ReadStatus.IDLE) {
            // If a read is in progress or has been requested, there cannot be anything in the queue,
            // otherwise we would have drained it from the queue and processed it during the read cycle.
            assert inboundBuffer == null || inboundBuffer.isEmpty();
            final RecvBufferAllocator.Handle allocHandle = unsafe.recvBufAllocHandle();
            unsafe.doRead0(frame, allocHandle);
            // We currently don't need to check for readEOS because the parent channel and child channel are limited
            // to the same EventLoop thread. There are a limited number of frame types that may come after EOS is
            // read (unknown, reset) and the trade off is less conditionals for the hot path (headers/data) at the
            // cost of additional readComplete notifications on the rare path.
            if (allocHandle.continueReading()) {
                maybeAddChannelToReadCompletePendingQueue();
            } else {
                unsafe.notifyReadComplete(allocHandle, true);
            }
        } else {
            if (inboundBuffer == null) {
                inboundBuffer = new ArrayDeque<>(4);
            }
            inboundBuffer.add(frame);
        }
    }

    void fireChildReadComplete() {
        assert executor().inEventLoop();
        assert readStatus != ReadStatus.IDLE || !readCompletePending;
        unsafe.notifyReadComplete(unsafe.recvBufAllocHandle(), false);
    }

    private final class Http2ChannelUnsafe implements Unsafe {
        private RecvBufferAllocator.Handle recvHandle;
        private boolean writeDoneAndNoFlush;
        private boolean closeInitiated;
        private boolean readEOS;

        @Override
        public void connect(final SocketAddress remoteAddress,
                            SocketAddress localAddress, Promise<Void> promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        public RecvBufferAllocator.Handle recvBufAllocHandle() {
            if (recvHandle == null) {
                recvHandle = config().getRecvBufferAllocator().newHandle();
                recvHandle.reset(config());
            }
            return recvHandle;
        }

        @Override
        public SocketAddress localAddress() {
            return parent().unsafe().localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return parent().unsafe().remoteAddress();
        }

        @Override
        public void register(Promise<Void> promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            if (registered) {
                promise.setFailure(new UnsupportedOperationException("Re-register is not supported"));
                return;
            }

            registered = true;

            promise.setSuccess(null);

            pipeline().fireChannelRegistered();
            if (isActive()) {
                pipeline().fireChannelActive();
                if (config().isAutoRead()) {
                    read();
                }
            }
        }

        @Override
        public void bind(SocketAddress localAddress, Promise<Void> promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        public void disconnect(Promise<Void> promise) {
            close(promise);
        }

        @Override
        public void close(final Promise<Void> promise) {
            if (!promise.setUncancellable()) {
                return;
            }
            if (closeInitiated) {
                if (closePromise.isDone()) {
                    // Closed already.
                    promise.setSuccess(null);
                } else  {
                    // This means close() was called before so we just register a listener and return
                    closeFuture().addListener(promise, (p, future) -> p.setSuccess(null));
                }
                return;
            }
            closeInitiated = true;
            // Just set to false as removing from an underlying queue would even be more expensive.
            readCompletePending = false;

            final boolean wasActive = isActive();

            // There is no need to update the local window as once the stream is closed all the pending bytes will be
            // given back to the connection window by the controller itself.

            // Only ever send a reset frame if the connection is still alive and if the stream was created before
            // as otherwise we may send a RST on a stream in an invalid state and cause a connection error.
            if (parent().isActive() && !readEOS && isStreamIdValid(stream.id())) {
                Http2StreamFrame resetFrame = new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream());
                write(resetFrame, newPromise());
                flush();
            }

            if (inboundBuffer != null) {
                for (;;) {
                    Object msg = inboundBuffer.poll();
                    if (msg == null) {
                        break;
                    }
                    ReferenceCountUtil.release(msg);
                }
                inboundBuffer = null;
            }

            // The promise should be notified before we call fireChannelInactive().
            outboundClosed = true;
            closePromise.setSuccess(null);
            promise.setSuccess(null);

            fireChannelInactiveAndDeregister(newPromise(), wasActive);
        }

        @Override
        public void closeForcibly() {
            close(newPromise());
        }

        @Override
        public void deregister(Promise<Void> promise) {
            fireChannelInactiveAndDeregister(promise, false);
        }

        private void fireChannelInactiveAndDeregister(Promise<Void> promise,
                                                      final boolean fireChannelInactive) {
            if (!promise.setUncancellable()) {
                return;
            }

            if (!registered) {
                promise.setSuccess(null);
                return;
            }

            // As a user may call deregister() from within any method while doing processing in the ChannelPipeline,
            // we need to ensure we do the actual deregister operation later. This is necessary to preserve the
            // behavior of the AbstractChannel, which always invokes channelUnregistered and channelInactive
            // events 'later' to ensure the current events in the handler are completed before these events.
            //
            // See:
            // https://github.com/netty/netty/issues/4435
            invokeLater(()-> {
                if (fireChannelInactive) {
                    pipeline.fireChannelInactive();
                }
                // The user can fire `deregister` events multiple times but we only want to fire the pipeline
                // event if the channel was actually registered.
                if (registered) {
                    registered = false;
                    pipeline.fireChannelUnregistered();
                }
                safeSetSuccess(promise);
            });
        }

        private void safeSetSuccess(Promise<Void> promise) {
            if (!promise.trySuccess(null)) {
                logger.warn("Failed to mark a promise as success because it is done already: {}", promise);
            }
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
                //     -> channel.unsafe.close()
                //       -> handlerA.channelInactive() - (2) another inbound handler method called while in (1) yet
                //
                // which means the execution of two inbound handler methods of the same handler overlap undesirably.
                executor().execute(task);
            } catch (RejectedExecutionException e) {
                logger.warn("Can't invoke task later as EventLoop rejected it", e);
            }
        }

        @Override
        public void beginRead() {
            if (!isActive()) {
                return;
            }
            updateLocalWindowIfNeeded();

            switch (readStatus) {
                case IDLE:
                    readStatus = ReadStatus.IN_PROGRESS;
                    doBeginRead();
                    break;
                case IN_PROGRESS:
                    readStatus = ReadStatus.REQUESTED;
                    break;
                default:
                    break;
            }
        }

        private Object pollQueuedMessage() {
            return inboundBuffer == null ? null : inboundBuffer.poll();
        }

        void doBeginRead() {
            // Process messages until there are none left (or the user stopped requesting) and also handle EOS.
            while (readStatus != ReadStatus.IDLE) {
                Object message = pollQueuedMessage();
                if (message == null) {
                    if (readEOS) {
                        unsafe.closeForcibly();
                    }
                    // We need to double check that there is nothing left to flush such as a
                    // window update frame.
                    flush();
                    break;
                }
                final RecvBufferAllocator.Handle allocHandle = recvBufAllocHandle();
                allocHandle.reset(config());
                boolean continueReading = false;
                do {
                    doRead0((Http2Frame) message, allocHandle);
                } while ((readEOS || (continueReading = allocHandle.continueReading()))
                        && (message = pollQueuedMessage()) != null);

                if (continueReading && isParentReadInProgress() && !readEOS) {
                    // Currently the parent and child channel are on the same EventLoop thread. If the parent is
                    // currently reading it is possible that more frames will be delivered to this child channel. In
                    // the case that this child channel still wants to read we delay the channelReadComplete on this
                    // child channel until the parent is done reading.
                    maybeAddChannelToReadCompletePendingQueue();
                } else {
                    notifyReadComplete(allocHandle, true);
                }
            }
        }

        void readEOS() {
            readEOS = true;
        }

        private void updateLocalWindowIfNeeded() {
            if (flowControlledBytes != 0) {
                int bytes = flowControlledBytes;
                flowControlledBytes = 0;
                Future<Void> future = write0(parentContext(), new DefaultHttp2WindowUpdateFrame(bytes).stream(stream));
                // window update frames are commonly swallowed by the Http2FrameCodec and the promise is synchronously
                // completed but the flow controller _may_ have generated a wire level WINDOW_UPDATE. Therefore we need,
                // to assume there was a write done that needs to be flushed or we risk flow control starvation.
                writeDoneAndNoFlush = true;
                // Add a listener which will notify and teardown the stream
                // when a window update fails if needed or check the result of the future directly if it was completed
                // already.
                // See https://github.com/netty/netty/issues/9663
                if (future.isDone()) {
                    windowUpdateFrameWriteComplete(AbstractHttp2StreamChannel.this, future);
                } else {
                    future.addListener(AbstractHttp2StreamChannel.this,
                                       AbstractHttp2StreamChannel::windowUpdateFrameWriteComplete);
                }
            }
        }

        void notifyReadComplete(RecvBufferAllocator.Handle allocHandle, boolean forceReadComplete) {
            if (!readCompletePending && !forceReadComplete) {
                return;
            }
            // Set to false just in case we added the channel multiple times before.
            readCompletePending = false;

            if (readStatus == ReadStatus.REQUESTED) {
                readStatus = ReadStatus.IN_PROGRESS;
            } else {
                readStatus = ReadStatus.IDLE;
            }

            allocHandle.readComplete();
            pipeline().fireChannelReadComplete();
            if (config().isAutoRead()) {
                read();
            }

            // Reading data may result in frames being written (e.g. WINDOW_UPDATE, RST, etc..). If the parent
            // channel is not currently reading we need to force a flush at the child channel, because we cannot
            // rely upon flush occurring in channelReadComplete on the parent channel.
            flush();
            if (readEOS) {
                unsafe.closeForcibly();
            }
        }

        void doRead0(Http2Frame frame, RecvBufferAllocator.Handle allocHandle) {
            final int bytes;
            if (frame instanceof Http2DataFrame) {
                bytes = ((Http2DataFrame) frame).initialFlowControlledBytes();

                // It is important that we increment the flowControlledBytes before we call fireChannelRead(...)
                // as it may cause a read() that will call updateLocalWindowIfNeeded() and we need to ensure
                // in this case that we accounted for it.
                //
                // See https://github.com/netty/netty/issues/9663
                flowControlledBytes += bytes;
            } else {
                bytes = MIN_HTTP2_FRAME_SIZE;
            }
            // Update before firing event through the pipeline to be consistent with other Channel implementation.
            allocHandle.attemptedBytesRead(bytes);
            allocHandle.lastBytesRead(bytes);
            allocHandle.incMessagesRead(1);

            pipeline().fireChannelRead(frame);
        }

        @Override
        public void write(Object msg, Promise<Void> promise) {
            // After this point its not possible to cancel a write anymore.
            if (!promise.setUncancellable()) {
                ReferenceCountUtil.release(msg);
                return;
            }

            if (!isActive() ||
                    // Once the outbound side was closed we should not allow header / data frames
                    outboundClosed && (msg instanceof Http2HeadersFrame || msg instanceof Http2DataFrame)) {
                ReferenceCountUtil.release(msg);
                promise.setFailure(new ClosedChannelException());
                return;
            }

            try {
                if (msg instanceof Http2StreamFrame) {
                    Http2StreamFrame frame = validateStreamFrame((Http2StreamFrame) msg).stream(stream());
                    writeHttp2StreamFrame(frame, promise);
                } else {
                    String msgStr = msg.toString();
                    ReferenceCountUtil.release(msg);
                    promise.setFailure(new IllegalArgumentException(
                            "Message must be an " + StringUtil.simpleClassName(Http2StreamFrame.class) +
                                    ": " + msgStr));
                }
            } catch (Throwable t) {
                promise.tryFailure(t);
            }
        }

        private void writeHttp2StreamFrame(Http2StreamFrame frame, Promise<Void> promise) {
            if (!firstFrameWritten && !isStreamIdValid(stream().id()) && !(frame instanceof Http2HeadersFrame)) {
                ReferenceCountUtil.release(frame);
                promise.setFailure(
                    new IllegalArgumentException("The first frame must be a headers frame. Was: "
                        + frame.name()));
                return;
            }

            final boolean firstWrite;
            if (firstFrameWritten) {
                firstWrite = false;
            } else {
                firstWrite = firstFrameWritten = true;
            }

            Future<Void> f = write0(parentContext(), frame);
            if (f.isDone()) {
                if (firstWrite) {
                    firstWriteComplete(f, promise);
                } else {
                    writeComplete(f, promise);
                }
            } else {
                final long bytes = FlowControlledFrameSizeEstimator.HANDLE_INSTANCE.size(frame);
                incrementPendingOutboundBytes(bytes, false);
                f.addListener(future ->  {
                    if (firstWrite) {
                        firstWriteComplete(future, promise);
                    } else {
                        writeComplete(future, promise);
                    }
                    decrementPendingOutboundBytes(bytes, false);
                });
                writeDoneAndNoFlush = true;
            }
        }

        private void firstWriteComplete(Future<?> future, Promise<Void> promise) {
            Throwable cause = future.cause();
            if (cause == null) {
                promise.setSuccess(null);
            } else {
                // If the first write fails there is not much we can do, just close
                closeForcibly();
                promise.setFailure(wrapStreamClosedError(cause));
            }
        }

        private void writeComplete(Future<?> future, Promise<Void> promise) {
            Throwable cause = future.cause();
            if (cause == null) {
                promise.setSuccess(null);
            } else {
                Throwable error = wrapStreamClosedError(cause);
                // To make it more consistent with AbstractChannel we handle all IOExceptions here.
                if (error instanceof IOException) {
                    if (config.isAutoClose()) {
                        // Close channel if needed.
                        closeForcibly();
                    } else {
                        // TODO: Once Http2StreamChannel extends DuplexChannel we should call shutdownOutput(...)
                        outboundClosed = true;
                    }
                }
                promise.setFailure(error);
            }
        }

        private Throwable wrapStreamClosedError(Throwable cause) {
            // If the error was caused by STREAM_CLOSED we should use a ClosedChannelException to better
            // mimic other transports and make it easier to reason about what exceptions to expect.
            if (cause instanceof Http2Exception && ((Http2Exception) cause).error() == Http2Error.STREAM_CLOSED) {
                return new ClosedChannelException().initCause(cause);
            }
            return cause;
        }

        private Http2StreamFrame validateStreamFrame(Http2StreamFrame frame) {
            if (frame.stream() != null && frame.stream() != stream) {
                String msgString = frame.toString();
                ReferenceCountUtil.release(frame);
                throw new IllegalArgumentException(
                        "Stream " + frame.stream() + " must not be set on the frame: " + msgString);
            }
            return frame;
        }

        @Override
        public void flush() {
            // If we are currently in the parent channel's read loop we should just ignore the flush.
            // We will ensure we trigger ctx.flush() after we processed all Channels later on and
            // so aggregate the flushes. This is done as ctx.flush() is expensive when as it may trigger an
            // write(...) or writev(...) operation on the socket.
            if (!writeDoneAndNoFlush || isParentReadInProgress()) {
                // There is nothing to flush so this is a NOOP.
                return;
            }
            // We need to set this to false before we call flush0(...) as FutureListener may produce more data
            // that are explicit flushed.
            writeDoneAndNoFlush = false;
            flush0(parentContext());
        }

        @Override
        public ChannelOutboundBuffer outboundBuffer() {
            // Always return null as we not use the ChannelOutboundBuffer and not even support it.
            return null;
        }
    }

    /**
     * {@link ChannelConfig} so that the high and low writebuffer watermarks can reflect the outbound flow control
     * window, without having to create a new {@link WriteBufferWaterMark} object whenever the flow control window
     * changes.
     */
    private static final class Http2StreamChannelConfig extends DefaultChannelConfig {
        Http2StreamChannelConfig(Channel channel) {
            super(channel);
        }

        @Override
        public MessageSizeEstimator getMessageSizeEstimator() {
            return FlowControlledFrameSizeEstimator.INSTANCE;
        }

        @Override
        public ChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
            throw new UnsupportedOperationException();
        }
    }

    private void maybeAddChannelToReadCompletePendingQueue() {
        if (!readCompletePending) {
            readCompletePending = true;
            addChannelToReadCompletePendingQueue();
        }
    }

    protected void flush0(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    protected Future<Void> write0(ChannelHandlerContext ctx, Object msg) {
        return ctx.write(msg);
    }

    protected abstract boolean isParentReadInProgress();
    protected abstract void addChannelToReadCompletePendingQueue();
    protected abstract ChannelHandlerContext parentContext();
}
