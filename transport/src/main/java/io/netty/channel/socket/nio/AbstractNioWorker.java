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
package io.netty.channel.socket.nio;

import static io.netty.channel.Channels.*;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MessageEvent;
import io.netty.channel.socket.Worker;
import io.netty.channel.socket.nio.SendBufferPool.SendBuffer;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.internal.DeadLockProofWorker;
import io.netty.util.internal.QueueFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

abstract class AbstractNioWorker implements Worker {
    /**
     * Internal Netty logger.
     */
    protected static final InternalLogger logger = InternalLoggerFactory
            .getInstance(AbstractNioWorker.class);

    private static final int CONSTRAINT_LEVEL = NioProviderMetadata.CONSTRAINT_LEVEL;

    static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.

    
    /**
     * Executor used to execute {@link Runnable}s such as registration task.
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
    protected volatile Thread thread;

    /**
     * The NIO {@link Selector}.
     */
    protected volatile Selector selector;

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
    protected final Queue<Runnable> registerTaskQueue = QueueFactory.createQueue(Runnable.class);

    /**
     * Queue of WriteTasks
     */
    protected final Queue<Runnable> writeTaskQueue = QueueFactory.createQueue(Runnable.class);

    private final Queue<Runnable> eventQueue = QueueFactory.createQueue(Runnable.class);

    
    private volatile int cancelledKeys; // should use AtomicInteger but we just need approximation

    protected final SendBufferPool sendBufferPool = new SendBufferPool();

    private final boolean allowShutdownOnIdle;

    AbstractNioWorker(Executor executor) {
        this(executor, true);
    }

    public AbstractNioWorker(Executor executor, boolean allowShutdownOnIdle) {
        this.executor = executor;
        this.allowShutdownOnIdle = allowShutdownOnIdle;
        
    }

    public void registerWithWorker(final Channel channel, final ChannelFuture future) {
        final Selector selector = start();

        try {
            if (channel instanceof NioServerSocketChannel) {
                final NioServerSocketChannel ch = (NioServerSocketChannel) channel;
                registerTaskQueue.add(new Runnable() {
                    
                    @Override
                    public void run() {
                        try {
                            ch.socket.register(selector, SelectionKey.OP_ACCEPT, ch);
                        } catch (Throwable t) {
                            future.setFailure(t);
                            fireExceptionCaught(channel, t);
                        }
                    }
                });
            } else if (channel instanceof NioClientSocketChannel) {
                final NioClientSocketChannel clientChannel = (NioClientSocketChannel) channel;
                
                registerTaskQueue.add(new Runnable() {
                    
                    @Override
                    public void run() {
                        try {
                            try {
                                clientChannel.getJdkChannel().register(selector, clientChannel.getRawInterestOps() | SelectionKey.OP_CONNECT, clientChannel);
                            } catch (ClosedChannelException e) {
                                clientChannel.getWorker().close(clientChannel, succeededFuture(channel));
                            }
                            int connectTimeout = channel.getConfig().getConnectTimeoutMillis();
                            if (connectTimeout > 0) {
                                clientChannel.connectDeadlineNanos = System.nanoTime() + connectTimeout * 1000000L;
                            }
                        } catch (Throwable t) {
                            future.setFailure(t);
                            fireExceptionCaught(channel, t);
                        }
                    }
                });
            } else if (channel instanceof AbstractNioChannel) {
                registerTaskQueue.add(new Runnable() {
                    
                    @Override
                    public void run() {
                        try {
                            registerTask((AbstractNioChannel) channel, future);
                        } catch (Throwable t) {
                            future.setFailure(t);
                            fireExceptionCaught(channel, t);
                        }
                        
                    }
                });
            } else {
                throw new UnsupportedOperationException("Unable to handle channel " + channel);
            }
            if (wakenUp.compareAndSet(false, true)) {
                selector.wakeup();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }

    }
    
    /**
     * Start the {@link AbstractNioWorker} and return the {@link Selector} that will be used for the {@link AbstractNioChannel}'s when they get registered
     * 
     * @return selector
     */
    protected final Selector start() {
        synchronized (startStopLock) {
            if (!started && selector == null) {
                // Open a selector if this worker didn't start yet.
                try {
                    this.selector = Selector.open();
                } catch (Throwable t) {
                    throw new ChannelException("Failed to create a selector.", t);
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
                        this.selector = null;
                        // The method will return to the caller at this point.
                    }
                }
            }

            assert selector != null && selector.isOpen();

            started = true;
        }
        return selector;
    }

