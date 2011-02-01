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
package org.jboss.netty.channel.socket.nio;

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.ReceiveBufferSizePredictor;
import org.jboss.netty.channel.socket.nio.SocketSendBufferPool.SendBuffer;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.jboss.netty.util.internal.LinkedTransferQueue;

/**
 * A class responsible for registering channels with {@link Selector}.
 * It also implements the {@link Selector} loop.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author Daniel Bevenius (dbevenius@jboss.com)
 *
 * @version $Rev: 2376 $, $Date: 2010-10-25 03:24:20 +0900 (Mon, 25 Oct 2010) $
 */
class NioDatagramWorker implements Runnable {
    /**
     * Internal Netty logger.
     */
    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(NioDatagramWorker.class);

    /**
     * This id of this worker.
     */
    private final int id;

    /**
     * This id of the NioDatagramPipelineSink.
     */
    private final int bossId;

    /**
     * Executor used to execute {@link Runnable}s such as
     * {@link ChannelRegistionTask}.
     */
    private final Executor executor;

    /**
     * Boolean to indicate if this worker has been started.
     */
    private boolean started;

    /**
     * If this worker has been started thread will be a reference to the thread
     * used when starting. i.e. the current thread when the run method is executed.
     */
    private volatile Thread thread;

    /**
     * The NIO {@link Selector}.
     */
    volatile Selector selector;

    /**
     * Boolean that controls determines if a blocked Selector.select should
     * break out of its selection process. In our case we use a timeone for
     * the select method and the select method will block for that time unless
     * waken up.
     */
    private final AtomicBoolean wakenUp = new AtomicBoolean();

    /**
     * Lock for this workers Selector.
     */
    private final ReadWriteLock selectorGuard = new ReentrantReadWriteLock();

    /**
     * Monitor object used to synchronize selector open/close.
     */
    private final Object startStopLock = new Object();

    /**
     * Queue of {@link ChannelRegistionTask}s
     */
    private final Queue<Runnable> registerTaskQueue = new LinkedTransferQueue<Runnable>();

    /**
     * Queue of WriteTasks
     */
    private final Queue<Runnable> writeTaskQueue = new LinkedTransferQueue<Runnable>();

    private volatile int cancelledKeys; // should use AtomicInteger but we just need approximation

    private final SocketSendBufferPool sendBufferPool = new SocketSendBufferPool();

    /**
     * Sole constructor.
     *
     * @param bossId This id of the NioDatagramPipelineSink
     * @param id The id of this worker
     * @param executor the {@link Executor} used to execute {@link Runnable}s
     *                 such as {@link ChannelRegistionTask}
     */
    NioDatagramWorker(final int bossId, final int id, final Executor executor) {
        this.bossId = bossId;
        this.id = id;
        this.executor = executor;
    }

    /**
     * Registers the passed-in channel with a selector.
     *
     * @param channel the channel to register
     * @param future  the {@link ChannelFuture} that has to be notified on
     *                completion
     */
    void register(final NioDatagramChannel channel, final ChannelFuture future) {
        final Runnable channelRegTask = new ChannelRegistionTask(channel,
                future);
        Selector selector;

        synchronized (startStopLock) {
            if (!started) {
                // Open a selector if this worker didn't start yet.
                try {
                    this.selector = selector = Selector.open();
                } catch (final Throwable t) {
                    throw new ChannelException("Failed to create a selector.",
                            t);
                }

                boolean success = false;
                try {
                    // Start the main selector loop. See run() for details.
                    executor.execute(new ThreadRenamingRunnable(this,
                            "New I/O datagram worker #" + bossId + "'-'" + id));
                    success = true;
                } finally {
                    if (!success) {
                        try {
                            // Release the Selector if the execution fails.
                            selector.close();
                        } catch (final Throwable t) {
                            logger.warn("Failed to close a selector.", t);
                        }
                        this.selector = selector = null;
                        // The method will return to the caller at this point.
                    }
                }
            } else {
                // Use the existing selector if this worker has been started.
                selector = this.selector;
            }
            assert selector != null && selector.isOpen();

            started = true;

            // "Add" the registration task to the register task queue.
            boolean offered = registerTaskQueue.offer(channelRegTask);
            assert offered;
        }

        if (wakenUp.compareAndSet(false, true)) {
            selector.wakeup();
        }
    }

