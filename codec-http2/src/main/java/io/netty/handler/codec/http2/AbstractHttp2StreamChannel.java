/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.isStreamIdValid;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ThrowableUtil;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Child {@link Channel} of another channel, for use for modeling streams as channels.
 */
abstract class AbstractHttp2StreamChannel extends AbstractChannel {

    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<AbstractHttp2StreamChannel> OUTBOUND_FLOW_CONTROL_WINDOW_UPDATER;

    /**
     * Used by subclasses to queue a close channel within the read queue. When read, it will close
     * the channel (using Unsafe) instead of notifying handlers of the message with {@code
     * channelRead()}. Additional inbound messages must not arrive after this one.
     */
    protected static final Object CLOSE_MESSAGE = new Object();
    /**
     * Used to add a message to the {@link ChannelOutboundBuffer}, so as to have it re-evaluate its writability state.
     */
    private static final Object REEVALUATE_WRITABILITY_MESSAGE = new Object();
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private static final ClosedChannelException CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ClosedChannelException(), AbstractHttp2StreamChannel.class, "doWrite(...)");
    /**
     * Number of bytes to consider non-payload messages, to determine when to stop reading. 9 is
     * arbitrary, but also the minimum size of an HTTP/2 frame. Primarily is non-zero.
     */
    private static final int ARBITRARY_MESSAGE_SIZE = 9;

    private final Http2StreamChannelConfig config = new Http2StreamChannelConfig(this);
    private final Queue<Object> inboundBuffer = new ArrayDeque<Object>(4);
    private final Runnable fireChildReadCompleteTask = new Runnable() {
        @Override
        public void run() {
            if (readInProgress) {
                readInProgress = false;
                unsafe().recvBufAllocHandle().readComplete();
                pipeline().fireChannelReadComplete();
            }
        }
    };

    private final Http2Stream2 stream;
    private boolean closed;
    private boolean readInProgress;

    /**
     * The flow control window of the remote side i.e. the number of bytes this channel is allowed to send to the remote
     * peer. The window can become negative if a channel handler ignores the channel's writability. We are using a long
     * so that we realistically don't have to worry about underflow.
     */
    @SuppressWarnings("UnusedDeclaration")
    private volatile long outboundFlowControlWindow;

    static {
        @SuppressWarnings("rawtypes")
        AtomicLongFieldUpdater<AbstractHttp2StreamChannel> updater = AtomicLongFieldUpdater.newUpdater(
                AbstractHttp2StreamChannel.class, "outboundFlowControlWindow");
        if (updater == null) {
            updater = AtomicLongFieldUpdater.newUpdater(AbstractHttp2StreamChannel.class, "outboundFlowControlWindow");
        }
        OUTBOUND_FLOW_CONTROL_WINDOW_UPDATER = updater;
    }

    protected AbstractHttp2StreamChannel(Channel parent, Http2Stream2 stream) {
        super(parent);
        this.stream = stream;
    }

    protected Http2Stream2 stream() {
        return stream;
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
        return !closed;
    }

    @Override
    public boolean isActive() {
        return isOpen();
    }

    @Override
    public boolean isWritable() {
        return isStreamIdValid(stream.id())
               // So that the channel doesn't become active before the initial flow control window has been set.
               && outboundFlowControlWindow > 0
               // Could be null if channel closed.
               && unsafe().outboundBuffer() != null
               && unsafe().outboundBuffer().isWritable();
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new Unsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return true;
    }

    @Override
    protected SocketAddress localAddress0() {
        return parent().localAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return parent().remoteAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doClose() throws Exception {
        closed = true;
        while (!inboundBuffer.isEmpty()) {
            ReferenceCountUtil.release(inboundBuffer.poll());
        }
    }

    @Override
    protected void doBeginRead() {
        if (readInProgress) {
            return;
        }

        final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        allocHandle.reset(config());
        if (inboundBuffer.isEmpty()) {
            readInProgress = true;
            return;
        }

        do {
            Object m = inboundBuffer.poll();
            if (m == null) {
                break;
            }
            if (!doRead0(m, allocHandle)) {
                // Channel closed, and already cleaned up.
                return;
            }
        } while (allocHandle.continueReading());

        allocHandle.readComplete();
        pipeline().fireChannelReadComplete();
    }

    @Override
    protected final void doWrite(ChannelOutboundBuffer in) throws Exception {
        if (closed) {
            throw CLOSED_CHANNEL_EXCEPTION;
        }
        final MessageSizeEstimator.Handle sizeEstimator = config().getMessageSizeEstimator().newHandle();
        for (;;) {
            final Object msg = in.current();
            if (msg == null) {
                break;
            }
            // TODO(buchgr): Detecting cancellation relies on ChannelOutboundBuffer internals. NOT COOL!
            if (msg == Unpooled.EMPTY_BUFFER /* The write was cancelled. */
                || msg == REEVALUATE_WRITABILITY_MESSAGE /* Write to trigger writability after window update. */) {
                in.remove();
                continue;
            }
            final int bytes = sizeEstimator.size(msg);
            /**
             * The flow control window needs to be decrement before stealing the message from the buffer (and thereby
             * decrementing the number of pending bytes). Else, when calling steal() the number of pending bytes could
             * be less than the writebuffer watermark (=flow control window) and thus trigger a writability change.
             *
             * This code must never trigger a writability change. Only reading window updates or channel writes may
             * change the channel's writability.
             */
            incrementOutboundFlowControlWindow(-bytes);
            final ChannelPromise promise = in.steal();
            if (bytes > 0) {
                promise.addListener(new ReturnFlowControlWindowOnFailureListener(bytes));
            }
            // TODO(buchgr): Should we also the change the writability if END_STREAM is set?
            try {
                doWrite(msg, promise);
            } catch (Throwable t) {
                promise.tryFailure(t);
            }
        }
        doWriteComplete();
    }

    /**
     * Process a single write. Guaranteed to eventually be followed by a {@link #doWriteComplete()},
     * which denotes the end of the batch of writes. May be called from any thread.
     */
    protected abstract void doWrite(Object msg, ChannelPromise promise) throws Exception;

    /**
     * Process end of batch of {@link #doWrite(ChannelOutboundBuffer)}s. May be called from any thread.
     */
    protected abstract void doWriteComplete();

    /**
     * {@code bytes}-count of bytes provided to {@link #fireChildRead} have been read. May be called
     * from any thread. Must not throw an exception.
     */
    protected abstract void bytesConsumed(int bytes);

    /**
     * Receive a read message. This does not notify handlers unless a read is in progress on the
     * channel. May be called from any thread.
     */
    protected void fireChildRead(final Object msg) {
        if (eventLoop().inEventLoop()) {
            fireChildRead0(msg);
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    fireChildRead0(msg);
                }
            });
        }
    }

    private void fireChildRead0(Object msg) {
        if (closed) {
            ReferenceCountUtil.release(msg);
            return;
        }
        if (readInProgress) {
            assert inboundBuffer.isEmpty();
            // Check for null because inboundBuffer doesn't support null; we want to be consistent
            // for what values are supported.
            RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
            readInProgress = doRead0(checkNotNull(msg, "msg"), allocHandle);
            if (!allocHandle.continueReading()) {
                fireChildReadCompleteTask.run();
            }
        } else {
            inboundBuffer.add(msg);
        }
    }

    protected void fireChildReadComplete() {
        if (eventLoop().inEventLoop()) {
            fireChildReadCompleteTask.run();
        } else {
            eventLoop().execute(fireChildReadCompleteTask);
        }
    }

    protected void incrementOutboundFlowControlWindow(int bytes) {
        if (bytes == 0) {
            return;
        }
        OUTBOUND_FLOW_CONTROL_WINDOW_UPDATER.addAndGet(this, bytes);
    }

    // Visible for testing
    long getOutboundFlowControlWindow() {
        return outboundFlowControlWindow;
    }

    /**
     * Returns whether reads should continue. The only reason reads shouldn't continue is that the
     * channel was just closed.
     */
    private boolean doRead0(Object msg, RecvByteBufAllocator.Handle allocHandle) {
        if (msg == CLOSE_MESSAGE) {
            allocHandle.readComplete();
            pipeline().fireChannelReadComplete();
            close();
            return false;
        }
        if (msg instanceof Http2WindowUpdateFrame) {
            Http2WindowUpdateFrame windowUpdate = (Http2WindowUpdateFrame) msg;
            incrementOutboundFlowControlWindow(windowUpdate.windowSizeIncrement());
            reevaluateWritability();
            return true;
        }
        int numBytesToBeConsumed = 0;
        if (msg instanceof Http2DataFrame) {
            numBytesToBeConsumed = dataFrameFlowControlBytes((Http2DataFrame) msg);
            allocHandle.lastBytesRead(numBytesToBeConsumed);
        } else {
            allocHandle.lastBytesRead(ARBITRARY_MESSAGE_SIZE);
        }
        allocHandle.incMessagesRead(1);
        pipeline().fireChannelRead(msg);
        if (numBytesToBeConsumed != 0) {
            bytesConsumed(numBytesToBeConsumed);
        }
        return true;
    }

    private void reevaluateWritability() {
        ChannelOutboundBuffer buffer = unsafe().outboundBuffer();
        // If the buffer is not writable but should be writable, then write and flush a dummy object
        // to trigger a writability change.
        if (!buffer.isWritable() && buffer.totalPendingWriteBytes() < config.getWriteBufferHighWaterMark()) {
            unsafe().outboundBuffer().addMessage(REEVALUATE_WRITABILITY_MESSAGE, 1, voidPromise());
            unsafe().flush();
        }
    }

    private static int dataFrameFlowControlBytes(Http2DataFrame frame) {
        return frame.content().readableBytes()
               + frame.padding()
               // +1 to account for the pad length field. See http://httpwg.org/specs/rfc7540.html#DATA
               + (frame.padding() & 1);
    }

    private final class Unsafe extends AbstractUnsafe {
        @Override
        public void connect(final SocketAddress remoteAddress,
                SocketAddress localAddress, final ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }
    }

    /**
     * Returns the flow-control size for DATA frames, and 0 for all other frames.
     */
    private static final class FlowControlledFrameSizeEstimator implements MessageSizeEstimator {

        private static final FlowControlledFrameSizeEstimator INSTANCE = new FlowControlledFrameSizeEstimator();

        private static final class EstimatorHandle implements MessageSizeEstimator.Handle {

            private static final EstimatorHandle INSTANCE = new EstimatorHandle();

            @Override
            public int size(Object msg) {
                if (msg instanceof Http2DataFrame) {
                    return dataFrameFlowControlBytes((Http2DataFrame) msg);
                }
                return 0;
            }
        }

        @Override
        public Handle newHandle() {
            return EstimatorHandle.INSTANCE;
        }
    }

    /**
     * {@link ChannelConfig} so that the high and low writebuffer watermarks can reflect the outbound flow control
     * window, without having to create a new {@link WriteBufferWaterMark} object whenever the flow control window
     * changes.
     */
    private final class Http2StreamChannelConfig extends DefaultChannelConfig {

        // TODO(buchgr): Overwrite the RecvByteBufAllocator. We only need it to implement max messages per read.
        Http2StreamChannelConfig(Channel channel) {
            super(channel);
        }

        @Override
        @Deprecated
        public int getWriteBufferHighWaterMark() {
            int window = (int) min(Integer.MAX_VALUE, outboundFlowControlWindow);
            return max(0, window);
        }

        @Override
        @Deprecated
        public int getWriteBufferLowWaterMark() {
            return getWriteBufferHighWaterMark();
        }

        @Override
        public MessageSizeEstimator getMessageSizeEstimator() {
            return FlowControlledFrameSizeEstimator.INSTANCE;
        }

        // TODO(buchgr): Throwing exceptions is not ideal. Maybe NO-OP and log a warning?
        @Override
        public WriteBufferWaterMark getWriteBufferWaterMark() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
            throw new UnsupportedOperationException();
        }

        @Override
        @Deprecated
        public ChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
            throw new UnsupportedOperationException();
        }

        @Override
        @Deprecated
        public ChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
            throw new UnsupportedOperationException();
        }

        @Override
        @Deprecated
        public ChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            throw new UnsupportedOperationException();
        }
    }

    private class ReturnFlowControlWindowOnFailureListener implements ChannelFutureListener {
        private final int bytes;

        ReturnFlowControlWindowOnFailureListener(int bytes) {
            this.bytes = bytes;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isSuccess()) {
                /**
                 * Return the flow control window of the failed data frame. We expect this code to be rarely executed
                 * and by implementing it as a window update, we don't have to worry about thread-safety.
                 */
                fireChildRead(new DefaultHttp2WindowUpdateFrame(bytes).stream(stream));
            }
        }
    }
}