    @Override
    public void run() {
        thread = Thread.currentThread();
        long lastConnectTimeoutCheckTimeNanos = System.nanoTime();

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
                processEventQueue();
                processWriteTaskQueue();
                processSelectedKeys(selector.selectedKeys());

                // Handle connection timeout every 10 milliseconds approximately.
                long currentTimeNanos = System.nanoTime();
                if (currentTimeNanos - lastConnectTimeoutCheckTimeNanos >= 10 * 1000000L) {
                    lastConnectTimeoutCheckTimeNanos = currentTimeNanos;
                    processConnectTimeout(selector.keys(), currentTimeNanos);
                }

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
                        if (allowShutdownOnIdle) {
                            // Give one more second.
                            shutdown = true;
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
    
    @Override
    public void executeInIoThread(Runnable task) {
        executeInIoThread(task, false);
    }
    
    /**
     * Execute the {@link Runnable} in a IO-Thread
     * 
     * @param task the {@link Runnable} to execute
     * @param alwaysAsync <code>true</code> if the {@link Runnable} should be executed in an async
     *                    fashion even if the current Thread == IO Thread
     */
    public void executeInIoThread(Runnable task, boolean alwaysAsync) {
        if (!alwaysAsync && isIoThread()) {
            task.run();
        } else {
            start();
            boolean added = eventQueue.offer(task);

            assert added;
            if (added) {
                // wake up the selector to speed things
                Selector selector = this.selector;
                if (selector != null) {
                    selector.wakeup();
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
        for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
            SelectionKey k = i.next();
            boolean removeKey = true;
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
                
                if ((readyOps & SelectionKey.OP_ACCEPT) != 0) {
                    removeKey = accept(k);
                }
                if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                    connect(k);
                }

            } catch (CancelledKeyException e) {
                close(k);
            } finally {
                if (removeKey) {
                    i.remove();
                }
            }
            


            if (cleanUpCancelledKeys()) {
                break; // break the loop to avoid ConcurrentModificationException
            }
        }
    }

    protected boolean accept(SelectionKey key) {
        NioServerSocketChannel channel = (NioServerSocketChannel) key.attachment();
        try {
            SocketChannel acceptedSocket = channel.socket.accept();
            if (acceptedSocket != null) {
                
                // TODO: Remove the casting stuff
                ChannelPipeline pipeline =
                        channel.getConfig().getPipelineFactory().getPipeline();
                registerTask(NioAcceptedSocketChannel.create(channel.getFactory(), pipeline, channel,
                        channel.getPipeline().getSink(), acceptedSocket, (NioWorker) this), null);
                return true;
            }
            return false;
        } catch (SocketTimeoutException e) {
            // Thrown every second to get ClosedChannelException
            // raised.
        } catch (CancelledKeyException e) {
            // Raised by accept() when the server socket was closed.
        } catch (ClosedSelectorException e) {
            // Raised by accept() when the server socket was closed.
        } catch (ClosedChannelException e) {
            // Closed as requested.
        } catch (Throwable e) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Failed to accept a connection.", e);
            }
        }
        return true;
    }
    
    
    protected void processConnectTimeout(Set<SelectionKey> keys, long currentTimeNanos) {
        ConnectException cause = null;
        for (SelectionKey k: keys) {
            if (!k.isValid()) {
                // Comment the close call again as it gave us major problems with ClosedChannelExceptions.
                //
                // See:
                // * https://github.com/netty/netty/issues/142
                // * https://github.com/netty/netty/issues/138
                //
                //close(k);
                continue;
            }
            
            // Something is ready so skip it
            if (k.readyOps() != 0) {
                continue;
            }
            // check if the channel is in
            Object attachment = k.attachment();
            if (attachment instanceof NioClientSocketChannel) {
                NioClientSocketChannel ch = (NioClientSocketChannel) attachment;
                if (!ch.isConnected() && ch.connectDeadlineNanos > 0 && currentTimeNanos >= ch.connectDeadlineNanos) {

                    if (cause == null) {
                        cause = new ConnectException("connection timed out");
                    }

                    ch.connectFuture.setFailure(cause);
                    fireExceptionCaught(ch, cause);
                    ch.getWorker().close(ch, succeededFuture(ch));
                }
            }
            
            
            
        }
    }

