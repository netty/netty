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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.ThrowableUtil;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Child {@link Channel} of another channel, for use for modeling streams as channels.
 */
abstract class AbstractHttp2StreamChannel extends AbstractChannel {
    /**
     * Used by subclasses to queue a close channel within the read queue. When read, it will close
     * the channel (using Unsafe) instead of notifying handlers of the message with {@code
     * channelRead()}. Additional inbound messages must not arrive after this one.
     */
    protected static final Object CLOSE_MESSAGE = new Object();
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private static final ClosedChannelException CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ClosedChannelException(), AbstractHttp2StreamChannel.class, "doWrite(...)");
    /**
     * Number of bytes to consider non-payload messages, to determine when to stop reading. 9 is
     * arbitrary, but also the minimum size of an HTTP/2 frame. Primarily is non-zero.
     */
    private static final int ARBITRARY_MESSAGE_SIZE = 9;

    private final ChannelConfig config = new DefaultChannelConfig(this);
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

    // Volatile, as parent and child channel may be on different eventloops.
    private volatile int streamId = -1;
    private boolean closed;
    private boolean readInProgress;

    protected AbstractHttp2StreamChannel(Channel parent) {
        super(parent);
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

        EventExecutor preferredExecutor = preferredEventExecutor();

        // TODO: this is pretty broken; futures should only be completed after they are processed on
        // the parent channel. However, it isn't currently possible due to ChannelOutboundBuffer's
        // behavior which requires completing the current future before getting the next message. It
        // should become easier once we have outbound flow control support.
        // https://github.com/netty/netty/issues/4941
        if (preferredExecutor.inEventLoop()) {
            for (;;) {
                Object msg = in.current();
                if (msg == null) {
                    break;
                }
                try {
                    doWrite(ReferenceCountUtil.retain(msg));
                } catch (Throwable t) {
                    // It would be nice to fail the future, but we can't do that if not on the event
                    // loop. So we instead opt for a solution that is consistent.
                    pipeline().fireExceptionCaught(t);
                }
                in.remove();
            }
            doWriteComplete();
        } else {
            // Use a copy because the original msgs will be recycled by AbstractChannel.
            final Object[] msgsCopy = new Object[in.size()];
            for (int i = 0; i < msgsCopy.length; i ++) {
                msgsCopy[i] = ReferenceCountUtil.retain(in.current());
                in.remove();
            }

            preferredExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    for (Object msg : msgsCopy) {
                        try {
                            doWrite(msg);
                        } catch (Throwable t) {
                            pipeline().fireExceptionCaught(t);
                        }
                    }
                    doWriteComplete();
                }
            });
        }
    }

    /**
     * Process a single write. Guaranteed to eventually be followed by a {@link #doWriteComplete()},
     * which denotes the end of the batch of writes. May be called from any thread.
     */
    protected abstract void doWrite(Object msg) throws Exception;

    /**
     * Process end of batch of {@link #doWrite()}s. May be called from any thread.
     */
    protected abstract void doWriteComplete();

    /**
     * The ideal thread for events like {@link #doWrite()} to be processed on. May be used for
     * efficient batching, but not required.
     */
    protected abstract EventExecutor preferredEventExecutor();

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

    /**
     * This method must only be called within the parent channel's eventloop.
     */
    protected void streamId(int streamId) {
        if (this.streamId != -1) {
            throw new IllegalStateException("Stream identifier may only be set once.");
        }
        this.streamId = ObjectUtil.checkPositiveOrZero(streamId, "streamId");
    }

    protected int streamId() {
        return streamId;
    }

    /**
     * Returns whether reads should continue. The only reason reads shouldn't continue is that the
     * channel was just closed.
     */
    private boolean doRead0(Object msg, RecvByteBufAllocator.Handle allocHandle) {
        if (msg == CLOSE_MESSAGE) {
            allocHandle.readComplete();
            pipeline().fireChannelReadComplete();
            unsafe().close(voidPromise());
            return false;
        }
        int numBytesToBeConsumed = 0;
        if (msg instanceof Http2DataFrame) {
            Http2DataFrame data = (Http2DataFrame) msg;
            numBytesToBeConsumed = data.content().readableBytes() + data.padding();
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

    private final class Unsafe extends AbstractUnsafe {
        @Override
        public void connect(final SocketAddress remoteAddress,
                SocketAddress localAddress, final ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }
    }
}
