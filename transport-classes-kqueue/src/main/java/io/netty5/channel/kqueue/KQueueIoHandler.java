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
package io.netty5.channel.kqueue;

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
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.UnstableApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.IntSupplier;

import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * {@link IoHandler} which uses kqueue under the covers. Only works on BSD!
 */
@UnstableApi
public final class KQueueIoHandler implements IoHandler {
    private static final Logger logger = LoggerFactory.getLogger(KQueueIoHandler.class);
    private static final AtomicIntegerFieldUpdater<KQueueIoHandler> WAKEN_UP_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(KQueueIoHandler.class, "wakenUp");
    private static final int KQUEUE_WAKE_UP_IDENT = 0;
    // `kqueue()` may return EINVAL when a large number such as Integer.MAX_VALUE is specified as timeout.
    // 24 hours would be a large enough value.
    // https://man.freebsd.org/cgi/man.cgi?query=kevent&apropos=0&sektion=0&manpath=FreeBSD+6.1-RELEASE&format=html#end
    private static final int KQUEUE_MAX_TIMEOUT_SECONDS = 86399; // 24 hours - 1 second

    static {
        // Ensure JNI is initialized by the time this class is loaded by this time!
        // We use unix-common methods in this class which are backed by JNI methods.
        KQueue.ensureAvailability();
    }