    protected void connect(SelectionKey k) {
        final NioClientSocketChannel ch = (NioClientSocketChannel) k.attachment();
        try {
            // TODO: Remove cast
            if (ch.getJdkChannel().finishConnect()) {
                registerTask(ch, ch.connectFuture);
            }
        } catch (Throwable t) {
            ch.connectFuture.setFailure(t);
            fireExceptionCaught(ch, t);
            k.cancel(); // Some JDK implementations run into an infinite loop without this.
            ch.getWorker().close(ch, succeededFuture(ch));
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
    

    
    protected void close(SelectionKey k) {
        Object attachment = k.attachment();
        if (attachment instanceof AbstractNioChannel) {
            AbstractNioChannel ch = (AbstractNioChannel) attachment;
            close(ch, succeededFuture(ch));
        } else if (attachment instanceof NioServerSocketChannel) {
            NioServerSocketChannel ch = (NioServerSocketChannel) attachment;
            close(ch, succeededFuture(ch));
        } else {
            // TODO: What todo ?
        }
    }

    public void writeFromUserCode(final AbstractNioChannel channel) {

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

    public void writeFromTaskLoop(AbstractNioChannel ch) {
        if (!ch.writeSuspended) {
            write0(ch);
        }
    }
    
    void writeFromSelectorLoop(final SelectionKey k) {
        AbstractNioChannel ch = (AbstractNioChannel) k.attachment();
        ch.writeSuspended = false;
        write0(ch);
    }

    
    protected boolean scheduleWriteIfNecessary(final AbstractNioChannel channel) {
        if (!isIoThread()) {
            if (channel.writeTaskInTaskQueue.compareAndSet(false, true)) {
                boolean offered = writeTaskQueue.offer(channel.writeTask);
                assert offered;
            }

            final Selector workerSelector = selector;
            if (workerSelector != null) {
                if (wakenUp.compareAndSet(false, true)) {
                    workerSelector.wakeup();
                }
            }
           
            return true;
        }

        return false;
    }       

    protected void write0(AbstractNioChannel channel) {
        boolean open = true;
        boolean addOpWrite = false;
        boolean removeOpWrite = false;
        boolean iothread = isIoThread();

        long writtenBytes = 0;

        final SendBufferPool sendBufferPool = this.sendBufferPool;
        
        final WritableByteChannel ch = channel.getJdkChannel();
        final Queue<MessageEvent> writeBuffer = channel.writeBufferQueue;
        final int writeSpinCount = channel.getConfig().getWriteSpinCount();
        synchronized (channel.writeLock) {
            channel.inWriteNowLoop = true;
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

                ChannelFuture future = evt.getFuture();
                try {
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
                    future.setFailure(t);
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

    /**
     * Return <code>true</code> if the current executing thread is the same as the one that runs the {@link #run()} method
     * 
     */
    boolean isIoThread() {
        return Thread.currentThread() == thread; 
    }
    
    protected void setOpWrite(AbstractNioChannel channel) {
        Selector selector = this.selector;
        SelectionKey key = channel.getJdkChannel().keyFor(selector);
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

    protected void clearOpWrite(AbstractNioChannel channel) {
        Selector selector = this.selector;
        SelectionKey key = channel.getJdkChannel().keyFor(selector);
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
    

    public void close(NioServerSocketChannel channel, ChannelFuture future) {
        boolean isIoThread = isIoThread();
        
        boolean bound = channel.isBound();
        try {
            if (channel.socket.isOpen()) {
                channel.socket.close();
                if (selector != null) {
                    selector.wakeup();
                }
            }

            // Make sure the boss thread is not running so that that the future
            // is notified after a new connection cannot be accepted anymore.
            // See NETTY-256 for more information.
            channel.shutdownLock.lock();
            try {
                if (channel.setClosed()) {
                    future.setSuccess();
                    if (bound) {
                        if (isIoThread) {
                            fireChannelUnbound(channel);
                        } else {
                            fireChannelUnboundLater(channel);
                        }
                    }
                    if (isIoThread) {
                        fireChannelClosed(channel);
                    } else {
                        fireChannelClosedLater(channel);
                    }
                } else {
                    future.setSuccess();
                }
            } finally {
                channel.shutdownLock.unlock();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            if (isIoThread) {
                fireExceptionCaught(channel, t);
            } else {
                fireExceptionCaughtLater(channel, t);
                
            }
        }
    }
    
    public void close(AbstractNioChannel channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();
        boolean iothread = isIoThread();
        
        try {
            channel.getJdkChannel().close();
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
                System.out.println(thread + "==" + channel.getWorker().thread);
                fireExceptionCaughtLater(channel, t);
            }
        }
    }

    protected void cleanUpWriteBuffer(AbstractNioChannel channel) {
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

            Queue<MessageEvent> writeBuffer = channel.writeBufferQueue;
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
            if (isIoThread()) {
                fireExceptionCaught(channel, cause);
            } else {
                fireExceptionCaughtLater(channel, cause);
            }
        }
    }

    public void setInterestOps(AbstractNioChannel channel, ChannelFuture future, int interestOps) {
        boolean changed = false;
        boolean iothread = isIoThread();
        try {
            // interestOps can change at any time and at any thread.
            // Acquire a lock to avoid possible race condition.
            synchronized (channel.interestOpsLock) {
                Selector selector = this.selector;
                SelectionKey key = channel.getJdkChannel().keyFor(selector);

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
                        if (!iothread &&
                            wakenUp.compareAndSet(false, true)) {
                            selector.wakeup();
                        }
                        changed = true;
                    }
                    break;
                case 1:
                case 2:
                    if (channel.getRawInterestOps() != interestOps) {
                        if (iothread) {
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
    
    /**
     * Read is called when a Selector has been notified that the underlying channel
     * was something to be read. The channel would previously have registered its interest
     * in read operations.
     *
     * @param k The selection key which contains the Selector registration information.
     */
    protected abstract boolean read(SelectionKey k);

    protected abstract void registerTask(AbstractNioChannel channel, ChannelFuture future);
    
}
