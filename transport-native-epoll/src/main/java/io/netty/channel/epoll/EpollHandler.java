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

import io.netty.channel.Channel;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.IoExecutionContext;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.SelectStrategy;

import io.netty.channel.SelectStrategyFactory;
import io.netty.channel.SingleThreadEventLoop;

import io.netty.channel.epoll.AbstractEpollChannel.AbstractEpollUnsafe;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.IovArray;
import io.netty.util.IntSupplier;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * {@link IoHandler} which uses epoll under the covers. Only works on Linux!
 */
public class EpollHandler implements IoHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EpollHandler.class);
    private static final AtomicIntegerFieldUpdater<EpollHandler> WAKEN_UP_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(EpollHandler.class, "wakenUp");

    static {
        // Ensure JNI is initialized by the time this class is loaded by this time!
        // We use unix-common methods in this class which are backed by JNI methods.
        Epoll.ensureAvailability();
    }

    // Pick a number that no task could have previously used.
    private long prevDeadlineNanos = SingleThreadEventLoop.nanoTime() - 1;
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
    private final IntSupplier selectNowSupplier = this::epollWaitNow;
    @SuppressWarnings("unused") // AtomicIntegerFieldUpdater
    private volatile int wakenUp;

    // See http://man7.org/linux/man-pages/man2/timerfd_create.2.html.
    private static final long MAX_SCHEDULED_TIMERFD_NS = 999999999;

    private static AbstractEpollChannel cast(Channel channel) {
        if (channel instanceof AbstractEpollChannel) {
            return (AbstractEpollChannel) channel;
        }
        throw new IllegalArgumentException("Channel of type " + StringUtil.simpleClassName(channel) + " not supported");
    }

    private EpollHandler() {
        this(0, DefaultSelectStrategyFactory.INSTANCE.newSelectStrategy());
    }

    // Package-private for tests.
    EpollHandler(int maxEvents, SelectStrategy strategy) {
        selectStrategy = strategy;
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
                Native.epollCtlAdd(epollFd.intValue(), eventFd.intValue(), Native.EPOLLIN);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to add eventFd filedescriptor to epoll", e);
            }
            this.timerFd = timerFd = Native.newTimerFd();
            try {
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

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link EpollHandler} instances.
     */
    public static IoHandlerFactory newFactory() {
        return EpollHandler::new;
    }

    /**
     * Returns a new {@link IoHandlerFactory} that creates {@link EpollHandler} instances.
     */
    public static IoHandlerFactory newFactory(final int maxEvents,
                                              final SelectStrategyFactory selectStrategyFactory) {
        checkPositiveOrZero(maxEvents, "maxEvents");
        requireNonNull(selectStrategyFactory, "selectStrategyFactory");
        return () -> new EpollHandler(maxEvents, selectStrategyFactory.newSelectStrategy());
    }

    private IovArray cleanIovArray() {
        if (iovArray == null) {
            iovArray = new IovArray();
        } else {
            iovArray.clear();
        }
        return iovArray;
    }

    private NativeDatagramPacketArray cleanDatagramPacketArray() {
        if (datagramPacketArray == null) {
            datagramPacketArray = new NativeDatagramPacketArray();
        } else {
            datagramPacketArray.clear();
        }
        return datagramPacketArray;
    }

    @Override
    public final void register(Channel channel) throws Exception {
        final AbstractEpollChannel epollChannel = cast(channel);
        epollChannel.register0(new EpollRegistration() {
            @Override
            public void update() throws IOException {
                EpollHandler.this.modify(epollChannel);
            }

            @Override
            public void remove() throws IOException {
                EpollHandler.this.remove(epollChannel);
            }

            @Override
            public IovArray cleanIovArray() {
                return EpollHandler.this.cleanIovArray();
            }

            @Override
            public NativeDatagramPacketArray cleanDatagramPacketArray() {
                return EpollHandler.this.cleanDatagramPacketArray();
            }
        });
        add(epollChannel);
    }

    @Override
    public final void deregister(Channel channel) throws Exception {
        cast(channel).deregister0();
    }

    @Override
    public final void wakeup(boolean inEventLoop) {
        if (!inEventLoop && WAKEN_UP_UPDATER.compareAndSet(this, 0, 1)) {
            // write to the evfd which will then wake-up epoll_wait(...)
            Native.eventFdWrite(eventFd.intValue(), 1L);
        }
    }

    /**
     * Register the given channel with this {@link EpollHandler}.
     */
    private void add(AbstractEpollChannel ch) throws IOException {
        int fd = ch.socket.intValue();
        Native.epollCtlAdd(epollFd.intValue(), fd, ch.flags);
        channels.put(fd, ch);
    }

    /**
     * The flags of the given epoll was modified so update the registration
     */
    private void modify(AbstractEpollChannel ch) throws IOException {
        Native.epollCtlMod(epollFd.intValue(), ch.socket.intValue(), ch.flags);
    }

    /**
     * Deregister the given channel from this {@link EpollHandler}.
     */
    private void remove(AbstractEpollChannel ch) throws IOException {
        if (ch.isOpen()) {
            int fd = ch.socket.intValue();
            if (channels.remove(fd) != null) {
                // Remove the epoll. This is only needed if it's still open as otherwise it will be automatically
                // removed once the file-descriptor is closed.
                Native.epollCtlDel(epollFd.intValue(), ch.fd().intValue());
            }
        }
    }

    private int epollWait(IoExecutionContext context, boolean oldWakeup) throws IOException {
        // If a task was submitted when wakenUp value was 1, the task didn't get a chance to produce wakeup event.
        // So we need to check task queue again before calling epoll_wait. If we don't, the task might be pended
        // until epoll_wait was timed out. It might be pended until idle timeout if IdleStateHandler existed
        // in pipeline.
        if (oldWakeup && !context.canBlock()) {
            return epollWaitNow();
        }

        int delaySeconds;
        int delayNanos;
        long curDeadlineNanos = context.deadlineNanos();
        if (curDeadlineNanos == prevDeadlineNanos) {
            delaySeconds = -1;
            delayNanos = -1;
        } else {
            long totalDelay = context.delayNanos(System.nanoTime());
            prevDeadlineNanos = curDeadlineNanos;
            delaySeconds = (int) min(totalDelay / 1000000000L, Integer.MAX_VALUE);
            delayNanos = (int) min(totalDelay - delaySeconds * 1000000000L, MAX_SCHEDULED_TIMERFD_NS);
        }
        return Native.epollWait(epollFd, events, timerFd, delaySeconds, delayNanos);
    }

    private int epollWaitNow() throws IOException {
        return Native.epollWait(epollFd, events, timerFd, 0, 0);
    }

    private int epollBusyWait() throws IOException {
        return Native.epollBusyWait(epollFd, events);
    }

    @Override
    public final int run(IoExecutionContext context) {
        int handled = 0;
        try {
            int strategy = selectStrategy.calculateStrategy(selectNowSupplier, !context.canBlock());
            switch (strategy) {
                case SelectStrategy.CONTINUE:
                    return 0 ;

                case SelectStrategy.BUSY_WAIT:
                    strategy = epollBusyWait();
                    break;

                case SelectStrategy.SELECT:
                    strategy = epollWait(context, WAKEN_UP_UPDATER.getAndSet(this, 0) == 1);

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
                        Native.eventFdWrite(eventFd.intValue(), 1L);
                    }
                    // fallthrough
                default:
            }
            if (strategy > 0) {
                handled = strategy;
                processReady(events, strategy);
            }
            if (allowGrowing && strategy == events.length()) {
                //increase the size of the array as we needed the whole space for the events
                events.increase();
            }
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

    @Override
    public void prepareToDestroy() {
        try {
            epollWaitNow();
        } catch (IOException ignore) {
            // ignore on close
        }
        // Using the intermediate collection to prevent ConcurrentModificationException.
        // In the `close()` method, the channel is deleted from `channels` map.
        AbstractEpollChannel[] localChannels = channels.values().toArray(new AbstractEpollChannel[0]);

        for (AbstractEpollChannel ch : localChannels) {
            ch.unsafe().close(ch.unsafe().voidPromise());
        }
    }

    private void processReady(EpollEventArray events, int ready) {
        for (int i = 0; i < ready; i ++) {
            final int fd = events.fd(i);
            if (fd == eventFd.intValue()) {
                // consume wakeup event.
                Native.eventFdRead(fd);
            } else if (fd == timerFd.intValue()) {
                // consume wakeup event, necessary because the timer is added with ET mode.
                Native.timerFdRead(fd);
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
    public final void destroy() {
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
