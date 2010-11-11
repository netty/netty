/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.internal.LinkedTransferQueue;

/**
 * Emulates buffered write operation.  This handler stores all write requests
 * into an unbounded {@link Queue} and flushes them to the downstream when
 * {@link #flush()} method is called.
 * <p>
 * Here is an example that demonstrates the usage:
 * <pre>
 * BufferedWriteHandler bufferedWriter = new BufferedWriteHandler();
 * ChannelPipeline p = ...;
 * p.addFirst("buffer", bufferedWriter);
 *
 * ...
 *
 * Channel ch = ...;
 *
 * // msg1, 2, and 3 are stored in the queue of bufferedWriter.
 * ch.write(msg1);
 * ch.write(msg2);
 * ch.write(msg3);
 *
 * // and will be flushed on request.
 * bufferedWriter.flush();
 * </pre>
 *
 * <h3>Auto-flush</h3>
 * The write request queue is automatically flushed when the associated
 * {@link Channel} is disconnected or closed.  However, it does not flush the
 * queue otherwise.  It means you have to call {@link #flush()} before the size
 * of the queue increases too much.  You can implement your own auto-flush
 * strategy by extending this handler:
 * <pre>
 * public class AutoFlusher extends {@link BufferedWriteHandler} {
 *
 *     private final AtomicLong bufferSize = new AtomicLong();
 *
 *     {@literal @Override}
 *     public void writeRequested({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         super.writeRequested(ctx, e);
 *
 *         {@link ChannelBuffer} data = ({@link ChannelBuffer}) e.getMessage();
 *         int newBufferSize = bufferSize.addAndGet(data.readableBytes());
 *
 *         // Flush the queue if it gets larger than 8KiB.
 *         if (newBufferSize > 8192) {
 *             flush();
 *             bufferSize.set(0);
 *         }
 *     }
 * }
 * </pre>
 *
 * <h3>Consolidate on flush</h3>
 *
 * If there are two or more write requests in the queue and all their message
 * type is {@link ChannelBuffer}, they can be merged into a single write request
 * to save the number of system calls.
 * <pre>
 * BEFORE consolidation:            AFTER consolidation:
 * +-------+-------+-------+        +-------------+
 * | Req C | Req B | Req A |------\\| Request ABC |
 * | "789" | "456" | "123" |------//| "123456789" |
 * +-------+-------+-------+        +-------------+
 * </pre>
 * This feature is disabled by default.  You can override the default when you
 * create this handler or call {@link #flush(boolean)}.  If you specified
 * {@code true} when you call the constructor, calling {@link #flush()} will
 * always consolidate the queue.  Otherwise, you have to call
 * {@link #flush(boolean)} with {@code true} to enable this feature for each
 * flush.
 * <p>
 * The disadvantage of consolidation is that the {@link ChannelFuture} and its
 * {@link ChannelFutureListener}s associated with the original write requests
 * might be notified later than when they are actually written out.  They will
 * always be notified when the consolidated write request is fully written.
 * <p>
 * The following example implements the consolidation strategy that reduces
 * the number of write requests based on the writability of a channel:
 * <pre>
 * public class ConsolidatingAutoFlusher extends {@link BufferedWriteHandler} {
 *
 *     public ConsolidatingAutoFlusher() {
 *         // Enable consolidation by default.
 *         super(true);
 *     }
 *
 *     {@literal @Override}
 *     public void channelOpen({@link ChannelHandlerContext} ctx, {@link ChannelStateEvent} e) throws Exception {
 *         {@link ChannelConfig} cfg = e.getChannel().getConfig();
 *         if (cfg instanceof {@link NioSocketChannelConfig}) {
 *             // Lower the watermark to increase the chance of consolidation.
 *             cfg.setWriteBufferLowWaterMark(0);
 *         }
 *         super.channelOpen(e);
 *     }
 *
 *     {@literal @Override}
 *     public void writeRequested({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) throws Exception {
 *         super.writeRequested(ctx, et);
 *         if (e.getChannel().isWritable()) {
 *             flush();
 *         }
 *     }
 *
 *     {@literal @Override}
 *     public void channelInterestChanged({@link ChannelHandlerContext} ctx, {@link ChannelStateEvent} e) throws Exception {
 *         if (e.getChannel().isWritable()) {
 *             flush();
 *         }
 *     }
 * }
 * </pre>
 *
 * <h3>Prioritized Writes</h3>
 *
 * You can implement prioritized writes by specifying an unbounded priority
 * queue in the constructor of this handler.  It will be required to design
 * the proper strategy to determine how often {@link #flush()} should be called.
 * For example, you could call {@link #flush()} periodically, using
 * {@link HashedWheelTimer} every second.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2243 $, $Date: 2010-04-16 14:01:55 +0900 (Fri, 16 Apr 2010) $
 *
 * @apiviz.landmark
 */
public class BufferedWriteHandler extends SimpleChannelHandler {

