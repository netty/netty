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

import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
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
import io.netty.util.internal.CleanableDirectBuffer;
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
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * {@link IoHandler} which is implemented in terms of the Linux-specific {@code io_uring} API.
 */
public final class IoUringIoHandler implements IoHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IoUringIoHandler.class);
    private static final int WAKEUP_CLOSED = 1 << 30;

    private final RingBuffer ringBuffer;
    private final IntObjectMap<IoUringBufferRing> registeredIoUringBufferRing;
    private final IntObjectMap<DefaultIoUringIoRegistration> registrations;
    // The maximum number of bytes for an InetAddress / Inet6Address
    private final byte[] inet4AddressArray = new byte[SockaddrIn.IPV4_ADDRESS_LENGTH];
    private final byte[] inet6AddressArray = new byte[SockaddrIn.IPV6_ADDRESS_LENGTH];

    private final AtomicBoolean eventfdAsyncNotify = new AtomicBoolean();
    private final AtomicInteger wakeupWriters = new AtomicInteger();
    private final FileDescriptor eventfd;
    private final CleanableDirectBuffer eventfdReadBufCleanable;
    private final ByteBuffer eventfdReadBuf;
    private final long eventfdReadBufAddress;
    private final CleanableDirectBuffer timeoutMemoryCleanable;
    private final ByteBuffer timeoutMemory;
    private final long timeoutMemoryAddress;
    private final IovArray iovArray;
    private final MsgHdrMemoryArray msgHdrMemoryArray;
    private long eventfdReadSubmitted;
    private boolean eventFdClosing;
    private volatile boolean shuttingDown;
    private boolean closeCompleted;
    private final PendingOpMap pendingOps;
    private int nextRegistrationId = 1;

    private static final long INVALID_ID = 0;
    private static final long EVENTFD_TOKEN = PendingOpMap.token(1);
    private static final long RINGFD_TOKEN = PendingOpMap.token(2);
    private static final int KERNEL_TIMESPEC_SIZE = 16; //__kernel_timespec

    private static final int KERNEL_TIMESPEC_TV_SEC_FIELD = 0;
    private static final int KERNEL_TIMESPEC_TV_NSEC_FIELD = 8;

    private final ThreadAwareExecutor executor;

    IoUringIoHandler(ThreadAwareExecutor executor, IoUringIoHandlerConfig config) {
        // Ensure that we load all native bits as otherwise it may fail when try to use native methods in IovArray
        IoUring.ensureAvailability();
        this.executor = requireNonNull(executor, "executor");
        requireNonNull(config, "config");
        int setupFlags = Native.setupFlags(config.singleIssuer());

        //The default cq size is always twice the ringSize.
        // It only makes sense when the user actually specifies the cq ring size.
        int cqSize = 2 * config.getRingSize();
        if (config.needSetupCqeSize()) {
            assert IoUring.isSetupCqeSizeSupported();
            setupFlags |= Native.IORING_SETUP_CQSIZE;
            cqSize = config.getCqSize();
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
        pendingOps = new PendingOpMap(IoUring.DEFAULT_PENDING_OPS_INITIAL_CAPACITY);
        eventfd = Native.newBlockingEventFd();
        eventfdReadBufCleanable = Buffer.allocateDirectBufferWithNativeOrder(Long.BYTES);
        eventfdReadBuf = eventfdReadBufCleanable.buffer();
        eventfdReadBufAddress = Buffer.memoryAddress(eventfdReadBuf);
        timeoutMemoryCleanable = Buffer.allocateDirectBufferWithNativeOrder(KERNEL_TIMESPEC_SIZE);
        timeoutMemory = timeoutMemoryCleanable.buffer();
        timeoutMemoryAddress = Buffer.memoryAddress(timeoutMemory);
        iovArray = new IovArray(IoUring.NUM_ELEMENTS_IOVEC);
        msgHdrMemoryArray = new MsgHdrMemoryArray((short) 1024);
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
            if (context.shouldReportActiveIoTime()) {
                context.reportActiveIoTime(0);
            }
            return 0;
        }
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        if (!completionQueue.hasCompletions() && context.canBlock()) {
            if (eventfdReadSubmitted == 0) {
                submitEventFdRead();
            }
            long timeoutNanos = context.deadlineNanos() == -1 ? -1 : context.delayNanos(System.nanoTime());
            submitAndWaitWithTimeout(submissionQueue, false, timeoutNanos);
        } else {
            // Even if we have some completions already pending we can still try to even fetch more.
            submitAndClearNow(submissionQueue);
        }

        int ioCompletions;
        if (context.shouldReportActiveIoTime()) {
            long activeIoStartTimeNanos = System.nanoTime();
            ioCompletions = processCompletionsAndHandleOverflow(submissionQueue, completionQueue, this::handle);
            long activeIoEndTimeNanos = System.nanoTime();
            context.reportActiveIoTime(activeIoEndTimeNanos - activeIoStartTimeNanos);
        } else {
            ioCompletions = processCompletionsAndHandleOverflow(submissionQueue, completionQueue, this::handle);
        }
        return ioCompletions;
    }

    private boolean needSubmit(int sqFlags) {
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        return submissionQueue.count() > 0
                || (sqFlags & (Native.IORING_SQ_CQ_OVERFLOW | Native.IORING_SQ_TASKRUN)) != 0;
    }

    private int processCompletionsAndHandleOverflow(SubmissionQueue submissionQueue, CompletionQueue completionQueue,
                                         CompletionCallback callback) {
        int ioCompletions = 0;
        for (int i = 0; i < 128; i++) {
            long packed = completionQueue.process(callback);
            int total = (int) (packed >>> 32);
            ioCompletions += (int) packed;
            int sqFlags = submissionQueue.flags();
            if ((sqFlags & Native.IORING_SQ_CQ_OVERFLOW) != 0) {
                logger.warn("CompletionQueue overflow detected, consider increasing size: {} ",
                        completionQueue.ringEntries);
            }
            if (total == 0) {
                if (!needSubmit(sqFlags)) {
                    break;
                }
                submitAndClearNow0(submissionQueue);
            }
        }
        return ioCompletions;
    }

    private int submitAndClearNow(SubmissionQueue submissionQueue) {
        if (needSubmit(submissionQueue.flags())) {
            return submitAndClearNow0(submissionQueue);
        }
        return 0;
    }

    private int submitAndClearNow0(SubmissionQueue submissionQueue) {

        int submitted = submissionQueue.submitAndGetNow();

        // Clear the iovArray as we can re-use it now as things are considered stable after submission:
        // See https://man7.org/linux/man-pages/man3/io_uring_prep_sendmsg.3.html
        iovArray.clear();
        msgHdrMemoryArray.clear();
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
                bufferRingSize, bufferRingConfig.batchSize(),
                bufferGroupId, bufferRingConfig.isIncremental(), bufferRingConfig.allocator(),
                bufferRingConfig.isBatchAllocation()
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

    private boolean handle(int res, int flags, long udata, ByteBuffer extraCqeData) {
        try {
            if (udata == EVENTFD_TOKEN) {
                handleEventFdRead();
                return false;
            }
            if (udata == RINGFD_TOKEN) {
                return false;
            }
            if (udata >= 0) {
                handleFastPath(res, flags, udata, extraCqeData);
                return true;
            }
            handleSlowPath(res, flags, udata, extraCqeData);
            return true;
        } catch (Error e) {
            throw e;
        } catch (Throwable throwable) {
            handleLoopException(throwable);
            return true;
        }
    }

    private void handleFastPath(int res, int flags, long udata, ByteBuffer extraCqeData) {
        int id = UserData.decodeId(udata);
        byte op = UserData.decodeOp(udata);
        long userData = UserData.decodeData(udata);
        DefaultIoUringIoRegistration registration = registrations.get(id);
        if (registration != null) {
            traceCompletion(registration, id, op, res);
            registration.handle(res, flags, op, userData, extraCqeData);
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("ignoring packed completion for unknown registration (registrationId={}, op={}, userData={},"
                            + " res={})",
                    id, Native.opToStr(op), userData, res);
        }
    }

    private void handleSlowPath(int res, int flags, long udata, ByteBuffer extraCqeData) {
        long sequence = PendingOpMap.tokenSequence(udata);
        int slot = pendingOps.findSlot(udata);
        if (slot != -1) {
            int registrationId = pendingOps.registrationId(slot);
            DefaultIoUringIoRegistration registration = registrations.get(registrationId);
            byte op = pendingOps.op(slot);
            long userData = pendingOps.userData(slot);

            // Recycle if this completion is terminal (no more CQEs expected for this SQE).
            if ((flags & Native.IORING_CQE_F_MORE) == 0) {
                pendingOps.release(slot);
            }

            // Resolve slow-path completions through the live registration table to align with the fast path.
            if (registration != null) {
                traceCompletion(registration, registrationId, op, res);
                registration.handle(res, flags, op, userData, extraCqeData);
                return;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("ignoring slow-path completion for missing registration (registrationId={}, seq={}, "
                                + "op={}, userData={}, res={})",
                        registrationId, sequence, Native.opToStr(op), userData, res);
            }
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("ignoring slow-path completion for unknown sequence (seq={}, res={})", sequence, res);
        }
    }

    private void traceCompletion(DefaultIoUringIoRegistration registration, int registrationId, byte op, int res) {
        if (!logger.isTraceEnabled()) {
            return;
        }
        int fd = registration.fd();
        if (fd != -1) {
            logger.trace("completed(ring {}): {}(fd={}, res={})",
                    ringBuffer.fd(), Native.opToStr(op), fd, res);
        } else {
            logger.trace("completed(ring {}): {}(registrationId={}, res={})",
                    ringBuffer.fd(), Native.opToStr(op), registrationId, res);
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
        eventfdReadSubmitted = submissionQueue.addEventFdRead(
                eventfd.intValue(), eventfdReadBufAddress, 0, 8, EVENTFD_TOKEN);
    }

    private int submitAndWaitWithTimeout(SubmissionQueue submissionQueue,
                                         boolean linkTimeout, long timeoutNanoSeconds) {
        if (timeoutNanoSeconds != -1) {
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
                submissionQueue.addLinkTimeout(timeoutMemoryAddress, RINGFD_TOKEN);
            } else {
                submissionQueue.addTimeout(timeoutMemoryAddress, RINGFD_TOKEN);
            }
        }
        int submitted = submissionQueue.submitAndGet();
        // Clear the iovArray as we can re-use it now as things are considered stable after submission:
        // See https://man7.org/linux/man-pages/man3/io_uring_prep_sendmsg.3.html
        iovArray.clear();
        msgHdrMemoryArray.clear();
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
        submissionQueue.addNop((byte) Native.IOSQE_IO_DRAIN, RINGFD_TOKEN);

        // Submit everything and wait until we could drain i.
        submissionQueue.submitAndGet();

        while (completionQueue.hasCompletions()) {
            processCompletionsAndHandleOverflow(submissionQueue, completionQueue, this::handle);
            if (submissionQueue.count() > 0) {
                submissionQueue.submitAndGetNow();
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
        // We need to also specify the Native.IOSQE_LINK flag for it to work as otherwise it is not correctly linked
        // with the timeout.
        // See:
        // - https://man7.org/linux/man-pages/man2/io_uring_enter.2.html
        // - https://git.kernel.dk/cgit/liburing/commit/?h=link-timeout&id=bc1bd5e97e2c758d6fd975bd35843b9b2c770c5a
        submissionQueue.addNop((byte) (Native.IOSQE_IO_DRAIN | Native.IOSQE_LINK), RINGFD_TOKEN);
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
                public boolean handle(int res, int flags, long udata, ByteBuffer extraCqeData) {
                    if (udata == EVENTFD_TOKEN) {
                        eventFdDrained = true;
                    }
                    return IoUringIoHandler.this.handle(res, flags, udata, extraCqeData);
                }
            }
            final DrainFdEventCallback handler = new DrainFdEventCallback();
            completionQueue.process(handler);
            while (!handler.eventFdDrained) {
                submissionQueue.submitAndGet();
                processCompletionsAndHandleOverflow(submissionQueue, completionQueue, handler);
            }
        }
        // We've consumed any pending eventfd read and `eventfdAsyncNotify` should never
        // transition back to false, thus we should never have any more events written.
        // So, if we have a read event pending, we can cancel it.
        if (eventfdReadSubmitted != 0) {
            submissionQueue.addCancel(eventfdReadSubmitted, EVENTFD_TOKEN);
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
        closeWakeupGate();
        try {
            eventfd.close();
        } catch (IOException e) {
            logger.warn("Failed to close eventfd", e);
        }
        eventfdReadBufCleanable.clean();
        timeoutMemoryCleanable.clean();
        iovArray.release();
        msgHdrMemoryArray.release();
    }

    @Override
    public IoRegistration register(IoHandle handle) throws Exception {
        IoUringIoHandle ioHandle = cast(handle);
        if (shuttingDown) {
            throw new IllegalStateException("IoUringIoHandler is shutting down");
        }
        int startId = nextRegistrationId;
        DefaultIoUringIoRegistration registration = new DefaultIoUringIoRegistration(executor, ioHandle);
        for (;;) {
            int id = nextRegistrationId();
            DefaultIoUringIoRegistration old = registrations.put(id, registration);
            if (old != null) {
                assert old.handle != registration.handle;
                registrations.put(id, old);
                if (nextRegistrationId == startId) {
                    throw new IllegalStateException("registration id space exhausted");
                }
            } else {
                registration.setId(id);
                ioHandle.registered();
                break;
            }
        }

        return registration;
    }

    private int nextRegistrationId() {
        //registrationId must stay positive because id > 0
        //it is used to distinguish normal fast-path completions from non-registration tokens.
        int id = nextRegistrationId;
        nextRegistrationId = id == Integer.MAX_VALUE ? 1 : id + 1;
        return id;
    }

    private final class DefaultIoUringIoRegistration implements IoRegistration {
        private final AtomicBoolean canceled = new AtomicBoolean();
        private final ThreadAwareExecutor executor;
        private final IoUringIoEvent event = new IoUringIoEvent(0, 0, (byte) 0, 0L);
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
            long userData = ioOps.userData();
            // Use the fast path when the full submission can still be encoded into packed UserData.
            if (canUseFastPath(userData)) {
                long packedSeq = UserData.encode(id, ioOps.opcode(), (short) userData);
                if (executor.isExecutorThread(Thread.currentThread())) {
                    submitFastPath0(ioOps, packedSeq);
                } else {
                    executor.execute(() -> submitFastPath0(ioOps, packedSeq));
                }
                return packedSeq;
            }
            long token = pendingOps.nextToken();
            if (executor.isExecutorThread(Thread.currentThread())) {
                submitSlowPath0(ioOps, token, userData);
            } else {
                executor.execute(() -> submitSlowPath0(ioOps, token, userData));
            }
            return token;
        }

        private void submitFastPath0(IoUringIoOps ioOps, long seq) {
            ringBuffer.ioUringSubmissionQueue().enqueueSqe(ioOps.opcode(), ioOps.flags(), ioOps.ioPrio(),
                    ioOps.fd(), ioOps.union1(), ioOps.union2(), ioOps.len(), ioOps.union3(), seq,
                    ioOps.union4(), ioOps.personality(), ioOps.union5(), ioOps.union6()
            );
            outstandingCompletions++;
        }

        private void submitSlowPath0(IoUringIoOps ioOps, long token, long userData) {
            pendingOps.registerNormal(token, id, ioOps.opcode(), userData);
            ringBuffer.ioUringSubmissionQueue().enqueueSqe(ioOps.opcode(), ioOps.flags(), ioOps.ioPrio(),
                    ioOps.fd(), ioOps.union1(), ioOps.union2(), ioOps.len(), ioOps.union3(), token,
                    ioOps.union4(), ioOps.personality(), ioOps.union5(), ioOps.union6()
            );
            outstandingCompletions++;
        }

        private boolean canUseFastPath(long userData) {
            return ((short) userData) == userData;
        }

        private int fd() {
            if (handle instanceof AbstractIoUringChannel) {
                return ((AbstractIoUringChannel) handle).fd().intValue();
            }
            return -1;
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
            handle.unregistered();
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

        void handle(int res, int flags, byte op, long userData, ByteBuffer extraCqeData) {
            event.update(res, flags, op, userData, extraCqeData);
            handle.handle(this, event);
            // Only decrement outstandingCompletions if IORING_CQE_F_MORE is not set as otherwise we know that we will
            // receive more completions for the intial request.
            if ((flags & Native.IORING_CQE_F_MORE) == 0 && --outstandingCompletions == 0 && removeLater) {
                // No more outstanding completions, remove the registration now.
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
            // Reserve a writer slot so the event-loop thread cannot close the eventfd while we are in the
            // middle of eventFdWrite(). If the gate has already been closed (loop is being destroyed),
            // simply drop the wakeup: there is no loop left to wake up, and writing to a closed (and
            // possibly recycled) fd would either throw EBADF or, worse, hit an unrelated fd.
            int s;
            do {
                s = wakeupWriters.get();
                if ((s & WAKEUP_CLOSED) != 0) {
                    return;
                }
            } while (!wakeupWriters.compareAndSet(s, s + 1));
            try {
                // write to the eventfd which will then trigger an eventfd read completion.
                Native.eventFdWrite(eventfd.intValue(), 1L);
            } finally {
                wakeupWriters.decrementAndGet();
            }
        }
    }

    private void closeWakeupGate() {
        int s;
        do {
            s = wakeupWriters.get();
        } while (!wakeupWriters.compareAndSet(s, s | WAKEUP_CLOSED));
        // Wait for any thread still inside eventFdWrite() to leave. eventFdWrite is a single write(2)
        // syscall on an eventfd, so this spin is bounded to a few microseconds in practice.
        while ((wakeupWriters.get() & ~WAKEUP_CLOSED) != 0) {
            Thread.onSpinWait();
        }
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return IoUringIoHandle.class.isAssignableFrom(handleType);
    }

    IovArray iovArray() {
        if (iovArray.isFull()) {
            // Submit so we can reuse the iovArray.
            submitAndClearNow(ringBuffer.ioUringSubmissionQueue());
            assert iovArray.count() == 0;
        }
        return iovArray;
    }

    MsgHdrMemoryArray msgHdrMemoryArray() {
        if (msgHdrMemoryArray.isFull()) {
            // Submit so we can reuse the msgHdrArray.
            submitAndClearNow(ringBuffer.ioUringSubmissionQueue());
        }
        return msgHdrMemoryArray;
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
        final IoUringIoHandlerConfig copy = ObjectUtil.checkNotNull(config, "config").verifyAndClone();
        return new IoHandlerFactory() {
            @Override
            public IoHandler newHandler(ThreadAwareExecutor eventLoop) {
                return new IoUringIoHandler(eventLoop, copy);
            }

            @Override
            public boolean isChangingThreadSupported() {
                return !copy.singleIssuer();
            }
        };
    }
}