    /**
     * Selector loop.
     */
    public void run() {
        // Store a ref to the current thread.
        thread = Thread.currentThread();

        final Selector selector = this.selector;
        boolean shutdown = false;

        for (;;) {
            wakenUp.set(false);

            if (NioProviderMetadata.CONSTRAINT_LEVEL != 0) {
                selectorGuard.writeLock().lock();
                // This empty synchronization block prevents the selector from acquiring its lock.
                selectorGuard.writeLock().unlock();
            }

            try {
                SelectorUtil.select(selector);

                // 'wakenUp.compareAndSet(false, true)' is always evaluated
                // before calling 'selector.wakeup()' to reduce the wake-up
                // overhead. (Selector.wakeup() is an expensive operation.)
                //
                // However, there is a race condition in this approach.
                // The race condition is triggered when 'wakenUp' is set to
                // true too early.
                //
                // 'wakenUp' is set to true too early if:
                // 1) Selector is waken up between 'wakenUp.set(false)' and
                //    'selector.select(...)'. (BAD)
                // 2) Selector is waken up between 'selector.select(...)' and
                //    'if (wakenUp.get()) { ... }'. (OK)
                //
                // In the first case, 'wakenUp' is set to true and the
                // following 'selector.select(...)' will wake up immediately.
                // Until 'wakenUp' is set to false again in the next round,
                // 'wakenUp.compareAndSet(false, true)' will fail, and therefore
                // any attempt to wake up the Selector will fail, too, causing
                // the following 'selector.select(...)' call to block
                // unnecessarily.
                //
                // To fix this problem, we wake up the selector again if wakenUp
                // is true immediately after selector.select(...).
                // It is inefficient in that it wakes up the selector for both
                // the first case (BAD - wake-up required) and the second case
                // (OK - no wake-up required).

                if (wakenUp.get()) {
                    selector.wakeup();
                }

                cancelledKeys = 0;
                processRegisterTaskQueue();
                processWriteTaskQueue();
                processSelectedKeys(selector.selectedKeys());

                // Exit the loop when there's nothing to handle (the registered
                // key set is empty.
                // The shutdown flag is used to delay the shutdown of this
                // loop to avoid excessive Selector creation when
                // connections are registered in a one-by-one manner instead of
                // concurrent manner.
                if (selector.keys().isEmpty()) {
                    if (shutdown || executor instanceof ExecutorService &&
                            ((ExecutorService) executor).isShutdown()) {
                        synchronized (startStopLock) {
                            if (registerTaskQueue.isEmpty() &&
                                    selector.keys().isEmpty()) {
                                started = false;
                                try {
                                    selector.close();
                                } catch (IOException e) {
                                    logger.warn("Failed to close a selector.",
                                            e);
                                } finally {
                                    this.selector = null;
                                }
                                break;
                            } else {
                                shutdown = false;
                            }
                        }
                    } else {
                        // Give one more second.
                        shutdown = true;
                    }
                } else {
                    shutdown = false;
                }
            } catch (Throwable t) {
                logger.warn("Unexpected exception in the selector loop.", t);

                // Prevent possible consecutive immediate failures that lead to
                // excessive CPU consumption.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }
    }

    /**
     * Will go through all the {@link ChannelRegistionTask}s in the
     * task queue and run them (registering them).
     */
    private void processRegisterTaskQueue() throws IOException {
        for (;;) {
            final Runnable task = registerTaskQueue.poll();
            if (task == null) {
                break;
            }

            task.run();
            cleanUpCancelledKeys();
        }
    }

    /**
     * Will go through all the WriteTasks and run them.
     */
    private void processWriteTaskQueue() throws IOException {
        for (;;) {
            final Runnable task = writeTaskQueue.poll();
            if (task == null) {
                break;
            }

            task.run();
            cleanUpCancelledKeys();
        }
    }

    private void processSelectedKeys(final Set<SelectionKey> selectedKeys) throws IOException {
        for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
            SelectionKey k = i.next();
            i.remove();
            try {
                int readyOps = k.readyOps();
                if ((readyOps & SelectionKey.OP_READ) != 0 || readyOps == 0) {
                    if (!read(k)) {
                        // Connection already closed - no need to handle write.
                        continue;
                    }
                }
                if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                    writeFromSelectorLoop(k);
                }
            } catch (CancelledKeyException e) {
                close(k);
            }

            if (cleanUpCancelledKeys()) {
                break; // Break the loop to avoid ConcurrentModificationException
            }
        }
    }

    private boolean cleanUpCancelledKeys() throws IOException {
        if (cancelledKeys >= NioWorker.CLEANUP_INTERVAL) {
            cancelledKeys = 0;
            selector.selectNow();
            return true;
        }
        return false;
    }

