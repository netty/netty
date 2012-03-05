/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import static io.netty.channel.Channels.fireChannelInterestChanged;
import io.netty.buffer.ChannelBuffer;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.MessageEvent;
import io.netty.channel.socket.nio.SocketSendBufferPool.SendBuffer;
import io.netty.util.internal.QueueFactory;
import io.netty.util.internal.ThreadLocalBoolean;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

abstract class AbstractNioChannel<C extends SelectableChannel & WritableByteChannel> extends AbstractChannel {

    /**
     * The {@link AbstractNioWorker}.
     */
    final AbstractNioWorker worker;

    /**
     * Monitor object to synchronize access to InterestedOps.
     */
    final Object interestOpsLock = new Object();

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
    final Queue<MessageEvent> writeBufferQueue = new WriteRequestQueue();

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
    
    protected AbstractNioChannel(Integer id, Channel parent, ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink, AbstractNioWorker worker, C ch) {
        super(id, parent, factory, pipeline, sink);
        this.worker = worker;
        this.channel = ch;
    }
    
    protected AbstractNioChannel(
            Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink, AbstractNioWorker worker, C ch)  {
        super(parent, factory, pipeline, sink);
        this.worker = worker;
        this.channel = ch;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        InetSocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress =
                    (InetSocketAddress) getLocalSocketAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        InetSocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress =
                    (InetSocketAddress) getRemoteSocketAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }
    
    @Override
    public abstract NioChannelConfig getConfig();
    
    int getRawInterestOps() {
        return super.getInterestOps();
    }

    void setRawInterestOpsNow(int interestOps) {
        super.setInterestOpsNow(interestOps);
    }

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return getUnsupportedOperationFuture();
        }
    }
    
    @Override
    public int getInterestOps() {
        if (!isOpen()) {
            return Channel.OP_WRITE;
        }

        int interestOps = getRawInterestOps();
        int writeBufferSize = this.writeBufferSize.get();
        if (writeBufferSize != 0) {
            if (highWaterMarkCounter.get() > 0) {
                int lowWaterMark = getConfig().getWriteBufferLowWaterMark();
                if (writeBufferSize >= lowWaterMark) {
                    interestOps |= Channel.OP_WRITE;
                } else {
                    interestOps &= ~Channel.OP_WRITE;
                }
            } else {
                int highWaterMark = getConfig().getWriteBufferHighWaterMark();
                if (writeBufferSize >= highWaterMark) {
                    interestOps |= Channel.OP_WRITE;
                } else {
                    interestOps &= ~Channel.OP_WRITE;
                }
            }
        } else {
            interestOps &= ~Channel.OP_WRITE;
        }

        return interestOps;
    }
    
    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }
    
    abstract InetSocketAddress getLocalSocketAddress() throws Exception;
    
    abstract InetSocketAddress getRemoteSocketAddress() throws Exception;
    
    private final class WriteRequestQueue implements BlockingQueue<MessageEvent> {
        private final ThreadLocalBoolean notifying = new ThreadLocalBoolean();

        private final BlockingQueue<MessageEvent> queue;

        public WriteRequestQueue() {
            this.queue = QueueFactory.createQueue(MessageEvent.class);
        }

        @Override
        public MessageEvent remove() {
            return queue.remove();
        }

        @Override
        public MessageEvent element() {
            return queue.element();
        }

        @Override
        public MessageEvent peek() {
            return queue.peek();
        }

        @Override
        public int size() {
            return queue.size();
        }

        @Override
        public boolean isEmpty() {
            return queue.isEmpty();
        }

        @Override
        public Iterator<MessageEvent> iterator() {
            return queue.iterator();
        }

        @Override
        public Object[] toArray() {
            return queue.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return queue.toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return queue.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends MessageEvent> c) {
            return queue.addAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return queue.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return queue.retainAll(c);
        }

        @Override
        public void clear() {
            queue.clear();        
        }

        @Override
        public boolean add(MessageEvent e) {
            return queue.add(e);
        }

        @Override
        public void put(MessageEvent e) throws InterruptedException {
            queue.put(e);
        }

        @Override
        public boolean offer(MessageEvent e, long timeout, TimeUnit unit) throws InterruptedException {
            return queue.offer(e, timeout, unit);
        }

        @Override
        public MessageEvent take() throws InterruptedException {
            return queue.take();
        }

        @Override
        public MessageEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }

        @Override
        public int remainingCapacity() {
            return queue.remainingCapacity();
        }

        @Override
        public boolean remove(Object o) {
            return queue.remove(o);
        }

        @Override
        public boolean contains(Object o) {
            return queue.contains(o);
        }

        @Override
        public int drainTo(Collection<? super MessageEvent> c) {
            return queue.drainTo(c);
        }

        @Override
        public int drainTo(Collection<? super MessageEvent> c, int maxElements) {
            return queue.drainTo(c, maxElements);
        }

        @Override
        public boolean offer(MessageEvent e) {
            boolean success = queue.offer(e);
            assert success;

            int messageSize = getMessageSize(e);
            int newWriteBufferSize = writeBufferSize.addAndGet(messageSize);
            int highWaterMark =  getConfig().getWriteBufferHighWaterMark();

            if (newWriteBufferSize >= highWaterMark) {
                if (newWriteBufferSize - messageSize < highWaterMark) {
                    highWaterMarkCounter.incrementAndGet();
                    if (!notifying.get()) {
                        notifying.set(Boolean.TRUE);
                        fireChannelInterestChanged(AbstractNioChannel.this);
                        notifying.set(Boolean.FALSE);
                    }
                }
            }
            return true;
        }

        @Override
        public MessageEvent poll() {
            MessageEvent e = queue.poll();
            if (e != null) {
                int messageSize = getMessageSize(e);
                int newWriteBufferSize = writeBufferSize.addAndGet(-messageSize);
                int lowWaterMark = getConfig().getWriteBufferLowWaterMark();

                if (newWriteBufferSize == 0 || newWriteBufferSize < lowWaterMark) {
                    if (newWriteBufferSize + messageSize >= lowWaterMark) {
                        highWaterMarkCounter.decrementAndGet();
                        if (isConnected() && !notifying.get()) {
                            notifying.set(Boolean.TRUE);
                            fireChannelInterestChanged(AbstractNioChannel.this);
                            notifying.set(Boolean.FALSE);
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

        @Override
        public void run() {
            writeTaskInTaskQueue.set(false);
            worker.writeFromTaskLoop(AbstractNioChannel.this);
        }
    }

}
