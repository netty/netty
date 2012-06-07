/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.queue;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.BlockingOperationException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.QueueFactory;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Emulates blocking read operation.  This handler stores all received messages
 * into a {@link BlockingQueue} and returns the received messages when
 * {@link #read()}, {@link #read(long, TimeUnit)}, {@link #readEvent()}, or
 * {@link #readEvent(long, TimeUnit)} method is called.
 * <p>
 * Please note that this handler is only useful for the cases where there are
 * very small number of connections, such as testing and simple client-side
 * application development.
 * <p>
 * Also, any handler placed after this handler will never receive
 * {@code messageReceived}, {@code exceptionCaught}, and {@code channelClosed}
 * events, hence it should be placed in the last place in a pipeline.
 * <p>
 * Here is an example that demonstrates the usage:
 * <pre>
 * {@link BlockingReadHandler}&lt;{@link ChannelBuffer}&gt; reader =
 *         new {@link BlockingReadHandler}&lt;{@link ChannelBuffer}&gt;();
 * {@link ChannelPipeline} p = ...;
 * p.addLast("reader", reader);
 *
 * ...
 *
 * // Read a message from a channel in a blocking manner.
 * try {
 *     {@link ChannelBuffer} buf = reader.read(60, TimeUnit.SECONDS);
 *     if (buf == null) {
 *         // Connection closed.
 *     } else {
 *         // Handle the received message here.
 *     }
 * } catch ({@link BlockingReadTimeoutException} e) {
 *     // Read timed out.
 * } catch (IOException e) {
 *     // Other read errors
 * }
 * </pre>
 *
 * @param <E> the type of the received messages
 */
public class BlockingReadHandler<E> extends ChannelInboundMessageHandlerAdapter<Object> {

    private static final Object INACTIVE = new Object();

    private volatile ChannelHandlerContext ctx;
    private final BlockingQueue<Object> queue;
    private volatile boolean closed;

    /**
     * Creates a new instance with the default unbounded {@link BlockingQueue}
     * implementation.
     */
    public BlockingReadHandler() {
        this(QueueFactory.createQueue());
    }

    /**
     * Creates a new instance with the specified {@link BlockingQueue}.
     */
    public BlockingReadHandler(BlockingQueue<Object> queue) {
        if (queue == null) {
            throw new NullPointerException("queue");
        }
        this.queue = queue;
    }

    @Override
    public ChannelBufferHolder<Object> newInboundBuffer(
            ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        return ChannelBufferHolders.messageBuffer(queue);
    }

    /**
     * Returns {@code true} if and only if the {@link Channel} associated with
     * this handler has been closed.
     */
    public boolean isClosed() {
        return closed;
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
        return filter(readEvent());
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
        return filter(readEvent(timeout, unit));
    }

    @SuppressWarnings("unchecked")
    private E filter(Object e) throws IOException {
        if (e == null || e == INACTIVE) {
            return null;
        }

        if (e instanceof Throwable) {
            throw (IOException) new IOException().initCause((Throwable) e);
        }

        return (E) e;
    }

    private Object readEvent() throws InterruptedException {
        detectDeadLock();
        if (isClosed()) {
            return null;
        }

        return queue.take();
    }

    private Object readEvent(long timeout, TimeUnit unit) throws InterruptedException, BlockingReadTimeoutException {
        detectDeadLock();
        if (isClosed()) {
            return null;
        }

        Object o = queue.poll(timeout, unit);
        if (o == null) {
            throw new BlockingReadTimeoutException();
        } else {
            return o;
        }
    }

    private void detectDeadLock() {
        if (ctx.executor().inEventLoop()) {
            throw new BlockingOperationException();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        addEvent(INACTIVE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
            Throwable cause) throws Exception {
        addEvent(cause);
    }

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        addEvent(INACTIVE);
    }

    private void addEvent(Object e) {
        if (!closed) {
            if (e == INACTIVE) {
                closed = true;
            }
            queue.add(e);
        }
    }
}
