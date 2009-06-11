/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
import java.nio.channels.NotYetConnectedException;
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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.jboss.netty.util.internal.LinkedTransferQueue;

/**
 * NioUdpWorker is responsible for registering channels with selector, and
 * also manages the select process.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Daniel Bevenius (dbevenius@jboss.com)
 * @version $Rev$, $Date$
 */
class NioUdpWorker implements Runnable {
    /**
     * Internal Netty logger.
     */
    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(NioUdpWorker.class);

    /**
     * Maximum packate size for UDP packets.
     * 65,536-byte maximum size of an IP datagram minus the 20-byte size of the IP header and the 8-byte size of the UDP header.
     */
    private static int MAX_PACKET_SIZE = 65507;

    /**
     * This id of this worker.
     */
    private final int id;

    /**
     * This id of the NioDatagramPipelineSink.
     */
    private final int bossId;

    /**
     * Executor used to exeucte runnables such as {@link ChannelRegistionTask}.
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
     * woken up.
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

    /**
     * Sole constructor.
     *
     * @param bossId This id of the NioDatagramPipelineSink.
     * @param id The id of this worker.
     * @param executor Executor used to exeucte runnables such as {@link ChannelRegistionTask}.
     */
    NioUdpWorker(final int bossId, final int id, final Executor executor) {
        this.bossId = bossId;
        this.id = id;
        this.executor = executor;
    }

    /**
     * Registers the passed-in channel with a selector.
     *
     * @param channel The channel to register.
     * @param future
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
                            "New I/O server worker #" + bossId + "'-'" + id));
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

            //
            if (NioProviderMetadata.CONSTRAINT_LEVEL != 0) {
                selectorGuard.writeLock().lock();
                // This empty synchronization block prevents the selector from acquiring its lock.
                selectorGuard.writeLock().unlock();
            }

            try {
                int selectedKeyCount = selector.select(500);

                // Wake up immediately in the next turn if someone might
                // have waken up the selector between 'wakenUp.set(false)'
                // and 'selector.select(...)'.
                if (wakenUp.get()) {
                    selector.wakeup();
                }

                processRegisterTaskQueue();
                processWriteTaskQueue();

                if (selectedKeyCount > 0) {
                    processSelectedKeys(selector.selectedKeys());
                }

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
    private void processRegisterTaskQueue() {
        for (;;) {
            final Runnable task = registerTaskQueue.poll();
            if (task == null) {
                break;
            }

            task.run();
        }
    }

    /**
     * Will go through all the WriteTasks and run them.
     */
    private void processWriteTaskQueue() {
        for (;;) {
            final Runnable task = writeTaskQueue.poll();
            if (task == null) {
                break;
            }

            task.run();
        }
    }

