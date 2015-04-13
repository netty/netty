/*
 * Copyright 2015 The Netty Project
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

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.OneTimeTask;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Child {@link Channel} of another channel, for use for modeling streams as channels.
 */
abstract class AbstractHttp2StreamChannel extends AbstractChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private static final int MAX_READER_STACK_DEPTH = 8;

    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final Queue<Object> inboundBuffer = new ArrayDeque<Object>();
    private final Runnable readTask = new Runnable() {
        @Override
        public void run() {
            ChannelPipeline pipeline = pipeline();
            int maxMessagesPerRead = config.getMaxMessagesPerRead();
            for (int messages = 0; messages < maxMessagesPerRead; messages++) {
                Object m = inboundBuffer.poll();
                if (m == null) {
                    break;
                }
                if (!doRead0(m)) {
                    return;
                }
            }
            pipeline.fireChannelReadComplete();
        }
    };
    private final Runnable fireChildReadCompleteTask = new Runnable() {
        @Override
        public void run() {
            if (readInProgress) {
                readInProgress = false;
                pipeline().fireChannelReadComplete();
            }
        }
    };

    private boolean closed;
    private boolean readInProgress;

    public AbstractHttp2StreamChannel(Channel parent) {
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
        return !closed;
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
    protected void doBind(SocketAddress localAddress) throws Exception { }

    @Override
    protected void doDisconnect() throws Exception {
        closed = true;
    }

    @Override
    protected void doClose() throws Exception {
        closed = true;
        while (!inboundBuffer.isEmpty()) {
            ReferenceCountUtil.release(inboundBuffer.poll());
        }
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (readInProgress) {
            return;
        }

        if (inboundBuffer.isEmpty()) {
            readInProgress = true;
            return;
        }

        final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();
        final Integer stackDepth = threadLocals.localChannelReaderStackDepth();
        if (stackDepth < MAX_READER_STACK_DEPTH) {
            threadLocals.setLocalChannelReaderStackDepth(stackDepth + 1);
            try {
                readTask.run();
            } finally {
                threadLocals.setLocalChannelReaderStackDepth(stackDepth);
            }
        } else {
            eventLoop().execute(readTask);
        }
    }

    @Override
    protected final void doWrite(ChannelOutboundBuffer in) throws Exception {
        if (closed) {
            throw new ClosedChannelException();
        }

        EventExecutor preferredExecutor = doWritePreferredEventExecutor();

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

            preferredExecutor.execute(new OneTimeTask() {
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

    protected abstract void doWrite(Object msg) throws Exception;

    protected abstract void doWriteComplete();

    protected abstract EventExecutor doWritePreferredEventExecutor();

    protected abstract void bytesConsumed(int bytes);

    protected void fireChildRead(final Object msg) {
        if (eventLoop().inEventLoop()) {
            fireChildRead0(msg);
        } else {
            eventLoop().execute(new OneTimeTask() {
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
            doRead0(msg);
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
     * Returns whether reads should continue.
     */
    private boolean doRead0(Object msg) {
        if (msg instanceof CloseMessage) {
            pipeline().fireChannelReadComplete();
            unsafe().close(newPromise());
            return false;
        }
        int numBytesToBeConsumed = 0;
        if (msg instanceof Http2DataFrame) {
            Http2DataFrame data = (Http2DataFrame) msg;
            numBytesToBeConsumed = data.content().readableBytes() + data.padding();
        }
        pipeline().fireChannelRead(msg);
        if (numBytesToBeConsumed != 0) {
            bytesConsumed(numBytesToBeConsumed);
        }
        return true;
    }

    private class Unsafe extends AbstractUnsafe {
        @Override
        public void connect(final SocketAddress remoteAddress,
                SocketAddress localAddress, final ChannelPromise promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            promise.setSuccess();
        }
    }

    /**
     * Used by subclasses to queue a close channel within the read queue. When read, it will close
     * the channel (using Unsafe) instead of notifying handlers of the message with {@code
     * channelRead()}. Additional inbound messages must not arrive after this one.
     */
    protected static final class CloseMessage { }
}
