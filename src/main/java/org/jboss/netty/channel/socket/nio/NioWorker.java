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
import org.jboss.netty.util.LinkedTransferQueue;
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
    private static final boolean USE_DIRECT_BUFFER = false;  // Hard-coded for now

    private final int bossId;
    private final int id;
    private final Executor executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Thread thread;
    private volatile Selector selector;
    private final AtomicBoolean wakenUp = new AtomicBoolean();
    private final ReadWriteLock selectorGuard = new ReentrantReadWriteLock();
    private final Object shutdownLock = new Object();
    private final Queue<Runnable> registerTaskQueue = new LinkedTransferQueue<Runnable>();
    private final Queue<Runnable> writeTaskQueue = new LinkedTransferQueue<Runnable>();

    NioWorker(int bossId, int id, Executor executor) {
        this.bossId = bossId;
        this.id = id;
        this.executor = executor;
    }

    void register(NioSocketChannel channel, ChannelFuture future) {
        boolean firstChannel = started.compareAndSet(false, true);
        Selector selector;
        if (firstChannel) {
            selectorGuard.writeLock().lock();
            try {
                this.selector = selector = Selector.open();
            } catch (IOException e) {
                throw new ChannelException(
                        "Failed to create a selector.", e);
            } finally {
                selectorGuard.writeLock().unlock();
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

        boolean server = !(channel instanceof NioClientSocketChannel);
        Runnable registerTask = new RegisterTask(selector, channel, future, server);
        if (firstChannel) {
            registerTask.run();
            String threadName =
                (server ? "New I/O server worker #"
                        : "New I/O client worker #") + bossId + '-' + id;

            executor.execute(new ThreadRenamingRunnable(this, threadName));
        } else {
            synchronized (shutdownLock) {
                registerTaskQueue.offer(registerTask);
            }
            if (wakenUp.compareAndSet(false, true)) {
                selector.wakeup();
            }
        }
    }

    public void run() {
        thread = Thread.currentThread();

        boolean shutdown = false;
        Selector selector = this.selector;
        for (;;) {
            wakenUp.set(false);

            if (CONSTRAINT_LEVEL != 0) {
                selectorGuard.writeLock().lock();
                    // This empty synchronization block prevents the selector
                    // from acquiring its lock.
                selectorGuard.writeLock().unlock();
            }

            try {
                int selectedKeyCount = selector.select(500);

                processRegisterTaskQueue();
                processWriteTaskQueue();

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

                        synchronized (shutdownLock) {
                            if (registerTaskQueue.isEmpty() && selector.keys().isEmpty()) {
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

    private void processRegisterTaskQueue() {
        for (;;) {
            final Runnable task = registerTaskQueue.poll();
            if (task == null) {
                break;
            }

            task.run();
        }
    }

    private void processWriteTaskQueue() {
        for (;;) {
            final Runnable task = writeTaskQueue.poll();
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

                // TODO Replace ReceiveBufferSizePredictor with
                //      ChannelBufferAllocator and let user specify it per
                //      Channel. (Netty 3.1)

                if (USE_DIRECT_BUFFER) {
                    readIntoDirectBuffer(k);
                } else {
                    readIntoHeapBuffer(k);
                }
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

    private static void readIntoHeapBuffer(SelectionKey k) {
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

    private ChannelBuffer preallocatedDirectBuffer;

    private static void readIntoDirectBuffer(SelectionKey k) {
        ScatteringByteChannel ch = (ScatteringByteChannel) k.channel();
        NioSocketChannel channel = (NioSocketChannel) k.attachment();

        ReceiveBufferSizePredictor predictor =
            channel.getConfig().getReceiveBufferSizePredictor();

        ChannelBuffer preallocatedDirectBuffer = channel.getWorker().preallocatedDirectBuffer;
        NioWorker worker = channel.getWorker();
        worker.preallocatedDirectBuffer = null;

        if (preallocatedDirectBuffer == null) {
            preallocatedDirectBuffer = ChannelBuffers.directBuffer(1048576);
        }

        int ret = 0;
        int readBytes = 0;
        boolean failure = true;
        try {
            while ((ret = preallocatedDirectBuffer.writeBytes(ch, preallocatedDirectBuffer.writableBytes())) > 0) {
                readBytes += ret;
                if (!preallocatedDirectBuffer.writable()) {
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
            ChannelBuffer slice = preallocatedDirectBuffer.slice(
                    preallocatedDirectBuffer.readerIndex(),
                    preallocatedDirectBuffer.readableBytes());
            preallocatedDirectBuffer.readerIndex(preallocatedDirectBuffer.writerIndex());
            if (preallocatedDirectBuffer.writable()) {
                worker.preallocatedDirectBuffer = preallocatedDirectBuffer;
            }
            fireMessageReceived(channel, slice);
        } else if (readBytes == 0) {
            worker.preallocatedDirectBuffer = preallocatedDirectBuffer;
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

    static void write(final NioSocketChannel channel, boolean mightNeedWakeup) {
        if (mightNeedWakeup) {
            NioWorker worker = channel.getWorker();
            if (worker != null) {
                Thread workerThread = worker.thread;
                if (workerThread != null && Thread.currentThread() != workerThread) {
                    if (channel.writeTaskInTaskQueue.compareAndSet(false, true)) {
                        worker.writeTaskQueue.offer(channel.writeTask);
                    }
                    if (worker.wakenUp.compareAndSet(false, true)) {
                        worker.selector.wakeup();
                    }
                    return;
                }
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

        synchronized (channel.writeLock) {
            Queue<MessageEvent> writeBuffer = channel.writeBuffer;
            evt = channel.currentWriteEvent;
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
                } catch (AsynchronousCloseException e) {
                    // Doesn't need a user attention - ignore.
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

        MessageEvent evt;
        ChannelBuffer buf;
        int bufIdx;
        int writtenBytes = 0;

        synchronized (channel.writeLock) {
            Queue<MessageEvent> writeBuffer = channel.writeBuffer;
            evt = channel.currentWriteEvent;
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
                } catch (AsynchronousCloseException e) {
                    // Doesn't need a user attention - ignore.
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

        // interestOps can change at any time and at any thread.
        // Acquire a lock to avoid possible race condition.
        synchronized (channel.interestOpsLock) {
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
        synchronized (channel.writeLock) {
            MessageEvent evt = channel.currentWriteEvent;
            if (evt != null) {
                channel.currentWriteEvent = null;
                channel.currentWriteIndex = 0;
                evt.getFuture().setFailure(cause);
                fireExceptionCaught(channel, cause);
            }

            Queue<MessageEvent> writeBuffer = channel.writeBuffer;
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
            // interestOps can change at any time and at any thread.
            // Acquire a lock to avoid possible race condition.
            synchronized (channel.interestOpsLock) {
                // Override OP_WRITE flag - a user cannot change this flag.
                interestOps &= ~Channel.OP_WRITE;
                interestOps |= channel.getInterestOps() & Channel.OP_WRITE;

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

    private static class RegisterTask implements Runnable {
        private final Selector selector;
        private final NioSocketChannel channel;
        private final ChannelFuture future;
        private final boolean server;

        RegisterTask(
                Selector selector,
                NioSocketChannel channel, ChannelFuture future, boolean server) {

            this.selector = selector;
            this.channel = channel;
            this.future = future;
            this.server = server;
        }

        public void run() {
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

            if (server) {
                fireChannelOpen(channel);
                fireChannelBound(channel, channel.getLocalAddress());
            } else if (!((NioClientSocketChannel) channel).boundManually) {
                fireChannelBound(channel, channel.getLocalAddress());
            }
            fireChannelConnected(channel, channel.getRemoteAddress());
        }
    }
}