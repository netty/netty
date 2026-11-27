/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoOps;
import io.netty.channel.IoRegistration;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.IntSupplier;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import io.netty.util.concurrent.ThreadAwareExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.lang.Math.min;

/**
 * {@link IoHandler} which uses kqueue under the covers. Only works on BSD!
 */
public final class KQueueIoHandler implements IoHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(KQueueIoHandler.class);
    private static final AtomicIntegerFieldUpdater<KQueueIoHandler> WAKEUP_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(KQueueIoHandler.class, "wakeupState");
    private static final int AWAKE = 0;
    private static final int SELECTING = 1;
    private static final int WAKEUP_REQUESTED = 2;
    private static final int KQUEUE_WAKE_UP_IDENT = 0;
    // `kqueue()` may return EINVAL when a large number such as Integer.MAX_VALUE is specified as timeout.
    // 24 hours would be a large enough value.
    // https://man.freebsd.org/cgi/man.cgi?query=kevent&apropos=0&sektion=0&manpath=FreeBSD+6.1-RELEASE&format=html#end
    private static final int KQUEUE_MAX_TIMEOUT_SECONDS = 86399; // 24 hours - 1 second

    {
        KQueue.ensureAvailability();
    }

    private final boolean allowGrowing;
    private final FileDescriptor kqueueFd;
    private final KQueueEventArray changeList;
    private final KQueueEventArray eventList;
    private final SelectStrategy selectStrategy;
    private final NativeArrays nativeArrays;
    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
            return kqueueWaitNow();
        }
    };
    private final ThreadAwareExecutor executor;
    private final Queue<DefaultKqueueIoRegistration> cancelledRegistrations = new ArrayDeque<>();
    private final LongObjectMap<DefaultKqueueIoRegistration> registrations = new LongObjectHashMap<>(4096);
    private long nextId;

    // AWAKE:            the event loop is running and will not block without checking for work first.
    // SELECTING:        the event loop is about to block in (or is blocked in) kevent(...).
    // WAKEUP_REQUESTED: another thread asked for a wakeup. If the event loop was SELECTING the user event was
    //                   triggered as well. Otherwise the request is only remembered and makes the next
    //                   kevent(...) non-blocking, which avoids a syscall for every task submitted to a running
    //                   event loop. A wakeup does not always come with a queued task (for example, suspension).
    private volatile int wakeupState = AWAKE;

    private long generateNextId() {
        boolean reset = false;
        for (;;) {
            if (nextId == Long.MAX_VALUE) {
                if (reset) {
                    throw new IllegalStateException("All possible ids in use");
                }
                reset = true;
            }
            nextId++;
            if (nextId == KQUEUE_WAKE_UP_IDENT) {
                continue;
            }
            if (!registrations.containsKey(nextId)) {
                return nextId;
            }
        }
    }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link KQueueIoHandler} instances.
     */
    public static IoHandlerFactory newFactory() {
        return newFactory(0, DefaultSelectStrategyFactory.INSTANCE);
    }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link KQueueIoHandler} instances.
     */
    public static IoHandlerFactory newFactory(final int maxEvents,
                                              final SelectStrategyFactory selectStrategyFactory) {
        KQueue.ensureAvailability();
        ObjectUtil.checkPositiveOrZero(maxEvents, "maxEvents");
        ObjectUtil.checkNotNull(selectStrategyFactory, "selectStrategyFactory");
        return new IoHandlerFactory() {
            @Override
            public IoHandler newHandler(ThreadAwareExecutor executor) {
                return new KQueueIoHandler(executor, maxEvents, selectStrategyFactory.newSelectStrategy());
            }

            @Override
            public boolean isChangingThreadSupported() {
                return true;
            }
        };
    }

    private KQueueIoHandler(ThreadAwareExecutor executor, int maxEvents, SelectStrategy strategy) {
        this.executor = ObjectUtil.checkNotNull(executor, "executor");
        this.selectStrategy = ObjectUtil.checkNotNull(strategy, "strategy");
        this.kqueueFd = Native.newKQueue();
        if (maxEvents == 0) {
            allowGrowing = true;
            maxEvents = 4096;
        } else {
            allowGrowing = false;
        }
        this.changeList = new KQueueEventArray(maxEvents);
        this.eventList = new KQueueEventArray(maxEvents);
        nativeArrays = new NativeArrays();
        int result = Native.keventAddUserEvent(kqueueFd.intValue(), KQUEUE_WAKE_UP_IDENT);
        if (result < 0) {
            destroy();
            throw new IllegalStateException("kevent failed to add user event with errno: " + (-result));
        }
    }

    @Override
    public void wakeup() {
        if (!executor.isExecutorThread(Thread.currentThread()) && wakeupState != WAKEUP_REQUESTED
                && WAKEUP_STATE_UPDATER.getAndSet(this, WAKEUP_REQUESTED) == SELECTING) {
            // Only needed if the event loop is about to block or already blocked. In every other state it will
            // see the request (or the work that came with it) before it blocks the next time.
            wakeup0();
        }
    }

    private void wakeup0() {
        Native.keventTriggerUserEvent(kqueueFd.intValue(), KQUEUE_WAKE_UP_IDENT);
        // Note that the result may return an error (e.g. errno = EBADF after the event loop has been shutdown).
        // So it is not very practical to assert the return value is always >= 0.
    }

    private int kqueueWait(IoHandlerContext context) throws IOException {
        if (!WAKEUP_STATE_UPDATER.compareAndSet(this, AWAKE, SELECTING)) {
            // A wakeup was requested while we were awake. It was only remembered, so consume it with a
            // non-blocking kevent(...) and let the caller look for the work that came with it.
            WAKEUP_STATE_UPDATER.lazySet(this, AWAKE);
            return kqueueWaitNow();
        }
        try {
            // Check again after publishing SELECTING. A task submitted before that is seen here and one submitted
            // later will trigger the user event. Without this the task might be pended until kqueueWait was timed
            // out. It might be pended until idle timeout if IdleStateHandler existed in pipeline.
            if (!context.canBlock() || wakeupState == WAKEUP_REQUESTED) {
                return kqueueWaitNow();
            }

            long totalDelay = context.delayNanos(System.nanoTime());
            int delaySeconds = (int) min(totalDelay / 1000000000L, KQUEUE_MAX_TIMEOUT_SECONDS);
            int delayNanos = (int) (totalDelay % 1000000000L);
            return kqueueWait(delaySeconds, delayNanos);
        } finally {
            WAKEUP_STATE_UPDATER.lazySet(this, AWAKE);
        }
    }

    private int kqueueWaitNow() throws IOException {
        return kqueueWait(0, 0);
    }

    private int kqueueWait(int timeoutSec, int timeoutNs) throws IOException {
        int numEvents = Native.keventWait(kqueueFd.intValue(), changeList, eventList, timeoutSec, timeoutNs);
        changeList.clear();
        return numEvents;
    }

    private int processReady(int ready) {
        int ioCount = 0;
        for (int i = 0; i < ready; ++i) {
            final short filter = eventList.filter(i);
            final short flags = eventList.flags(i);
            final int ident = eventList.ident(i);
            if (filter == Native.EVFILT_USER || (flags & Native.EV_ERROR) != 0) {
                // EV_ERROR is returned if the FD is closed synchronously (which removes from kqueue) and then
                // we later attempt to delete the filters from kqueue.
                assert filter != Native.EVFILT_USER ||
                        (filter == Native.EVFILT_USER && ident == KQUEUE_WAKE_UP_IDENT);
                continue;
            }

            ioCount++;
            long id = eventList.udata(i);
            DefaultKqueueIoRegistration registration = registrations.get(id);
            if (registration == null) {
                // This may happen if the channel has already been closed, and it will be removed from kqueue anyways.
                // We also handle EV_ERROR above to skip this even early if it is a result of a referencing a closed and
                // thus removed from kqueue FD.
                logger.warn("events[{}]=[{}, {}, {}] had no registration!", i, ident, id, filter);
                continue;
            }
            registration.handle(ident, filter, flags, eventList.fflags(i), eventList.data(i), id);
        }
        return ioCount;
    }

    @Override
    public int run(IoHandlerContext context) {
        int handled = 0;
        try {
            int strategy = selectStrategy.calculateStrategy(selectNowSupplier, !context.canBlock());
            switch (strategy) {
                case SelectStrategy.CONTINUE:
                    if (context.shouldReportActiveIoTime()) {
                        context.reportActiveIoTime(0); // Report zero as we did no I/O.
                    }
                    return 0;

                case SelectStrategy.BUSY_WAIT:
                    // fall-through to SELECT since the busy-wait is not supported with kqueue

                case SelectStrategy.SELECT:
                    strategy = kqueueWait(context);
                    // fall-through
                default:
            }

            if (strategy > 0) {
                if (context.shouldReportActiveIoTime()) {
                    long activeIoStartTimeNanos = System.nanoTime();
                    handled = processReady(strategy);
                    long activeIoEndTimeNanos = System.nanoTime();
                    context.reportActiveIoTime(activeIoEndTimeNanos - activeIoStartTimeNanos);
                } else {
                    handled = processReady(strategy);
                }
            } else if (context.shouldReportActiveIoTime()) {
                context.reportActiveIoTime(0);
            }

            if (allowGrowing && strategy == eventList.capacity()) {
                //increase the size of the array as we needed the whole space for the events
                eventList.realloc(false);
            }
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            handleLoopException(t);
        } finally {
            processCancelledRegistrations();
        }
        return handled;
    }

    // Process all previous cannceld registrations and remove them from the registration map.
    private void processCancelledRegistrations() {
        for (;;) {
            DefaultKqueueIoRegistration cancelledRegistration = cancelledRegistrations.poll();
            if (cancelledRegistration == null) {
                return;
            }
            DefaultKqueueIoRegistration removed = registrations.remove(cancelledRegistration.id);
            assert removed == cancelledRegistration;
            removed.handle.unregistered();
        }
    }

    private static void handleLoopException(Throwable t) {
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
        try {
            kqueueWaitNow();
        } catch (IOException e) {
            // ignore on close
        }

        // Using the intermediate collection to prevent ConcurrentModificationException.
        // In the `close()` method, the channel is deleted from `channels` map.
        DefaultKqueueIoRegistration[] copy = registrations.values().toArray(new DefaultKqueueIoRegistration[0]);

        for (DefaultKqueueIoRegistration reg: copy) {
            reg.close();
        }

        processCancelledRegistrations();
    }

    @Override
    public void destroy() {
        try {
            try {
                kqueueFd.close();
            } catch (IOException e) {
                logger.warn("Failed to close the kqueue fd.", e);
            }
        } finally {
            // Cleanup all native memory!
            nativeArrays.free();
            changeList.free();
            eventList.free();
        }
    }

    @Override
    public IoRegistration register(IoHandle handle) {
        final KQueueIoHandle kqueueHandle = cast(handle);
        if (kqueueHandle.ident() == KQUEUE_WAKE_UP_IDENT) {
            throw new IllegalArgumentException("ident " + KQUEUE_WAKE_UP_IDENT + " is reserved for internal usage");
        }

        DefaultKqueueIoRegistration registration = new DefaultKqueueIoRegistration(
                executor, kqueueHandle);
        DefaultKqueueIoRegistration old = registrations.put(registration.id, registration);
        if (old != null) {
            // This should never happen but just in case.
            registrations.put(old.id, old);
            throw new IllegalStateException();
        }
        handle.registered();
        return registration;
    }

    private static KQueueIoHandle cast(IoHandle handle) {
        if (handle instanceof KQueueIoHandle) {
            return (KQueueIoHandle) handle;
        }
        throw new IllegalArgumentException("IoHandle of type " + StringUtil.simpleClassName(handle) + " not supported");
    }

    private static KQueueIoOps cast(IoOps ops) {
        if (ops instanceof KQueueIoOps) {
            return (KQueueIoOps) ops;
        }
        throw new IllegalArgumentException("IoOps of type " + StringUtil.simpleClassName(ops) + " not supported");
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return KQueueIoHandle.class.isAssignableFrom(handleType);
    }

    private final class DefaultKqueueIoRegistration implements IoRegistration {
        private boolean cancellationPending;
        private final AtomicBoolean canceled = new AtomicBoolean();
        private final KQueueIoEvent event = new KQueueIoEvent();

        final KQueueIoHandle handle;
        final long id;
        private final ThreadAwareExecutor executor;

        DefaultKqueueIoRegistration(ThreadAwareExecutor executor, KQueueIoHandle handle) {
            this.executor = executor;
            this.handle = handle;
            id = generateNextId();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T attachment() {
            return (T) nativeArrays;
        }

        @Override
        public long submit(IoOps ops) {
            KQueueIoOps kQueueIoOps = cast(ops);
            if (!isValid()) {
                return -1;
            }
            short filter = kQueueIoOps.filter();
            short flags = kQueueIoOps.flags();
            int fflags = kQueueIoOps.fflags();
            long data = kQueueIoOps.data();
            if (executor.isExecutorThread(Thread.currentThread())) {
                evSet(filter, flags, fflags, data);
            } else {
                executor.execute(() -> evSet(filter, flags, fflags, data));
            }
            return 0;
        }

        void handle(int ident, short filter, short flags, int fflags, long data, long udata) {
            if (cancellationPending) {
                // This registration was already cancelled but not removed from the map yet, just ignore.
                return;
            }
            event.update(ident, filter, flags, fflags, data, udata);
            handle.handle(this, event);
        }

        private void evSet(short filter, short flags, int fflags, long data) {
            if (cancellationPending) {
                // This registration was already cancelled but not removed from the map yet, just ignore.
                return;
            }
            changeList.evSet(handle.ident(), filter, flags, fflags, data, id);
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
            // Let's add the registration to our cancelledRegistrations queue so we will process it
            // after we processed all events. This is needed as otherwise we might end up removing it
            // from the registration map while we still have some unprocessed events.
            cancellationPending = true;
            cancelledRegistrations.offer(this);
        }

        void close() {
            cancel();
            try {
                handle.close();
            } catch (Exception e) {
                logger.debug("Exception during closing " + handle, e);
            }
        }
    }
}
