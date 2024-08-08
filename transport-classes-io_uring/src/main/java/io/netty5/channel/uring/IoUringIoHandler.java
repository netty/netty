/*
 * Copyright 2022 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.channel.EventLoop;
import io.netty5.channel.IoExecutionContext;
import io.netty5.channel.IoHandle;
import io.netty5.channel.IoHandler;
import io.netty5.channel.IoHandlerFactory;
import io.netty5.channel.IoOps;
import io.netty5.channel.IoRegistration;
import io.netty5.channel.unix.FileDescriptor;
import io.netty5.util.collection.IntObjectHashMap;
import io.netty5.util.collection.IntObjectMap;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * {@link IoHandler} which is implemented in terms of the Linux-specific {@code io_uring} API.
 */
public final class IoUringIoHandler implements IoHandler, CompletionCallback {
    private static final Logger logger = LoggerFactory.getLogger(IoUringIoHandler.class);
    private static final short RING_CLOSE = 1;

    private final RingBuffer ringBuffer;
    private final IntObjectMap<DefaultIoUringIoRegistration> registrations;

    private final AtomicBoolean eventfdAsyncNotify = new AtomicBoolean();
    private final FileDescriptor eventfd;
    private final long eventfdReadBuf;
    private long eventfdReadSubmitted;

    private boolean eventFdClosing;
    private volatile boolean shuttingDown;
    private boolean closeCompleted;
    private int nextRegistrationId = Integer.MIN_VALUE;

    // these two ids are used internally any so can't be used by nextRegistrationId().
    private static final int EVENTFD_ID = Integer.MAX_VALUE;
    private static final int RINGFD_ID = EVENTFD_ID - 1;

    IoUringIoHandler(RingBuffer ringBuffer) {
        // Ensure that we load all native bits as otherwise it may fail when try to use native methods in IovArray
        IoUring.ensureAvailability();
        this.ringBuffer = requireNonNull(ringBuffer, "ringBuffer");
        registrations = new IntObjectHashMap<>();
        eventfd = Native.newBlockingEventFd();
        eventfdReadBuf = PlatformDependent.allocateMemory(8);
    }

    @Override
    public int run(IoExecutionContext context) {
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        if (!completionQueue.hasCompletions() && context.canBlock()) {
            if (eventfdReadSubmitted == 0) {
                submitEventFdRead();
            }
            if (context.deadlineNanos() != -1) {
                submitTimeout(context);
            }
            submissionQueue.submitAndWait();
        } else {
            submissionQueue.submit();
        }
        return completionQueue.process(this);
    }

