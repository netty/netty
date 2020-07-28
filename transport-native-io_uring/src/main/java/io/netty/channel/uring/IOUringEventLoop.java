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
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.LongObjectHashMap;

import java.util.Set;
import java.util.concurrent.Executor;

import static io.netty.channel.unix.Errors.*;

final class IOUringEventLoop extends SingleThreadEventLoop {

    // events should be unique to identify which event type that was
    private long eventIdCounter;
    private final LongObjectHashMap<Event> events = new LongObjectHashMap<Event>();
    private final IntObjectMap<AbstractIOUringChannel> channels = new IntObjectHashMap<AbstractIOUringChannel>(4096);
    private RingBuffer ringBuffer;

    IOUringEventLoop(final EventLoopGroup parent, final Executor executor, final boolean addTaskWakesUp) {
        super(parent, executor, addTaskWakesUp);
        ringBuffer = Native.createRingBuffer(32);
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
        for (;;) {
            final IOUringCompletionQueue ioUringCompletionQueue = ringBuffer.getIoUringCompletionQueue();
            final IOUringCqe ioUringCqe = ioUringCompletionQueue.peek(); // or waiting

            if (ioUringCqe != null) {
                final Event event = events.get(ioUringCqe.getEventId());
                System.out.println("Completion EventId: " + ioUringCqe.getEventId());

                if (event != null) {
                    switch (event.getOp()) {
                    case ACCEPT:
                        System.out.println("EventLoop Accept Res: " + ioUringCqe.getRes());
                        if (ioUringCqe.getRes() != -1 && ioUringCqe.getRes() != ERRNO_EAGAIN_NEGATIVE &&
                            ioUringCqe.getRes() != ERRNO_EWOULDBLOCK_NEGATIVE) {
                            AbstractIOUringServerChannel abstractIOUringServerChannel =
                                    (AbstractIOUringServerChannel) event.getAbstractIOUringChannel();
                            System.out.println("EventLoop Fd: " + abstractIOUringServerChannel.getSocket().intValue());
                            final IOUringRecvByteAllocatorHandle allocHandle =
                                    (IOUringRecvByteAllocatorHandle) event.getAbstractIOUringChannel().unsafe()
                                                                          .recvBufAllocHandle();
                            final ChannelPipeline pipeline = event.getAbstractIOUringChannel().pipeline();

                            allocHandle.lastBytesRead(ioUringCqe.getRes());
                            if (allocHandle.lastBytesRead() > 0) {
                                allocHandle.incMessagesRead(1);
                                try {
                                    pipeline.fireChannelRead(abstractIOUringServerChannel
                                                                     .newChildChannel(allocHandle.lastBytesRead()));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                allocHandle.readComplete();
                                pipeline.fireChannelReadComplete();
                            }
                        }
                        long eventId = incrementEventIdCounter();
                        event.setId(eventId);
                        ringBuffer.getIoUringSubmissionQueue()
                                  .add(eventId, EventType.ACCEPT, event.getAbstractIOUringChannel()
                                       .getSocket().intValue(),
                                       0,
                                       0,
                                       0);
                        addNewEvent(event);
                        ringBuffer.getIoUringSubmissionQueue().submit();
                        break;
                    case READ:
                        System.out.println("EventLoop Read Res: " + ioUringCqe.getRes());
                        System.out.println("EventLoop Fd: " + event.getAbstractIOUringChannel().getSocket().intValue());
                        ByteBuf byteBuf = event.getReadBuffer();
                        int localReadAmount = ioUringCqe.getRes();
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
                        System.out.println("EventLoop Write Res: " + ioUringCqe.getRes());
                        System.out.println("EventLoop Fd: " + event.getAbstractIOUringChannel().getSocket().intValue());
                        System.out.println("EventLoop Pipeline: " + event.getAbstractIOUringChannel().eventLoop());
                        ChannelOutboundBuffer channelOutboundBuffer = event
                                .getAbstractIOUringChannel().unsafe().outboundBuffer();
                        //remove bytes
                        int localFlushAmount = ioUringCqe.getRes();
                        if (localFlushAmount > 0) {
                            channelOutboundBuffer.removeBytes(localFlushAmount);
                        }
                        try {
                            event.getAbstractIOUringChannel().doWrite(channelOutboundBuffer);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }
            //run tasks
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
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public RingBuffer getRingBuffer() {
        return ringBuffer;
    }
}
