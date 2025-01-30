/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.channel.Channel;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoOps;
import io.netty.channel.IoRegistration;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.IntSupplier;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.ThreadAwareExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.min;
import static java.lang.System.nanoTime;

/**
 * {@link IoHandler} which uses epoll under the covers. Only works on Linux!
 */
public class EpollIoHandler implements IoHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EpollIoHandler.class);
    private static final long EPOLL_WAIT_MILLIS_THRESHOLD =
            SystemPropertyUtil.getLong("io.netty.channel.epoll.epollWaitThreshold", 10);

    static {
        // Ensure JNI is initialized by the time this class is loaded by this time!
        // We use unix-common methods in this class which are backed by JNI methods.
        Epoll.ensureAvailability();
    }

    // Pick a number that no task could have previously used.
    private long prevDeadlineNanos = nanoTime() - 1;
    private FileDescriptor epollFd;
    private FileDescriptor eventFd;
    private FileDescriptor timerFd;
    private final IntObjectMap<DefaultEpollIoRegistration> registrations = new IntObjectHashMap<>(4096);
    private final boolean allowGrowing;
    private final EpollEventArray events;
    private final NativeArrays nativeArrays;

    private final SelectStrategy selectStrategy;
    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
            return epollWaitNow();
        }
    };
    private final ThreadAwareExecutor executor;

    private static final long AWAKE = -1L;
    private static final long NONE = Long.MAX_VALUE;

    // nextWakeupNanos is:
    //    AWAKE            when EL is awake
    //    NONE             when EL is waiting with no wakeup scheduled
    //    other value T    when EL is waiting with wakeup scheduled at time T
    private final AtomicLong nextWakeupNanos = new AtomicLong(AWAKE);
    private boolean pendingWakeup;

    private int numChannels;

    // See https://man7.org/linux/man-pages/man2/timerfd_create.2.html.
    private static final long MAX_SCHEDULED_TIMERFD_NS = 999999999;

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link EpollIoHandler} instances.
     */
    public static IoHandlerFactory newFactory() {
        return newFactory(0, DefaultSelectStrategyFactory.INSTANCE);
    }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link EpollIoHandler} instances.
     */
    public static IoHandlerFactory newFactory(final int maxEvents,
                                              final SelectStrategyFactory selectStrategyFactory) {
        ObjectUtil.checkPositiveOrZero(maxEvents, "maxEvents");
        ObjectUtil.checkNotNull(selectStrategyFactory, "selectStrategyFactory");
        return executor -> new EpollIoHandler(executor, maxEvents, selectStrategyFactory.newSelectStrategy());
    }

    // Package-private for testing
    EpollIoHandler(ThreadAwareExecutor executor, int maxEvents, SelectStrategy strategy) {
        this.executor = ObjectUtil.checkNotNull(executor, "executor");
        selectStrategy = ObjectUtil.checkNotNull(strategy, "strategy");
        if (maxEvents == 0) {
            allowGrowing = true;
            events = new EpollEventArray(4096);
        } else {
            allowGrowing = false;
            events = new EpollEventArray(maxEvents);
        }
        nativeArrays = new NativeArrays();
        openFileDescriptors();
    }

    private static EpollIoHandle cast(IoHandle handle) {
        if (handle instanceof EpollIoHandle) {
            return (EpollIoHandle) handle;
        }
        throw new IllegalArgumentException("IoHandle of type " + StringUtil.simpleClassName(handle) + " not supported");
    }

    private static EpollIoOps cast(IoOps ops) {
        if (ops instanceof EpollIoOps) {
            return (EpollIoOps) ops;
        }
        throw new IllegalArgumentException("IoOps of type " + StringUtil.simpleClassName(ops) + " not supported");
    }

    /**
     * This method is intended for use by a process checkpoint/restore
     * integration, such as OpenJDK CRaC.
     */
    @UnstableApi
    public void openFileDescriptors() {
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
                closeFileDescriptor(epollFd);
                closeFileDescriptor(eventFd);
                closeFileDescriptor(timerFd);
            }
        }
    }

    private static void closeFileDescriptor(FileDescriptor fd) {
        if (fd != null) {
            try {
                fd.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Override
    public void wakeup() {
        if (!executor.isExecutorThread(Thread.currentThread()) && nextWakeupNanos.getAndSet(AWAKE) != AWAKE) {
            // write to the evfd which will then wake-up epoll_wait(...)
            Native.eventFdWrite(eventFd.intValue(), 1L);
        }
    }

    @Override
    public void prepareToDestroy() {
        // Using the intermediate collection to prevent ConcurrentModificationException.
        // In the `close()` method, the channel is deleted from `channels` map.
        DefaultEpollIoRegistration[] copy = registrations.values().toArray(new DefaultEpollIoRegistration[0]);

        for (DefaultEpollIoRegistration reg: copy) {
            reg.close();
        }
    }

    @Override
    public void destroy() {
        try {
            closeFileDescriptors();
        } finally {
            nativeArrays.free();
            events.free();
        }
    }

    private final class DefaultEpollIoRegistration implements IoRegistration {
        private final ThreadAwareExecutor executor;
        private final AtomicBoolean canceled = new AtomicBoolean();
        final EpollIoHandle handle;

        DefaultEpollIoRegistration(ThreadAwareExecutor executor, EpollIoHandle handle) {
            this.executor = executor;
            this.handle = handle;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T attachment() {
            return (T) nativeArrays;
        }

        @Override
        public long submit(IoOps ops) {
            EpollIoOps epollIoOps = cast(ops);
            try {
                if (!isValid()) {
                    return -1;
                }
                Native.epollCtlMod(epollFd.intValue(), handle.fd().intValue(), epollIoOps.value);
                return epollIoOps.value;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean isValid() {
            return !canceled.get();
        }

        @Override
        public boolean cancel() {
            if (!canceled.compareAndSet(false, true)) {
                return false;
            }
            if (executor.isExecutorThread(Thread.currentThread())) {
                cancel0();
            } else {
                executor.execute(this::cancel0);
            }
            return true;
        }

        private void cancel0() {
            int fd = handle.fd().intValue();
            DefaultEpollIoRegistration old = registrations.remove(fd);
            if (old != null) {
                if (old != this) {
                    // The Channel mapping was already replaced due FD reuse, put back the stored Channel.
                    registrations.put(fd, old);
                    return;
                } else if (old.handle instanceof AbstractEpollChannel.AbstractEpollUnsafe) {
                    numChannels--;
                }
                if (handle.fd().isOpen()) {
                    try {
                        // Remove the fd registration from epoll. This is only needed if it's still open as otherwise
                        // it will be automatically removed once the file-descriptor is closed.
                        Native.epollCtlDel(epollFd.intValue(), fd);
                    } catch (IOException e) {
                        logger.debug("Unable to remove fd {} from epoll {}", fd, epollFd.intValue());
                    }
                }
            }
        }

        void close() {
            try {
                cancel();
            } catch (Exception e) {
                logger.debug("Exception during canceling " + this, e);
            }
            try {
                handle.close();
            } catch (Exception e) {
                logger.debug("Exception during closing " + handle, e);
            }
        }

        void handle(long ev) {
            handle.handle(this, EpollIoOps.eventOf((int) ev));
        }
    }

    @Override
    public IoRegistration register(IoHandle handle)
            throws Exception {
        final EpollIoHandle epollHandle = cast(handle);
        DefaultEpollIoRegistration registration = new DefaultEpollIoRegistration(executor, epollHandle);
        int fd = epollHandle.fd().intValue();
        Native.epollCtlAdd(epollFd.intValue(), fd, EpollIoOps.EPOLLERR.value);
        DefaultEpollIoRegistration old = registrations.put(fd, registration);

        // We either expect to have no registration in the map with the same FD or that the FD of the old registration
        // is already closed.
        assert old == null || !old.isValid();

        if (epollHandle instanceof AbstractEpollChannel.AbstractEpollUnsafe) {
            numChannels++;
        }
        return registration;
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return EpollIoHandle.class.isAssignableFrom(handleType);
    }

    int numRegisteredChannels() {
        return numChannels;
    }

    List<Channel> registeredChannelsList() {
        IntObjectMap<DefaultEpollIoRegistration> ch = registrations;
        if (ch.isEmpty()) {
            return Collections.emptyList();
        }

        List<Channel> channels = new ArrayList<>(ch.size());
        for (DefaultEpollIoRegistration registration : ch.values()) {
            if (registration.handle instanceof AbstractEpollChannel.AbstractEpollUnsafe) {
                channels.add(((AbstractEpollChannel.AbstractEpollUnsafe) registration.handle).channel());
            }
        }
        return Collections.unmodifiableList(channels);
    }

    private long epollWait(IoHandlerContext context, long deadlineNanos) throws IOException {
        if (deadlineNanos == NONE) {
            return Native.epollWait(epollFd, events, timerFd,
                    Integer.MAX_VALUE, 0, EPOLL_WAIT_MILLIS_THRESHOLD); // disarm timer
        }
        long totalDelay = context.delayNanos(System.nanoTime());
        int delaySeconds = (int) min(totalDelay / 1000000000L, Integer.MAX_VALUE);
        int delayNanos = (int) min(totalDelay - delaySeconds * 1000000000L, MAX_SCHEDULED_TIMERFD_NS);
        return Native.epollWait(epollFd, events, timerFd, delaySeconds, delayNanos, EPOLL_WAIT_MILLIS_THRESHOLD);
    }

    private int epollWaitNoTimerChange() throws IOException {
        return Native.epollWait(epollFd, events, false);
    }

    private int epollWaitNow() throws IOException {
        return Native.epollWait(epollFd, events, true);
    }

    private int epollBusyWait() throws IOException {
        return Native.epollBusyWait(epollFd, events);
    }

    private int epollWaitTimeboxed() throws IOException {
        // Wait with 1 second "safeguard" timeout
        return Native.epollWait(epollFd, events, 1000);
    }

    @Override
    public int run(IoHandlerContext context) {
        int handled = 0;
        try {
            int strategy = selectStrategy.calculateStrategy(selectNowSupplier, !context.canBlock());
            switch (strategy) {
                case SelectStrategy.CONTINUE:
                    return 0;

                case SelectStrategy.BUSY_WAIT:
                    strategy = epollBusyWait();
                    break;

                case SelectStrategy.SELECT:
                    if (pendingWakeup) {
                        // We are going to be immediately woken so no need to reset wakenUp
                        // or check for timerfd adjustment.
                        strategy = epollWaitTimeboxed();
                        if (strategy != 0) {
                            break;
                        }
                        // We timed out so assume that we missed the write event due to an
                        // abnormally failed syscall (the write itself or a prior epoll_wait)
                        logger.warn("Missed eventfd write (not seen after > 1 second)");
                        pendingWakeup = false;
                        if (!context.canBlock()) {
                            break;
                        }
                        // fall-through
                    }

                    long curDeadlineNanos = context.deadlineNanos();
                    if (curDeadlineNanos == -1L) {
                        curDeadlineNanos = NONE; // nothing on the calendar
                    }
                    nextWakeupNanos.set(curDeadlineNanos);
                    try {
                        if (context.canBlock()) {
                            if (curDeadlineNanos == prevDeadlineNanos) {
                                // No timer activity needed
                                strategy = epollWaitNoTimerChange();
                            } else {
                                // Timerfd needs to be re-armed or disarmed
                                long result = epollWait(context, curDeadlineNanos);
                                // The result contains the actual return value and if a timer was used or not.
                                // We need to "unpack" using the helper methods exposed in Native.
                                strategy = Native.epollReady(result);
                                prevDeadlineNanos = Native.epollTimerWasUsed(result) ? curDeadlineNanos : NONE;
                            }
                        }
                    } finally {
                        // Try get() first to avoid much more expensive CAS in the case we
                        // were woken via the wakeup() method (submitted task)
                        if (nextWakeupNanos.get() == AWAKE || nextWakeupNanos.getAndSet(AWAKE) == AWAKE) {
                            pendingWakeup = true;
                        }
                    }
                    // fallthrough
                default:
            }
            if (strategy > 0) {
                handled = strategy;
                if (processReady(events, strategy)) {
                    prevDeadlineNanos = NONE;
                }
            }
            if (allowGrowing && strategy == events.length()) {
                //increase the size of the array as we needed the whole space for the events
                events.increase();
            }
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            handleLoopException(t);
        }
        return handled;
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

    // Returns true if a timerFd event was encountered
    private boolean processReady(EpollEventArray events, int ready) {
        boolean timerFired = false;
        for (int i = 0; i < ready; i ++) {
            final int fd = events.fd(i);
            if (fd == eventFd.intValue()) {
                pendingWakeup = false;
            } else if (fd == timerFd.intValue()) {
                timerFired = true;
            } else {
                final long ev = events.events(i);

                DefaultEpollIoRegistration registration = registrations.get(fd);
                if (registration != null) {
                    registration.handle(ev);
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
        return timerFired;
    }

    /**
     * This method is intended for use by process checkpoint/restore
     * integration, such as OpenJDK CRaC.
     * It's up to the caller to ensure that there is no concurrent use
     * of the FDs while these are closed, e.g. by blocking the executor.
     */
    @UnstableApi
    public void closeFileDescriptors() {
        // Ensure any in-flight wakeup writes have been performed prior to closing eventFd.
        while (pendingWakeup) {
            try {
                int count = epollWaitTimeboxed();
                if (count == 0) {
                    // We timed-out so assume that the write we're expecting isn't coming
                    break;
                }
                for (int i = 0; i < count; i++) {
                    if (events.fd(i) == eventFd.intValue()) {
                        pendingWakeup = false;
                        break;
                    }
                }
            } catch (IOException ignore) {
                // ignore
            }
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

        try {
            epollFd.close();
        } catch (IOException e) {
            logger.warn("Failed to close the epoll fd.", e);
        }
    }
}
