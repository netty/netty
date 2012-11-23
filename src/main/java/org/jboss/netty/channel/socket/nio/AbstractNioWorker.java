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
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.Worker;
import org.jboss.netty.channel.socket.nio.SocketSendBufferPool.SendBuffer;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.jboss.netty.util.internal.DeadLockProofWorker;

import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.jboss.netty.channel.Channels.*;

abstract class AbstractNioWorker implements Worker, ExternalResourceReleasable {


    private static final AtomicInteger nextId = new AtomicInteger();

    final int id = nextId.incrementAndGet();

    /**
     * Internal Netty logger.
     */
    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(AbstractNioWorker.class);

    private static final int CONSTRAINT_LEVEL = NioProviderMetadata.CONSTRAINT_LEVEL;

    static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.


    /**
     * Executor used to execute {@link Runnable}s such as channel registration
     * task.
     */
    private final Executor executor;

    /**
     * If this worker has been started thread will be a reference to the thread
     * used when starting. i.e. the current thread when the run method is executed.
     */
    protected volatile Thread thread;

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
    protected final AtomicBoolean wakenUp = new AtomicBoolean();

    /**
     * Lock for this workers Selector.
     */
    private final ReadWriteLock selectorGuard = new ReentrantReadWriteLock();

    /**
     * Monitor object used to synchronize selector open/close.
     */
    private final Object startStopLock = new Object();

    /**
     * Queue of channel registration tasks.
     */
    private final Queue<Runnable> registerTaskQueue = new ConcurrentLinkedQueue<Runnable>();

    /**
     * Queue of WriteTasks
     */
    protected final Queue<Runnable> writeTaskQueue = new ConcurrentLinkedQueue<Runnable>();

    private final Queue<Runnable> eventQueue = new ConcurrentLinkedQueue<Runnable>();


    private volatile int cancelledKeys; // should use AtomicInteger but we just need approximation

    protected final SocketSendBufferPool sendBufferPool = new SocketSendBufferPool();

    AbstractNioWorker(Executor executor) {
        this.executor = executor;
        openSelector();
    }

    void register(AbstractNioChannel<?> channel, ChannelFuture future) {

        synchronized (startStopLock) {
            if (selector == null) {
                // the selector was null this means the Worker has already been shutdown.
                throw new RejectedExecutionException("Worker has already been shutdown");
            }
            Runnable registerTask = createRegisterTask(channel, future);

            boolean offered = registerTaskQueue.offer(registerTask);
            assert offered;

            if (wakenUp.compareAndSet(false, true)) {
                selector.wakeup();
            }

        }
    }

