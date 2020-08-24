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
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.LongObjectHashMap;

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
    private static long ETIME = -62;

    // nextWakeupNanos is:
    //    AWAKE            when EL is awake
    //    NONE             when EL is waiting with no wakeup scheduled
    //    other value T    when EL is waiting with wakeup scheduled at time T
    private final AtomicLong nextWakeupNanos = new AtomicLong(AWAKE);
    private final FileDescriptor eventfd;

    private long prevDeadlineNanos = NONE;
    private boolean pendingWakeup;
    //private final FileDescriptor eventFd;

    IOUringEventLoop(final EventLoopGroup parent, final Executor executor, final boolean addTaskWakesUp) {
        super(parent, executor, addTaskWakesUp);
        ringBuffer = Native.createRingBuffer(32);
        eventfd = Native.newEventFd();
        long eventId = incrementEventIdCounter();
        Event event = new Event();
        event.setOp(EventType.POLL_EVENTFD);
        event.setId(eventId);
        addNewEvent(event);
        ringBuffer.getIoUringSubmissionQueue().addPoll(eventId, eventfd.intValue(), event.getOp());
        ringBuffer.getIoUringSubmissionQueue().submit();
    }

    public long incrementEventIdCounter() {
        long eventId = eventIdCounter;
        System.out.println(" incrementEventIdCounter EventId: " + eventId);
        eventIdCounter++;
        return eventId;
    }

    public void add(AbstractIOUringChannel ch) {
        System.out.println("Add Channel: " + ch.socket.intValue());
        int fd = ch.socket.intValue();

        channels.put(fd, ch);
    }

    public void remove(AbstractIOUringChannel ch) {
        System.out.println("Remove Channel: " + ch.socket.intValue());
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
        System.out.println("CloseAll IOUringEvenloop");
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
            long curDeadlineNanos = nextScheduledTaskDeadlineNanos();
            if (curDeadlineNanos == -1L) {
                curDeadlineNanos = NONE; // nothing on the calendar
            }
            nextWakeupNanos.set(curDeadlineNanos);
            long ioStartTime = 0;

            if (!hasTasks()) {
                try {
                    if (curDeadlineNanos != prevDeadlineNanos) {
                        prevDeadlineNanos = curDeadlineNanos;
                        Event event = new Event();
                        long eventId = incrementEventIdCounter();
                        event.setId(eventId);
                        event.setOp(EventType.TIMEOUT);
                        addNewEvent(event);
                        submissionQueue.addTimeout(curDeadlineNanos, eventId);
                    }
                    final IOUringCqe ioUringCqe = completionQueue.ioUringWaitCqe();
                    if (ioUringCqe != null) {
                        final Event event = events.get(ioUringCqe.getEventId());
                        System.out.println("Completion EventId: " + ioUringCqe.getEventId());
                        ioStartTime = System.nanoTime();
                        if (event != null) {
                            processEvent(ioUringCqe.getRes(), event);
                        }
                    }
                } finally {

                    if (nextWakeupNanos.get() == AWAKE || nextWakeupNanos.getAndSet(AWAKE) == AWAKE) {
                        pendingWakeup = true;
                    }
                }
            }

            //Todo ioRatio?
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
                System.out.println("Exception error " + t);
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
            System.out.println("EventLoop Read Res: " + res);
            System.out.println("EventLoop Fd: " + event.getAbstractIOUringChannel().getSocket().intValue());
            ByteBuf byteBuf = event.getReadBuffer();
            int localReadAmount = res;
            if (localReadAmount > 0) {
                byteBuf.writerIndex(byteBuf.writerIndex() + localReadAmount);
            }

            final IOUringRecvByteAllocatorHandle allocHandle =
                    (IOUringRecvByteAllocatorHandle) event.getAbstractIOUringChannel().unsafe()
                                                          .recvBufAllocHandle();
            final ChannelPipeline pipeline = event.getAbstractIOUringChannel().pipeline();

            allocHandle.lastBytesRead(localReadAmount);
            if (allocHandle.lastBytesRead() <= 0) {
                // nothing was read, release the buffer.
                byteBuf.release();
                byteBuf = null;
                break;
            }

            allocHandle.incMessagesRead(1);
            //readPending = false;
            pipeline.fireChannelRead(byteBuf);
            byteBuf = null;
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
            event.getAbstractIOUringChannel().executeReadEvent();
        break;
        case WRITE:
            System.out.println("EventLoop Write Res: " + res);
            System.out.println("EventLoop Fd: " + event.getAbstractIOUringChannel().getSocket().intValue());
            System.out.println("EventLoop Pipeline: " + event.getAbstractIOUringChannel().eventLoop());
            ChannelOutboundBuffer channelOutboundBuffer = event
                    .getAbstractIOUringChannel().unsafe().outboundBuffer();
            //remove bytes
            int localFlushAmount = res;
            if (localFlushAmount > 0) {
                channelOutboundBuffer.removeBytes(localFlushAmount);
            }

            try {
                event.getAbstractIOUringChannel().doWrite(channelOutboundBuffer);
            } catch (Exception e) {
                e.printStackTrace();
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
        case POLL_LINK:
            //Todo error handling error
            logger.info("POLL_LINK Res: {}", res);
            break;
        case POLL_RDHUP:
            if (!event.getAbstractIOUringChannel().isActive()) {
               event.getAbstractIOUringChannel().shutdownInput(true);
            }
            break;
        }
        this.events.remove(event.getId());
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