    /**
     * Read is called when a Selector has been notified that the underlying channel
     * was something to be read. The channel would previously have registered its interest
     * in read operations.
     *
     * @param key The selection key which contains the Selector registration information.
     */
    private boolean read(final SelectionKey key) {
        final NioDatagramChannel channel = (NioDatagramChannel) key.attachment();
        ReceiveBufferSizePredictor predictor =
            channel.getConfig().getReceiveBufferSizePredictor();
        final ChannelBufferFactory bufferFactory = channel.getConfig().getBufferFactory();
        final DatagramChannel nioChannel = (DatagramChannel) key.channel();

        // Allocating a non-direct buffer with a max udp packge size.
        // Would using a direct buffer be more efficient or would this negatively
        // effect performance, as direct buffer allocation has a higher upfront cost
        // where as a ByteBuffer is heap allocated.
        final ByteBuffer byteBuffer = ByteBuffer.allocate(
                predictor.nextReceiveBufferSize()).order(bufferFactory.getDefaultOrder());

        boolean failure = true;
        SocketAddress remoteAddress = null;
        try {
            // Receive from the channel in a non blocking mode. We have already been notified that
            // the channel is ready to receive.
            remoteAddress = nioChannel.receive(byteBuffer);
            failure = false;
        } catch (ClosedChannelException e) {
            // Can happen, and does not need a user attention.
        } catch (Throwable t) {
            fireExceptionCaught(channel, t);
        }

        if (remoteAddress != null) {
            // Flip the buffer so that we can wrap it.
            byteBuffer.flip();

            int readBytes = byteBuffer.remaining();
            if (readBytes > 0) {
                // Update the predictor.
                predictor.previousReceiveBufferSize(readBytes);

                // Notify the interested parties about the newly arrived message.
                fireMessageReceived(
                        channel, bufferFactory.getBuffer(byteBuffer), remoteAddress);
            }
        }

        if (failure) {
            close(channel, succeededFuture(channel));
            return false;
        }

        return true;
    }

    private void close(SelectionKey k) {
        final NioDatagramChannel ch = (NioDatagramChannel) k.attachment();
        close(ch, succeededFuture(ch));
    }

    void writeFromUserCode(final NioDatagramChannel channel) {
        /*
         * Note that we are not checking if the channel is connected. Connected
         * has a different meaning in UDP and means that the channels socket is
         * configured to only send and receive from a given remote peer.
         */
        if (!channel.isBound()) {
            cleanUpWriteBuffer(channel);
            return;
        }

        if (scheduleWriteIfNecessary(channel)) {
            return;
        }

        // From here, we are sure Thread.currentThread() == workerThread.

        if (channel.writeSuspended) {
            return;
        }

        if (channel.inWriteNowLoop) {
            return;
        }

        write0(channel);
    }

    void writeFromTaskLoop(final NioDatagramChannel ch) {
        if (!ch.writeSuspended) {
            write0(ch);
        }
    }

    void writeFromSelectorLoop(final SelectionKey k) {
        NioDatagramChannel ch = (NioDatagramChannel) k.attachment();
        ch.writeSuspended = false;
        write0(ch);
    }

    private boolean scheduleWriteIfNecessary(final NioDatagramChannel channel) {
        final Thread workerThread = thread;
        if (workerThread == null || Thread.currentThread() != workerThread) {
            if (channel.writeTaskInTaskQueue.compareAndSet(false, true)) {
                // "add" the channels writeTask to the writeTaskQueue.
                boolean offered = writeTaskQueue.offer(channel.writeTask);
                assert offered;
            }

            final Selector selector = this.selector;
            if (selector != null) {
                if (wakenUp.compareAndSet(false, true)) {
                    selector.wakeup();
                }
            }
            return true;
        }

        return false;
    }

