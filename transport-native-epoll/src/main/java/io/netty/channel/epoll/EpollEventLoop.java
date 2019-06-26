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
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.StandInThread;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ReflectionUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;

/**
 * {@link EventLoop} which uses epoll under the covers. Only works on Linux!
 */
class EpollEventLoop extends SingleThreadEventLoop {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EpollEventLoop.class);

    static int EVENT_ARRAY_COUNT = 32; // 2

    static {
        // Ensure JNI is initialized by the time this class is loaded by this time!
        // We use unix-common methods in this class which are backed by JNI methods.
        Epoll.ensureAvailability();
    }

    final FileDescriptor epollFd;
    private final IntObjectMap<AbstractEpollChannel> channels = new IntObjectHashMap<AbstractEpollChannel>(4096);
    private final boolean allowGrowing;

    final EpollLoop epollLoop;
    final EpollThread epollThread;

    final TagTeamConsumerArrayQueue<Runnable> taskQueue;

    // These are initialized on first use
    private IovArray iovArray;
    private NativeDatagramPacketArray datagramPacketArray;

    private final SelectStrategy selectStrategy;
//    private final IntSupplier selectNowSupplier = new IntSupplier() {
//        @Override
//        public int get() throws Exception {
//            return epollWaitNow();
//        }
//    };
//    private volatile int ioRatio = 50;

    EpollEventLoop(EventLoopGroup parent, Executor executor, int maxEvents,
                   SelectStrategy strategy, RejectedExecutionHandler rejectedExecutionHandler,
                   EventLoopTaskQueueFactory queueFactory) {
        super(parent, executor, true, newTagTeamQueue(), newTaskQueue(queueFactory),
                rejectedExecutionHandler);
        taskQueue = (TagTeamConsumerArrayQueue<Runnable>) taskQueue();
        selectStrategy = ObjectUtil.checkNotNull(strategy, "strategy");
        allowGrowing = maxEvents == 0;

        this.epollFd = Native.newEpollCreate();
        boolean success = false;
        try {
            epollLoop = new EpollLoop(epollFd);
            epollThread = new EpollThread(epollLoop);
            epollThread.setDaemon(true);
            success = true;
        } finally {
            if (!success) {
                try {
                    epollFd.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private static Queue<Runnable> newTaskQueue(EventLoopTaskQueueFactory queueFactory) {
        if (queueFactory == null) {
            return newTaskQueue0(DEFAULT_MAX_PENDING_TASKS);
        }
        return queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
    }

    private static Queue<Runnable> newTagTeamQueue() {
		return new TagTeamConsumerArrayQueue<Runnable>(32768);
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

    /**
     * Returns the percentage of the desired amount of time spent for I/O in the event loop.
     */
    public int getIoRatio() {
//        return ioRatio;
        return 50;
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the event loop.  The default value is
     * {@code 50}, which means the event loop will try to spend the same amount of time for I/O as for non-I/O tasks.
     */
    public void setIoRatio(int ioRatio) {
        if (ioRatio <= 0 || ioRatio > 100) {
            throw new IllegalArgumentException("ioRatio: " + ioRatio + " (expected: 0 < ioRatio <= 100)");
        }
//        this.ioRatio = ioRatio;
    }

    @Override
    public int registeredChannels() {
        return channels.size();
    }

    static final class EpollThread extends FastThreadLocalThread implements StandInThread {
        private Thread thread;

        public EpollThread(EpollLoop loop) {
            super(loop);
        }

        /**
         * Must be called from main thread
         */
        void startFromMainThread() {
            thread = Thread.currentThread();
            setName(thread.getName() + "-epoll");
            setThreadLocalMap(InternalThreadLocalMap.get());
            shareThreadLocalsTo(this);
            start();
        }

        @Override
        public Thread mainThread() {
            return thread;
        }
    }

    final class EpollLoop implements Runnable {
        private final EpollEventArray[] events;
        private final FileDescriptor eventFd;
        private final FileDescriptor timerFd;

        //TODO maybe padding for these?
        private volatile int eventsProducerIndex;
        // this is just a local copy of volatile eventsProducerIdx
        private int pEventsProducerIdx;
        volatile int eventsConsumerIdx;

        int nextEventsIdx(int idx) {
            return idx == events.length - 1 ? 0 : idx + 1;
        }

        private volatile boolean shutdown; // just for this thread

        EpollLoop(FileDescriptor epollFd) {
            //TODO size and count TBD .. derive from maxEvents
            events = new EpollEventArray[EVENT_ARRAY_COUNT];
            for (int i = 0; i < events.length; i++) {
                events[i] = new EpollEventArray(128);
            }
            boolean success = false;
            FileDescriptor eventFd = null;
            FileDescriptor timerFd = null;
            try {
                this.eventFd = eventFd = Native.newEventFd();
                try {
                    // It is important to use EPOLLET here as we only want to get the notification once per
                    // wakeup and don't call eventfd_read(...).
                    Native.epollCtlAdd(epollFd.intValue(), eventFd.intValue(), Native.EPOLLIN | Native.EPOLLET);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to add eventFd filedescriptor to epoll", e);
                }
                // timerFd not actually used
                this.timerFd = timerFd = Native.newTimerFd();
                success = true;
            } finally {
                if (!success) {
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

        @Override
        public void run() {
            // epoll/secondary thread loop
            while (epollWait()) {
                do {
                    runAllTasks();
                    // Re-check events before going to sleep
                } while (epollWaitNow() || !taskQueue.secondaryEnterWait());

                if (timerRescheduleRequired || isShuttingDown()) {
                    wakeup(false); // wake up primary thread
                    timerRescheduleRequired = false;
                }
            }
        }

        /**
         * @return null if shutdown
         */
        private EpollEventArray nextFreeArray() throws InterruptedException {
            int next = nextEventsIdx(pEventsProducerIdx);
            while (eventsConsumerIdx == next) {
                // events array is full, need to wait for consumer to catch up
                LockSupport.park(this);
                if (shutdown) {
                    return null;
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
            EpollEventArray arr = events[pEventsProducerIdx];
            if (allowGrowing && arr.ready == arr.length()) {
                //increase the size of the array as we needed the whole space for the events
                arr.increase();
            }
            return arr;
        }

        /**
         * Returns true in "active consumer" state or false if shutdown
         */
        private boolean epollWait() {
            for (;;) {
                try {
                    EpollEventArray arr = nextFreeArray();
                    if (arr == null) {
                        return false; //shutdown
                    }
                    int ready = Native.epollWait(epollFd, arr, timerFd, -1, -1);
                    if (shutdown) {
                        return false;
                    }
                    if (ready <= 0) {
                        continue;
                    }
                    arr.ready = ready;
                    eventsProducerIndex = pEventsProducerIdx = nextEventsIdx(pEventsProducerIdx);
                    int rc = taskQueue.secondaryOfferAndTryToGrabControl(PROCESS_IO_TASK);
                    if (rc == 1) {
                        return true;
                    }
                    if (rc != 0) {
                        throw new IllegalArgumentException("queue full"); //TODO handling TBD
                    }
                } catch (Exception e) {
                    handleLoopException(e);
                }
            }
        }

        /**
         * Called only when epoll thread has control.
         * Returns true if any events were handled, false otherwise
         */
        private boolean epollWaitNow() {
            try {
                EpollEventArray arr = nextFreeArray();
                if (arr != null) {
                    int ready = Native.epollWait(epollFd, arr, timerFd, 0, 0);
                    if (ready > 0) {
                        arr.ready = ready;
                        eventsProducerIndex = pEventsProducerIdx = nextEventsIdx(pEventsProducerIdx);
                        if (!taskQueue.offer(PROCESS_IO_TASK)) {
                            //TODO handle full queue properly
                            throw new IllegalStateException("queue full");
                        }
                        return true;
                    }
                }
            } catch(Exception e) {
                handleLoopException(e);
            }
            return false;
        }

        private final Runnable PROCESS_IO_TASK = new Runnable() {
            // local copy of volatile eventsConsumerIdx
            private int pEventsConsumerIdx = 0;

            @Override
            public void run() {
                int indexBefore = pEventsConsumerIdx;
                EpollEventArray eea = events[indexBefore];
                processReady(eea, eea.ready);
                eventsConsumerIdx = pEventsConsumerIdx = nextEventsIdx(pEventsConsumerIdx);
                if (indexBefore == nextEventsIdx(eventsProducerIndex)) {
                    LockSupport.unpark(epollThread);
                }
            }
        };
        
        void processReady(EpollEventArray events, int ready) {
            for (int i = 0; i < ready; i ++) {
                final int fd = events.fd(i);
                if (fd == eventFd.intValue()) { //  || fd == timerFd.intValue()) { // (no longer timerFd events)
                    // Just ignore as we use ET mode for the eventfd and timerfd.
                    //
                    // See also https://stackoverflow.com/a/12492308/1074097
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

        void shutdown() {
            shutdown = true;
            Native.eventFdWrite(eventFd.intValue(), 1L);
            LockSupport.unpark(epollThread);
        }

        void cleanup() {
            try {
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
                for (EpollEventArray arr : events) {
                    arr.free();
                }
            }
        }
    }

    @Override
    public final boolean inEventLoop(Thread thread) {
        return super.inEventLoop(thread) || thread == epollThread;
    }

    @Override
    protected void run() {
        taskQueue.setConsumerThread(Thread.currentThread());
        epollThread.startFromMainThread();

        // primary thread loop
        for (;;) {
            try {
                Runnable r = waitForTask();
                if (r != null) {
                    safeExecute(r);
                    runAllTasks(true);
                } else {
                    runAllTasks();
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

        try {
            epollLoop.shutdown();
            epollThread.join();
        } catch (InterruptedException e) {
            logger.warn(e); //TODO TBD
            Thread.currentThread().interrupt();
        }
    }

    private long nextWakeupTimeNanos = -1L;

    private Runnable waitForTask() throws InterruptedException {
        // assert taskQueue.getState() == ST_PRIMARY_ACTIVE;
        nextWakeupTimeNanos = rawDeadlineNanos();
        return nextWakeupTimeNanos == -1L ? taskQueue.take()
                : taskQueue.pollUntil(nanoTimeBase() + nextWakeupTimeNanos);
    }

    @Override
    protected final void addTask(Runnable task) {
        ObjectUtil.checkNotNull(task, "task");
        if (isShutdown()) {
            reject();
        } else if (!taskQueue.offer(task, wakesUpForTask(task))) {
            reject(task);
        }
    }

    // accessed only from epoll thread
    boolean timerRescheduleRequired;

    @Override
    protected void newTaskScheduled(long deadlineNanos) {
        if (!timerRescheduleRequired
                && (nextWakeupTimeNanos == -1L || nextWakeupTimeNanos > deadlineNanos)
                && Thread.currentThread() == epollThread) {
            timerRescheduleRequired = true;
        }
    }

    @Override
    protected final boolean runAllTasks() {
        return runAllTasks(false);
    }

    private final boolean runAllTasks(boolean ranAtLeastOne) {
        for (long consumerIndex = taskQueue.plainCurrentConsumerIndex();;) {
            final long producerIndexBefore = taskQueue.currentProducerIndex();
            final boolean hasTasks = producerIndexBefore != consumerIndex;
            if (hasTasks) {
                // Run only the tasks which are already in the queue
                // prior to running expired time-scheduled tasks
                do {
                    safeExecute(taskQueue.poll());
                } while (++consumerIndex < producerIndexBefore);
            }
            if (!runExpiredScheduledTasks() && !hasTasks) {
                break;
            }
            ranAtLeastOne = true;
        }
        if (ranAtLeastOne) {
            updateLastExecutionTime();
        }
        afterRunningAllTasks();
        return ranAtLeastOne;
    }

    private boolean runExpiredScheduledTasks() {
        if (hasAnyScheduledTasks()) {
            // Only run those already expired when this method was called
            final long nanoTimeBefore = nanoTime();
            Runnable r = pollScheduledTask(nanoTimeBefore);
            if (r != null) {
                do {
                    safeExecute(r);
                } while ((r = pollScheduledTask(nanoTimeBefore)) != null);
                return true;
            }
        }
        return false;
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
        // Using the intermediate collection to prevent ConcurrentModificationException.
        // In the `close()` method, the channel is deleted from `channels` map.
        AbstractEpollChannel[] localChannels = channels.values().toArray(new AbstractEpollChannel[0]);

        for (AbstractEpollChannel ch: localChannels) {
            ch.unsafe().close(ch.unsafe().voidPromise());
        }
    }

    @Override
    protected void cleanup() {
        try {
            epollLoop.cleanup();
            try {
                epollFd.close();
            } catch (IOException e) {
                logger.warn("Failed to close the epoll fd.", e);
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
        }
    }

    // optional - best-effort
    private static void shareThreadLocalsTo(Thread target) {
        try {
            //TODO PrivilegedAction
            java.lang.reflect.Field map = Thread.class.getDeclaredField("threadLocals");
            if (ReflectionUtil.trySetAccessible(map, true) == null) {
                // this ensures the current thread's map is initialized
                // before we copy it
                ThreadLocal<Integer> tempTl = new ThreadLocal<Integer>();
                tempTl.set(1);
                tempTl.remove();
                map.set(target, map.get(Thread.currentThread()));
            }
        } catch(Exception e) {
            //TODO maybe log
        }
    }
}