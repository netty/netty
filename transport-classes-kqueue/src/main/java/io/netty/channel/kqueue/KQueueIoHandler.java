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
import io.netty.channel.EventLoop;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoExecutionContext;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoOpt;
import io.netty.channel.IoRegistration;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.IovArray;
import io.netty.util.IntSupplier;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
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
 * {@link EventLoop} which uses kqueue under the covers. Only works on BSD!
 */
final class KQueueIoHandler implements IoHandler {
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
    private final KQueueEventIoOpt[] eventIoOpts;
    private final SelectStrategy selectStrategy;
    private final IovArray iovArray = new IovArray();
    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
            return kqueueWaitNow();
        }
    };
    private final IntObjectMap<DefaultKqueueRegistration> registrations = new IntObjectHashMap<>(4096);
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
        return new IoHandlerFactory() {
            @Override
            public IoHandler newHandler() {
                return new KQueueIoHandler(maxEvents, selectStrategyFactory.newSelectStrategy());
            }
        };
    }

    private KQueueIoHandler(int maxEvents, SelectStrategy strategy) {
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
        // Pre-create the KQueueEventIoOpt array to reuse these and so reduce object creation.
        this.eventIoOpts = new KQueueEventIoOpt[maxEvents];
        for (int i = 0; i < eventIoOpts.length; i++) {
            eventIoOpts[i] = new KQueueEventIoOpt();
        }
        int result = Native.keventAddUserEvent(kqueueFd.intValue(), KQUEUE_WAKE_UP_IDENT);
        if (result < 0) {
            destroy();
            throw new IllegalStateException("kevent failed to add user event with errno: " + (-result));
        }
    }

    /**
     * Return a cleared {@link IovArray} that can be used for writes in this {@link EventLoop}.
     */
    private IovArray cleanArray() {
        iovArray.clear();
        return iovArray;
    }

    @Override
    public void wakeup(boolean inEventLoop) {
        if (!inEventLoop && WAKEN_UP_UPDATER.compareAndSet(this, 0, 1)) {
            wakeup();
        }
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

            DefaultKqueueRegistration registration = registrations.get(ident);
            if (registration == null) {
                // This may happen if the channel has already been closed, and it will be removed from kqueue anyways.
                // We also handle EV_ERROR above to skip this even early if it is a result of a referencing a closed and
                // thus removed from kqueue FD.
                logger.warn("events[{}]=[{}, {}] had no registration!", i, ident, filter);
                continue;
            }
            KQueueEventIoOpt eventIoOpt = eventIoOpts[i];
            eventIoOpt.update(ident, filter, flags, eventList.fflags(i), eventList.data(i));
            registration.handle.handle(registration, eventIoOpt);
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

    int numRegisteredChannels() {
        return numChannels;
    }

    List<Channel> registeredChannelsList() {
        IntObjectMap<DefaultKqueueRegistration> ch = registrations;
        if (ch.isEmpty()) {
            return Collections.emptyList();
        }

        List<Channel> channels = new ArrayList<>(ch.size());

        for (DefaultKqueueRegistration registration : ch.values()) {
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
        DefaultKqueueRegistration[] copy = registrations.values().toArray(new DefaultKqueueRegistration[0]);

        for (DefaultKqueueRegistration reg: copy) {
            reg.cancel();
            try {
                reg.handle.close();
            } catch (Exception e) {
                logger.debug("Exception during closing " + reg.handle, e);
            }
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
            changeList.free();
            eventList.free();
        }
    }

    @Override
    public IoRegistration register(IoEventLoop eventLoop, IoHandle handle,
                                                                   IoOpt initialOpts) {
        final KQueueIoHandle kqueueHandle = cast(handle);
        if (kqueueHandle.ident() == KQUEUE_WAKE_UP_IDENT) {
            throw new IllegalArgumentException("ident " + KQUEUE_WAKE_UP_IDENT + " is reserved for internal usage");
        }
        KQueueEventIoOpt eventIoOpt = cast(initialOpts);
        DefaultKqueueRegistration registration = new DefaultKqueueRegistration(
                eventLoop, kqueueHandle);
        DefaultKqueueRegistration old = registrations.put(kqueueHandle.ident(), registration);
        if (old != null) {
            // restore old mapping and throw exception
            registrations.put(kqueueHandle.ident(), old);
            throw new IllegalStateException("registration for the KQueueIoHandle.ident() already exists");
        }

        registration.addOpt(eventIoOpt);
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

    private static KQueueEventIoOpt cast(IoOpt opt) {
        if (opt instanceof KQueueEventIoOpt) {
            return (KQueueEventIoOpt) opt;
        }
        throw new IllegalArgumentException("IoOpt of type " + StringUtil.simpleClassName(opt) + " not supported");
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return KQueueIoHandle.class.isAssignableFrom(handleType);
    }

    private final class DefaultKqueueRegistration extends AtomicBoolean implements KQueueInternalRegistration {
        final KQueueIoHandle handle;

        private final IoEventLoop eventLoop;

        DefaultKqueueRegistration(IoEventLoop eventLoop, KQueueIoHandle handle) {
            this.eventLoop = eventLoop;
            this.handle = handle;
        }

        @Override
        public void addOpt(KQueueEventIoOpt opt) {
            if (opt.ident() != handle.ident()) {
                throw new IllegalArgumentException("ident does not match KQueueIoHandle.ident()");
            }
            if (!isValid()) {
                return;
            }
            if (eventLoop.inEventLoop()) {
                evSet(opt.filter(), opt.flags(), opt.fflags());
            } else {
                eventLoop.execute(() -> evSet(opt.filter(), opt.flags(), opt.fflags()));
            }
        }

        @Override
        public IovArray cleanArray() {
            return KQueueIoHandler.this.cleanArray();
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
            DefaultKqueueRegistration old = registrations.remove(ident);
            if (old != null) {
                if (old != this) {
                    // The Channel mapping was already replaced due FD reuse, put back the stored Channel.
                    registrations.put(ident, old);
                } else if (old.handle instanceof AbstractKQueueChannel.AbstractKQueueUnsafe) {
                    numChannels--;
                }
            }
        }
    }
}
