/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.IovArray;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

final class IOUringEventLoop extends SingleThreadEventLoop implements
                                                           IOUringCompletionQueue.IOUringCompletionQueueCallback {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IOUringEventLoop.class);

    private final IntObjectMap<AbstractIOUringChannel> channels = new IntObjectHashMap<AbstractIOUringChannel>(4096);
    private final RingBuffer ringBuffer;

    private static final long AWAKE = -1L;
    private static final long NONE = Long.MAX_VALUE;

    // nextWakeupNanos is:
    //    AWAKE            when EL is awake
    //    NONE             when EL is waiting with no wakeup scheduled
    //    other value T    when EL is waiting with wakeup scheduled at time T
    private final AtomicLong nextWakeupNanos = new AtomicLong(AWAKE);
    private final FileDescriptor eventfd;

    private final IovArrays iovArrays;

    private long prevDeadlineNanos = NONE;
    private boolean pendingWakeup;

    IOUringEventLoop(final EventLoopGroup parent, final Executor executor, final boolean addTaskWakesUp) {
        super(parent, executor, addTaskWakesUp);
        // Ensure that we load all native bits as otherwise it may fail when try to use native methods in IovArray
        IOUring.ensureAvailability();

        // TODO: Let's hard code this to 8 IovArrays to keep the memory overhead kind of small. We may want to consider
        //       allow to change this in the future.
        iovArrays = new IovArrays(8);
        ringBuffer = Native.createRingBuffer(new Runnable() {
            @Override
            public void run() {
                // Once we submitted its safe to clear the IovArrays and so be able to re-use these.
                iovArrays.clear();
            }
        });

        eventfd = Native.newEventFd();
        logger.trace("New EventLoop: {}", this.toString());
    }

    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return newTaskQueue0(maxPendingTasks);
    }

    private static Queue<Runnable> newTaskQueue0(int maxPendingTasks) {
        // This event loop never calls takeTask()
        return maxPendingTasks == Integer.MAX_VALUE? PlatformDependent.<Runnable>newMpscQueue()
                : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }

    public void add(AbstractIOUringChannel ch) {
        logger.trace("Add Channel: {} ", ch.socket.intValue());
        int fd = ch.socket.intValue();

        channels.put(fd, ch);
    }

    public void remove(AbstractIOUringChannel ch) {
        logger.trace("Remove Channel: {}", ch.socket.intValue());
        int fd = ch.socket.intValue();

        AbstractIOUringChannel old = channels.remove(fd);
        if (old != null && old != ch) {
            // The Channel mapping was already replaced due FD reuse, put back the stored Channel.
            channels.put(fd, old);

            // If we found another Channel in the map that is mapped to the same FD the given Channel MUST be closed.
            assert !ch.isOpen();
        }
    }

    private void closeAll() {
        logger.trace("CloseAll IOUringEvenloop");
        // Using the intermediate collection to prevent ConcurrentModificationException.
        // In the `close()` method, the channel is deleted from `channels` map.
        AbstractIOUringChannel[] localChannels = channels.values().toArray(new AbstractIOUringChannel[0]);

        for (AbstractIOUringChannel ch : localChannels) {
            ch.unsafe().close(ch.unsafe().voidPromise());
        }
    }

    @Override
    protected void run() {
        final IOUringCompletionQueue completionQueue = ringBuffer.getIoUringCompletionQueue();
        final IOUringSubmissionQueue submissionQueue = ringBuffer.getIoUringSubmissionQueue();

        // Lets add the eventfd related events before starting to do any real work.
        submissionQueue.addPollIn(eventfd.intValue());
        submissionQueue.submit();

        for (;;) {
            logger.trace("Run IOUringEventLoop {}", this.toString());
            long curDeadlineNanos = nextScheduledTaskDeadlineNanos();
            if (curDeadlineNanos == -1L) {
                curDeadlineNanos = NONE; // nothing on the calendar
            }
            nextWakeupNanos.set(curDeadlineNanos);

            // Only submit a timeout if there are no tasks to process and do a blocking operation
            // on the completionQueue.
            if (!hasTasks()) {
                try {
                    if (curDeadlineNanos != prevDeadlineNanos) {
                        prevDeadlineNanos = curDeadlineNanos;
                        submissionQueue.addTimeout(deadlineToDelayNanos(curDeadlineNanos));
                        submissionQueue.submit();
                    }

                    // Check there were any completion events to process
                    if (completionQueue.process(this) == -1) {
                        // Block if there is nothing to process after this try again to call process(....)
                        logger.trace("ioUringWaitCqe {}", this.toString());
                        completionQueue.ioUringWaitCqe();
                    }
                } catch (Throwable t) {
                    //Todo handle exception
                } finally {
                    if (nextWakeupNanos.get() == AWAKE || nextWakeupNanos.getAndSet(AWAKE) == AWAKE) {
                        pendingWakeup = true;
                    }
                }
            }

            completionQueue.process(this);

            // Always call runAllTasks() as it will also fetch the scheduled tasks that are ready.
            runAllTasks();

            submissionQueue.submit();
            try {
                if (isShuttingDown()) {
                    closeAll();
                    submissionQueue.submit();

                    if (confirmShutdown()) {
                        break;
                    }
                }
            } catch (Throwable t) {
                logger.info("Exception error: {}", t);
            }
        }
    }

    @Override
    public boolean handle(int fd, int res, long flags, int op, int pollMask) {
        IOUringSubmissionQueue submissionQueue = ringBuffer.getIoUringSubmissionQueue();
        final AbstractIOUringChannel channel;
        if (op == Native.IORING_OP_READ || op == Native.IORING_OP_ACCEPT) {
            channel = handleRead(fd, res);
        } else if (op == Native.IORING_OP_WRITEV || op == Native.IORING_OP_WRITE) {
            channel = handleWrite(fd, res);
        } else if (op == Native.IORING_OP_POLL_ADD) {
            if (eventfd.intValue() == fd) {
                if (res == Native.ERRNO_ECANCELED_NEGATIVE) {
                    return true;
                }
                channel = null;
                pendingWakeup = false;
                handleEventFd(submissionQueue);
            } else {
                channel = handlePollAdd(fd, res, pollMask);
            }
        } else if (op == Native.IORING_OP_POLL_REMOVE) {
            if (res == Errors.ERRNO_ENOENT_NEGATIVE) {
                logger.trace("IORING_POLL_REMOVE not successful");
            } else if (res == 0) {
                logger.trace("IORING_POLL_REMOVE successful");
            }

            channel = channels.get(fd);
            if (channel != null && !channel.ioScheduled()) {
                // We cancelled the POLL ops which means we are done and should remove the mapping.
                channels.remove(fd);
            }
        } else if (op == Native.IORING_OP_CONNECT) {
            channel = handleConnect(fd, res);
        } else if (op == Native.IORING_OP_TIMEOUT) {
            if (res == Native.ERRNO_ETIME_NEGATIVE) {
                prevDeadlineNanos = NONE;
            }
            channel = null;
        } else {
            return true;
        }
        if (channel != null) {
            ((AbstractIOUringChannel.AbstractUringUnsafe) channel.unsafe()).processDelayedClose();
        }
        return true;
    }

    private AbstractIOUringChannel handleRead(int fd, int res) {
        AbstractIOUringChannel readChannel = channels.get(fd);
        if (readChannel != null) {
            ((AbstractIOUringChannel.AbstractUringUnsafe) readChannel.unsafe()).readComplete(res);
        }
        return readChannel;
    }

    private AbstractIOUringChannel handleWrite(int fd, int res) {
        AbstractIOUringChannel writeChannel = channels.get(fd);
        if (writeChannel != null) {
            ((AbstractIOUringChannel.AbstractUringUnsafe) writeChannel.unsafe()).writeComplete(res);
        }
        return writeChannel;
    }

    private AbstractIOUringChannel handlePollAdd(int fd, int res, int pollMask) {
        AbstractIOUringChannel channel = channels.get(fd);
        if (channel != null) {
            if ((pollMask & Native.POLLOUT) != 0) {
                ((AbstractIOUringChannel.AbstractUringUnsafe) channel.unsafe()).pollOut(res);
            }
            if ((pollMask & Native.POLLIN) != 0) {
                ((AbstractIOUringChannel.AbstractUringUnsafe) channel.unsafe()).pollIn(res);
            }
            if ((pollMask & Native.POLLRDHUP) != 0) {
                ((AbstractIOUringChannel.AbstractUringUnsafe) channel.unsafe()).pollRdHup(res);
            }
        }
        return channel;
    }

    private void handleEventFd(IOUringSubmissionQueue submissionQueue) {
        // We need to consume the data as otherwise we would see another event
        // in the completionQueue without
        // an extra eventfd_write(....)
        Native.eventFdRead(eventfd.intValue());

        submissionQueue.addPollIn(eventfd.intValue());
    }

    private AbstractIOUringChannel handleConnect(int fd, int res) {
        AbstractIOUringChannel channel = channels.get(fd);
        if (channel != null) {
            ((AbstractIOUringChannel.AbstractUringUnsafe) channel.unsafe()).connectComplete(res);
        }
        return channel;
    }

    @Override
    protected void cleanup() {
        try {
            eventfd.close();
        } catch (IOException e) {
            logger.warn("Failed to close the event fd.", e);
        }
        ringBuffer.close();
        iovArrays.release();
    }

    public RingBuffer getRingBuffer() {
        return ringBuffer;
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop && nextWakeupNanos.getAndSet(AWAKE) != AWAKE) {
            // write to the evfd which will then wake-up epoll_wait(...)
            Native.eventFdWrite(eventfd.intValue(), 1L);
        }
    }

    public IovArray iovArray() {
        IovArray iovArray = iovArrays.next();
        if (iovArray == null) {
            ringBuffer.getIoUringSubmissionQueue().submit();
            iovArray = iovArrays.next();
            assert iovArray != null;
        }
        return iovArray;
    }
}
