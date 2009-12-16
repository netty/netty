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

import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.internal.LinkedTransferQueue;

/**
 * Emulates buffered write operation.  This handler stores all write requests
 * into an unbounded {@link Queue} and flushed them to the downstream when
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
 * queue when the size of the queue increases.  You can implement your own
 * auto-flush strategy by extending this handler:
 * <pre>
 * public class AutoFlusher extends BufferedWriteHandler {
 *
 *     private final AtomicLong bufferSize = new AtomicLong();
 *
 *     public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) {
 *         super.writeRequested(ctx, e);
 *
 *         ChannelBuffer data = (ChannelBuffer) e.getMessage();
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
 * <h3>Prioritized Writes</h3>
 * <p>
 * You can implement prioritized writes by specifying an unbounded priority
 * queue in the constructor of this handler.  It will be required to design
 * the proper strategy to determine how often {@link #flush()} should be called.
 * In most cases, it should be enough to call {@link #flush()} periodically,
 * using {@link HashedWheelTimer} for example.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class BufferedWriteHandler extends SimpleChannelDownstreamHandler {

    private final Queue<MessageEvent> queue;
    private volatile ChannelHandlerContext ctx;

    /**
     * Creates a new instance with the default unbounded {@link BlockingQueue}
     * implementation.
     */
    public BufferedWriteHandler() {
        this(new LinkedTransferQueue<MessageEvent>());
    }

    /**
     * Creates a new instance with the specified thread-safe unbounded
     * {@link Queue}.  Please note that specifying a bounded {@link Queue} or
     * a thread-unsafe {@link Queue} will result in an unspecified behavior.
     */
    public BufferedWriteHandler(Queue<MessageEvent> queue) {
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        this.queue = queue;
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
        final ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            // No write request was made.
            return;
        }

        final Queue<MessageEvent> queue = getQueue();
        for (;;) {
            MessageEvent e = queue.poll();
            if (e == null) {
                break;
            }
            ctx.sendDownstream(e);
        }
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
            flush();
        } finally {
            ctx.sendDownstream(e);
        }
    }

    @Override
    public void closeRequested(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        try {
            flush();
        } finally {
            ctx.sendDownstream(e);
        }
    }
}
