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

import io.netty.buffer.Unpooled;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoOps;
import io.netty.channel.IoRegistration;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.IovArray;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.ThreadAwareExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * {@link IoHandler} which is implemented in terms of the Linux-specific {@code io_uring} API.
 */
public final class IoUringIoHandler implements IoHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IoUringIoHandler.class);

    private final RingBuffer ringBuffer;
    private final IntObjectMap<IoUringBufferRing> registeredIoUringBufferRing;
    private final IntObjectMap<DefaultIoUringIoRegistration> registrations;
    // The maximum number of bytes for an InetAddress / Inet6Address
    private final byte[] inet4AddressArray = new byte[SockaddrIn.IPV4_ADDRESS_LENGTH];
    private final byte[] inet6AddressArray = new byte[SockaddrIn.IPV6_ADDRESS_LENGTH];

    private final AtomicBoolean eventfdAsyncNotify = new AtomicBoolean();
    private final FileDescriptor eventfd;
    private final ByteBuffer eventfdReadBuf;
    private final long eventfdReadBufAddress;
    private final ByteBuffer timeoutMemory;
    private final long timeoutMemoryAddress;
    private final IovArray iovArray;
    private long eventfdReadSubmitted;
    private boolean eventFdClosing;
    private volatile boolean shuttingDown;
    private boolean closeCompleted;
    private int nextRegistrationId = Integer.MIN_VALUE;

    // these two ids are used internally any so can't be used by nextRegistrationId().
    private static final int EVENTFD_ID = Integer.MAX_VALUE;
    private static final int RINGFD_ID = EVENTFD_ID - 1;
    private static final int INVALID_ID = 0;

    private static final int KERNEL_TIMESPEC_SIZE = 16; //__kernel_timespec

    private static final int KERNEL_TIMESPEC_TV_SEC_FIELD = 0;
    private static final int KERNEL_TIMESPEC_TV_NSEC_FIELD = 8;

    private final CompletionBuffer completionBuffer;
    private final ThreadAwareExecutor executor;

    IoUringIoHandler(ThreadAwareExecutor executor, IoUringIoHandlerConfig config) {
        // Ensure that we load all native bits as otherwise it may fail when try to use native methods in IovArray
        IoUring.ensureAvailability();
        this.executor = requireNonNull(executor, "executor");
        requireNonNull(config, "config");
        int setupFlags = Native.setupFlags();

        //The default cq size is always twice the ringSize.
        // It only makes sense when the user actually specifies the cq ring size.
        int cqSize = 2 * config.getRingSize();
        if (config.needSetupCqeSize()) {
            if (!IoUring.isSetupCqeSizeSupported()) {
                throw new UnsupportedOperationException("IORING_SETUP_CQSIZE is not supported");
            }
            setupFlags |= Native.IORING_SETUP_CQSIZE;
            cqSize = config.checkCqSize(config.getCqSize());
        }
        this.ringBuffer = Native.createRingBuffer(config.getRingSize(), cqSize, setupFlags);
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

        registeredIoUringBufferRing = new IntObjectHashMap<>();
        Collection<IoUringBufferRingConfig> bufferRingConfigs = config.getInternBufferRingConfigs();
        if (bufferRingConfigs != null && !bufferRingConfigs.isEmpty()) {
            if (!IoUring.isRegisterBufferRingSupported()) {
                // Close ringBuffer before throwing to ensure we release all memory on failure.
                ringBuffer.close();
                throw new UnsupportedOperationException("IORING_REGISTER_PBUF_RING is not supported");
            }
            for (IoUringBufferRingConfig bufferRingConfig : bufferRingConfigs) {
                try {
                    IoUringBufferRing ring = newBufferRing(ringBuffer.fd(), bufferRingConfig);
                    registeredIoUringBufferRing.put(bufferRingConfig.bufferGroupId(), ring);
                } catch (Errors.NativeIoException e) {
                    for (IoUringBufferRing bufferRing : registeredIoUringBufferRing.values()) {
                        bufferRing.close();
                    }
                    // Close ringBuffer before throwing to ensure we release all memory on failure.
                    ringBuffer.close();
                    throw new UncheckedIOException(e);
                }
            }
        }

        registrations = new IntObjectHashMap<>();
        eventfd = Native.newBlockingEventFd();
        eventfdReadBuf = Buffer.allocateDirectWithNativeOrder(Long.BYTES);
        eventfdReadBufAddress = Buffer.memoryAddress(eventfdReadBuf);
        this.timeoutMemory = Buffer.allocateDirectWithNativeOrder(KERNEL_TIMESPEC_SIZE);
        this.timeoutMemoryAddress = Buffer.memoryAddress(timeoutMemory);
        // We buffer a maximum of 2 * CompletionQueue.ringCapacity completions before we drain them in batches.
        // Also as we never submit an udata which is 0L we use this as the tombstone marker.
        completionBuffer = new CompletionBuffer(ringBuffer.ioUringCompletionQueue().ringCapacity * 2, 0);

        iovArray = new IovArray(Unpooled.wrappedBuffer(
                Buffer.allocateDirectWithNativeOrder(IoUring.NUM_ELEMENTS_IOVEC * IovArray.IOV_SIZE))
                .setIndex(0, 0));
    }

    @Override
    public void initialize() {
        ringBuffer.enable();
        // Fill all buffer rings now.
        for (IoUringBufferRing bufferRing : registeredIoUringBufferRing.values()) {
            bufferRing.initialize();
        }
    }

    @Override
    public int run(IoHandlerContext context) {
        if (closeCompleted) {
            return 0;
        }
        int processedPerRun = 0;
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        if (!completionQueue.hasCompletions() && context.canBlock()) {
            if (eventfdReadSubmitted == 0) {
                submitEventFdRead();
            }
            long timeoutNanos = context.deadlineNanos() == -1 ? -1 : context.delayNanos(System.nanoTime());
            submitAndWaitWithTimeout(submissionQueue, false, timeoutNanos);
        } else {
            submitAndClear(submissionQueue);
        }
        for (;;) {
            // we might call submitAndRunNow() while processing stuff in the completionArray we need to
            // add the processed completions to processedPerRun.
            int processed = drainAndProcessAll(completionQueue, this::handle);
            processedPerRun += processed;

            // Let's submit again.
            // If we were not able to submit anything and there was nothing left in the completionBuffer we will
            // break out of the loop and return to the caller.
            if (submitAndClear(submissionQueue) == 0 && processed == 0) {
                break;
            }
        }

        return processedPerRun;
    }

    void submitAndRunNow(long udata) {
        if (closeCompleted) {
            return;
        }
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        if (submitAndClear(submissionQueue) > 0) {
            completionBuffer.drain(completionQueue);
            completionBuffer.processOneNow(this::handle, udata);
        }
    }

    private int submitAndClear(SubmissionQueue submissionQueue) {
        int submitted = submissionQueue.submit();

        // Clear the iovArray as we can re-use it now as things are considered stable after submission:
        // See https://man7.org/linux/man-pages/man3/io_uring_prep_sendmsg.3.html
        iovArray.clear();
        return submitted;
    }

    private static IoUringBufferRing newBufferRing(int ringFd, IoUringBufferRingConfig bufferRingConfig)
            throws Errors.NativeIoException {
        short bufferRingSize = bufferRingConfig.bufferRingSize();
        short bufferGroupId = bufferRingConfig.bufferGroupId();
        int flags = bufferRingConfig.isIncremental() ? Native.IOU_PBUF_RING_INC : 0;
        long ioUringBufRingAddr = Native.ioUringRegisterBufRing(ringFd, bufferRingSize, bufferGroupId, flags);
        if (ioUringBufRingAddr < 0) {
            throw Errors.newIOException("ioUringRegisterBufRing", (int) ioUringBufRingAddr);
        }
        return new IoUringBufferRing(ringFd,
                Buffer.wrapMemoryAddressWithNativeOrder(ioUringBufRingAddr, Native.ioUringBufRingSize(bufferRingSize)),
                bufferRingSize, bufferRingConfig.batchSize(), bufferRingConfig.maxUnreleasedBuffers(),
                bufferGroupId, bufferRingConfig.isIncremental(), bufferRingConfig.allocator()
        );
    }

    IoUringBufferRing findBufferRing(short bgId) {
        IoUringBufferRing cached = registeredIoUringBufferRing.get(bgId);
        if (cached != null) {
            return cached;
        }
        throw new IllegalArgumentException(
                String.format("Cant find bgId:%d, please register it in ioUringIoHandler", bgId)
        );
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
                // Just return
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
                eventfd.intValue(), eventfdReadBufAddress, 0, 8, udata);
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

            timeoutMemory.putLong(KERNEL_TIMESPEC_TV_SEC_FIELD, seconds);
            timeoutMemory.putLong(KERNEL_TIMESPEC_TV_NSEC_FIELD, nanoSeconds);
            if (linkTimeout) {
                submissionQueue.addLinkTimeout(timeoutMemoryAddress, udata);
            } else {
                submissionQueue.addTimeout(timeoutMemoryAddress, udata);
            }
        }
        int submitted = submissionQueue.submitAndWait();
        // Clear the iovArray as we can re-use it now as things are considered stable after submission:
        // See https://man7.org/linux/man-pages/man3/io_uring_prep_sendmsg.3.html
        iovArray.clear();
        return submitted;
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

        // Write to the eventfd to ensure that if we submitted a read for the eventfd we will see the completion event.
        Native.eventFdWrite(eventfd.intValue(), 1L);

        // Ensure all previously submitted IOs get to complete before tearing down everything.
        long udata = UserData.encode(RINGFD_ID, Native.IORING_OP_NOP, (short) 0);
        submissionQueue.addNop((byte) Native.IOSQE_IO_DRAIN, udata);

        // Submit everything and wait until we could drain i.
        submissionQueue.submitAndWait();
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
        // We need to also specify the Native.IOSQE_LINK flag for it to work as otherwise it is not correctly linked
        // with the timeout.
        // See:
        // - https://man7.org/linux/man-pages/man2/io_uring_enter.2.html
        // - https://git.kernel.dk/cgit/liburing/commit/?h=link-timeout&id=bc1bd5e97e2c758d6fd975bd35843b9b2c770c5a
        submissionQueue.addNop((byte) (Native.IOSQE_IO_DRAIN | Native.IOSQE_LINK), udata);
        // ... but only wait for 200 milliseconds on this
        submitAndWaitWithTimeout(submissionQueue, true, TimeUnit.MILLISECONDS.toNanos(200));
        completionQueue.process(this::handle);
        for (IoUringBufferRing ioUringBufferRing : registeredIoUringBufferRing.values()) {
            ioUringBufferRing.close();
        }
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
        Buffer.free(eventfdReadBuf);
        Buffer.free(timeoutMemory);
        iovArray.release();
    }

    @Override
    public IoRegistration register(IoHandle handle) throws Exception {
        IoUringIoHandle ioHandle = cast(handle);
        if (shuttingDown) {
            throw new IllegalStateException("IoUringIoHandler is shutting down");
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

    private final class DefaultIoUringIoRegistration implements IoRegistration {
        private final AtomicBoolean canceled = new AtomicBoolean();
        private final ThreadAwareExecutor executor;
        private final IoUringIoEvent event = new IoUringIoEvent(0, 0, (byte) 0, (short) 0);
        final IoUringIoHandle handle;

        private boolean removeLater;
        private int outstandingCompletions;
        private int id;

        DefaultIoUringIoRegistration(ThreadAwareExecutor executor, IoUringIoHandle handle) {
            this.executor = executor;
            this.handle = handle;
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
            if ((ioOps.flags() & Native.IOSQE_CQE_SKIP_SUCCESS) != 0) {
                // Because we expect at least 1 completion per submission we can't support IOSQE_CQE_SKIP_SUCCESS
                // as it will only produce a completion on failure.
                throw new IllegalArgumentException("IOSQE_CQE_SKIP_SUCCESS not supported");
            }
            long udata = UserData.encode(id, ioOps.opcode(), ioOps.data());
            if (executor.isExecutorThread(Thread.currentThread())) {
                submit0(ioOps, udata);
            } else {
                executor.execute(() -> submit0(ioOps, udata));
            }
            return udata;
        }

        private void submit0(IoUringIoOps ioOps, long udata) {
            ringBuffer.ioUringSubmissionQueue().enqueueSqe(ioOps.opcode(), ioOps.flags(), ioOps.ioPrio(),
                    ioOps.fd(), ioOps.union1(), ioOps.union2(), ioOps.len(), ioOps.union3(), udata,
                    ioOps.union4(), ioOps.personality(), ioOps.union5(), ioOps.union6()
            );
            outstandingCompletions++;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T attachment() {
            return (T) IoUringIoHandler.this;
        }

        @Override
        public boolean isValid() {
            return !canceled.get();
        }

        @Override
        public boolean cancel() {
            if (!canceled.compareAndSet(false, true)) {
                // Already cancelled.
                return false;
            }
            if (executor.isExecutorThread(Thread.currentThread())) {
                tryRemove();
            } else {
                executor.execute(this::tryRemove);
            }
            return true;
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
            assert executor.isExecutorThread(Thread.currentThread());
            try {
                handle.close();
            } catch (Exception e) {
                logger.debug("Exception during closing " + handle, e);
            }
        }

        void handle(int res, int flags, byte op, short data) {
            event.update(res, flags, op, data);
            handle.handle(this, event);
            // Only decrement outstandingCompletions if IORING_CQE_F_MORE is not set as otherwise we know that we will
            // receive more completions for the intial request.
            if ((flags & Native.IORING_CQE_F_MORE) == 0 && --outstandingCompletions == 0 && removeLater) {
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
        if (!executor.isExecutorThread(Thread.currentThread()) &&
                !eventfdAsyncNotify.getAndSet(true)) {
            // write to the eventfd which will then trigger an eventfd read completion.
            Native.eventFdWrite(eventfd.intValue(), 1L);
        }
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return IoUringIoHandle.class.isAssignableFrom(handleType);
    }

    IovArray iovArray() {
        if (iovArray.isFull()) {
            // Submit so we can reuse the iovArray.
            submitAndClear(ringBuffer.ioUringSubmissionQueue());
        }
        assert iovArray.count() == 0;
        return iovArray;
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
        return newFactory(new IoUringIoHandlerConfig());
    }

    /**
     * Create a new {@link IoHandlerFactory} that can be used to create {@link IoUringIoHandler}s.
     * Each {@link IoUringIoHandler} will use a ring of size {@code ringSize}.
     *
     * @param  ringSize     the size of the ring.
     * @return              factory
     */
    public static IoHandlerFactory newFactory(int ringSize) {
        IoUringIoHandlerConfig configuration = new IoUringIoHandlerConfig();
        configuration.setRingSize(ringSize);
        return eventLoop -> new IoUringIoHandler(eventLoop, configuration);
    }

    /**
     * Create a new {@link IoHandlerFactory} that can be used to create {@link IoUringIoHandler}s.
     * Each {@link IoUringIoHandler} will use same option
     * @param config the io_uring configuration
     * @return factory
     */
    public static IoHandlerFactory newFactory(IoUringIoHandlerConfig config) {
        IoUring.ensureAvailability();
        ObjectUtil.checkNotNull(config, "config");
        return eventLoop -> new IoUringIoHandler(eventLoop, config);
    }
}
