/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.epoll.AbstractEpollChannel.AbstractEpollUnsafe;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.IovArray;
import io.netty.util.IntSupplier;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.min;

/**
 * {@link EventLoop} which uses epoll under the covers. Only works on Linux!
 */
class EpollEventLoop extends SingleThreadEventLoop {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EpollEventLoop.class);
    /**
     * The maximum deadline value before overlap occurs on the time source.
     */
    private static final long MAXIMUM_DEADLINE = initialNanoTime() - 1;

    static {
        // Ensure JNI is initialized by the time this class is loaded by this time!
        // We use unix-common methods in this class which are backed by JNI methods.
        Epoll.ensureAvailability();
    }

    /**
     * Note that we use deadline instead of delay because deadline is just a fixed number but delay requires interacting
     * with the time source (e.g. calling System.nanoTime()) which can be expensive.
     */
    private final AtomicLong nextDeadlineNanos = new AtomicLong(MAXIMUM_DEADLINE);
    private final AtomicInteger wakenUp = new AtomicInteger();
    private final FileDescriptor epollFd;
    private final FileDescriptor eventFd;
    private final FileDescriptor timerFd;
    private final IntObjectMap<AbstractEpollChannel> channels = new IntObjectHashMap<AbstractEpollChannel>(4096);
    private final boolean allowGrowing;
    private final EpollEventArray events;

    // These are initialized on first use
    private IovArray iovArray;
    private NativeDatagramPacketArray datagramPacketArray;

    private final SelectStrategy selectStrategy;
    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
            return epollWaitNow();
        }
    };

    EpollEventLoop(EventLoopGroup parent, Executor executor, int maxEvents,
                   SelectStrategy strategy, RejectedExecutionHandler rejectedExecutionHandler,
                   EventLoopTaskQueueFactory queueFactory) {
        super(parent, executor, false, newTaskQueue(queueFactory), newTaskQueue(queueFactory),
                rejectedExecutionHandler);
        selectStrategy = ObjectUtil.checkNotNull(strategy, "strategy");
        if (maxEvents == 0) {
            allowGrowing = true;
            events = new EpollEventArray(4096);
        } else {
            allowGrowing = false;
            events = new EpollEventArray(maxEvents);
        }
        boolean success = false;
        FileDescriptor epollFd = null;
        FileDescriptor eventFd = null;
        FileDescriptor timerFd = null;
        try {
            this.epollFd = epollFd = Native.newEpollCreate();
            this.eventFd = eventFd = Native.newEventFd();
            try {
                // It is important to use EPOLLET here as we only want to get the notification once per
                // wakeup and don't call eventfd_read(...).
                Native.epollCtlAdd(epollFd.intValue(), eventFd.intValue(), Native.EPOLLIN | Native.EPOLLET);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to add eventFd filedescriptor to epoll", e);
            }
            this.timerFd = timerFd = Native.newTimerFd();
            try {
                // It is important to use EPOLLET here as we only want to get the notification once per
                // wakeup and don't call read(...).
                Native.epollCtlAdd(epollFd.intValue(), timerFd.intValue(), Native.EPOLLIN | Native.EPOLLET);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to add timerFd filedescriptor to epoll", e);
            }
            success = true;
        } finally {
            if (!success) {
                if (epollFd != null) {
                    try {
                        epollFd.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (eventFd != null) {
                    try {
                        eventFd.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (timerFd != null) {
                    try {
                        timerFd.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
    }

    private static Queue<Runnable> newTaskQueue(
            EventLoopTaskQueueFactory queueFactory) {
        if (queueFactory == null) {
            return newTaskQueue0(DEFAULT_MAX_PENDING_TASKS);
        }
        return queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
    }

    /**
     * Return a cleared {@link IovArray} that can be used for writes in this {@link EventLoop}.
     */
    IovArray cleanIovArray() {
        if (iovArray == null) {
            iovArray = new IovArray();
        } else {
            iovArray.clear();
        }
        return iovArray;
    }

    /**
     * Return a cleared {@link NativeDatagramPacketArray} that can be used for writes in this {@link EventLoop}.
     */
    NativeDatagramPacketArray cleanDatagramPacketArray() {
        if (datagramPacketArray == null) {
            datagramPacketArray = new NativeDatagramPacketArray();
        } else {
            datagramPacketArray.clear();
        }
        return datagramPacketArray;
    }

    @Override
    protected void executeScheduledRunnable(Runnable runnable, boolean isAddition, long deadlineNanos) {
        if (isAddition) {
            try {
                trySetTimerFd(deadlineNanos);
            } catch (IOException cause) {
                throw new RejectedExecutionException(cause);
            }
        }
        // else this is a removal of scheduled task and we could attempt to detect if this task was responsible
        // for the next delay, and find the next lowest delay in the queue to re-set the timer. However this
        // is not practical for the following reasons:
        // 1. The data structure is a PriorityQueue, and the scheduled task has not yet been removed. This means
        //    we would have to add/remove the head element to find the "next timeout".
        // 2. We are not on the EventLoop thread, and the PriorityQueue is not thread safe. We could attempt
        //    to do (1) if we are on the EventLoop but when the EventLoop wakes up it checks if the timeout changes
        //    when it is woken up and before it calls epoll_wait again and adjusts the timer accordingly.
        // The result is we wait until we are in the EventLoop and doing the actual removal, and also processing
        // regular polling in the EventLoop too.

        execute(runnable);
    }

    @Override
    protected boolean wakesUpForScheduledRunnable() {
        return false;
    }

    @Override
    protected boolean runAllTasks() {
        // This method is overridden to ensure that all the expired scheduled tasks are executed during shutdown, and
        // any other execute all scenarios in the base class.
        return runScheduledAndExecutorTasks(4);
    }

    private void trySetTimerFd(long candidateNextDeadline) throws IOException {
        for (;;) {
            long nextDeadline = nextDeadlineNanos.get();
            if (nextDeadline - candidateNextDeadline <= 0) {
                break;
            }
            if (nextDeadlineNanos.compareAndSet(nextDeadline, candidateNextDeadline)) {
                setTimerFd(deadlineToDelayNanos(candidateNextDeadline));
                // We are setting the timerFd outside of the EventLoop so it is possible that we raced with another call
                // to set the timer and temporarily increased the value, in which case we should set it back to the
                // lower value.
                nextDeadline = nextDeadlineNanos.get();
                if (nextDeadline - candidateNextDeadline < 0) {
                    setTimerFd(deadlineToDelayNanos(nextDeadline));
                }
                break;
            }
        }
    }

    private void setTimerFd(long candidateNextDelayNanos) throws IOException {
        if (candidateNextDelayNanos > 0) {
            final int delaySeconds = (int) min(candidateNextDelayNanos / 1000000000L, Integer.MAX_VALUE);
            final int delayNanos = (int) min(candidateNextDelayNanos - delaySeconds * 1000000000L, Integer.MAX_VALUE);
            Native.timerFdSetTime(timerFd.intValue(), delaySeconds, delayNanos);
        } else {
            // Setting the timer to 0, 0 will disarm it, so we have a few options:
            // 1. Set the timer wakeup to 1ns (1 system call).
            // 2. Use the eventFd to force a wakeup and disarm the timer (2 system calls).
            // For now we are using option (1) because there are less system calls, and we will correctly reset the
            // nextDeadlineNanos state when the EventLoop processes the timer wakeup.
            Native.timerFdSetTime(timerFd.intValue(), 0, 1);
        }
    }

    private void checkScheduleTaskQueueForNewDelay() throws IOException {
        final long deadlineNanos = nextScheduledTaskDeadlineNanos();
        if (deadlineNanos != -1) {
            trySetTimerFd(deadlineNanos);
        }
        // Don't disarm the timerFd even if there are no more queued tasks. Since we are setting timerFd from outside
        // the EventLoop it is possible that another thread has set the timer and we may miss a wakeup if we disarm
        // the timer here. Instead we wait for the timer wakeup on the EventLoop and clear state for the next timer.
    }

    @Override
    protected void minimumDelayScheduledTaskRemoved(@SuppressWarnings("unused") Runnable task,
                                                    @SuppressWarnings("unused") long deadlineNanos) {
        // It is OK to reset nextDeadlineNanos here because we are in the event loop thread, and the event loop is
        // guaranteed to transfer all pending ScheduledFutureTasks from the executor queue to the scheduled
        // PriorityQueue and we will set the next expiration time. If another thread races with this thread inserting a
        // ScheduledFutureTasks into the executor queue it should be OK, assuming the executor queue insertion is
        // visible to the event loop thread.
        //
        // Assume the current minimum timer is delayNanos = 10
        // Thread A -> execute(ScheduledFutureTask(delayNanos = 12)),
        //             add ScheduledFutureTask to the executor queue
        //             fail to set nextDeadlineNanos, so no call to setTimerFd is made
        // EventLoop -> minimumDelayScheduledTaskRemoved(10),
        //              set nextDeadlineNanos to MAXIMUM_DEADLINE
        //              ... process more tasks ...
        //              drain all the tasks from the executor queue, see that 12 is the next delay, call setTimerFd
        nextDeadlineNanos.set(MAXIMUM_DEADLINE);

        // Note that we don't actually call setTimerFd here, we don't want to interrupt the actual timerFd and let
        // the end of the EventLoop determine what the timerFd value should be (after execute queue is drained).
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop && wakenUp.getAndSet(1) == 0) {
            // write to the evfd which will then wake-up epoll_wait(...)
            Native.eventFdWrite(eventFd.intValue(), 1L);
        }
    }

    /**
     * Register the given epoll with this {@link EventLoop}.
     */
    void add(AbstractEpollChannel ch) throws IOException {
        assert inEventLoop();
        int fd = ch.socket.intValue();
        Native.epollCtlAdd(epollFd.intValue(), fd, ch.flags);
        AbstractEpollChannel old = channels.put(fd, ch);

        // We either expect to have no Channel in the map with the same FD or that the FD of the old Channel is already
        // closed.
        assert old == null || !old.isOpen();
    }

    /**
     * The flags of the given epoll was modified so update the registration
     */
    void modify(AbstractEpollChannel ch) throws IOException {
        assert inEventLoop();
        Native.epollCtlMod(epollFd.intValue(), ch.socket.intValue(), ch.flags);
    }

    /**
     * Deregister the given epoll from this {@link EventLoop}.
     */
    void remove(AbstractEpollChannel ch) throws IOException {
        assert inEventLoop();
        int fd = ch.socket.intValue();

        AbstractEpollChannel old = channels.remove(fd);
        if (old != null && old != ch) {
            // The Channel mapping was already replaced due FD reuse, put back the stored Channel.
            channels.put(fd, old);

            // If we found another Channel in the map that is mapped to the same FD the given Channel MUST be closed.
            assert !ch.isOpen();
        } else if (ch.isOpen()) {
            // Remove the epoll. This is only needed if it's still open as otherwise it will be automatically
            // removed once the file-descriptor is closed.
            Native.epollCtlDel(epollFd.intValue(), fd);
        }
    }

    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return newTaskQueue0(maxPendingTasks);
    }

    private static Queue<Runnable> newTaskQueue0(int maxPendingTasks) {
        // This event loop never calls takeTask()
        return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }

    @Override
    public int registeredChannels() {
        return channels.size();
    }

    private int epollWait() throws IOException {
        // If a task was submitted when wakenUp value was 1, the task didn't get a chance to produce wakeup event.
        // So we need to check task queue again before calling epoll_wait. If we don't, the task might be pended
        // until epoll_wait was timed out. It might be pended until idle timeout if IdleStateHandler existed
        // in pipeline.
        return Native.epollWait(epollFd, events, hasTasks());
    }

    private int epollWaitNow() throws IOException {
        return Native.epollWait(epollFd, events, true);
    }

    private int epollBusyWait() throws IOException {
        return Native.epollBusyWait(epollFd, events);
    }

    @Override
    protected void run() {
        for (;;) {
            try {
                int strategy = selectStrategy.calculateStrategy(selectNowSupplier, hasTasks());
                switch (strategy) {
                    case SelectStrategy.CONTINUE:
                        continue;

                    case SelectStrategy.BUSY_WAIT:
                        strategy = epollBusyWait();
                        break;

                    case SelectStrategy.SELECT:
                        if (wakenUp.get() == 1) {
                            wakenUp.set(0);
                        }
                        if (!hasTasks()) {
                            strategy = epollWait();
                        }
                        // fallthrough
                    default:
                }

                try {
                    processReady(events, strategy);
                } finally {
                    try {
                        // Note the timerFd code depends upon running all the tasks on each event loop run. This is so
                        // we can get an accurate "next wakeup time" after the event loop run completes.
                        runAllTasks();
                    } finally {
                        // No need to drainScheduledQueue() after the fact, because all in event loop scheduling results
                        // in direct addition to the scheduled priority queue.

                        // When we are in the EventLoop we don't bother setting the timerFd for each scheduled task, but
                        // instead defer the processing until the end of the EventLoop to reduce the timerFd
                        // modifications.
                        checkScheduleTaskQueueForNewDelay();
                    }
                }
                if (allowGrowing && strategy == events.length()) {
                    //increase the size of the array as we needed the whole space for the events
                    events.increase();
                }
            } catch (Throwable t) {
                handleLoopException(t);
            }
            // Always handle shutdown even if the loop processing threw an exception.
            try {
                if (isShuttingDown()) {
                    closeAll();
                    if (confirmShutdown()) {
                        break;
                    }
                }
            } catch (Throwable t) {
                handleLoopException(t);
            }
        }
    }

    /**
     * Visible only for testing!
     */
    void handleLoopException(Throwable t) {
        logger.warn("Unexpected exception in the selector loop.", t);

        // Prevent possible consecutive immediate failures that lead to
        // excessive CPU consumption.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore.
        }
    }

    private void closeAll() {
        try {
            epollWaitNow();
        } catch (IOException ignore) {
            // ignore on close
        }

        // Using the intermediate collection to prevent ConcurrentModificationException.
        // In the `close()` method, the channel is deleted from `channels` map.
        AbstractEpollChannel[] localChannels = channels.values().toArray(new AbstractEpollChannel[0]);

        for (AbstractEpollChannel ch: localChannels) {
            ch.unsafe().close(ch.unsafe().voidPromise());
        }
    }

    private void processReady(EpollEventArray events, int ready) {
        for (int i = 0; i < ready; ++i) {
            final int fd = events.fd(i);
            if (fd == eventFd.intValue()) {
                // Just ignore as we use ET mode for the eventfd and timerfd.
                //
                // See also https://stackoverflow.com/a/12492308/1074097
            } else if (fd == timerFd.intValue()) {
                // consume wakeup event, necessary because the timer is added with ET mode.
                Native.timerFdRead(fd);

                // The timer is normalized, monotonically increasing, and the next value is always set to the minimum
                // value of the pending timers. When the timer fires we can unset the timer value.
                // Worst case another thread races with this thread and we set another timer event while processing
                // this timer event and we get a duplicate wakeup some time in the future.
                //
                // This works because we always drain all ScheduledFutureTasks from the executor queue to the scheduled
                // PriorityQueue and we will set the next expiration time. If another thread races with this thread
                // inserting a ScheduledFutureTasks into the executor queue it should be OK, assuming the executor queue
                // insertion is visible to the event loop thread.
                //
                // Assume the current minimum timer is nextDeadlineNanos = 10
                // Thread A -> execute(ScheduledFutureTask(delayNanos = 12)),
                //             add ScheduledFutureTask to the executor queue
                //             fail to set nextDeadlineNanos, so no call to setTimerFd is made
                // EventLoop -> process timer wakeup here, set nextDeadlineNanos to MAXIMUM_DEADLINE
                //              ... process more tasks ...
                //              drain all the tasks from executor queue, see 12 is the next delay, call setTimerFd
                nextDeadlineNanos.set(MAXIMUM_DEADLINE);
            } else {
                final long ev = events.events(i);

                AbstractEpollChannel ch = channels.get(fd);
                if (ch != null) {
                    // Don't change the ordering of processing EPOLLOUT | EPOLLRDHUP / EPOLLIN if you're not 100%
                    // sure about it!
                    // Re-ordering can easily introduce bugs and bad side-effects, as we found out painfully in the
                    // past.
                    AbstractEpollUnsafe unsafe = (AbstractEpollUnsafe) ch.unsafe();

                    // First check for EPOLLOUT as we may need to fail the connect ChannelPromise before try
                    // to read from the file descriptor.
                    // See https://github.com/netty/netty/issues/3785
                    //
                    // It is possible for an EPOLLOUT or EPOLLERR to be generated when a connection is refused.
                    // In either case epollOutReady() will do the correct thing (finish connecting, or fail
                    // the connection).
                    // See https://github.com/netty/netty/issues/3848
                    if ((ev & (Native.EPOLLERR | Native.EPOLLOUT)) != 0) {
                        // Force flush of data as the epoll is writable again
                        unsafe.epollOutReady();
                    }

                    // Check EPOLLIN before EPOLLRDHUP to ensure all data is read before shutting down the input.
                    // See https://github.com/netty/netty/issues/4317.
                    //
                    // If EPOLLIN or EPOLLERR was received and the channel is still open call epollInReady(). This will
                    // try to read from the underlying file descriptor and so notify the user about the error.
                    if ((ev & (Native.EPOLLERR | Native.EPOLLIN)) != 0) {
                        // The Channel is still open and there is something to read. Do it now.
                        unsafe.epollInReady();
                    }

                    // Check if EPOLLRDHUP was set, this will notify us for connection-reset in which case
                    // we may close the channel directly or try to read more data depending on the state of the
                    // Channel and als depending on the AbstractEpollChannel subtype.
                    if ((ev & Native.EPOLLRDHUP) != 0) {
                        unsafe.epollRdHupReady();
                    }
                } else {
                    // We received an event for an fd which we not use anymore. Remove it from the epoll_event set.
                    try {
                        Native.epollCtlDel(epollFd.intValue(), fd);
                    } catch (IOException ignore) {
                        // This can happen but is nothing we need to worry about as we only try to delete
                        // the fd from the epoll set as we not found it in our mappings. So this call to
                        // epollCtlDel(...) is just to ensure we cleanup stuff and so may fail if it was
                        // deleted before or the file descriptor was closed before.
                    }
                }
            }
        }
    }

    @Override
    protected void cleanup() {
        try {
            try {
                epollFd.close();
            } catch (IOException e) {
                logger.warn("Failed to close the epoll fd.", e);
            }
            try {
                eventFd.close();
            } catch (IOException e) {
                logger.warn("Failed to close the event fd.", e);
            }
            try {
                timerFd.close();
            } catch (IOException e) {
                logger.warn("Failed to close the timer fd.", e);
            }
        } finally {
            // release native memory
            if (iovArray != null) {
                iovArray.release();
                iovArray = null;
            }
            if (datagramPacketArray != null) {
                datagramPacketArray.release();
                datagramPacketArray = null;
            }
            events.free();
        }
    }
}