    // Create a new selector and "transfer" all channels from the old
    // selector to the new one
    private Selector recreateSelector() throws IOException {
        Selector newSelector = Selector.open();
        Selector selector = this.selector;
        this.selector = newSelector;

        // loop over all the keys that are registered with the old Selector
        // and register them with the new one
        for (SelectionKey key: selector.keys()) {
            SelectableChannel ch = key.channel();
            int ops = key.interestOps();
            Object att = key.attachment();
            // cancel the old key
            key.cancel();

            try {
                // register the channel with the new selector now
                ch.register(newSelector, ops, att);
            } catch (ClosedChannelException e) {
                // close channel
                AbstractNioChannel<?> channel = (AbstractNioChannel<?>) att;
                close(channel, succeededFuture(channel));
            }
        }
        try {
            // time to close the old selector as everything else is registered to the new one
            selector.close();
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close a selector.", t);
            }
        }
        if (logger.isWarnEnabled()) {
            logger.warn("Recreated Selector because of possible jdk epoll(..) bug");
        }
        return newSelector;
    }

    /**
     * Start the {@link AbstractNioWorker} and return the {@link Selector} that will be used for
     * the {@link AbstractNioChannel}'s when they get registered
     *
     * @return selector
     */
    private void openSelector() {
        try {
            selector = Selector.open();
        } catch (Throwable t) {
            throw new ChannelException("Failed to create a selector.", t);
        }

        // Start the worker thread with the new Selector.
        boolean success = false;
        try {
            DeadLockProofWorker.start(executor, new ThreadRenamingRunnable(this, "New I/O  worker #" + id));
            success = true;
        } finally {
            if (!success) {
                // Release the Selector if the execution fails.
                try {
                    selector.close();
                } catch (Throwable t) {
                    logger.warn("Failed to close a selector.", t);
                }
                selector = null;
                // The method will return to the caller at this point.
            }
        }
        assert selector != null && selector.isOpen();
    }


    public void run() {
        thread = Thread.currentThread();

        boolean shutdown = false;
        int selectReturnsImmediately = 0;
        Selector selector = this.selector;

        // use 80% of the timeout for measure
        final long minSelectTimeout = SelectorUtil.SELECT_TIMEOUT_NANOS * 80 / 100;
        boolean wakenupFromLoop = false;
        for (;;) {
            wakenUp.set(false);

            if (CONSTRAINT_LEVEL != 0) {
                selectorGuard.writeLock().lock();
                    // This empty synchronization block prevents the selector
                    // from acquiring its lock.
                selectorGuard.writeLock().unlock();
            }

            try {
                long beforeSelect = System.nanoTime();
                int selected = SelectorUtil.select(selector);
                if (SelectorUtil.EPOLL_BUG_WORKAROUND && selected == 0 && !wakenupFromLoop && !wakenUp.get()) {
                    long timeBlocked = System.nanoTime() - beforeSelect;

                    if (timeBlocked < minSelectTimeout) {
                        boolean notConnected = false;
                        // loop over all keys as the selector may was unblocked because of a closed channel
                        for (SelectionKey key: selector.keys()) {
                            SelectableChannel ch = key.channel();
                            try {
                                if (ch instanceof DatagramChannel && !((DatagramChannel) ch).isConnected() ||
                                        ch instanceof SocketChannel && !((SocketChannel) ch).isConnected()) {
                                    notConnected = true;
                                    // cancel the key just to be on the safe side
                                    key.cancel();
                                }
                            } catch (CancelledKeyException e) {
                                // ignore
                            }
                        }
                        if (notConnected) {
                            selectReturnsImmediately = 0;
                        } else {
                            // returned before the minSelectTimeout elapsed with nothing select.
                            // this may be the cause of the jdk epoll(..) bug, so increment the counter
                            // which we use later to see if its really the jdk bug.
                            selectReturnsImmediately ++;

                        }

                    } else {
                        selectReturnsImmediately = 0;
                    }

                    if (selectReturnsImmediately == 1024) {
                        // The selector returned immediately for 10 times in a row,
                        // so recreate one selector as it seems like we hit the
                        // famous epoll(..) jdk bug.
                        selector = recreateSelector();
                        selectReturnsImmediately = 0;
                        wakenupFromLoop = false;
                        // try to select again
                        continue;
                    }
                } else {
                    // reset counter
                    selectReturnsImmediately = 0;
                }

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
                    wakenupFromLoop = true;
                    selector.wakeup();
                } else {
                    wakenupFromLoop = false;
                }

                cancelledKeys = 0;
                processRegisterTaskQueue();
                processEventQueue();
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

    public void executeInIoThread(Runnable task) {
        executeInIoThread(task, false);
    }

    /**
     * Execute the {@link Runnable} in a IO-Thread
     *
     * @param task
     *            the {@link Runnable} to execute
     * @param alwaysAsync
     *            <code>true</code> if the {@link Runnable} should be executed
     *            in an async fashion even if the current Thread == IO Thread
     */
    public void executeInIoThread(Runnable task, boolean alwaysAsync) {
        if (!alwaysAsync && Thread.currentThread() == thread) {
            task.run();
        } else {
            eventQueue.offer(task);

            synchronized (startStopLock) {
                // check if the selector was shutdown already or was not started yet. If so execute all
                // submitted tasks in the calling thread
                if (selector == null) {
                    // execute everything in the event queue as the
                    for (;;) {
                        Runnable r = eventQueue.poll();
                        if (r == null) {
                            break;
                        }
                        r.run();
                    }
                } else {
                    if (wakenUp.compareAndSet(false, true))  {
                        // wake up the selector to speed things
                        Selector selector = this.selector;
                        if (selector != null) {
                            selector.wakeup();
                        }
                    }
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

    private void processEventQueue() throws IOException {
        for (;;) {
            final Runnable task = eventQueue.poll();
            if (task == null) {
                break;
            }
            task.run();
            cleanUpCancelledKeys();
        }
    }

    private void processSelectedKeys(Set<SelectionKey> selectedKeys) throws IOException {
        // check if the set is empty and if so just return to not create garbage by
        // creating a new Iterator every time even if there is nothing to process.
        // See https://github.com/netty/netty/issues/597
        if (selectedKeys.isEmpty()) {
            return;
        }
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



    private void close(SelectionKey k) {
        AbstractNioChannel<?> ch = (AbstractNioChannel<?>) k.attachment();
        close(ch, succeededFuture(ch));
    }

    void writeFromUserCode(final AbstractNioChannel<?> channel) {
        if (!channel.isConnected()) {
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

    void writeFromTaskLoop(AbstractNioChannel<?> ch) {
        if (!ch.writeSuspended) {
            write0(ch);
        }
    }

    void writeFromSelectorLoop(final SelectionKey k) {
        AbstractNioChannel<?> ch = (AbstractNioChannel<?>) k.attachment();
        ch.writeSuspended = false;
        write0(ch);
    }

    protected abstract boolean scheduleWriteIfNecessary(AbstractNioChannel<?> channel);

    protected void write0(AbstractNioChannel<?> channel) {
        boolean open = true;
        boolean addOpWrite = false;
        boolean removeOpWrite = false;
        boolean iothread = isIoThread(channel);

        long writtenBytes = 0;

        final SocketSendBufferPool sendBufferPool = this.sendBufferPool;
        final WritableByteChannel ch = channel.channel;
        final Queue<MessageEvent> writeBuffer = channel.writeBufferQueue;
        final int writeSpinCount = channel.getConfig().getWriteSpinCount();
        synchronized (channel.writeLock) {
            channel.inWriteNowLoop = true;
            for (;;) {

                MessageEvent evt = channel.currentWriteEvent;
                SendBuffer buf = null;
                ChannelFuture future = null;
                try {
                    if (evt == null) {
                        if ((channel.currentWriteEvent = evt = writeBuffer.poll()) == null) {
                            removeOpWrite = true;
                            channel.writeSuspended = false;
                            break;
                        }
                        future = evt.getFuture();

                        channel.currentWriteBuffer = buf = sendBufferPool.acquire(evt.getMessage());
                    } else {
                        future = evt.getFuture();
                        buf = channel.currentWriteBuffer;

                    }

                    long localWrittenBytes = 0;
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

                    if (buf.finished()) {
                        // Successful write - proceed to the next message.
                        buf.release();
                        channel.currentWriteEvent = null;
                        channel.currentWriteBuffer = null;
                        evt = null;
                        buf = null;
                        future.setSuccess();
                    } else {
                        // Not written fully - perhaps the kernel buffer is full.
                        addOpWrite = true;
                        channel.writeSuspended = true;

                        if (localWrittenBytes > 0) {
                            // Notify progress listeners if necessary.
                            future.setProgress(
                                    localWrittenBytes,
                                    buf.writtenBytes(), buf.totalBytes());
                        }
                        break;
                    }
                } catch (AsynchronousCloseException e) {
                    // Doesn't need a user attention - ignore.
                } catch (Throwable t) {
                    if (buf != null) {
                        buf.release();
                    }
                    channel.currentWriteEvent = null;
                    channel.currentWriteBuffer = null;
                    buf = null;
                    evt = null;
                    if (future != null) {
                        future.setFailure(t);
                    }
                    if (iothread) {
                        fireExceptionCaught(channel, t);
                    } else {
                        fireExceptionCaughtLater(channel, t);
                    }
                    if (t instanceof IOException) {
                        open = false;
                        close(channel, succeededFuture(channel));
                    }
                }
            }
            channel.inWriteNowLoop = false;

            // Initially, the following block was executed after releasing
            // the writeLock, but there was a race condition, and it has to be
            // executed before releasing the writeLock:
            //
            //     https://issues.jboss.org/browse/NETTY-410
            //
            if (open) {
                if (addOpWrite) {
                    setOpWrite(channel);
                } else if (removeOpWrite) {
                    clearOpWrite(channel);
                }
            }
        }
        if (iothread) {
            fireWriteComplete(channel, writtenBytes);
        } else {
            fireWriteCompleteLater(channel, writtenBytes);
        }
    }

    static boolean isIoThread(AbstractNioChannel<?> channel) {
        return Thread.currentThread() == channel.worker.thread;
    }

    protected void setOpWrite(AbstractNioChannel<?> channel) {
        Selector selector = this.selector;
        SelectionKey key = channel.channel.keyFor(selector);
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

    protected void clearOpWrite(AbstractNioChannel<?> channel) {
        Selector selector = this.selector;
        SelectionKey key = channel.channel.keyFor(selector);
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


    void close(AbstractNioChannel<?> channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        boolean iothread = isIoThread(channel);

        try {
            channel.channel.close();
            cancelledKeys ++;

            if (channel.setClosed()) {
                future.setSuccess();
                if (connected) {
                    if (iothread) {
                        fireChannelDisconnected(channel);
                    } else {
                        fireChannelDisconnectedLater(channel);
                    }
                }
                if (bound) {
                    if (iothread) {
                        fireChannelUnbound(channel);
                    } else {
                        fireChannelUnboundLater(channel);
                    }
                }

                cleanUpWriteBuffer(channel);
                if (iothread) {
                    fireChannelClosed(channel);
                } else {
                    fireChannelClosedLater(channel);
                }
            } else {
                future.setSuccess();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            if (iothread) {
                fireExceptionCaught(channel, t);
            } else {
                fireExceptionCaughtLater(channel, t);
            }
        }
    }

    protected static void cleanUpWriteBuffer(AbstractNioChannel<?> channel) {
        Exception cause = null;
        boolean fireExceptionCaught = false;

        // Clean up the stale messages in the write buffer.
        synchronized (channel.writeLock) {
            MessageEvent evt = channel.currentWriteEvent;
            if (evt != null) {
                // Create the exception only once to avoid the excessive overhead
                // caused by fillStackTrace.
                if (channel.isOpen()) {
                    cause = new NotYetConnectedException();
                } else {
                    cause = new ClosedChannelException();
                }

                ChannelFuture future = evt.getFuture();
                if (channel.currentWriteBuffer != null) {
                    channel.currentWriteBuffer.release();
                    channel.currentWriteBuffer = null;
                }
                channel.currentWriteEvent = null;
                evt = null;
                future.setFailure(cause);
                fireExceptionCaught = true;
            }

            Queue<MessageEvent> writeBuffer = channel.writeBufferQueue;
            for (;;) {
                evt = writeBuffer.poll();
                if (evt == null) {
                    break;
                }
                // Create the exception only once to avoid the excessive overhead
                // caused by fillStackTrace.
                if (cause == null) {
                    if (channel.isOpen()) {
                        cause = new NotYetConnectedException();
                    } else {
                        cause = new ClosedChannelException();
                    }
                    fireExceptionCaught = true;
                }
                evt.getFuture().setFailure(cause);


            }
        }

        if (fireExceptionCaught) {
            if (isIoThread(channel)) {
                fireExceptionCaught(channel, cause);
            } else {
                fireExceptionCaughtLater(channel, cause);
            }
        }
    }

    void setInterestOps(AbstractNioChannel<?> channel, ChannelFuture future, int interestOps) {
        boolean changed = false;
        boolean iothread = isIoThread(channel);
        try {
            // interestOps can change at any time and at any thread.
            // Acquire a lock to avoid possible race condition.
            synchronized (channel.interestOpsLock) {
                Selector selector = this.selector;
                SelectionKey key = channel.channel.keyFor(selector);

                // Override OP_WRITE flag - a user cannot change this flag.
                interestOps &= ~Channel.OP_WRITE;
                interestOps |= channel.getRawInterestOps() & Channel.OP_WRITE;

                if (key == null || selector == null) {
                    if (channel.getRawInterestOps() != interestOps) {
                        changed = true;
                    }

                    // Not registered to the worker yet.
                    // Set the rawInterestOps immediately; RegisterTask will pick it up.
                    channel.setRawInterestOpsNow(interestOps);

                    future.setSuccess();
                    if (changed) {
                        if (iothread) {
                            fireChannelInterestChanged(channel);
                        } else {
                            fireChannelInterestChangedLater(channel);
                        }
                    }

                    return;
                }

                switch (CONSTRAINT_LEVEL) {
                case 0:
                    if (channel.getRawInterestOps() != interestOps) {
                        key.interestOps(interestOps);
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
                            key.interestOps(interestOps);
                            changed = true;
                        } else {
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
                if (iothread) {
                    fireChannelInterestChanged(channel);
                } else {
                    fireChannelInterestChangedLater(channel);
                }
            }
        } catch (CancelledKeyException e) {
            // setInterestOps() was called on a closed channel.
            ClosedChannelException cce = new ClosedChannelException();
            future.setFailure(cce);
            if (iothread) {
                fireExceptionCaught(channel, cce);
            } else {
                fireExceptionCaughtLater(channel, cce);
            }
        } catch (Throwable t) {
            future.setFailure(t);
            if (iothread) {
                fireExceptionCaught(channel, t);
            } else {
                fireExceptionCaughtLater(channel, t);
            }
        }
    }


    public void releaseExternalResources() {
        sendBufferPool.releaseExternalResources();
    }

    /**
     * Read is called when a Selector has been notified that the underlying channel
     * was something to be read. The channel would previously have registered its interest
     * in read operations.
     *
     * @param k The selection key which contains the Selector registration information.
     */
    protected abstract boolean read(SelectionKey k);

    /**
     * Create a new {@link Runnable} which will register the {@link AbstractNioWorker} with the {@link Channel}
     *
     * @param channel
     * @param future
     * @return task
     */
    protected abstract Runnable createRegisterTask(AbstractNioChannel<?> channel, ChannelFuture future);

}