    private void write0(final NioDatagramChannel channel) {

        boolean addOpWrite = false;
        boolean removeOpWrite = false;

        long writtenBytes = 0;

        final SocketSendBufferPool sendBufferPool = this.sendBufferPool;
        final DatagramChannel ch = channel.getDatagramChannel();
        final Queue<MessageEvent> writeBuffer = channel.writeBufferQueue;
        final int writeSpinCount = channel.getConfig().getWriteSpinCount();
        synchronized (channel.writeLock) {
            // inform the channel that write is in-progress
            channel.inWriteNowLoop = true;

            // loop forever...
            for (;;) {
                MessageEvent evt = channel.currentWriteEvent;
                SendBuffer buf;
                if (evt == null) {
                    if ((channel.currentWriteEvent = evt = writeBuffer.poll()) == null) {
                        removeOpWrite = true;
                        channel.writeSuspended = false;
                        break;
                    }

                    channel.currentWriteBuffer = buf = sendBufferPool.acquire(evt.getMessage());
                } else {
                    buf = channel.currentWriteBuffer;
                }

                try {
                    long localWrittenBytes = 0;
                    SocketAddress raddr = evt.getRemoteAddress();
                    if (raddr == null) {
                        for (int i = writeSpinCount; i > 0; i --) {
                            localWrittenBytes = buf.transferTo(ch);
                            if (localWrittenBytes != 0) {
                                writtenBytes += localWrittenBytes;
                                break;
                            }
                            if (buf.finished()) {
                                break;
                            }
                        }
                    } else {
                        for (int i = writeSpinCount; i > 0; i --) {
                            localWrittenBytes = buf.transferTo(ch, raddr);
                            if (localWrittenBytes != 0) {
                                writtenBytes += localWrittenBytes;
                                break;
                            }
                            if (buf.finished()) {
                                break;
                            }
                        }
                    }

                    if (localWrittenBytes > 0 || buf.finished()) {
                        // Successful write - proceed to the next message.
                        buf.release();
                        ChannelFuture future = evt.getFuture();
                        channel.currentWriteEvent = null;
                        channel.currentWriteBuffer = null;
                        evt = null;
                        buf = null;
                        future.setSuccess();
                    } else {
                        // Not written at all - perhaps the kernel buffer is full.
                        addOpWrite = true;
                        channel.writeSuspended = true;
                        break;
                    }
                } catch (final AsynchronousCloseException e) {
                    // Doesn't need a user attention - ignore.
                } catch (final Throwable t) {
                    buf.release();
                    ChannelFuture future = evt.getFuture();
                    channel.currentWriteEvent = null;
                    channel.currentWriteBuffer = null;
                    buf = null;
                    evt = null;
                    future.setFailure(t);
                    fireExceptionCaught(channel, t);
                }
            }
            channel.inWriteNowLoop = false;
        }

        fireWriteComplete(channel, writtenBytes);

        if (addOpWrite) {
            setOpWrite(channel);
        } else if (removeOpWrite) {
            clearOpWrite(channel);
        }
    }

