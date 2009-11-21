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
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.CancelledKeyException;
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
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.CompositeChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.ReceiveBufferSizePredictor;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.jboss.netty.util.internal.IoWorkerRunnable;
import org.jboss.netty.util.internal.LinkedTransferQueue;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
class NioWorker implements Runnable {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(NioWorker.class);

    private static final int CONSTRAINT_LEVEL = NioProviderMetadata.CONSTRAINT_LEVEL;

    static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.

    private final int bossId;
    private final int id;
    private final Executor executor;
    private boolean started;
    private volatile Thread thread;
    volatile Selector selector;
    private final AtomicBoolean wakenUp = new AtomicBoolean();
    private final ReadWriteLock selectorGuard = new ReentrantReadWriteLock();
    private final Object startStopLock = new Object();
    private final Queue<Runnable> registerTaskQueue = new LinkedTransferQueue<Runnable>();
    private final Queue<Runnable> writeTaskQueue = new LinkedTransferQueue<Runnable>();
    private volatile int cancelledKeys; // should use AtomicInteger but we just need approximation

    NioWorker(int bossId, int id, Executor executor) {
        this.bossId = bossId;
        this.id = id;
        this.executor = executor;
    }

    void register(NioSocketChannel channel, ChannelFuture future) {

        boolean server = !(channel instanceof NioClientSocketChannel);
        Runnable registerTask = new RegisterTask(channel, future, server);
        Selector selector;

        synchronized (startStopLock) {
            if (!started) {
                // Open a selector if this worker didn't start yet.
                try {
                    this.selector = selector = Selector.open();
                } catch (Throwable t) {
                    throw new ChannelException(
                            "Failed to create a selector.", t);
                }

                // Start the worker thread with the new Selector.
                String threadName =
                    (server ? "New I/O server worker #"
                            : "New I/O client worker #") + bossId + '-' + id;

                boolean success = false;
                try {
                    executor.execute(
                            new IoWorkerRunnable(
                                    new ThreadRenamingRunnable(this, threadName)));
                    success = true;
                } finally {
                    if (!success) {
                        // Release the Selector if the execution fails.
                        try {
                            selector.close();
                        } catch (Throwable t) {
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
            boolean offered = registerTaskQueue.offer(registerTask);
            assert offered;
        }

        if (wakenUp.compareAndSet(false, true)) {
            selector.wakeup();
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
                selector.select(500);

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

                // Exit the loop when there's nothing to handle.
                // The shutdown flag is used to delay the shutdown of this
                // loop to avoid excessive Selector creation when
                // connections are registered in a one-by-one manner instead of
                // concurrent manner.
                if (selector.keys().isEmpty()) {
                    if (shutdown ||
                        executor instanceof ExecutorService && ((ExecutorService) executor).isShutdown()) {

                        synchronized (startStopLock) {
                            if (registerTaskQueue.isEmpty() && selector.keys().isEmpty()) {
                                started = false;
                                try {
                                    selector.close();
                                } catch (IOException e) {
                                    logger.warn(
                                            "Failed to close a selector.", e);
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
                logger.warn(
                        "Unexpected exception in the selector loop.", t);

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

    private void processSelectedKeys(Set<SelectionKey> selectedKeys) throws IOException {
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
                    write(k);
                }
            } catch (CancelledKeyException e) {
                close(k);
            }

            if (cleanUpCancelledKeys()) {
                break; // break the loop to avoid ConcurrentModificationException
            }
        }
    }

    private boolean cleanUpCancelledKeys() throws IOException {
        if (cancelledKeys >= CLEANUP_INTERVAL) {
            cancelledKeys = 0;
            selector.selectNow();
            return true;
        }
        return false;
    }

    private static boolean read(SelectionKey k) {
        ScatteringByteChannel ch = (ScatteringByteChannel) k.channel();
        NioSocketChannel channel = (NioSocketChannel) k.attachment();

        ReceiveBufferSizePredictor predictor =
            channel.getConfig().getReceiveBufferSizePredictor();
        ChannelBufferFactory bufferFactory =
            channel.getConfig().getBufferFactory();

        ChannelBuffer buffer =
            bufferFactory.getBuffer(predictor.nextReceiveBufferSize());

        int ret = 0;
        int readBytes = 0;
        boolean failure = true;
        try {
            while ((ret = buffer.writeBytes(ch, buffer.writableBytes())) > 0) {
                readBytes += ret;
                if (!buffer.writable()) {
                    break;
                }
            }
            failure = false;
        } catch (AsynchronousCloseException e) {
            // Can happen, and does not need a user attention.
        } catch (Throwable t) {
            fireExceptionCaught(channel, t);
        }

        if (readBytes > 0) {
            // Update the predictor.
            predictor.previousReceiveBufferSize(readBytes);

            // Fire the event.
            fireMessageReceived(channel, buffer);
        }

        if (ret < 0 || failure) {
            close(k);
            return false;
        }

        return true;
    }

    private static void write(SelectionKey k) {
        NioSocketChannel ch = (NioSocketChannel) k.attachment();
        write(ch, false);
    }

    private static void close(SelectionKey k) {
        NioSocketChannel ch = (NioSocketChannel) k.attachment();
        close(ch, succeededFuture(ch));
    }

    static void write(final NioSocketChannel channel, boolean mightNeedWakeup) {
        if (!channel.isConnected()) {
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

    private static boolean scheduleWriteIfNecessary(final NioSocketChannel channel) {
        final NioWorker worker = channel.worker;
        final Thread currentThread = Thread.currentThread();
        final Thread workerThread = worker.thread;
        if (currentThread != workerThread) {
            if (channel.writeTaskInTaskQueue.compareAndSet(false, true)) {
                boolean offered = worker.writeTaskQueue.offer(channel.writeTask);
                assert offered;
            }

            if (!(channel instanceof NioAcceptedSocketChannel) ||
                ((NioAcceptedSocketChannel) channel).bossThread != currentThread) {
                final Selector workerSelector = worker.selector;
                if (workerSelector != null) {
                    if (worker.wakenUp.compareAndSet(false, true)) {
                        workerSelector.wakeup();
                    }
                }
            } else {
                // A write request can be made from an acceptor thread (boss)
                // when a user attempted to write something in:
                //
                //   * channelOpen()
                //   * channelBound()
                //   * channelConnected().
                //
                // In this case, there's no need to wake up the selector because
                // the channel is not even registered yet at this moment.
            }

            return true;
        }

        return false;
    }

    private static void writeNow(NioSocketChannel channel, int writeSpinCount) {

        boolean open = true;
        boolean addOpWrite = false;
        boolean removeOpWrite = false;

        MessageEvent evt;
        ChannelBuffer buf;
        int bufIdx;
        int writtenBytes = 0;

        Queue<MessageEvent> writeBuffer = channel.writeBuffer;
        synchronized (channel.writeLock) {
            channel.inWriteNowLoop = true;
            evt = channel.currentWriteEvent;
            for (;;) {
                if (evt == null) {
                    evt = writeBuffer.poll();
                    if (evt == null) {
                        channel.currentWriteEvent = null;
                        removeOpWrite = true;
                        break;
                    }

                    evt = consolidateComposite(evt);
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
                            writtenBytes += localWrittenBytes;
                            break;
                        }
                    }

                    if (bufIdx == buf.writerIndex()) {
                        // Successful write - proceed to the next message.
                        channel.currentWriteEvent = null;
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
                    channel.currentWriteEvent = evt;
                    channel.currentWriteIndex = bufIdx;
                } catch (Throwable t) {
                    channel.currentWriteEvent = null;
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

    static MessageEvent consolidateComposite(MessageEvent e) {
        // Convert a composite buffer into a simple buffer to save memory
        // bandwidth on full write buffer.
        // This method should be eliminated once gathering write works.
        Object m = e.getMessage();
        if (m instanceof CompositeChannelBuffer) {
            e = new DownstreamMessageEvent(
                    e.getChannel(), e.getFuture(),
                    ((CompositeChannelBuffer) m).copy(),
                    e.getRemoteAddress());
        }
        return e;
    }

    private static void setOpWrite(NioSocketChannel channel) {
        NioWorker worker = channel.worker;
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

    private static void clearOpWrite(NioSocketChannel channel) {
        NioWorker worker = channel.worker;
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

    static void close(NioSocketChannel channel, ChannelFuture future) {
        NioWorker worker = channel.worker;

        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        try {
            channel.socket.close();
            worker.cancelledKeys ++;

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

    private static void cleanUpWriteBuffer(NioSocketChannel channel) {
        Exception cause = null;
        boolean fireExceptionCaught = false;

        // Clean up the stale messages in the write buffer.
        synchronized (channel.writeLock) {
            MessageEvent evt = channel.currentWriteEvent;
            if (evt != null) {
                channel.currentWriteEvent = null;
                channel.currentWriteIndex = 0;

                // Create the exception only once to avoid the excessive overhead
                // caused by fillStackTrace.
                if (channel.isOpen()) {
                    cause = new NotYetConnectedException();
                } else {
                    cause = new ClosedChannelException();
                }
                evt.getFuture().setFailure(cause);
                fireExceptionCaught = true;
            }

            Queue<MessageEvent> writeBuffer = channel.writeBuffer;
            if (!writeBuffer.isEmpty()) {
                // Create the exception only once to avoid the excessive overhead
                // caused by fillStackTrace.
                if (cause == null) {
                    if (channel.isOpen()) {
                        cause = new NotYetConnectedException();
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

    static void setInterestOps(
            NioSocketChannel channel, ChannelFuture future, int interestOps) {
        boolean changed = false;
        try {
            // interestOps can change at any time and at any thread.
            // Acquire a lock to avoid possible race condition.
            synchronized (channel.interestOpsLock) {
                NioWorker worker = channel.worker;
                Selector selector = worker.selector;
                SelectionKey key = channel.socket.keyFor(selector);

                if (key == null || selector == null) {
                    // Not registered to the worker yet.
                    // Set the rawInterestOps immediately; RegisterTask will pick it up.
                    channel.setRawInterestOpsNow(interestOps);
                    return;
                }

                // Override OP_WRITE flag - a user cannot change this flag.
                interestOps &= ~Channel.OP_WRITE;
                interestOps |= channel.getRawInterestOps() & Channel.OP_WRITE;

                switch (CONSTRAINT_LEVEL) {
                case 0:
                    if (channel.getRawInterestOps() != interestOps) {
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
                    if (channel.getRawInterestOps() != interestOps) {
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
                channel.setRawInterestOpsNow(interestOps);
                fireChannelInterestChanged(channel);
            }
        } catch (CancelledKeyException e) {
            // setInterestOps() was called on a closed channel.
            ClosedChannelException cce = new ClosedChannelException();
            future.setFailure(cce);
            fireExceptionCaught(channel, cce);
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private final class RegisterTask implements Runnable {
        private final NioSocketChannel channel;
        private final ChannelFuture future;
        private final boolean server;

        RegisterTask(
                NioSocketChannel channel, ChannelFuture future, boolean server) {

            this.channel = channel;
            this.future = future;
            this.server = server;
        }

        public void run() {
            SocketAddress localAddress = channel.getLocalAddress();
            SocketAddress remoteAddress = channel.getRemoteAddress();
            if (localAddress == null || remoteAddress == null) {
                if (future != null) {
                    future.setFailure(new ClosedChannelException());
                }
                close(channel, succeededFuture(channel));
                return;
            }

            try {
                if (server) {
                    channel.socket.configureBlocking(false);
                }

                synchronized (channel.interestOpsLock) {
                    channel.socket.register(
                            selector, channel.getRawInterestOps(), channel);
                }
                if (future != null) {
                    future.setSuccess();
                }
            } catch (IOException e) {
                if (future != null) {
                    future.setFailure(e);
                }
                close(channel, succeededFuture(channel));
                if (!(e instanceof ClosedChannelException)) {
                    throw new ChannelException(
                            "Failed to register a socket to the selector.", e);
                }
            }

            if (!server) {
                if (!((NioClientSocketChannel) channel).boundManually) {
                    fireChannelBound(channel, localAddress);
                }
                fireChannelConnected(channel, remoteAddress);
            }
        }
    }
}
