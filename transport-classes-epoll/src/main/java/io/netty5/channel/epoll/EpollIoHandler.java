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
package io.netty5.channel.epoll;

import io.netty5.channel.DefaultSelectStrategyFactory;
import io.netty5.channel.EventLoop;
import io.netty5.channel.IoExecutionContext;
import io.netty5.channel.IoHandle;
import io.netty5.channel.IoHandler;
import io.netty5.channel.IoHandlerFactory;
import io.netty5.channel.IoOps;
import io.netty5.channel.SelectStrategy;
import io.netty5.channel.SelectStrategyFactory;
import io.netty5.channel.unix.FileDescriptor;
import io.netty5.channel.unix.IovArray;
import io.netty5.util.collection.IntObjectHashMap;
import io.netty5.util.collection.IntObjectMap;
import io.netty5.util.concurrent.Ticker;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.UnstableApi;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * {@link IoHandler} which uses epoll under the covers. Only works on Linux!
 */
public class EpollIoHandler implements IoHandler {
    private static final long EPOLL_WAIT_MILLIS_THRESHOLD =
            SystemPropertyUtil.getLong("io.netty5.channel.epoll.epollWaitThreshold", 10);

    private static final Logger logger;

    static {
        logger = LoggerFactory.getLogger(EpollIoHandler.class);
        // Ensure JNI is initialized by the time this class is loaded by this time!
        // We use unix-common methods in this class which are backed by JNI methods.
        Epoll.ensureAvailability();
    }

    // Pick a number that no task could have previously used.
    private long prevDeadlineNanos = Ticker.systemTicker().nanoTime() - 1;
    private FileDescriptor epollFd;
    private FileDescriptor eventFd;
    private FileDescriptor timerFd;
    private final IntObjectMap<DefaultEpollIoRegistration> registrations = new IntObjectHashMap<>(4096);
    private final boolean allowGrowing;
    private final EpollEventArray events;

    // These are initialized on first use
    private IovArray iovArray;
    private NativeDatagramPacketArray datagramPacketArray;

    private final SelectStrategy selectStrategy;
    private final IntSupplier selectNowSupplier = () -> {
        try {
            return epollWaitNow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    };

    private static final long AWAKE = -1L;
    private static final long NONE = Long.MAX_VALUE;

    // nextWakeupNanos is:
    //    AWAKE            when EL is awake
    //    NONE             when EL is waiting with no wakeup scheduled
    //    other value T    when EL is waiting with wakeup scheduled at time T
    private final AtomicLong nextWakeupNanos = new AtomicLong(AWAKE);

    private boolean pendingWakeup;

    // See https://man7.org/linux/man-pages/man2/timerfd_create.2.html.
    private static final long MAX_SCHEDULED_TIMERFD_NS = 999999999;

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

    private EpollIoHandler() {
        this(0, DefaultSelectStrategyFactory.INSTANCE.newSelectStrategy());
    }

    // Package-private for tests.
    @VisibleForTesting
    EpollIoHandler(int maxEvents, SelectStrategy strategy) {
        selectStrategy = strategy;
        if (maxEvents == 0) {
            allowGrowing = true;
            events = new EpollEventArray(4096);
        } else {
            allowGrowing = false;
            events = new EpollEventArray(maxEvents);
        }
        openFileDescriptors();
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

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link EpollIoHandler} instances.
     */
    public static IoHandlerFactory newFactory() {
        return EpollIoHandler::new;
    }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link EpollIoHandler} instances.
     */
    public static IoHandlerFactory newFactory(final int maxEvents,
                                              final SelectStrategyFactory selectStrategyFactory) {
        checkPositiveOrZero(maxEvents, "maxEvents");
        requireNonNull(selectStrategyFactory, "selectStrategyFactory");
        return () -> new EpollIoHandler(maxEvents, selectStrategyFactory.newSelectStrategy());
    }

