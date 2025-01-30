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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.lang.Math.min;

/**
 * {@link IoHandler} which uses kqueue under the covers. Only works on BSD!
 */
public final class KQueueIoHandler implements IoHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(KQueueIoHandler.class);
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
    private final NativeArrays nativeArrays;
    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
            return kqueueWaitNow();
        }
    };
    private final ThreadAwareExecutor executor;
    private final IntObjectMap<DefaultKqueueIoRegistration> registrations = new IntObjectHashMap<>(4096);
    private int numChannels;

    private volatile int wakenUp;

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
        ObjectUtil.checkPositiveOrZero(maxEvents, "maxEvents");
        ObjectUtil.checkNotNull(selectStrategyFactory, "selectStrategyFactory");
        return executor -> new KQueueIoHandler(executor, maxEvents, selectStrategyFactory.newSelectStrategy());
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
        if (!executor.isExecutorThread(Thread.currentThread())
                && WAKEN_UP_UPDATER.compareAndSet(this, 0, 1)) {
            wakeup0();
        }
    }

    private void wakeup0() {
        Native.keventTriggerUserEvent(kqueueFd.intValue(), KQUEUE_WAKE_UP_IDENT);
        // Note that the result may return an error (e.g. errno = EBADF after the event loop has been shutdown).
        // So it is not very practical to assert the return value is always >= 0.
    }

    private int kqueueWait(IoHandlerContext context, boolean oldWakeup) throws IOException {
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
    public int run(IoHandlerContext context) {
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
                        wakeup0();
                    }
                    // fall-through
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

    int numRegisteredChannels() {
        return numChannels;
    }

    List<Channel> registeredChannelsList() {
        IntObjectMap<DefaultKqueueIoRegistration> ch = registrations;
        if (ch.isEmpty()) {
            return Collections.emptyList();
        }

        List<Channel> channels = new ArrayList<>(ch.size());

        for (DefaultKqueueIoRegistration registration : ch.values()) {
            if (registration.handle instanceof AbstractKQueueChannel.AbstractKQueueUnsafe) {
                channels.add(((AbstractKQueueChannel.AbstractKQueueUnsafe) registration.handle).channel());
            }
        }
        return Collections.unmodifiableList(channels);
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
        DefaultKqueueIoRegistration old = registrations.put(kqueueHandle.ident(), registration);
        if (old != null) {
            // restore old mapping and throw exception
            registrations.put(kqueueHandle.ident(), old);
            throw new IllegalStateException("registration for the KQueueIoHandle.ident() already exists");
        }

        if (kqueueHandle instanceof AbstractKQueueChannel.AbstractKQueueUnsafe) {
            numChannels++;
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

    private final class DefaultKqueueIoRegistration implements IoRegistration {
        private final AtomicBoolean canceled = new AtomicBoolean();
        private final KQueueIoEvent event = new KQueueIoEvent();

        final KQueueIoHandle handle;

        private final ThreadAwareExecutor executor;

        DefaultKqueueIoRegistration(ThreadAwareExecutor executor, KQueueIoHandle handle) {
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
            KQueueIoOps kQueueIoOps = cast(ops);
            if (!isValid()) {
                return -1;
            }
            short filter = kQueueIoOps.filter();
            short flags = kQueueIoOps.flags();
            int fflags = kQueueIoOps.fflags();
            if (executor.isExecutorThread(Thread.currentThread())) {
                evSet(filter, flags, fflags);
            } else {
                executor.execute(() -> evSet(filter, flags, fflags));
            }
            return 0;
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
            int ident = handle.ident();
            DefaultKqueueIoRegistration old = registrations.remove(ident);
            if (old != null) {
                if (old != this) {
                    // The Channel mapping was already replaced due FD reuse, put back the stored Channel.
                    registrations.put(ident, old);
                } else if (old.handle instanceof AbstractKQueueChannel.AbstractKQueueUnsafe) {
                    numChannels--;
                }
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
}