    @Override
    public void handle(int res, int flags, int id, byte op, short data) {
        if (id == EVENTFD_ID) {
            handleEventFdRead();
            return;
        }
        if (id == RINGFD_ID) {
            if (op == Native.IORING_OP_NOP && data == RING_CLOSE) {
                completeRingClose();
            }
            return;
        }
        if (op == Native.IORING_OP_ASYNC_CANCEL) {
            // We don't care about the result of async cancels; they are best-effort.
            return;
        }
        DefaultIoUringIoRegistration registration = registrations.get(id);
        if (registration == null) {
            logger.debug("ignoring {} completion for unknown registration (id={}, res={})",
                    Native.opToStr(op), id, res);
            return;
        }
        registration.handle(res, flags, op, data);
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
                eventfd.intValue(), eventfdReadBuf, 0, 8, EVENTFD_ID, (short) 0);
    }

    private void submitTimeout(IoExecutionContext context) {
        long delayNanos = context.delayNanos(System.nanoTime());
        ringBuffer.ioUringSubmissionQueue().addTimeout(
                ringBuffer.fd(), delayNanos, RINGFD_ID, (short) 0);
    }

    @Override
    public void prepareToDestroy() {
        shuttingDown = true;
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        SubmissionQueue submissionQueue = ringBuffer.ioUringSubmissionQueue();
        List<DefaultIoUringIoRegistration> copy = new ArrayList<>(registrations.values());

        // Ensure all previously submitted IOs get to complete before closing all fds.
        submissionQueue.addNop(ringBuffer.fd(), Native.IOSQE_IO_DRAIN, RINGFD_ID, (short) 0);

        for (DefaultIoUringIoRegistration registration: copy) {
            registration.close();
            if (submissionQueue.count() > 0) {
                submissionQueue.submit();
            }
            if (completionQueue.hasCompletions()) {
                completionQueue.process(this::handle);
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
        submissionQueue.addNop(ringBuffer.fd(), Native.IOSQE_IO_DRAIN, RINGFD_ID, (short) 0);
        // ... but only wait for 200 milliseconds on this
        submissionQueue.addLinkTimeout(ringBuffer.fd(), TimeUnit.MILLISECONDS.toNanos(200), RINGFD_ID, (short) 0);
        submissionQueue.submitAndWait();
        completionQueue.process(this);
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
                public void handle(int res, int flags, int id, byte op, short data) {
                    if (id == EVENTFD_ID) {
                        eventFdDrained = true;
                    }
                    IoUringIoHandler.this.handle(res, flags, id, op, data);
                }
            }
            final DrainFdEventCallback handler = new DrainFdEventCallback();
            completionQueue.process(handler);
            while (!handler.eventFdDrained) {
                submissionQueue.submitAndWait();
                completionQueue.process(handler);
            }
        }
        // We've consumed any pending eventfd read and `eventfdAsyncNotify` should never
        // transition back to false, thus we should never have any more events written.
        // So, if we have a read event pending, we can cancel it.
        if (eventfdReadSubmitted != 0) {
            submissionQueue.addCancel(eventfd.intValue(), eventfdReadSubmitted, EVENTFD_ID);
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
    }

    @Override
    public IoRegistration register(EventLoop eventLoop, IoHandle handle) throws Exception {
        IoUringIoHandle ioHandle = cast(handle);
        if (shuttingDown) {
            throw new RejectedExecutionException("IoEventLoop is shutting down");
        }
        DefaultIoUringIoRegistration registration = new DefaultIoUringIoRegistration(eventLoop, ioHandle);
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

        ringBuffer.ioUringSubmissionQueue().incrementHandledFds();
        return registration;
    }

    private int nextRegistrationId() {
        int id;
        do {
            id = nextRegistrationId++;
        } while (id == RINGFD_ID || id == EVENTFD_ID);
        return id;
    }

    private final class DefaultIoUringIoRegistration extends AtomicBoolean implements IoUringIoRegistration {
        private final EventLoop eventLoop;
        private final IoUringIoEvent event = new IoUringIoEvent(0, 0, 0, (byte) 0, (short) 0);
        final IoUringIoHandle handle;

        private boolean removeLater;
        private int outstandingCompletions;
        private int id;

        DefaultIoUringIoRegistration(EventLoop eventLoop, IoUringIoHandle handle) {
            this.eventLoop = eventLoop;
            this.handle = handle;
        }

        void setId(int id) {
            this.id = id;
        }

        @Override
        public long submit(IoOps ops) {
            IoUringIoOps ioOps = (IoUringIoOps) ops;
            long udata = UserData.encode(id, ioOps.opcode(), ioOps.data());
            if (!isValid()) {
                return udata;
            }
            if (eventLoop.inEventLoop()) {
                submit0(ioOps, udata);
            } else {
                eventLoop.execute(() -> submit0(ioOps, udata));
            }
            return udata;
        }

        private void submit0(IoUringIoOps ioOps, long udata) {
            ringBuffer.ioUringSubmissionQueue().enqueueSqe(ioOps.opcode(), ioOps.flags(), ioOps.ioPrio(),
                    ioOps.rwFlags(), ioOps.fd(), ioOps.bufferAddress(), ioOps.length(), ioOps.offset(), udata);
            outstandingCompletions++;
        }

        @Override
        public IoUringIoHandler ioHandler() {
            return IoUringIoHandler.this;
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
                tryRemove();
            } else {
                eventLoop.execute(this::tryRemove);
            }
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
            ringBuffer.ioUringSubmissionQueue().decrementHandledFds();
        }

        void close() {
            assert eventLoop.inEventLoop();
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

        void handle(int res, int flags, byte op, short data) {
            event.update(res, flags, id, op, data);
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
    public void wakeup(EventLoop eventLoop) {
        if (!eventLoop.inEventLoop() && !eventfdAsyncNotify.getAndSet(true)) {
            // write to the eventfd which will then trigger an eventfd read completion.
            Native.eventFdWrite(eventfd.intValue(), 1L);
        }
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return IoUringIoHandle.class.isAssignableFrom(handleType);
    }

    int remaining() {
        return ringBuffer.ioUringSubmissionQueue().remaining();
    }

    void submit() {
        ringBuffer.ioUringSubmissionQueue().submit();
    }

    public static IoHandlerFactory newFactory() {
        IoUring.ensureAvailability();
        return () -> {
            RingBuffer ringBuffer = Native.createRingBuffer();
            return new IoUringIoHandler(ringBuffer);
        };
    }

    public static IoHandlerFactory newFactory(int ringSize) {
        IoUring.ensureAvailability();
        return () -> {
            RingBuffer ringBuffer = Native.createRingBuffer(ringSize);
            return new IoUringIoHandler(ringBuffer);
        };
    }

    public static IoHandlerFactory newFactory(int ringSize, int kernelWorkerOffloadThreshold) {
        IoUring.ensureAvailability();
        return () -> {
            RingBuffer ringBuffer = Native.createRingBuffer(ringSize, kernelWorkerOffloadThreshold);
            return new IoUringIoHandler(ringBuffer);
        };
    }
}
