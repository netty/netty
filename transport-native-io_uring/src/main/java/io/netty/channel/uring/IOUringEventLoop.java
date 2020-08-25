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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty.channel.unix.Errors.*;

final class IOUringEventLoop extends SingleThreadEventLoop {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IOUringEventLoop.class);

    //Todo set config ring buffer size
    private final int ringSize = 32;

    //just temporary -> Todo use ErrorsStaticallyReferencedJniMethods like in Epoll
    private final int SOCKET_ERROR_EPIPE = -32;
    private static long ETIME = -62;

    // events should be unique to identify which event type that was
    private long eventIdCounter;
    private final LongObjectHashMap<Event> events = new LongObjectHashMap<Event>();
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

    private long prevDeadlineNanos = NONE;
    private boolean pendingWakeup;

    IOUringEventLoop(final EventLoopGroup parent, final Executor executor, final boolean addTaskWakesUp) {
        super(parent, executor, addTaskWakesUp);

        ringBuffer = Native.createRingBuffer(ringSize);
        eventfd = Native.newEventFd();
        long eventId = incrementEventIdCounter();
        Event event = new Event();
        event.setOp(EventType.POLL_EVENTFD);
        event.setId(eventId);
        addNewEvent(event);
        ringBuffer.getIoUringSubmissionQueue().addPoll(eventId, eventfd.intValue(), event.getOp());
        ringBuffer.getIoUringSubmissionQueue().submit();
        logger.info("New EventLoop: {}", this.toString());
    }

    public long incrementEventIdCounter() {
        long eventId = eventIdCounter;
        logger.info("incrementEventIdCounter EventId: {}", eventId);
        eventIdCounter++;
        return eventId;
    }

    public void add(AbstractIOUringChannel ch) {
        logger.info("Add Channel: {} ", ch.socket.intValue());
        int fd = ch.socket.intValue();

        channels.put(fd, ch);
    }

    public void remove(AbstractIOUringChannel ch) {
        logger.info("Remove Channel: {}", ch.socket.intValue());
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
        logger.info("CloseAll IOUringEvenloop");
        // Using the intermediate collection to prevent ConcurrentModificationException.
        // In the `close()` method, the channel is deleted from `channels` map.
        AbstractIOUringChannel[] localChannels = channels.values().toArray(new AbstractIOUringChannel[0]);

        for (AbstractIOUringChannel ch : localChannels) {
            ch.unsafe().close(ch.unsafe().voidPromise());
        }
    }

    public void addNewEvent(Event event) {
        events.put(event.getId(), event);
    }

    @Override
    protected void run() {
        final IOUringCompletionQueue completionQueue = ringBuffer.getIoUringCompletionQueue();
        final IOUringSubmissionQueue submissionQueue = ringBuffer.getIoUringSubmissionQueue();
        for (;;) {
            logger.info("Run IOUringEventLoop {}", this.toString());
            long curDeadlineNanos = nextScheduledTaskDeadlineNanos();
            if (curDeadlineNanos == -1L) {
                curDeadlineNanos = NONE; // nothing on the calendar
            }
            nextWakeupNanos.set(curDeadlineNanos);
            IOUringCqe ioUringCqe;

            // Only submit a timeout if there are no tasks to process and do a blocking operation
            // on the completionQueue.
            if (!hasTasks()) {
                try {
                    if (curDeadlineNanos != prevDeadlineNanos) {
                        prevDeadlineNanos = curDeadlineNanos;
                        Event event = new Event();
                        long eventId = incrementEventIdCounter();
                        event.setId(eventId);
                        event.setOp(EventType.TIMEOUT);
                        addNewEvent(event);
                        submissionQueue.addTimeout(deadlineToDelayNanos(curDeadlineNanos), eventId);
                        submissionQueue.submit();
                    }

                    // Block if there is nothing to process.
                    logger.debug("ioUringWaitCqe {}", this.toString());
                    ioUringCqe = completionQueue.ioUringWaitCqe();
                } finally {

                    if (nextWakeupNanos.get() == AWAKE || nextWakeupNanos.getAndSet(AWAKE) == AWAKE) {
                        pendingWakeup = true;
                    }
                }
            } else {
                // Just poll as there are tasks to process so we don't want to block.
                logger.debug("poll {}", this.toString());
                ioUringCqe = completionQueue.poll();
            }

            while (ioUringCqe != null) {
                final Event event = events.get(ioUringCqe.getEventId());

                if (event != null) {
                    logger.debug("EventType Incoming: " + event.getOp().name());
                    processEvent(ioUringCqe.getRes(), event);
                }

                // Process one entry after the other until there are none left. This will ensure we process
                // all of these before we try to consume tasks.
                ioUringCqe = completionQueue.poll();
                logger.debug("poll {}", this.toString());
            }

            if (hasTasks()) {
                runAllTasks();
            }

            try {
                if (isShuttingDown()) {
                    closeAll();
                    if (confirmShutdown()) {
                        break;
                    }
                }
            } catch (Throwable t) {
                logger.info("Exception error: {}", t);
            }
        }
    }

    private void processEvent(final int res, final Event event) {
        IOUringSubmissionQueue submissionQueue = ringBuffer.getIoUringSubmissionQueue();
        switch (event.getOp()) {
        case ACCEPT:
            logger.info("EventLoop Accept filedescriptor: {}", res);
            event.getAbstractIOUringChannel().setUringInReadyPending(false);
            if (res != -1 && res != ERRNO_EAGAIN_NEGATIVE &&
                res != ERRNO_EWOULDBLOCK_NEGATIVE) {
                AbstractIOUringServerChannel abstractIOUringServerChannel =
                        (AbstractIOUringServerChannel) event.getAbstractIOUringChannel();
                logger.info("server filedescriptor Fd: {}", abstractIOUringServerChannel.getSocket().intValue());
                final IOUringRecvByteAllocatorHandle allocHandle =
                        (IOUringRecvByteAllocatorHandle) event.getAbstractIOUringChannel().unsafe()
                                                              .recvBufAllocHandle();
                final ChannelPipeline pipeline = event.getAbstractIOUringChannel().pipeline();

                allocHandle.lastBytesRead(res);
                if (allocHandle.lastBytesRead() > 0) {
                    allocHandle.incMessagesRead(1);
                    try {
                        final Channel childChannel =
                                abstractIOUringServerChannel.newChildChannel(allocHandle.lastBytesRead());
                        pipeline.fireChannelRead(childChannel);
                        pollRdHup((AbstractIOUringChannel) childChannel);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    allocHandle.readComplete();
                    pipeline.fireChannelReadComplete();
                }
            }

            //Todo refactoring method name
            event.getAbstractIOUringChannel().executeReadEvent();
            break;
        case READ:
            boolean close = false;
            ByteBuf byteBuf = null;
            int localReadAmount = res;
            final IOUringRecvByteAllocatorHandle allocHandle =
                    (IOUringRecvByteAllocatorHandle) event.getAbstractIOUringChannel().unsafe()
                                                          .recvBufAllocHandle();
            final ChannelPipeline pipeline = event.getAbstractIOUringChannel().pipeline();
            try {
                logger.info("EventLoop Read Res: {}", res);
                logger.info("EventLoop Fd: {}", event.getAbstractIOUringChannel().getSocket().intValue());
                event.getAbstractIOUringChannel().setUringInReadyPending(false);
                byteBuf = event.getReadBuffer();
                if (localReadAmount > 0) {
                    byteBuf.writerIndex(byteBuf.writerIndex() + localReadAmount);
                }

                allocHandle.lastBytesRead(localReadAmount);
                if (allocHandle.lastBytesRead() <= 0) {
                    // nothing was read, release the buffer.
                    byteBuf.release();
                    byteBuf = null;
                    close = allocHandle.lastBytesRead() < 0;
                    if (close) {
                        // There is nothing left to read as we received an EOF.
                        event.getAbstractIOUringChannel().shutdownInput(false);
                    }
                    allocHandle.readComplete();
                    pipeline.fireChannelReadComplete();
                    break;
                }

                allocHandle.incMessagesRead(1);
                pipeline.fireChannelRead(byteBuf);
                byteBuf = null;
                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();

                logger.info("READ autoRead {}", event.getAbstractIOUringChannel().config().isAutoRead());
                if (event.getAbstractIOUringChannel().config().isAutoRead()) {
                    event.getAbstractIOUringChannel().executeReadEvent();
                }
            } catch (Throwable t) {
                handleReadException(event.getAbstractIOUringChannel(), pipeline, byteBuf, t, close, allocHandle);
            }
            break;
        case WRITE:
            //localFlushAmount -> res
            logger.info("EventLoop Write Res: {}", res);
            logger.info("EventLoop Fd: {}", event.getAbstractIOUringChannel().getSocket().intValue());
            ChannelOutboundBuffer channelOutboundBuffer = event
                    .getAbstractIOUringChannel().unsafe().outboundBuffer();
            AbstractIOUringChannel channel = event.getAbstractIOUringChannel();

            if (res == SOCKET_ERROR_EPIPE) {
                event.getAbstractIOUringChannel().shutdownInput(false);
                break;
            }

            if (res > 0) {
                channelOutboundBuffer.removeBytes(res);
                channel.setWriteable(true);
                try {
                    event.getAbstractIOUringChannel().doWrite(channelOutboundBuffer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            break;
        case TIMEOUT:
            if (res == ETIME) {
                prevDeadlineNanos = NONE;
            }

            break;
        case POLL_EVENTFD:
            pendingWakeup = false;
            //Todo eventId is already used
            long eventId = incrementEventIdCounter();
            event.setId(eventId);
            event.setOp(EventType.POLL_EVENTFD);
            addNewEvent(event);
            submissionQueue.addPoll(eventId, eventfd.intValue(), event.getOp());
            // Submit so its picked up
            submissionQueue.submit();
            break;
        case POLL_LINK:
            //Todo error handling error
            logger.info("POLL_LINK Res: {}", res);
            break;
        case POLL_RDHUP:
            if (!event.getAbstractIOUringChannel().isActive()) {
               event.getAbstractIOUringChannel().shutdownInput(true);
            }
            break;
        case POLL_OUT:
            logger.info("POLL_OUT Res: {}", res);
            break;
        }
        this.events.remove(event.getId());
    }

    @Override
    protected void cleanup() {
        try {
            eventfd.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        ringBuffer.close();
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

    private void handleReadException(AbstractIOUringChannel channel, ChannelPipeline pipeline, ByteBuf byteBuf,
                                     Throwable cause, boolean close,
                                     IOUringRecvByteAllocatorHandle allocHandle) {
        if (byteBuf != null) {
            if (byteBuf.isReadable()) {
                pipeline.fireChannelRead(byteBuf);
            } else {
                byteBuf.release();
            }
        }
        allocHandle.readComplete();
        pipeline.fireChannelReadComplete();
        pipeline.fireExceptionCaught(cause);
        if (close || cause instanceof IOException) {
            channel.shutdownInput(false);
        } else {
            if (channel.config().isAutoRead()) {
                channel.executeReadEvent();
            }
        }
    }

   //to be notified when the filedesciptor is closed
    private void pollRdHup(AbstractIOUringChannel channel) {
        //all childChannels should poll POLLRDHUP
        long eventId = incrementEventIdCounter();
        Event event = new Event();
        event.setOp(EventType.POLL_RDHUP);
        event.setId(eventId);
        event.setAbstractIOUringChannel(channel);
        addNewEvent(event);
        ringBuffer.getIoUringSubmissionQueue().addPoll(eventId, channel.socket.intValue(), event.getOp());
        ringBuffer.getIoUringSubmissionQueue().submit();
    }
}