    IovArray cleanIovArray() {
        if (iovArray == null) {
            iovArray = new IovArray();
        } else {
            iovArray.clear();
        }
        return iovArray;
    }

    NativeDatagramPacketArray cleanDatagramPacketArray() {
        if (datagramPacketArray == null) {
            datagramPacketArray = new NativeDatagramPacketArray();
        } else {
            datagramPacketArray.clear();
        }
        return datagramPacketArray;
    }

    private final class DefaultEpollIoRegistration extends AtomicBoolean implements EpollIoRegistration {
        private final EventLoop eventLoop;
        final EpollIoHandle handle;

        DefaultEpollIoRegistration(EventLoop eventLoop, EpollIoHandle handle) {
            this.eventLoop = eventLoop;
            this.handle = handle;
        }

        @Override
        public long submit(IoOps ops) throws Exception {
            EpollIoOps epollIoOps = cast(ops);
            try {
                if (!isValid()) {
                    return -1;
                }
                Native.epollCtlMod(epollFd.intValue(), handle.fd().intValue(), epollIoOps.value);
                return epollIoOps.value;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public EpollIoHandler ioHandler() {
            return EpollIoHandler.this;
        }

        @Override
        public boolean isValid() {
            return !get();
        }

        @Override
        public void cancel() throws IOException {
            if (getAndSet(true)) {
                return;
            }
            if (handle.fd().isOpen()) {
                // Remove the fd registration from epoll. This is only needed if it's still open as otherwise it will
                // be automatically removed once the file-descriptor is closed.
                Native.epollCtlDel(epollFd.intValue(), handle.fd().intValue());
            }
            if (eventLoop.inEventLoop()) {
                cancel0();
            } else {
                eventLoop.execute(this::cancel0);
            }
        }

        private void cancel0() {
            int fd = handle.fd().intValue();
            DefaultEpollIoRegistration old = registrations.remove(fd);
            if (old != this) {
                // The Channel mapping was already replaced due FD reuse, put back the stored Channel.
                registrations.put(fd, old);
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
    public EpollIoRegistration register(EventLoop eventLoop, IoHandle handle)
            throws Exception {
        final EpollIoHandle epollHandle = cast(handle);
        DefaultEpollIoRegistration registration = new DefaultEpollIoRegistration(eventLoop, epollHandle);
        int fd = epollHandle.fd().intValue();
        Native.epollCtlAdd(epollFd.intValue(), fd, EpollIoOps.EPOLLERR.value);
        DefaultEpollIoRegistration old = registrations.put(fd, registration);

        // We either expect to have no registration in the map with the same FD or that the FD of the old registration
        // is already closed / cancelled.
        assert old == null || !old.isValid() || !old.handle.fd().isOpen();

        return registration;
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return EpollIoHandle.class.isAssignableFrom(handleType);
    }

    @Override
    public void wakeup(EventLoop eventLoop) {
        if (!eventLoop.inEventLoop() && nextWakeupNanos.getAndSet(AWAKE) != AWAKE) {
            // write to the evfd which will then wake-up epoll_wait(...)
            Native.eventFdWrite(eventFd.intValue(), 1L);
        }
    }

    private long epollWait(IoExecutionContext context, long deadlineNanos) throws IOException {
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
    public final int run(IoExecutionContext context) {
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
                    // fall-through
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
        } catch (Error error) {
            throw error;
        } catch (Throwable t) {
            handleLoopException(t);
        }
        return handled;
    }

    /**
     * Visible only for testing!
     */
    @VisibleForTesting
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

    @Override
    public void prepareToDestroy() {
        // Using the intermediate collection to prevent ConcurrentModificationException.
        // In the `close()` method, the channel is deleted from `channels` map.
        DefaultEpollIoRegistration[] copy = registrations.values().toArray(new DefaultEpollIoRegistration[0]);

        for (DefaultEpollIoRegistration reg: copy) {
            reg.close();
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

    @Override
    public final void destroy() {
        try {
            closeFileDescriptors();
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