    private final Queue<MessageEvent> queue;
    private final boolean consolidateOnFlush;
    private volatile ChannelHandlerContext ctx;

    /**
     * Creates a new instance with the default unbounded {@link BlockingQueue}
     * implementation and without buffer consolidation.
     */
    public BufferedWriteHandler() {
        this(false);
    }

    /**
     * Creates a new instance with the specified thread-safe unbounded
     * {@link Queue} and without buffer consolidation.  Please note that
     * specifying a bounded {@link Queue} or a thread-unsafe {@link Queue} will
     * result in an unspecified behavior.
     */
    public BufferedWriteHandler(Queue<MessageEvent> queue) {
        this(queue, false);
    }

    /**
     * Creates a new instance with the default unbounded {@link BlockingQueue}
     * implementation.
     *
     * @param consolidateOnFlush
     *        {@code true} if and only if the buffered write requests are merged
     *        into a single write request on {@link #flush()}
     */
    public BufferedWriteHandler(boolean consolidateOnFlush) {
        this(new LinkedTransferQueue<MessageEvent>(), consolidateOnFlush);
    }

    /**
     * Creates a new instance with the specified thread-safe unbounded
     * {@link Queue}.  Please note that specifying a bounded {@link Queue} or
     * a thread-unsafe {@link Queue} will result in an unspecified behavior.
     *
     * @param consolidateOnFlush
     *        {@code true} if and only if the buffered write requests are merged
     *        into a single write request on {@link #flush()}
     */
    public BufferedWriteHandler(Queue<MessageEvent> queue, boolean consolidateOnFlush) {
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        this.queue = queue;
        this.consolidateOnFlush = consolidateOnFlush;
    }

    public boolean isConsolidateOnFlush() {
        return consolidateOnFlush;
    }

    /**
     * Returns the queue which stores the write requests.  The default
     * implementation returns the queue which was specified in the constructor.
     */
    protected Queue<MessageEvent> getQueue() {
        return queue;
    }

    /**
     * Sends the queued write requests to the downstream.
     */
    public void flush() {
        flush(consolidateOnFlush);
    }

    /**
     * Sends the queued write requests to the downstream.
     *
     * @param consolidateOnFlush
     *        {@code true} if and only if the buffered write requests are merged
     *        into a single write request
     */
    public void flush(boolean consolidateOnFlush) {
        final ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            // No write request was made.
            return;
        }

        final Queue<MessageEvent> queue = getQueue();
        if (consolidateOnFlush) {
            if (queue.isEmpty()) {
                return;
            }

            List<MessageEvent> pendingWrites = new ArrayList<MessageEvent>();
            synchronized (this) {
                for (;;) {
                    MessageEvent e = queue.poll();
                    if (e == null) {
                        break;
                    }
                    if (!(e.getMessage() instanceof ChannelBuffer)) {
                        if ((pendingWrites = consolidatedWrite(pendingWrites)) == null) {
                            pendingWrites = new ArrayList<MessageEvent>();
                        }
                        ctx.sendDownstream(e);
                    } else {
                        pendingWrites.add(e);
                    }
                }
                consolidatedWrite(pendingWrites);
            }
        } else {
            synchronized (this) {
                for (;;) {
                    MessageEvent e = queue.poll();
                    if (e == null) {
                        break;
                    }
                    ctx.sendDownstream(e);
                }
            }
        }
    }

    private List<MessageEvent> consolidatedWrite(final List<MessageEvent> pendingWrites) {
        final int size = pendingWrites.size();
        if (size == 1) {
            ctx.sendDownstream(pendingWrites.remove(0));
            return pendingWrites;
        } else if (size == 0) {
            return pendingWrites;
        }

        ChannelBuffer[] data = new ChannelBuffer[size];
        for (int i = 0; i < data.length; i ++) {
            data[i] = (ChannelBuffer) pendingWrites.get(i).getMessage();
        }

        ChannelBuffer composite = ChannelBuffers.wrappedBuffer(data);
        ChannelFuture future = Channels.future(ctx.getChannel());
        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future)
                    throws Exception {
                if (future.isSuccess()) {
                    for (MessageEvent e: pendingWrites) {
                        e.getFuture().setSuccess();
                    }
                } else {
                    Throwable cause = future.getCause();
                    for (MessageEvent e: pendingWrites) {
                        e.getFuture().setFailure(cause);
                    }
                }
            }
        });

        Channels.write(ctx, future, composite);
        return null;
    }

    /**
     * Stores all write requests to the queue so that they are actually written
     * on {@link #flush()}.
     */
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        if (this.ctx == null) {
            this.ctx = ctx;
        } else {
            assert this.ctx == ctx;
        }

        getQueue().add(e);
    }

    @Override
    public void disconnectRequested(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        try {
            flush(consolidateOnFlush);
        } finally {
            ctx.sendDownstream(e);
        }
    }

    @Override
    public void closeRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        try {
            flush(consolidateOnFlush);
        } finally {
            ctx.sendDownstream(e);
        }
    }
}
