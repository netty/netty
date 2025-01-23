/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.IoExecutorContext;
import io.netty.channel.IoExecutor;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoOps;
import io.netty.channel.IoRegistration;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * {@link IoHandler} which is implemented in terms of the Linux-specific {@code io_uring} API.
 */
public final class IoUringIoHandler implements IoHandler {

    // Special IoUringIoOps that will cause a submission and running of all completions.
    static final IoUringIoOps SUBMIT_AND_RUN_ALL = new IoUringIoOps(
            (byte) -1, (byte) -1, (short) -1, -1, -1, -1, -1, -1, (short) -1, (short) -1, (short) -1, -1, -1);

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IoUringIoHandler.class);
    private static final short RING_CLOSE = 1;

    private final RingBuffer ringBuffer;
    private final IntObjectMap<DefaultIoUringIoRegistration> registrations;
    // The maximum number of bytes for an InetAddress / Inet6Address
    private final byte[] inet4AddressArray = new byte[SockaddrIn.IPV4_ADDRESS_LENGTH];
    private final byte[] inet6AddressArray = new byte[SockaddrIn.IPV6_ADDRESS_LENGTH];

    private final AtomicBoolean eventfdAsyncNotify = new AtomicBoolean();
    private final FileDescriptor eventfd;
    private final long eventfdReadBuf;
    private final long timeoutMemoryAddress;

    private long eventfdReadSubmitted;
    private boolean eventFdClosing;
    private volatile boolean shuttingDown;
    private boolean closeCompleted;
    private int nextRegistrationId = Integer.MIN_VALUE;
    private int processedPerRun;

    // these two ids are used internally any so can't be used by nextRegistrationId().
    private static final int EVENTFD_ID = Integer.MAX_VALUE;
    private static final int RINGFD_ID = EVENTFD_ID - 1;
    private static final int INVALID_ID = 0;

    private static final int KERNEL_TIMESPEC_SIZE = 16; //__kernel_timespec

    private static final int KERNEL_TIMESPEC_TV_SEC_FIELD = 0;
    private static final int KERNEL_TIMESPEC_TV_NSEC_FIELD = 8;

    private final CompletionBuffer completionBuffer;
    private final IoExecutor executor;

    IoUringIoHandler(IoExecutor executor, IoUringIoHandlerConfiguration config) {
        // Ensure that we load all native bits as otherwise it may fail when try to use native methods in IovArray
        IoUring.ensureAvailability();
        this.executor = requireNonNull(executor, "executor");
        requireNonNull(config, "config");
        this.ringBuffer = Native.createRingBuffer(config.getRingSize(), Native.setupFlags());
        if (IoUring.isRegisterIowqMaxWorkersSupported() && config.needRegisterIowqMaxWorker()) {
            int maxBoundedWorker = Math.max(config.getMaxBoundedWorker(), 0);
            int maxUnboundedWorker = Math.max(config.getMaxUnboundedWorker(), 0);
            int result = Native.ioUringRegisterIoWqMaxWorkers(ringBuffer.fd(), maxBoundedWorker, maxUnboundedWorker);
            if (result < 0) {
                // Close ringBuffer before throwing to ensure we release all memory on failure.
                ringBuffer.close();
                throw new UncheckedIOException(Errors.newIOException("io_uring_register", result));
            }
        }
        registrations = new IntObjectHashMap<>();
        eventfd = Native.newBlockingEventFd();
        eventfdReadBuf = PlatformDependent.allocateMemory(8);
        this.timeoutMemoryAddress = PlatformDependent.allocateMemory(KERNEL_TIMESPEC_SIZE);

        // We buffer a maximum of 2 * CompletionQueue.ringSize completions before we drain them in batches.
        // Also as we never submit an udata which is 0L we use this as the tombstone marker.
        completionBuffer = new CompletionBuffer(ringBuffer.ioUringCompletionQueue().ringSize * 2, 0);
    }

    @Override
    public void initialize() {
        ringBuffer.enable();
    }

    @Override
    public int run(IoExecutorContext context) {
        processedPerRun = 0;
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        if (!completionQueue.hasCompletions() && context.canBlock()) {
            if (eventfdReadSubmitted == 0) {
                submitEventFdRead();
            }
            long timeoutNanos = context.deadlineNanos() == -1 ? -1 : context.delayNanos(System.nanoTime());
            submitAndWaitWithTimeout(submissionQueue, false, timeoutNanos);
        } else {
            submissionQueue.submit();
        }
        // we might call submitAndRunNow() while processing stuff in the completionArray we need to
        // add the processed completions to processedPerRun as this might also be updated by submitAndRunNow()
        processedPerRun += drainAndProcessAll(completionQueue, this::handle);

        // Let's submit one more time as the completions might have added things to the submission queue.
        submissionQueue.submit();

        return processedPerRun;
    }

    private void submitAndRunNow() {
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        if (submissionQueue.submit() > 0) {
            processedPerRun += drainAndProcessAll(completionQueue, this::handle);
        }
    }

    private int drainAndProcessAll(CompletionQueue completionQueue, CompletionCallback callback) {
        int processed = 0;
        for (;;) {
            boolean drainedAll = completionBuffer.drain(completionQueue);
            processed += completionBuffer.processNow(callback);
            if (drainedAll) {
                break;
            }
        }
        return processed;
    }

    private static void handleLoopException(Throwable throwable) {
        logger.warn("Unexpected exception in the IO event loop.", throwable);

        // Prevent possible consecutive immediate failures that lead to
        // excessive CPU consumption.
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignore) {
            // ignore
        }
    }

    private boolean handle(int res, int flags, long udata) {
        try {
            int id = UserData.decodeId(udata);
            byte op = UserData.decodeOp(udata);
            short data = UserData.decodeData(udata);

            if (logger.isTraceEnabled()) {
                logger.trace("completed(ring {}): {}(id={}, res={})",
                        ringBuffer.fd(), Native.opToStr(op), data, res);
            }
            if (id == EVENTFD_ID) {
                handleEventFdRead();
                return true;
            }
            if (id == RINGFD_ID) {
                if (op == Native.IORING_OP_NOP && data == RING_CLOSE) {
                    completeRingClose();
                }
                return true;
            }
            DefaultIoUringIoRegistration registration = registrations.get(id);
            if (registration == null) {
                logger.debug("ignoring {} completion for unknown registration (id={}, res={})",
                        Native.opToStr(op), id, res);
                return true;
            }
            registration.handle(res, flags, op, data);
            return true;
        } catch (Error e) {
            throw e;
        } catch (Throwable throwable) {
            handleLoopException(throwable);
            return true;
        }
    }

    private void handleEventFdRead() {
        eventfdReadSubmitted = 0;
        if (!eventFdClosing) {
            eventfdAsyncNotify.set(false);
            submitEventFdRead();
        }
    }

    private void submitEventFdRead() {
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        long udata = UserData.encode(EVENTFD_ID, Native.IORING_OP_READ, (short) 0);

        eventfdReadSubmitted = submissionQueue.addEventFdRead(
                eventfd.intValue(), eventfdReadBuf, 0, 8, udata);
    }

    private int submitAndWaitWithTimeout(SubmissionQueue submissionQueue,
                                         boolean linkTimeout, long timeoutNanoSeconds) {
        if (timeoutNanoSeconds != -1) {
            long udata = UserData.encode(RINGFD_ID,
                    linkTimeout ? Native.IORING_OP_LINK_TIMEOUT : Native.IORING_OP_TIMEOUT, (short) 0);
            // We use the same timespec pointer for all add*Timeout operations. This only works because we call
            // submit directly after it. This ensures the submitted timeout is considered "stable" and so can be reused.
            long seconds, nanoSeconds;
            if (timeoutNanoSeconds == 0) {
                seconds = 0;
                nanoSeconds = 0;
            } else {
                seconds = (int) min(timeoutNanoSeconds / 1000000000L, Integer.MAX_VALUE);
                nanoSeconds = (int) max(timeoutNanoSeconds - seconds * 1000000000L, 0);
            }

            PlatformDependent.putLong(timeoutMemoryAddress + KERNEL_TIMESPEC_TV_SEC_FIELD, seconds);
            PlatformDependent.putLong(timeoutMemoryAddress + KERNEL_TIMESPEC_TV_NSEC_FIELD, nanoSeconds);

            if (linkTimeout) {
                submissionQueue.addLinkTimeout(timeoutMemoryAddress, udata);
            } else {
                submissionQueue.addTimeout(timeoutMemoryAddress, udata);
            }
        }
        return submissionQueue.submitAndWait();
    }

    @Override
    public void prepareToDestroy() {
        shuttingDown = true;
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();

        List<DefaultIoUringIoRegistration> copy = new ArrayList<>(registrations.values());

        for (DefaultIoUringIoRegistration registration: copy) {
            registration.close();
        }

        // Ensure all previously submitted IOs get to complete before tearing down everything.
        long udata = UserData.encode(RINGFD_ID, Native.IORING_OP_NOP, (short) 0);
        submissionQueue.addNop((byte) Native.IOSQE_IO_DRAIN, udata);
        submissionQueue.submit();
        while (completionQueue.hasCompletions()) {
            completionQueue.process(this::handle);

            if (submissionQueue.count() > 0) {
                submissionQueue.submit();
            }
        }
    }

    @Override
    public void destroy() {
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        drainEventFd();
        if (submissionQueue.remaining() < 2) {
            // We need to submit 2 linked operations. Since they are linked, we cannot allow a submit-call to
            // separate them. We don't have enough room (< 2) in the queue, so we submit now to make more room.
            submissionQueue.submit();
        }
        // Try to drain all the IO from the queue first...
        long udata = UserData.encode(RINGFD_ID, Native.IORING_OP_NOP, (short) 0);
        submissionQueue.addNop((byte) Native.IOSQE_IO_DRAIN, udata);
        // ... but only wait for 200 milliseconds on this
        submitAndWaitWithTimeout(submissionQueue, true, TimeUnit.MILLISECONDS.toNanos(200));
        completionQueue.process(this::handle);
        completeRingClose();
    }

    // We need to prevent the race condition where a wakeup event is submitted to a file descriptor that has
    // already been freed (and potentially reallocated by the OS). Because submitted events is gated on the
    // `eventfdAsyncNotify` flag we can close the gate but may need to read any outstanding events that have
    // (or will) be written.
    private void drainEventFd() {
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        assert !eventFdClosing;
        eventFdClosing = true;
        boolean eventPending = eventfdAsyncNotify.getAndSet(true);
        if (eventPending) {
            // There is an event that has been or will be written by another thread, so we must wait for the event.
            // Make sure we're actually listening for writes to the event fd.
            while (eventfdReadSubmitted == 0) {
                submitEventFdRead();
                submissionQueue.submit();
            }
            // Drain the eventfd of the pending wakup.
            class DrainFdEventCallback implements CompletionCallback {
                boolean eventFdDrained;

                @Override
                public boolean handle(int res, int flags, long udata) {
                    if (UserData.decodeId(udata) == EVENTFD_ID) {
                        eventFdDrained = true;
                    }
                    return IoUringIoHandler.this.handle(res, flags, udata);
                }
            }
            final DrainFdEventCallback handler = new DrainFdEventCallback();
            drainAndProcessAll(completionQueue, handler);
            completionQueue.process(handler);
            while (!handler.eventFdDrained) {
                submissionQueue.submitAndWait();
                drainAndProcessAll(completionQueue, handler);
            }
        }
        // We've consumed any pending eventfd read and `eventfdAsyncNotify` should never
        // transition back to false, thus we should never have any more events written.
        // So, if we have a read event pending, we can cancel it.
        if (eventfdReadSubmitted != 0) {
            long udata = UserData.encode(EVENTFD_ID, Native.IORING_OP_ASYNC_CANCEL, (short) 0);
            submissionQueue.addCancel(eventfdReadSubmitted, udata);
            eventfdReadSubmitted = 0;
            submissionQueue.submit();
        }
    }

    private void completeRingClose() {
        if (closeCompleted) {
            // already done.
            return;
        }
        closeCompleted = true;
        ringBuffer.close();
        try {
            eventfd.close();
        } catch (IOException e) {
            logger.warn("Failed to close eventfd", e);
        }
        PlatformDependent.freeMemory(eventfdReadBuf);
        PlatformDependent.freeMemory(timeoutMemoryAddress);
    }

    @Override
    public IoRegistration register(IoHandle handle) throws Exception {
        IoUringIoHandle ioHandle = cast(handle);
        if (shuttingDown) {
            throw new RejectedExecutionException("IoEventLoop is shutting down");
        }
        DefaultIoUringIoRegistration registration = new DefaultIoUringIoRegistration(executor, ioHandle);
        for (;;) {
            int id = nextRegistrationId();
            DefaultIoUringIoRegistration old = registrations.put(id, registration);
            if (old != null) {
                assert old.handle != registration.handle;
                registrations.put(id, old);
            } else {
                registration.setId(id);
                break;
            }
        }

        return registration;
    }

    private int nextRegistrationId() {
        int id;
        do {
            id = nextRegistrationId++;
        } while (id == RINGFD_ID || id == EVENTFD_ID || id == INVALID_ID);
        return id;
    }

    private final class DefaultIoUringIoRegistration implements IoUringIoRegistration {
        private final Promise<?> cancellationPromise;
        private final IoExecutor executor;
        private final IoUringIoEvent event = new IoUringIoEvent(0, 0, (byte) 0, (short) 0);
        final IoUringIoHandle handle;

        private boolean removeLater;
        private int outstandingCompletions;
        private int id;

        DefaultIoUringIoRegistration(IoExecutor executor, IoUringIoHandle handle) {
            this.executor = executor;
            this.handle = handle;
            this.cancellationPromise = executor.newPromise();
        }

        void setId(int id) {
            this.id = id;
        }

        @Override
        public long submit(IoOps ops) {
            IoUringIoOps ioOps = (IoUringIoOps) ops;
            if (!isValid()) {
                return INVALID_ID;
            }
            long udata = UserData.encode(id, ioOps.opcode(), ioOps.data());
            if (executor.inExecutorThread(Thread.currentThread())) {
                submit0(ioOps, udata);
            } else {
                executor.execute(() -> submit0(ioOps, udata));
            }
            return udata;
        }

        private void submit0(IoUringIoOps ioOps, long udata) {
            if (ioOps == SUBMIT_AND_RUN_ALL) {
                submitAndRunNow();
            } else {
                ringBuffer.ioUringSubmissionQueue().enqueueSqe(ioOps.opcode(), ioOps.flags(), ioOps.ioPrio(),
                        ioOps.fd(), ioOps.union1(), ioOps.union2(), ioOps.len(), ioOps.union3(), udata,
                        ioOps.union4(), ioOps.personality(), ioOps.union5(), ioOps.union6()
                );
                outstandingCompletions++;
            }
        }

        @Override
        public IoUringIoHandler ioHandler() {
            return IoUringIoHandler.this;
        }

        @Override
        public void cancel() {
            if (!cancellationPromise.trySuccess(null)) {
                // Already cancelled.
                return;
            }
            if (executor.inExecutorThread(Thread.currentThread())) {
                tryRemove();
            } else {
                executor.execute(this::tryRemove);
            }
        }

        @Override
        public Future<?> cancelFuture() {
            return cancellationPromise;
        }

        private void tryRemove() {
            if (outstandingCompletions > 0) {
                // We have some completions outstanding, we will remove the id <-> registration mapping
                // once these are done.
                removeLater = true;
                return;
            }
            remove();
        }

        private void remove() {
            DefaultIoUringIoRegistration old = registrations.remove(id);
            assert old == this;
        }

        void close() {
            // Closing the handle will also cancel the registration.
            // It's important that we not manually cancel as close() might need to submit some work to the ring.
            assert executor.inExecutorThread(Thread.currentThread());
            try {
                handle.close();
            } catch (Exception e) {
                logger.debug("Exception during closing " + handle, e);
            }
        }

        void handle(int res, int flags, byte op, short data) {
            event.update(res, flags, op, data);
            handle.handle(this, event);
            if (--outstandingCompletions == 0 && removeLater) {
                // No more outstanding completions, remove the fd <-> registration mapping now.
                removeLater = false;
                remove();
            }
        }
    }

    private static IoUringIoHandle cast(IoHandle handle) {
        if (handle instanceof IoUringIoHandle) {
            return (IoUringIoHandle) handle;
        }
        throw new IllegalArgumentException("IoHandle of type " + StringUtil.simpleClassName(handle) + " not supported");
    }

    @Override
    public void wakeup() {
        if (!executor.inExecutorThread(Thread.currentThread()) &&
                !eventfdAsyncNotify.getAndSet(true)) {
            // write to the eventfd which will then trigger an eventfd read completion.
            Native.eventFdWrite(eventfd.intValue(), 1L);
        }
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return IoUringIoHandle.class.isAssignableFrom(handleType);
    }

    /**
     * {@code byte[]} that can be used as temporary storage to encode the ipv4 address
     */
    byte[] inet4AddressArray() {
        return inet4AddressArray;
    }

    /**
     * {@code byte[]} that can be used as temporary storage to encode the ipv6 address
     */
    byte[] inet6AddressArray() {
        return inet6AddressArray;
    }

    /**
     * Create a new {@link IoHandlerFactory} that can be used to create {@link IoUringIoHandler}s.
     *
     * @return factory
     */
    public static IoHandlerFactory newFactory() {
        return newFactory(new IoUringIoHandlerConfiguration());
    }

    /**
     * Create a new {@link IoHandlerFactory} that can be used to create {@link IoUringIoHandler}s.
     * Each {@link IoUringIoHandler} will use a ring of size {@code ringSize}.
     *
     * @param  ringSize     the size of the ring.
     * @return              factory
     */
    public static IoHandlerFactory newFactory(int ringSize) {
        IoUringIoHandlerConfiguration configuration = new IoUringIoHandlerConfiguration();
        configuration.setRingSize(ringSize);
        return eventLoop -> new IoUringIoHandler(eventLoop, configuration);
    }

    /**
     * Create a new {@link IoHandlerFactory} that can be used to create {@link IoUringIoHandler}s.
     * Each {@link IoUringIoHandler} will use same option
     * @param config the io_uring configuration
     * @return factory
     */
    public static IoHandlerFactory newFactory(IoUringIoHandlerConfiguration config) {
        IoUring.ensureAvailability();
        ObjectUtil.checkNotNull(config, "config");
        return eventLoop -> new IoUringIoHandler(eventLoop, config);
    }
}
