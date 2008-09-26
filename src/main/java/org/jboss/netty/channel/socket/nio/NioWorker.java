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
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
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

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class NioWorker implements Runnable {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(NioWorker.class);

    private static final int CONSTRAINT_LEVEL = NioProviderMetadata.CONSTRAINT_LEVEL;

    private final int bossId;
    private final int id;
    private final Executor executor;
    private final AtomicBoolean started = new AtomicBoolean();
    volatile Thread thread;
    volatile Selector selector;
    final AtomicBoolean wakenUp = new AtomicBoolean();
    final ReadWriteLock selectorGuard = new ReentrantReadWriteLock();
    final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<Runnable>();

    NioWorker(int bossId, int id, Executor executor) {
        this.bossId = bossId;
        this.id = id;
        this.executor = executor;
    }

    void register(NioSocketChannel channel, ChannelFuture future) {
        boolean firstChannel = started.compareAndSet(false, true);
        Selector selector;
        if (firstChannel) {
            try {
                this.selector = selector = Selector.open();
            } catch (IOException e) {
                throw new ChannelException(
                        "Failed to create a selector.", e);
            }
        } else {
            selector = this.selector;
            if (selector == null) {
                do {
                    Thread.yield();
                    selector = this.selector;
                } while (selector == null);
            }
        }

        if (firstChannel) {
            try {
                channel.socket.register(selector, SelectionKey.OP_READ, channel);
                if (future != null) {
                    future.setSuccess();
                }
            } catch (ClosedChannelException e) {
                future.setFailure(e);
                throw new ChannelException(
                        "Failed to register a socket to the selector.", e);
            }

            boolean server = !(channel instanceof NioClientSocketChannel);
            if (server) {
                fireChannelOpen(channel);
                fireChannelBound(channel, channel.getLocalAddress());
            } else if (!((NioClientSocketChannel) channel).boundManually) {
                fireChannelBound(channel, channel.getLocalAddress());
            }
            fireChannelConnected(channel, channel.getRemoteAddress());

            String threadName =
                (server ? "New I/O server worker #"
                        : "New I/O client worker #") + bossId + '-' + id;

            executor.execute(new ThreadRenamingRunnable(this, threadName));
        } else {
            selectorGuard.readLock().lock();
            try {
                if (wakenUp.compareAndSet(false, true)) {
                    selector.wakeup();
                }

                try {
                    channel.socket.register(selector, SelectionKey.OP_READ, channel);
                    if (future != null) {
                        future.setSuccess();
                    }
                } catch (ClosedChannelException e) {
                    future.setFailure(e);
                    throw new ChannelException(
                            "Failed to register a socket to the selector.", e);
                }

                boolean server = !(channel instanceof NioClientSocketChannel);
                if (server) {
                    fireChannelOpen(channel);
                    fireChannelBound(channel, channel.getLocalAddress());
                } else if (!((NioClientSocketChannel) channel).boundManually) {
                    fireChannelBound(channel, channel.getLocalAddress());
                }
                fireChannelConnected(channel, channel.getRemoteAddress());
            } finally {
                selectorGuard.readLock().unlock();
            }
        }
    }

    public void run() {
        thread = Thread.currentThread();

        boolean shutdown = false;
        Selector selector = this.selector;
        for (;;) {
            wakenUp.set(false);

            selectorGuard.writeLock().lock();
                // This empty synchronization block prevents the selector
                // from acquiring its lock.
            selectorGuard.writeLock().unlock();

            try {
                int selectedKeyCount = selector.select(500);

                processTaskQueue();

                if (selectedKeyCount > 0) {
                    processSelectedKeys(selector.selectedKeys());
                }

                // Exit the loop when there's nothing to handle.
                // The shutdown flag is used to delay the shutdown of this
                // loop to avoid excessive Selector creation when
                // connections are registered in a one-by-one manner instead of
                // concurrent manner.
                if (selector.keys().isEmpty()) {
                    if (shutdown ||
                        executor instanceof ExecutorService && ((ExecutorService) executor).isShutdown()) {

                        selectorGuard.writeLock().lock();
                        try {
                            if (selector.keys().isEmpty()) {
                                try {
                                    selector.close();
                                } catch (IOException e) {
                                    logger.warn(
                                            "Failed to close a selector.", e);
                                } finally {
                                    this.selector = null;
                                }
                                started.set(false);
                                break;
                            } else {
                                shutdown = false;
                            }
                        } finally {
                            selectorGuard.writeLock().unlock();
                        }
                    } else {
                        // Give one more second.
                        shutdown = true;
                    }
                } else {
                    shutdown = false;
                }
            } catch (Throwable t) {
                logger.warn(
                        "Unexpected exception in the selector loop.", t);

                // Prevent possible consecutive immediate failures.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }
    }

    private void processTaskQueue() {
        for (;;) {
            final Runnable task = taskQueue.poll();
            if (task == null) {
                break;
            }

            task.run();
        }
    }

    private static void processSelectedKeys(Set<SelectionKey> selectedKeys) {
        for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
            SelectionKey k = i.next();
            i.remove();

            if (!k.isValid()) {
                close(k);
                continue;
            }

            if (k.isReadable()) {
                read(k);
            }

            if (!k.isValid()) {
                close(k);
                continue;
            }

            if (k.isWritable()) {
                write(k);
            }
        }
    }

    private static void read(SelectionKey k) {
        ScatteringByteChannel ch = (ScatteringByteChannel) k.channel();
        NioSocketChannel channel = (NioSocketChannel) k.attachment();

        ReceiveBufferSizePredictor predictor =
            channel.getConfig().getReceiveBufferSizePredictor();
        ChannelBuffer buf = ChannelBuffers.buffer(predictor.nextReceiveBufferSize());

        int ret = 0;
        int readBytes = 0;
        boolean failure = true;
        try {
            while ((ret = buf.writeBytes(ch, buf.writableBytes())) > 0) {
                readBytes += ret;
                if (!buf.writable()) {
                    break;
                }
            }
            failure = false;
        } catch (AsynchronousCloseException e) {
            // Can happen, and doesn't need a user attention.
        } catch (Throwable t) {
            fireExceptionCaught(channel, t);
        }

        if (readBytes > 0) {
            // Update the predictor.
            predictor.previousReceiveBufferSize(readBytes);

            // Fire the event.
            fireMessageReceived(channel, buf);
        }

        if (ret < 0 || failure) {
            close(k);
        }
    }

    private static void write(SelectionKey k) {
        NioSocketChannel ch = (NioSocketChannel) k.attachment();
        write(ch, false);
    }

    private static void close(SelectionKey k) {
        NioSocketChannel ch = (NioSocketChannel) k.attachment();
        close(ch, ch.getSucceededFuture());
    }

    // FIXME I/O 스레드냐 아니냐에 따라서 task queue 에 안넣거나 넣거나 IoSocketHandler, IoSocketDispatcher
    static void write(final NioSocketChannel channel, boolean mightNeedWakeup) {
        if (mightNeedWakeup) {
            NioWorker worker = channel.getWorker();
            if (worker != null && Thread.currentThread() != worker.thread) {
                if (channel.writeTaskInTaskQueue.compareAndSet(false, true)) {
                    worker.taskQueue.offer(channel.writeTask);
                }
                if (worker.wakenUp.compareAndSet(false, true)) {
                    worker.selector.wakeup();
                }
                return;
            }
        }

        if (!channel.isConnected()) {
            cleanUpWriteBuffer(channel);
            return;
        }

        final NioSocketChannelConfig cfg = channel.getConfig();
        final int writeSpinCount = cfg.getWriteSpinCount();
        final int maxWrittenBytes;
        if (cfg.isReadWriteFair()) {
            // Set limitation for the number of written bytes for read-write
            // fairness.  I used maxReadBufferSize * 3 / 2, which yields best
            // performance in my experience while not breaking fairness much.
            int previousReceiveBufferSize =
                cfg.getReceiveBufferSizePredictor().nextReceiveBufferSize();
            maxWrittenBytes = previousReceiveBufferSize + previousReceiveBufferSize >>> 1;
            writeFair(channel, mightNeedWakeup, writeSpinCount, maxWrittenBytes);
        } else {
            writeUnfair(channel, mightNeedWakeup, writeSpinCount);
        }

    }

    private static void writeUnfair(NioSocketChannel channel,
            boolean mightNeedWakeup, final int writeSpinCount) {

        boolean open = true;
        boolean addOpWrite = false;
        boolean removeOpWrite = false;

        MessageEvent evt;
        ChannelBuffer buf;
        int bufIdx;

        synchronized (channel.writeBuffer) {
            evt = channel.currentWriteEvent;
            for (;;) {
                if (evt == null) {
                    evt = channel.writeBuffer.poll();
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
                        int localWrittenBytes = buf.getBytes(
                            bufIdx,
                            channel.socket,
                            buf.writerIndex() - bufIdx);

                        if (localWrittenBytes != 0) {
                            bufIdx += localWrittenBytes;
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
                } catch (Throwable t) {
                    evt.getFuture().setFailure(t);
                    evt = null;
                    fireExceptionCaught(channel, t);
                    if (t instanceof IOException) {
                        open = false;
                        close(channel, channel.getSucceededFuture());
                    }
                }
            }
        }

        if (open) {
            if (addOpWrite) {
                setOpWrite(channel, true, mightNeedWakeup);
            } else if (removeOpWrite) {
                setOpWrite(channel, false, mightNeedWakeup);
            }
        }
    }

    private static void writeFair(NioSocketChannel channel,
            boolean mightNeedWakeup, final int writeSpinCount,
            final int maxWrittenBytes) {

        boolean open = true;
        boolean addOpWrite = false;
        boolean removeOpWrite = false;

        int writtenBytes = 0;
        MessageEvent evt;
        ChannelBuffer buf;
        int bufIdx;

        synchronized (channel.writeBuffer) {
            evt = channel.currentWriteEvent;
            for (;;) {
                if (evt == null) {
                    evt = channel.writeBuffer.poll();
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
                        int localWrittenBytes = buf.getBytes(
                            bufIdx,
                            channel.socket,
                            Math.min(
                                    maxWrittenBytes - writtenBytes,
                                    buf.writerIndex() - bufIdx));

                        if (localWrittenBytes != 0) {
                            writtenBytes += localWrittenBytes;
                            bufIdx += localWrittenBytes;
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
                } catch (Throwable t) {
                    evt.getFuture().setFailure(t);
                    evt = null;
                    fireExceptionCaught(channel, t);
                    if (t instanceof IOException) {
                        open = false;
                        close(channel, channel.getSucceededFuture());
                    }
                }
            }
        }

        if (open) {
            if (addOpWrite) {
                setOpWrite(channel, true, mightNeedWakeup);
            } else if (removeOpWrite) {
                setOpWrite(channel, false, mightNeedWakeup);
            }
        }
    }

    private static void setOpWrite(
            NioSocketChannel channel, boolean opWrite, boolean mightNeedWakeup) {
        NioWorker worker = channel.getWorker();
        if (worker == null) {
            IllegalStateException cause =
                new IllegalStateException("Channel not connected yet (null worker)");
            fireExceptionCaught(channel, cause);
            return;
        }

        Selector selector = worker.selector;
        SelectionKey key = channel.socket.keyFor(selector);
        if (key == null) {
            return;
        }
        if (!key.isValid()) {
            close(key);
            return;
        }
        int interestOps;
        boolean changed = false;
        if (opWrite) {
            if (!mightNeedWakeup) {
                interestOps = channel.getInterestOps();
                if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                    interestOps |= SelectionKey.OP_WRITE;
                    key.interestOps(interestOps);
                    changed = true;
                }
            } else {
                switch (CONSTRAINT_LEVEL) {
                case 0:
                    interestOps = channel.getInterestOps();
                    if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                        interestOps |= SelectionKey.OP_WRITE;
                        key.interestOps(interestOps);
                        if (Thread.currentThread() != worker.thread &&
                            worker.wakenUp.compareAndSet(false, true)) {
                            selector.wakeup();
                        }
                        changed = true;
                    }
                    break;
                case 1:
                case 2:
                    interestOps = channel.getInterestOps();
                    if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                        if (Thread.currentThread() == worker.thread) {
                            interestOps |= SelectionKey.OP_WRITE;
                            key.interestOps(interestOps);
                            changed = true;
                        } else {
                            worker.selectorGuard.readLock().lock();
                            try {
                                if (worker.wakenUp.compareAndSet(false, true)) {
                                    selector.wakeup();
                                }
                                interestOps |= SelectionKey.OP_WRITE;
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
        } else {
            if (!mightNeedWakeup) {
                interestOps = channel.getInterestOps();
                if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                    interestOps &= ~SelectionKey.OP_WRITE;
                    key.interestOps(interestOps);
                    changed = true;
                }
            } else {
                switch (CONSTRAINT_LEVEL) {
                case 0:
                    interestOps = channel.getInterestOps();
                    if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                        interestOps &= ~SelectionKey.OP_WRITE;
                        key.interestOps(interestOps);
                        if (Thread.currentThread() != worker.thread &&
                            worker.wakenUp.compareAndSet(false, true)) {
                            selector.wakeup();
                        }
                        changed = true;
                    }
                    break;
                case 1:
                case 2:
                    interestOps = channel.getInterestOps();
                    if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                        if (Thread.currentThread() == worker.thread) {
                            interestOps &= ~SelectionKey.OP_WRITE;
                            key.interestOps(interestOps);
                            changed = true;
                        } else {
                            worker.selectorGuard.readLock().lock();
                            try {
                                if (worker.wakenUp.compareAndSet(false, true)) {
                                    selector.wakeup();
                                }
                                interestOps &= ~SelectionKey.OP_WRITE;
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
        }

        if (changed) {
            channel.setInterestOpsNow(interestOps);
            fireChannelInterestChanged(channel, interestOps);
        }
    }

    static void close(NioSocketChannel channel, ChannelFuture future) {
        NioWorker worker = channel.getWorker();
        if (worker != null) {
            Selector selector = worker.selector;
            SelectionKey key = channel.socket.keyFor(selector);
            if (key != null) {
                key.cancel();
            }
        }

        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        try {
            channel.socket.close();
            future.setSuccess();
            if (channel.setClosed()) {
                if (connected) {
                    if (channel.getInterestOps() != Channel.OP_WRITE) {
                        channel.setInterestOpsNow(Channel.OP_WRITE);
                        fireChannelInterestChanged(channel, Channel.OP_WRITE);
                    }
                    fireChannelDisconnected(channel);
                }
                if (bound) {
                    fireChannelUnbound(channel);
                }

                cleanUpWriteBuffer(channel);
                fireChannelClosed(channel);
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private static void cleanUpWriteBuffer(NioSocketChannel channel) {
        // Create the exception only once to avoid the excessive overhead
        // caused by fillStackTrace.
        Exception cause;
        if (channel.isOpen()) {
            cause = new NotYetConnectedException();
        } else {
            cause = new ClosedChannelException();
        }

        // Clean up the stale messages in the write buffer.
        synchronized (channel.writeBuffer) {
            MessageEvent evt = channel.currentWriteEvent;
            if (evt != null) {
                channel.currentWriteEvent = null;
                channel.currentWriteIndex = 0;
                evt.getFuture().setFailure(cause);
                fireExceptionCaught(channel, cause);
            }

            for (;;) {
                evt = channel.writeBuffer.poll();
                if (evt == null) {
                    break;
                }

                evt.getFuture().setFailure(cause);
                fireExceptionCaught(channel, cause);
            }
        }
    }

    static void setInterestOps(
            NioSocketChannel channel, ChannelFuture future, int interestOps) {
        NioWorker worker = channel.getWorker();
        if (worker == null) {
            IllegalStateException cause =
                new IllegalStateException("Channel not connected yet (null worker)");
            future.setFailure(cause);
            fireExceptionCaught(channel, cause);
            return;
        }

        Selector selector = worker.selector;
        SelectionKey key = channel.socket.keyFor(selector);
        if (key == null || selector == null) {
            IllegalStateException cause =
                new IllegalStateException("Channel not connected yet (SelectionKey not found)");
            future.setFailure(cause);
            fireExceptionCaught(channel, cause);
        }

        boolean changed = false;
        try {
            switch (CONSTRAINT_LEVEL) {
            case 0:
                if (channel.getInterestOps() != interestOps) {
                    key.interestOps(interestOps);
                    if (Thread.currentThread() != worker.thread &&
                        worker.wakenUp.compareAndSet(false, true)) {
                        selector.wakeup();
                    }
                    changed = true;
                }
                break;
            case 1:
            case 2:
                if (channel.getInterestOps() != interestOps) {
                    if (Thread.currentThread() == worker.thread) {
                        key.interestOps(interestOps);
                        changed = true;
                    } else {
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

            future.setSuccess();
            if (changed) {
                channel.setInterestOpsNow(interestOps);
                fireChannelInterestChanged(channel, interestOps);
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }
}