    private void setOpWrite(final NioDatagramChannel channel) {
        Selector selector = this.selector;
        SelectionKey key = channel.getDatagramChannel().keyFor(selector);
        if (key == null) {
            return;
        }
        if (!key.isValid()) {
            close(key);
            return;
        }

        // interestOps can change at any time and at any thread.
        // Acquire a lock to avoid possible race condition.
        synchronized (channel.interestOpsLock) {
            int interestOps = channel.getRawInterestOps();
            if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                interestOps |= SelectionKey.OP_WRITE;
                key.interestOps(interestOps);
                channel.setRawInterestOpsNow(interestOps);
            }
        }
    }

    private void clearOpWrite(NioDatagramChannel channel) {
        Selector selector = this.selector;
        SelectionKey key = channel.getDatagramChannel().keyFor(selector);
        if (key == null) {
            return;
        }
        if (!key.isValid()) {
            close(key);
            return;
        }

        // interestOps can change at any time and at any thread.
        // Acquire a lock to avoid possible race condition.
        synchronized (channel.interestOpsLock) {
            int interestOps = channel.getRawInterestOps();
            if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                interestOps &= ~SelectionKey.OP_WRITE;
                key.interestOps(interestOps);
                channel.setRawInterestOpsNow(interestOps);
            }
        }
    }

    static void disconnect(NioDatagramChannel channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        try {
            channel.getDatagramChannel().disconnect();
            future.setSuccess();
            if (connected) {
                fireChannelDisconnected(channel);
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    void close(final NioDatagramChannel channel,
            final ChannelFuture future) {
        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        try {
            channel.getDatagramChannel().close();
            cancelledKeys ++;

            if (channel.setClosed()) {
                future.setSuccess();
                if (connected) {
                    fireChannelDisconnected(channel);
                }
                if (bound) {
                    fireChannelUnbound(channel);
                }

                cleanUpWriteBuffer(channel);
                fireChannelClosed(channel);
            } else {
                future.setSuccess();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void cleanUpWriteBuffer(final NioDatagramChannel channel) {
        Exception cause = null;
        boolean fireExceptionCaught = false;

        // Clean up the stale messages in the write buffer.
        synchronized (channel.writeLock) {
            MessageEvent evt = channel.currentWriteEvent;
            if (evt != null) {
                // Create the exception only once to avoid the excessive overhead
                // caused by fillStackTrace.
                if (channel.isOpen()) {
                    cause = new NotYetBoundException();
                } else {
                    cause = new ClosedChannelException();
                }

                ChannelFuture future = evt.getFuture();
                channel.currentWriteBuffer.release();
                channel.currentWriteBuffer = null;
                channel.currentWriteEvent = null;
                evt = null;
                future.setFailure(cause);
                fireExceptionCaught = true;
            }

            Queue<MessageEvent> writeBuffer = channel.writeBufferQueue;
            if (!writeBuffer.isEmpty()) {
                // Create the exception only once to avoid the excessive overhead
                // caused by fillStackTrace.
                if (cause == null) {
                    if (channel.isOpen()) {
                        cause = new NotYetBoundException();
                    } else {
                        cause = new ClosedChannelException();
                    }
                }

                for (;;) {
                    evt = writeBuffer.poll();
                    if (evt == null) {
                        break;
                    }
                    evt.getFuture().setFailure(cause);
                    fireExceptionCaught = true;
                }
            }
        }

        if (fireExceptionCaught) {
            fireExceptionCaught(channel, cause);
        }
    }

    void setInterestOps(final NioDatagramChannel channel,
            ChannelFuture future, int interestOps) {

        boolean changed = false;
        try {
            // interestOps can change at any time and by any thread.
            // Acquire a lock to avoid possible race condition.
            synchronized (channel.interestOpsLock) {
                final Selector selector = this.selector;
                final SelectionKey key = channel.getDatagramChannel().keyFor(selector);

                if (key == null || selector == null) {
                    // Not registered to the worker yet.
                    // Set the rawInterestOps immediately; RegisterTask will pick it up.
                    channel.setRawInterestOpsNow(interestOps);
                    return;
                }

                // Override OP_WRITE flag - a user cannot change this flag.
                interestOps &= ~Channel.OP_WRITE;
                interestOps |= channel.getRawInterestOps() & Channel.OP_WRITE;

                switch (NioProviderMetadata.CONSTRAINT_LEVEL) {
                case 0:
                    if (channel.getRawInterestOps() != interestOps) {
                        // Set the interesteOps on the SelectionKey
                        key.interestOps(interestOps);
                        // If the worker thread (the one that that might possibly be blocked
                        // in a select() call) is not the thread executing this method wakeup
                        // the select() operation.
                        if (Thread.currentThread() != thread &&
                                wakenUp.compareAndSet(false, true)) {
                            selector.wakeup();
                        }
                        changed = true;
                    }
                    break;
                case 1:
                case 2:
                    if (channel.getRawInterestOps() != interestOps) {
                        if (Thread.currentThread() == thread) {
                            // Going to set the interestOps from the same thread.
                            // Set the interesteOps on the SelectionKey
                            key.interestOps(interestOps);
                            changed = true;
                        } else {
                            // Going to set the interestOps from a different thread
                            // and some old provides will need synchronization.
                            selectorGuard.readLock().lock();
                            try {
                                if (wakenUp.compareAndSet(false, true)) {
                                    selector.wakeup();
                                }
                                key.interestOps(interestOps);
                                changed = true;
                            } finally {
                                selectorGuard.readLock().unlock();
                            }
                        }
                    }
                    break;
                default:
                    throw new Error();
                }
                if (changed) {
                    channel.setRawInterestOpsNow(interestOps);
                }
            }

            future.setSuccess();
            if (changed) {
                fireChannelInterestChanged(channel);
            }
        } catch (final CancelledKeyException e) {
            // setInterestOps() was called on a closed channel.
            ClosedChannelException cce = new ClosedChannelException();
            future.setFailure(cce);
            fireExceptionCaught(channel, cce);
        } catch (final Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    /**
     * RegisterTask is a task responsible for registering a channel with a
     * selector.
     */
    private final class ChannelRegistionTask implements Runnable {
        private final NioDatagramChannel channel;

        private final ChannelFuture future;

        ChannelRegistionTask(final NioDatagramChannel channel,
                final ChannelFuture future) {
            this.channel = channel;
            this.future = future;
        }

        /**
         * This runnable's task. Does the actual registering by calling the
         * underlying DatagramChannels peer DatagramSocket register method.
         *
         */
        public void run() {
            final SocketAddress localAddress = channel.getLocalAddress();
            if (localAddress == null) {
                if (future != null) {
                    future.setFailure(new ClosedChannelException());
                }
                close(channel, succeededFuture(channel));
                return;
            }

            try {
                synchronized (channel.interestOpsLock) {
                    channel.getDatagramChannel().register(
                            selector, channel.getRawInterestOps(), channel);
                }
                if (future != null) {
                    future.setSuccess();
                }
            } catch (final ClosedChannelException e) {
                if (future != null) {
                    future.setFailure(e);
                }
                close(channel, succeededFuture(channel));
                throw new ChannelException(
                        "Failed to register a socket to the selector.", e);
            }
        }
    }
}
