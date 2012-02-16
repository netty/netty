/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp;

import static io.netty.channel.Channels.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
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

import com.sun.nio.sctp.MessageInfo;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.MessageEvent;
import io.netty.channel.ReceiveBufferSizePredictor;
import io.netty.channel.sctp.SctpSendBufferPool.SendBuffer;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.internal.DeadLockProofWorker;
import io.netty.util.internal.QueueFactory;

/**
 */
@SuppressWarnings("unchecked")
class SctpWorker implements Runnable {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SctpWorker.class);

    private static final int CONSTRAINT_LEVEL = SctpProviderMetadata.CONSTRAINT_LEVEL;

    static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.

    private final Executor executor;
    private boolean started;
    private volatile Thread thread;
    volatile Selector selector;
    private final AtomicBoolean wakenUp = new AtomicBoolean();
    private final ReadWriteLock selectorGuard = new ReentrantReadWriteLock();
    private final Object startStopLock = new Object();
    private final Queue<Runnable> registerTaskQueue = QueueFactory.createQueue(Runnable.class);
    private final Queue<Runnable> writeTaskQueue = QueueFactory.createQueue(Runnable.class);
    private volatile int cancelledKeys; // should use AtomicInteger but we just need approximation

    private final SctpReceiveBufferPool recvBufferPool = new SctpReceiveBufferPool();
    private final SctpSendBufferPool sendBufferPool = new SctpSendBufferPool();

    private SctpNotificationHandler notificationHandler;

    SctpWorker(Executor executor) {
        this.executor = executor;
    }

    void register(SctpChannelImpl channel, ChannelFuture future) {

        boolean server = !(channel instanceof SctpClientChannel);
        Runnable registerTask = new RegisterTask(channel, future, server);
        notificationHandler = new SctpNotificationHandler(channel);
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
                boolean success = false;
                try {
                    DeadLockProofWorker.start(executor, this);
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

    @Override
    public void run() {
        thread = Thread.currentThread();

        boolean shutdown = false;
        Selector selector = this.selector;
        for (; ;) {
            wakenUp.set(false);

            if (CONSTRAINT_LEVEL != 0) {
                selectorGuard.writeLock().lock();
                // This empty synchronization block prevents the selector
                // from acquiring its lock.
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
        for (; ;) {
            final Runnable task = registerTaskQueue.poll();
            if (task == null) {
                break;
            }

            task.run();
            cleanUpCancelledKeys();
        }
    }

    private void processWriteTaskQueue() throws IOException {
        for (; ;) {
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

    private boolean read(SelectionKey k) {
        final SctpChannelImpl channel = (SctpChannelImpl) k.attachment();

        final ReceiveBufferSizePredictor predictor =
                channel.getConfig().getReceiveBufferSizePredictor();
        final int predictedRecvBufSize = predictor.nextReceiveBufferSize();

        boolean messageReceived = false;
        boolean failure = true;
        MessageInfo messageInfo = null;

        ByteBuffer bb = recvBufferPool.acquire(predictedRecvBufSize);
        try {
            messageInfo = channel.channel.receive(bb, null, notificationHandler);
            if (messageInfo != null) {
                messageReceived = true;
                if (!messageInfo.isUnordered()) {
                    failure = false;
                } else {
                    logger.error("Received unordered SCTP Packet");
                    failure = true;
                }
            } else {
                messageReceived = false;
                failure = false;
            }
        } catch (ClosedChannelException e) {
            // Can happen, and does not need a user attention.
        } catch (Throwable t) {
            fireExceptionCaught(channel, t);
        }

        if (messageReceived) {
            bb.flip();

            final ChannelBufferFactory bufferFactory =
                    channel.getConfig().getBufferFactory();
            final int receivedBytes = bb.remaining();
            final ChannelBuffer buffer = bufferFactory.getBuffer(receivedBytes);
            buffer.setBytes(0, bb);
            buffer.writerIndex(receivedBytes);

            recvBufferPool.release(bb);

            // Update the predictor.
            predictor.previousReceiveBufferSize(receivedBytes);

            // Fire the event.
            fireMessageReceived(channel,
                    new SctpFrame(messageInfo, buffer),
                    messageInfo.address());
        } else {
            recvBufferPool.release(bb);
        }

        if (channel.channel.isBlocking() && !messageReceived || failure) {
            k.cancel(); // Some JDK implementations run into an infinite loop without this.
            close(channel, succeededFuture(channel));
            return false;
        }

        return true;
    }

    private void close(SelectionKey k) {
        SctpChannelImpl ch = (SctpChannelImpl) k.attachment();
        close(ch, succeededFuture(ch));
    }

    void writeFromUserCode(final SctpChannelImpl channel) {
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

    void writeFromTaskLoop(final SctpChannelImpl ch) {
        if (!ch.writeSuspended) {
            write0(ch);
        }
    }

    void writeFromSelectorLoop(final SelectionKey k) {
        SctpChannelImpl ch = (SctpChannelImpl) k.attachment();
        ch.writeSuspended = false;
        write0(ch);
    }

    private boolean scheduleWriteIfNecessary(final SctpChannelImpl channel) {
        final Thread currentThread = Thread.currentThread();
        final Thread workerThread = thread;
        if (currentThread != workerThread) {
            if (channel.writeTaskInTaskQueue.compareAndSet(false, true)) {
                boolean offered = writeTaskQueue.offer(channel.writeTask);
                assert offered;
            }

            if (!(channel instanceof SctpAcceptedChannel) ||
                    ((SctpAcceptedChannel) channel).bossThread != currentThread) {
                final Selector workerSelector = selector;
                if (workerSelector != null) {
                    if (wakenUp.compareAndSet(false, true)) {
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

    private void write0(SctpChannelImpl channel) {
        boolean open = true;
        boolean addOpWrite = false;
        boolean removeOpWrite = false;

        long writtenBytes = 0;

        final SctpSendBufferPool sendBufferPool = this.sendBufferPool;
        final com.sun.nio.sctp.SctpChannel ch = channel.channel;
        final Queue<MessageEvent> writeBuffer = channel.writeBuffer;
        final int writeSpinCount = channel.getConfig().getWriteSpinCount();
        synchronized (channel.writeLock) {
            channel.inWriteNowLoop = true;
            for (; ;) {
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

                ChannelFuture future = evt.getFuture();
                try {
                    long localWrittenBytes = 0;
                    for (int i = writeSpinCount; i > 0; i--) {
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
                    buf.release();
                    channel.currentWriteEvent = null;
                    channel.currentWriteBuffer = null;
                    buf = null;
                    evt = null;
                    future.setFailure(t);
                    fireExceptionCaught(channel, t);
                    if (t instanceof IOException) {
                        open = false;
                        close(channel, succeededFuture(channel));
                    }
                }
            }
            channel.inWriteNowLoop = false;
        }

        if (open) {
            if (addOpWrite) {
                setOpWrite(channel);
            } else if (removeOpWrite) {
                clearOpWrite(channel);
            }
        }

        fireWriteComplete(channel, writtenBytes);
    }

    private void setOpWrite(SctpChannelImpl channel) {
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

    private void clearOpWrite(SctpChannelImpl channel) {
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

    void close(SctpChannelImpl channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        try {
            channel.channel.close();
            cancelledKeys++;

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

    private void cleanUpWriteBuffer(SctpChannelImpl channel) {
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
                channel.currentWriteBuffer.release();
                channel.currentWriteBuffer = null;
                channel.currentWriteEvent = null;
                evt = null;
                future.setFailure(cause);
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

                for (; ;) {
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

    void setInterestOps(
            SctpChannelImpl channel, ChannelFuture future, int interestOps) {
        boolean changed = false;
        try {
            // interestOps can change at any time and at any thread.
            // Acquire a lock to avoid possible race condition.
            synchronized (channel.interestOpsLock) {
                Selector selector = this.selector;
                SelectionKey key = channel.channel.keyFor(selector);

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
        private final SctpChannelImpl channel;
        private final ChannelFuture future;
        private final boolean server;

        RegisterTask(
                SctpChannelImpl channel, ChannelFuture future, boolean server) {

            this.channel = channel;
            this.future = future;
            this.server = server;
        }

        @Override
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
                    channel.channel.configureBlocking(false);
                }

                synchronized (channel.interestOpsLock) {
                    channel.channel.register(
                            selector, channel.getRawInterestOps(), channel);
                }
                channel.setConnected();
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
                if (!((SctpClientChannel) channel).boundManually) {
                    fireChannelBound(channel, localAddress);
                }
                fireChannelConnected(channel, remoteAddress);
            }
        }
    }
}