    private final boolean allowGrowing;
    private final FileDescriptor kqueueFd;
    private final KQueueEventArray changeList;
    private final KQueueEventArray eventList;
    private final SelectStrategy selectStrategy;
    private final IovArray iovArray = new IovArray();
    private final IntSupplier selectNowSupplier = () -> {
        try {
            return kqueueWaitNow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    };

    private final IntObjectMap<DefaultKqueueIoRegistration> registrations = new IntObjectHashMap<>(4096);

    private volatile int wakenUp;

    private KQueueIoHandler() {
        this(0, DefaultSelectStrategyFactory.INSTANCE.newSelectStrategy());
    }

    private KQueueIoHandler(int maxEvents, SelectStrategy strategy) {
        selectStrategy = requireNonNull(strategy, "strategy");
        this.kqueueFd = Native.newKQueue();
        if (maxEvents == 0) {
            allowGrowing = true;
            maxEvents = 4096;
        } else {
            allowGrowing = false;
        }
        changeList = new KQueueEventArray(maxEvents);
        eventList = new KQueueEventArray(maxEvents);
        int result = Native.keventAddUserEvent(kqueueFd.intValue(), KQUEUE_WAKE_UP_IDENT);
        if (result < 0) {
            destroy();
            throw new IllegalStateException("kevent failed to add user event with errno: " + (-result));
        }
    }

    @Override
    public void wakeup(EventLoop eventLoop) {
        if (!eventLoop.inEventLoop() && WAKEN_UP_UPDATER.compareAndSet(this, 0, 1)) {
            wakeup();
        }
    }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link KQueueIoHandler} instances.
     */
    public static IoHandlerFactory newFactory() {
        return KQueueIoHandler::new;
    }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link KQueueIoHandler} instances.
     */
    public static IoHandlerFactory newFactory(final int maxEvents,
                                              final SelectStrategyFactory selectStrategyFactory) {
        checkPositiveOrZero(maxEvents, "maxEvents");
        requireNonNull(selectStrategyFactory, "selectStrategyFactory");
        return () -> new KQueueIoHandler(maxEvents, selectStrategyFactory.newSelectStrategy());
    }

    @Override
    public KQueueIoRegistration register(EventLoop eventLoop, IoHandle handle) {
        final KQueueIoHandle kqueueHandle = cast(handle);
        if (kqueueHandle.ident() == KQUEUE_WAKE_UP_IDENT) {
            throw new IllegalArgumentException("ident " + KQUEUE_WAKE_UP_IDENT + " is reserved for internal usage");
        }

        DefaultKqueueIoRegistration registration = new DefaultKqueueIoRegistration(
                eventLoop, kqueueHandle);
        DefaultKqueueIoRegistration old = registrations.put(kqueueHandle.ident(), registration);
        if (old != null && !old.isValid()) {
            // restore old mapping and throw exception
            registrations.put(kqueueHandle.ident(), old);
            throw new IllegalStateException("registration for the KQueueIoHandle.ident() already exists");
        }
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

    private final class DefaultKqueueIoRegistration extends AtomicBoolean implements KQueueIoRegistration {
        private final KQueueIoEvent event = new KQueueIoEvent();

        final KQueueIoHandle handle;

        private final EventLoop eventLoop;

        DefaultKqueueIoRegistration(EventLoop eventLoop, KQueueIoHandle handle) {
            this.eventLoop = eventLoop;
            this.handle = handle;
        }

        @Override
        public long submit(IoOps ops) {
            KQueueIoOps kQueueIoOps = cast(ops);
            if (kQueueIoOps.ident() != handle.ident()) {
                throw new IllegalArgumentException("ident does not match KQueueIoHandle.ident()");
            }
            if (!isValid()) {
                return -1;
            }
            if (eventLoop.inEventLoop()) {
                evSet(kQueueIoOps.filter(), kQueueIoOps.flags(), kQueueIoOps.fflags());
            } else {
                eventLoop.execute(() -> evSet(kQueueIoOps.filter(), kQueueIoOps.flags(), kQueueIoOps.fflags()));
            }
            return 0;
        }

        @Override
        public KQueueIoHandler ioHandler() {
            return KQueueIoHandler.this;
        }

        void handle(int ident, short filter, short flags, int fflags, long data) {
            event.update(ident, filter, flags, fflags, data);
            handle.handle(this, event);
        }

        private void evSet(short filter, short flags, int fflags) {
            changeList.evSet(handle.ident(), filter, flags, fflags);
        }

        @Override
        public boolean isValid() {
            return !get();
        }

        @Override
        public void cancel() {
            if (getAndSet(true)) {
                return;
            }
            if (eventLoop.inEventLoop()) {
                cancel0();
            } else {
                eventLoop.execute(this::cancel0);
            }
        }

        private void cancel0() {
            int ident = handle.ident();
            DefaultKqueueIoRegistration old = registrations.remove(ident);
            if (old != this) {
                // The Channel mapping was already replaced due FD reuse, put back the stored Channel.
                registrations.put(ident, old);
            }
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

    IovArray cleanArray() {
        iovArray.clear();
        return iovArray;
    }

    private void wakeup() {
        Native.keventTriggerUserEvent(kqueueFd.intValue(), KQUEUE_WAKE_UP_IDENT);
        // Note that the result may return an error (e.g. errno = EBADF after the event loop has been shutdown).
        // So it is not very practical to assert the return value is always >= 0.
    }

    private int kqueueWait(IoExecutionContext context, boolean oldWakeup) throws IOException {
        // If a task was submitted when wakenUp value was 1, the task didn't get a chance to produce wakeup event.
        // So we need to check task queue again before calling kqueueWait. If we don't, the task might be pended
        // until kqueueWait was timed out. It might be pended until idle timeout if IdleStateHandler existed
        // in pipeline.
        if (oldWakeup && !context.canBlock()) {
            return kqueueWaitNow();
        }

        long totalDelay = context.delayNanos(System.nanoTime());
        int delaySeconds = (int) min(totalDelay / 1000000000L, KQUEUE_MAX_TIMEOUT_SECONDS);
        int delayNanos = (int) (totalDelay % 1000000000L);
        return kqueueWait(delaySeconds, delayNanos);
    }

    private int kqueueWaitNow() throws IOException {
        return kqueueWait(0, 0);
    }

    private int kqueueWait(int timeoutSec, int timeoutNs) throws IOException {
        int numEvents = Native.keventWait(kqueueFd.intValue(), changeList, eventList, timeoutSec, timeoutNs);
        changeList.clear();
        return numEvents;
    }

    private void processReady(int ready) {
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

            DefaultKqueueIoRegistration registration = registrations.get(ident);
            if (registration == null) {
                // This may happen if the channel has already been closed, and it will be removed from kqueue anyways.
                // We also handle EV_ERROR above to skip this even early if it is a result of a referencing a closed and
                // thus removed from kqueue FD.
                logger.warn("events[{}]=[{}, {}] had no registration!", i, ident, filter);
                continue;
            }
            registration.handle(ident, filter, flags, eventList.fflags(i), eventList.data(i));
        }
    }

    @Override
    public int run(IoExecutionContext context) {
        int handled = 0;
        try {
            int strategy = selectStrategy.calculateStrategy(selectNowSupplier, !context.canBlock());
            switch (strategy) {
                case SelectStrategy.CONTINUE:
                    return 0;

                case SelectStrategy.BUSY_WAIT:
                    // fall-through to SELECT since the busy-wait is not supported with kqueue

                case SelectStrategy.SELECT:
                    strategy = kqueueWait(context, WAKEN_UP_UPDATER.getAndSet(this, 0) == 1);

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

                    if (wakenUp == 1) {
                        wakeup();
                    }
                    // fallthrough
                default:
            }

            if (strategy > 0) {
                handled = strategy;
                processReady(strategy);
            }
            if (allowGrowing && strategy == eventList.capacity()) {
                //increase the size of the array as we needed the whole space for the events
                eventList.realloc(false);
            }
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            handleLoopException(t);
        }
        return handled;
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
            changeList.free();
            eventList.free();
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
}
