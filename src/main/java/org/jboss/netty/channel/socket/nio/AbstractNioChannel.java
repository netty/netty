/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.nio.SocketSendBufferPool.SendBuffer;
import org.jboss.netty.util.internal.ThreadLocalBoolean;

import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jboss.netty.channel.Channels.*;
import static org.jboss.netty.channel.socket.nio.AbstractNioWorker.isIoThread;

abstract class AbstractNioChannel<C extends SelectableChannel & WritableByteChannel> extends AbstractChannel {

    /**
     * The {@link AbstractNioWorker}.
     */
    final AbstractNioWorker worker;

    /**
     * Monitor object for synchronizing access to the {@link WriteRequestQueue}.
     */
    final Object writeLock = new Object();

    /**
     * WriteTask that performs write operations.
     */
    final Runnable writeTask = new WriteTask();

    /**
     * Indicates if there is a {@link WriteTask} in the task queue.
     */
    final AtomicBoolean writeTaskInTaskQueue = new AtomicBoolean();

    /**
     * Queue of write {@link MessageEvent}s.
     */
    final WriteRequestQueue writeBufferQueue = new WriteRequestQueue();

    /**
     * Keeps track of the number of bytes that the {@link WriteRequestQueue} currently
     * contains.
     */
    final AtomicInteger writeBufferSize = new AtomicInteger();

    /**
     * Keeps track of the highWaterMark.
     */
    final AtomicInteger highWaterMarkCounter = new AtomicInteger();

    /**
     * The current write {@link MessageEvent}
     */
    MessageEvent currentWriteEvent;
    SendBuffer currentWriteBuffer;

    /**
     * Boolean that indicates that write operation is in progress.
     */
    boolean inWriteNowLoop;
    boolean writeSuspended;

    private volatile InetSocketAddress localAddress;
    volatile InetSocketAddress remoteAddress;

    final C channel;

    protected AbstractNioChannel(
            Integer id, Channel parent, ChannelFactory factory, ChannelPipeline pipeline,
            ChannelSink sink, AbstractNioWorker worker, C ch) {
        super(id, parent, factory, pipeline, sink);
        this.worker = worker;
        channel = ch;
    }

    protected AbstractNioChannel(
            Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink, AbstractNioWorker worker, C ch)  {
        super(parent, factory, pipeline, sink);
        this.worker = worker;
        channel = ch;
    }

    /**
     * Return the {@link AbstractNioWorker} that handle the IO of the
     * {@link AbstractNioChannel}
     *
     * @return worker
     */
    public AbstractNioWorker getWorker() {
        return worker;
    }

    public InetSocketAddress getLocalAddress() {
        InetSocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                localAddress = getLocalSocketAddress();
                if (localAddress.getAddress().isAnyLocalAddress()) {
                    // Don't cache on a wildcard address so the correct one
                    // will be cached once the channel is connected/bound
                    return localAddress;
                }
                this.localAddress = localAddress;
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    public InetSocketAddress getRemoteAddress() {
        InetSocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress =
                    getRemoteSocketAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    public abstract NioChannelConfig getConfig();

    @Override
    protected int getInternalInterestOps() {
        return super.getInternalInterestOps();
    }

    @Override
    protected void setInternalInterestOps(int interestOps) {
        super.setInternalInterestOps(interestOps);
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    abstract InetSocketAddress getLocalSocketAddress() throws Exception;

    abstract InetSocketAddress getRemoteSocketAddress() throws Exception;

    final class WriteRequestQueue {
        private final ThreadLocalBoolean notifying = new ThreadLocalBoolean();

        private final Queue<MessageEvent> queue;

        public WriteRequestQueue() {
            queue = new ConcurrentLinkedQueue<MessageEvent>();
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public boolean offer(MessageEvent e) {
            boolean success = queue.offer(e);
            assert success;

            int messageSize = getMessageSize(e);
            int newWriteBufferSize = writeBufferSize.addAndGet(messageSize);
            final int highWaterMark =  getConfig().getWriteBufferHighWaterMark();

            if (newWriteBufferSize >= highWaterMark) {
                if (newWriteBufferSize - messageSize < highWaterMark) {
                    highWaterMarkCounter.incrementAndGet();
                    if (setUnwritable()) {
                        if (isIoThread(AbstractNioChannel.this)) {
                            if (!notifying.get()) {
                                notifying.set(Boolean.TRUE);
                                fireChannelInterestChanged(AbstractNioChannel.this);
                                notifying.set(Boolean.FALSE);
                            }
                        } else {
                            // Adjusting writability requires careful synchronization.
                            //
                            // Consider for instance:
                            //
                            // T1 repeated offer: go above high water mark
                            //      context switch *before* calling setUnwritable
                            // T2 repeated poll: go under low water mark
                            //      context switch *after* calling setWritable
                            // T1 setUnwritable
                            //
                            // At this point the channel is incorrectly marked unwritable and would
                            // remain so until the high water mark were exceeded again, which may
                            // never happen if the application did control flow based on writability.
                            //
                            // The simplest solution would be to use a mutex to protect both the
                            // buffer size and the writability bit, however that would impose a
                            // serious performance penalty.
                            //
                            // A better approach would be to always call setUnwritable in the io
                            // thread, which does not impact performance as the interestChanged
                            // event has to be fired from there anyway.
                            //
                            // However this could break code which expects isWritable to immediately
                            // be updated after a write() from a non-io thread.
                            //
                            // Instead, we re-check the buffer size before firing the interest
                            // changed event and revert the change to the writability bit if needed.
                            worker.executeInIoThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (writeBufferSize.get() >= highWaterMark || setWritable()) {
                                        fireChannelInterestChanged(AbstractNioChannel.this);
                                    }
                                }
                            });
                        }
                    }
                }
            }
            return true;
        }

        public MessageEvent poll() {
            MessageEvent e = queue.poll();
            if (e != null) {
                int messageSize = getMessageSize(e);
                int newWriteBufferSize = writeBufferSize.addAndGet(-messageSize);
                int lowWaterMark = getConfig().getWriteBufferLowWaterMark();

                if (newWriteBufferSize == 0 || newWriteBufferSize < lowWaterMark) {
                    if (newWriteBufferSize + messageSize >= lowWaterMark) {
                        highWaterMarkCounter.decrementAndGet();
                        if (isConnected()) {
                            // write events are only ever processed from the channel io thread
                            // except when cleaning up the buffer, which only happens on close()
                            // changing that would require additional synchronization around
                            // writeBufferSize and writability changes.
                            assert isIoThread(AbstractNioChannel.this);
                            if (setWritable()) {
                                notifying.set(Boolean.TRUE);
                                fireChannelInterestChanged(AbstractNioChannel.this);
                                notifying.set(Boolean.FALSE);
                            }
                        }
                    }
                }
            }
            return e;
        }

        private int getMessageSize(MessageEvent e) {
            Object m = e.getMessage();
            if (m instanceof ChannelBuffer) {
                return ((ChannelBuffer) m).readableBytes();
            }
            return 0;
        }
    }

    private final class WriteTask implements Runnable {

        WriteTask() {
        }

        public void run() {
            writeTaskInTaskQueue.set(false);
            worker.writeFromTaskLoop(AbstractNioChannel.this);
        }
    }

}
