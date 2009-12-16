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

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.util.internal.IoWorkerRunnable;
import org.jboss.netty.util.internal.LinkedTransferQueue;

/**
 * Emulates blocking read operation.  This handler stores all received messages
 * into a {@link BlockingQueue} and returns the received messages when
 * {@link #read()} or {@link #read(long, TimeUnit)} method is called.
 * <p>
 * Please note that this handler is only useful for the cases where there are
 * very small number of connections, such as testing and simple client-side
 * application development.
 * <p>
 * Also, any handler placed after this handler will never receive a
 * {@code messageReceived} event and an {@code exceptionCaught} event, hence it
 * should be placed in the last place in a pipeline.
 * <p>
 * Here is an example that demonstrates the usage:
 * <pre>
 * BlockingReadHandler&lt;ChannelBuffer&gt; reader =
 *         new BlockingReadHandler&lt;ChannelBuffer&gt;();
 * ChannelPipeline p = ...;
 * p.addLast("reader", reader);
 *
 * ...
 *
 * // Read a message from a channel in a blocking manner.
 * try {
 *     ChannelBuffer buf = reader.read(60, TimeUnit.SECONDS);
 *     if (buf == null) {
 *         // Connection closed.
 *     } else {
 *         // Handle the received message here.
 *     }
 * } catch (BlockingReadTimeoutException e) {
 *     // Read timed out.
 * } catch (IOException e) {
 *     // Other read errors
 * }
 * </pre>
 *
 * @param <E> the type of the received messages
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class BlockingReadHandler<E> extends SimpleChannelUpstreamHandler
                            implements LifeCycleAwareChannelHandler {

    private final BlockingQueue<ChannelEvent> queue;
    private volatile Channel channel;

    /**
     * Creates a new instance with the default unbounded {@link BlockingQueue}
     * implementation.
     */
    public BlockingReadHandler() {
        this(new LinkedTransferQueue<ChannelEvent>());
    }

    /**
     * Creates a new instance with the specified {@link BlockingQueue}.
     */
    public BlockingReadHandler(BlockingQueue<ChannelEvent> queue) {
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        this.queue = queue;
    }

    /**
     * Returns the queue which stores the received messages.  The default
     * implementation returns the queue which was specified in the constructor.
     */
    protected BlockingQueue<ChannelEvent> getQueue() {
        return queue;
    }

    /**
     * Returns {@code true} if and only if the {@link Channel} associated with
     * this handler has been closed.
     *
     * @throws IllegalStateException
     *         if this handler was not added to a {@link ChannelPipeline} yet
     */
    public boolean isClosed() {
        Channel ch = channel;
        if (ch == null) {
            throw new IllegalStateException("not added to the pipeline yet");
        }

        return ch.getCloseFuture().isDone();
    }

    /**
     * Waits until a new message is received or the associated {@link Channel}
     * is closed.
     *
     * @return the received message or {@code null} if the associated
     *         {@link Channel} has been closed
     * @throws IOException
     *         if failed to receive a new message
     * @throws InterruptedException
     *         if the operation has been interrupted
     */
    public E read() throws IOException, InterruptedException {
        ChannelEvent e = readEvent();
        if (e == null) {
            return null;
        }

        if (e instanceof MessageEvent) {
            return getMessage((MessageEvent) e);
        } else if (e instanceof ExceptionEvent) {
            throw (IOException) new IOException().initCause(((ExceptionEvent) e).getCause());
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Waits until a new message is received or the associated {@link Channel}
     * is closed.
     *
     * @param timeout
     *        the amount time to wait until a new message is received.
     *        If no message is received within the timeout,
     *        {@link BlockingReadTimeoutException} is thrown.
     * @param unit
     *        the unit of {@code timeout}
     *
     * @return the received message or {@code null} if the associated
     *         {@link Channel} has been closed
     * @throws BlockingReadTimeoutException
     *         if no message was received within the specified timeout
     * @throws IOException
     *         if failed to receive a new message
     * @throws InterruptedException
     *         if the operation has been interrupted
     */
    public E read(long timeout, TimeUnit unit) throws IOException, InterruptedException {
        ChannelEvent e = readEvent(timeout, unit);
        if (e == null) {
            return null;
        }

        if (e instanceof MessageEvent) {
            return getMessage((MessageEvent) e);
        } else if (e instanceof ExceptionEvent) {
            throw (IOException) new IOException().initCause(((ExceptionEvent) e).getCause());
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Waits until a new {@link ChannelEvent} is received or the associated
     * {@link Channel} is closed.
     *
     * @return a {@link MessageEvent} or an {@link ExceptionEvent}.
     *         {@code null} if the associated {@link Channel} has been closed
     * @throws InterruptedException
     *         if the operation has been interrupted
     */
    public ChannelEvent readEvent() throws InterruptedException {
        detectDeadLock();
        if (isClosed()) {
            if (getQueue().isEmpty()) {
                return null;
            }
        }

        return getQueue().take();
    }

    /**
     * Waits until a new {@link ChannelEvent} is received or the associated
     * {@link Channel} is closed.
     *
     * @param timeout
     *        the amount time to wait until a new {@link ChannelEvent} is
     *        received.  If no message is received within the timeout,
     *        {@link BlockingReadTimeoutException} is thrown.
     * @param unit
     *        the unit of {@code timeout}
     *
     * @return a {@link MessageEvent} or an {@link ExceptionEvent}.
     *         {@code null} if the associated {@link Channel} has been closed
     * @throws BlockingReadTimeoutException
     *         if no event was received within the specified timeout
     * @throws InterruptedException
     *         if the operation has been interrupted
     */
    public ChannelEvent readEvent(long timeout, TimeUnit unit) throws InterruptedException, BlockingReadTimeoutException {
        detectDeadLock();
        if (isClosed()) {
            if (getQueue().isEmpty()) {
                return null;
            }
        }

        ChannelEvent e = getQueue().poll(timeout, unit);
        if (e == null) {
            throw new BlockingReadTimeoutException();
        } else {
            return e;
        }
    }

    private void detectDeadLock() {
        if (IoWorkerRunnable.IN_IO_THREAD.get()) {
            throw new IllegalStateException(
                    "read(...) in I/O thread causes a dead lock or " +
                    "sudden performance drop. Implement a state machine or " +
                    "call await*() from a different thread.");
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        getQueue().put(e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        getQueue().put(e);
    }


    @SuppressWarnings("unchecked")
    private E getMessage(MessageEvent e) {
        return (E) e.getMessage();
    }

    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        if (channel != null) {
            throw new IllegalStateException(
                    "cannot be added to multiple pipelines");
        }
        channel = ctx.getChannel();
    }

    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }
}
