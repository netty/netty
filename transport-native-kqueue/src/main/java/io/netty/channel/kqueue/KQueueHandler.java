/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.Channel;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.IoExecutionContext;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SelectStrategyFactory;

import io.netty.channel.kqueue.AbstractKQueueChannel.AbstractKQueueUnsafe;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.IovArray;
import io.netty.util.IntSupplier;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.lang.Math.min;

/**
 * {@link IoHandler} which uses kqueue under the covers. Only works on BSD!
 */
@UnstableApi
public final class KQueueHandler implements IoHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(KQueueHandler.class);
    private static final AtomicIntegerFieldUpdater<KQueueHandler> WAKEN_UP_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(KQueueHandler.class, "wakenUp");
    private static final int KQUEUE_WAKE_UP_IDENT = 0;

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
    private final IntSupplier selectNowSupplier = new IntSupplier() {
        @Override
        public int get() throws Exception {
            return kqueueWaitNow();
        }
    };
    private final IntObjectMap<AbstractKQueueChannel> channels = new IntObjectHashMap<AbstractKQueueChannel>(4096);

    private volatile int wakenUp;

    private static AbstractKQueueChannel cast(Channel channel) {
        if (channel instanceof AbstractKQueueChannel) {
            return (AbstractKQueueChannel) channel;
        }
        throw new IllegalArgumentException("Channel of type " + StringUtil.simpleClassName(channel) + " not supported");
    }

    private KQueueHandler() {
        this(0, DefaultSelectStrategyFactory.INSTANCE.newSelectStrategy());
    }

    private KQueueHandler(int maxEvents, SelectStrategy strategy) {
        selectStrategy = ObjectUtil.checkNotNull(strategy, "strategy");
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

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link KQueueHandler} instances.
     */
    public static IoHandlerFactory newFactory() {
        return new IoHandlerFactory() {
            @Override
            public IoHandler newHandler() {
                return new KQueueHandler();
            }
        };
    }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link KQueueHandler} instances.
     */
    public static IoHandlerFactory newFactory(final int maxEvents,
                                              final SelectStrategyFactory selectStrategyFactory) {
        ObjectUtil.checkPositiveOrZero(maxEvents, "maxEvents");
        ObjectUtil.checkNotNull(selectStrategyFactory, "selectStrategyFactory");
        return new IoHandlerFactory() {
            @Override
            public IoHandler newHandler() {
                return new KQueueHandler(maxEvents, selectStrategyFactory.newSelectStrategy());
            }
        };
    }

    @Override
    public void register(Channel channel) {
        final AbstractKQueueChannel kQueueChannel = cast(channel);
        final int id = kQueueChannel.fd().intValue();
        channels.put(id, kQueueChannel);

        kQueueChannel.register0(new KQueueRegistration() {
            @Override
            public void evSet(short filter, short flags, int fflags) {
                KQueueHandler.this.evSet(kQueueChannel, filter, flags, fflags);
            }

            @Override
            public IovArray cleanArray() {
                return KQueueHandler.this.cleanArray();
            }
        });
    }

    @Override
    public void deregister(Channel channel) throws Exception {
        AbstractKQueueChannel kQueueChannel = cast(channel);
        channels.remove(kQueueChannel.fd().intValue());
        kQueueChannel.deregister0();
    }

    private void evSet(AbstractKQueueChannel ch, short filter, short flags, int fflags) {
        changeList.evSet(ch, filter, flags, fflags);
    }

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
        int delaySeconds = (int) min(totalDelay / 1000000000L, Integer.MAX_VALUE);
        return kqueueWait(delaySeconds, (int) min(totalDelay - delaySeconds * 1000000000L, Integer.MAX_VALUE));
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
            final int fd = eventList.fd(i);
            if (filter == Native.EVFILT_USER || (flags & Native.EV_ERROR) != 0) {
                // EV_ERROR is returned if the FD is closed synchronously (which removes from kqueue) and then
                // we later attempt to delete the filters from kqueue.
                assert filter != Native.EVFILT_USER ||
                        (filter == Native.EVFILT_USER && fd == KQUEUE_WAKE_UP_IDENT);
                continue;
            }

            AbstractKQueueChannel channel = channels.get(fd);
            if (channel == null) {
                // This may happen if the channel has already been closed, and it will be removed from kqueue anyways.
                // We also handle EV_ERROR above to skip this even early if it is a result of a referencing a closed and
                // thus removed from kqueue FD.
                logger.warn("events[{}]=[{}, {}] had no channel!", i, fd, filter);
                continue;
            }

            AbstractKQueueUnsafe unsafe = (AbstractKQueueUnsafe) channel.unsafe();
            // First check for EPOLLOUT as we may need to fail the connect ChannelPromise before try
            // to read from the file descriptor.
            if (filter == Native.EVFILT_WRITE) {
                unsafe.writeReady();
            } else if (filter == Native.EVFILT_READ) {
                // Check READ before EOF to ensure all data is read before shutting down the input.
                unsafe.readReady(eventList.data(i));
            } else if (filter == Native.EVFILT_SOCK && (eventList.fflags(i) & Native.NOTE_RDHUP) != 0) {
                unsafe.readEOF();
            }

            // Check if EV_EOF was set, this will notify us for connection-reset in which case
            // we may close the channel directly or try to read more data depending on the state of the
            // Channel and also depending on the AbstractKQueueChannel subtype.
            if ((flags & Native.EV_EOF) != 0) {
                unsafe.readEOF();
            }
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