    private static void processSelectedKeys(final Set<SelectionKey> selectedKeys) {
        for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
            final SelectionKey key = i.next();
            i.remove();
            try {
                if (key.isReadable()) {
                    read(key);
                }
                if (key.isWritable()) {
                    write(key);
                }
            } catch (final CancelledKeyException ignore) {
                close(key);
            }
        }
    }

    private static void write(SelectionKey k) {
        write((NioDatagramChannel) k.attachment(), false);
    }

    /**
     * Read is called when a Selector has been notified that the underlying channel
     * was something to be read. The channel would previously have registered its interest
     * in read operations.
     *
     * @param key The selection key which contains the Selector registration information.
     */
    private static void read(final SelectionKey key) {
        final NioDatagramChannel nioDatagramChannel = (NioDatagramChannel) key
                .attachment();

        final DatagramChannel datagramChannel = (DatagramChannel) key.channel();
        try {
            // Allocating a non-direct buffer with a max udp packge size.
            // Would using a direct buffer be more efficient or would this negatively
            // effect performance, as direct buffer allocation has a higher upfront cost
            // where as a ByteBuffer is heap allocated.
            final ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_PACKET_SIZE);

            // Recieve from the channel in a non blocking mode. We have already been notified that
            // the channel is ready to receive.
            final SocketAddress remoteAddress = datagramChannel
                    .receive(byteBuffer);

            // Flip the buffer so that we can wrap it.
            byteBuffer.flip();
            // Create a Netty ChannelByffer by wrapping the ByteBuffer.
            final ChannelBuffer channelBuffer = ChannelBuffers
                    .wrappedBuffer(byteBuffer);

            logger.debug("ChannelBuffer : " + channelBuffer +
                    ", remoteAdress: " + remoteAddress);

            // Notify the interested parties about the newly arrived message (channelBuffer).
            fireMessageReceived(nioDatagramChannel, channelBuffer,
                    remoteAddress);
        } catch (final Throwable t) {
            if (!nioDatagramChannel.getDatagramChannel().socket().isClosed()) {
                fireExceptionCaught(nioDatagramChannel, t);
            }
        }
    }

    private static void close(SelectionKey k) {
        final NioDatagramChannel ch = (NioDatagramChannel) k.attachment();
        close(ch, succeededFuture(ch));
    }

    static void write(final NioDatagramChannel channel,
            final boolean mightNeedWakeup) {
        /*
         * Note that we are not checking if the channel is connected. Connected has a different
         * meaning in UDP and means that the channels socket is configured to only send and
         * recieve from a given remote peer.
         */
        if (!channel.isOpen()) {
            cleanUpWriteBuffer(channel);
            return;
        }

        if (mightNeedWakeup && scheduleWriteIfNecessary(channel)) {
            return;
        }

        if (channel.inWriteNowLoop) {
            scheduleWriteIfNecessary(channel);
        } else {
            writeNow(channel, channel.getConfig().getWriteSpinCount());
        }
    }

    private static boolean scheduleWriteIfNecessary(
            final NioDatagramChannel channel) {
        final NioUdpWorker worker = channel.worker;
        final Thread workerThread = worker.thread;

        if (workerThread == null || Thread.currentThread() != workerThread) {
            if (channel.writeTaskInTaskQueue.compareAndSet(false, true)) {
                // "add" the channels writeTask to the writeTaskQueue.
                boolean offered = worker.writeTaskQueue
                        .offer(channel.writeTask);
                assert offered;
            }

            final Selector workerSelector = worker.selector;
            if (workerSelector != null) {
                if (worker.wakenUp.compareAndSet(false, true)) {
                    workerSelector.wakeup();
                }
            }
            return true;
        }

        return false;
    }

    private static void writeNow(final NioDatagramChannel channel,
            final int writeSpinCount) {
        boolean open = true;
        boolean addOpWrite = false;
        boolean removeOpWrite = false;

        MessageEvent evt;
        ChannelBuffer buf;
        int bufIdx;
        int writtenBytes = 0;

        Queue<MessageEvent> writeBuffer = channel.writeBufferQueue;
        synchronized (channel.writeLock) {
            // inform the channel that write is in-progress
            channel.inWriteNowLoop = true;
            // get the write event.
            evt = channel.currentWriteEvent;

            // loop forever...
            for (;;) {
                if (evt == null) {
                    evt = writeBuffer.poll();
                    if (evt == null) {
                        channel.currentWriteEvent = null;
                        removeOpWrite = true;
                        break;
                    }

                    buf = (ChannelBuffer) evt.getMessage();
                    bufIdx = buf.readerIndex();
                } else {
                    buf = (ChannelBuffer) evt.getMessage();
                    bufIdx = channel.currentWriteIndex;
                }

                try {
                    for (int i = writeSpinCount; i > 0; i --) {
                        ChannelBuffer buffer = (ChannelBuffer) evt.getMessage();
                        int localWrittenBytes = channel.getDatagramChannel()
                                .send(buffer.toByteBuffer(),
                                        evt.getRemoteAddress());
                        if (localWrittenBytes != 0) {
                            bufIdx += localWrittenBytes;
                            writtenBytes += localWrittenBytes;
                            break;
                        }
                    }

                    if (bufIdx == buf.writerIndex()) {
                        // Successful write - proceed to the next message.
                        evt.getFuture().setSuccess();
                        evt = null;
                    } else {
                        // Not written fully - perhaps the kernel buffer is full.
                        channel.currentWriteEvent = evt;
                        channel.currentWriteIndex = bufIdx;
                        addOpWrite = true;
                        break;
                    }
                } catch (final AsynchronousCloseException e) {
                    // Doesn't need a user attention - ignore.
                } catch (final Throwable t) {
                    evt.getFuture().setFailure(t);
                    evt = null;
                    fireExceptionCaught(channel, t);
                    if (t instanceof IOException) {
                        open = false;
                        close(channel, succeededFuture(channel));
                    }
                }
            }
            channel.inWriteNowLoop = false;
        }

        fireWriteComplete(channel, writtenBytes);

        if (open) {
            if (addOpWrite) {
                setOpWrite(channel);
            } else if (removeOpWrite) {
                clearOpWrite(channel);
            }
        }
    }

    private static void setOpWrite(final NioDatagramChannel channel) {
        NioUdpWorker worker = channel.worker;
        Selector selector = worker.selector;
        SelectionKey key = channel.getDatagramChannel().keyFor(selector);
        if (key == null) {
            return;
        }
        if (!key.isValid()) {
            close(key);
            return;
        }
        int interestOps;
        boolean changed = false;

        // interestOps can change at any time and at any thread.
        // Acquire a lock to avoid possible race condition.
        synchronized (channel.interestOpsLock) {
            interestOps = channel.getRawInterestOps();
            if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                interestOps |= SelectionKey.OP_WRITE;
                key.interestOps(interestOps);
                changed = true;
            }
        }

        if (changed) {
            channel.setRawInterestOpsNow(interestOps);
        }
    }

    private static void clearOpWrite(NioDatagramChannel channel) {
        NioUdpWorker worker = channel.worker;
        Selector selector = worker.selector;
        SelectionKey key = channel.getDatagramChannel().keyFor(selector);
        if (key == null) {
            return;
        }
        if (!key.isValid()) {
            close(key);
            return;
        }
        int interestOps;
        boolean changed = false;

        // interestOps can change at any time and at any thread.
        // Acquire a lock to avoid possible race condition.
        synchronized (channel.interestOpsLock) {
            interestOps = channel.getRawInterestOps();
            if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                interestOps &= ~SelectionKey.OP_WRITE;
                key.interestOps(interestOps);
                changed = true;
            }
        }

        if (changed) {
            channel.setRawInterestOpsNow(interestOps);
        }
    }

    static void close(final NioDatagramChannel channel,
            final ChannelFuture future) {
        NioUdpWorker worker = channel.worker;
        Selector selector = worker.selector;
        SelectionKey key = channel.getDatagramChannel().keyFor(selector);
        if (key != null) {
            key.cancel();
        }

        boolean connected = channel.isOpen();
        boolean bound = channel.isBound();
        try {
            channel.getDatagramChannel().close();
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

    private static void cleanUpWriteBuffer(final NioDatagramChannel channel) {
        // Create the exception only once to avoid the excessive overhead
        // caused by fillStackTrace.
        Exception cause;
        if (channel.isOpen()) {
            cause = new NotYetConnectedException();
        } else {
            cause = new ClosedChannelException();
        }

        // Clean up the stale messages in the write buffer.
        synchronized (channel.writeLock) {
            MessageEvent evt = channel.currentWriteEvent;
            if (evt != null) {
                channel.currentWriteEvent = null;
                channel.currentWriteIndex = 0;
                evt.getFuture().setFailure(cause);
                fireExceptionCaught(channel, cause);
            }

            Queue<MessageEvent> writeBuffer = channel.writeBufferQueue;
            for (;;) {
                evt = writeBuffer.poll();
                if (evt == null) {
                    break;
                }
                evt.getFuture().setFailure(cause);
                fireExceptionCaught(channel, cause);
            }
        }
    }

    static void setInterestOps(final NioDatagramChannel channel,
            ChannelFuture future, int interestOps) {
        final NioUdpWorker worker = channel.worker;
        final Selector selector = worker.selector;

        final SelectionKey key = channel.getDatagramChannel().keyFor(selector);
        if (key == null || selector == null) {
            Exception cause = new NotYetConnectedException();
            future.setFailure(cause);
            fireExceptionCaught(channel, cause);
        }

        boolean changed = false;
        try {
            // interestOps can change at any time and by any thread.
            // Acquire a lock to avoid possible race condition.
            synchronized (channel.interestOpsLock) {
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
                        if (Thread.currentThread() != worker.thread &&
                                worker.wakenUp.compareAndSet(false, true)) {
                            selector.wakeup();
                        }
                        changed = true;
                    }
                    break;
                case 1:
                case 2:
                    if (channel.getRawInterestOps() != interestOps) {
                        if (Thread.currentThread() == worker.thread) {
                            // Going to set the interestOps from the same thread.
                            // Set the interesteOps on the SelectionKey
                            key.interestOps(interestOps);
                            changed = true;
                        } else {
                            // Going to set the interestOps from a different thread
                            // and some old provides will need synchronization.
                            worker.selectorGuard.readLock().lock();
                            try {
                                if (worker.wakenUp.compareAndSet(false, true)) {
                                    selector.wakeup();
                                }
                                key.interestOps(interestOps);
                                changed = true;
                            } finally {
                                worker.selectorGuard.readLock().unlock();
                            }
                        }
                    }
                    break;
                default:
                    throw new Error();
                }
            }

            future.setSuccess();
            if (changed) {
                channel.setRawInterestOpsNow(interestOps);
                fireChannelInterestChanged(channel);
            }
        } catch (final Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    /**
     * RegisterTask is a task responsible for registering a channel with a
     * selector.
     *
     * @author <a href="mailto:dbevenius@jboss.com">Daniel Bevenius</a>
     *
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
                // Register interest in both reads and writes.
                channel.getDatagramChannel().register(selector,
                        SelectionKey.OP_READ | SelectionKey.OP_WRITE, channel);
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
            fireChannelConnected(channel, localAddress);
        }
    }
}
