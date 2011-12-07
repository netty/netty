/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.sctp;

import com.sun.nio.sctp.Association;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.sctp.SctpSendBufferPool.SendBuffer;
import org.jboss.netty.util.internal.ThreadLocalBoolean;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jboss.netty.channel.Channels.fireChannelInterestChanged;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://github.com/jestan">Jestan Nirojan</a>
 *
 */
class SctpChannelImpl extends AbstractChannel implements SctpChannel {

    private static final int ST_OPEN = 0;
    private static final int ST_BOUND = 1;
    private static final int ST_CONNECTED = 2;
    private static final int ST_CLOSED = -1;
    volatile int state = ST_OPEN;

    final com.sun.nio.sctp.SctpChannel underlayingChannel;
    final SctpWorker worker;
    private final NioSctpChannelConfig config;
    private volatile InetSocketAddress localAddress;
    private volatile InetSocketAddress remoteAddress;

    final Object interestOpsLock = new Object();
    final Object writeLock = new Object();

    final Runnable writeTask = new WriteTask();
    final AtomicBoolean writeTaskInTaskQueue = new AtomicBoolean();

    final Queue<MessageEvent> writeBuffer = new WriteRequestQueue();
    final AtomicInteger writeBufferSize = new AtomicInteger();
    final AtomicInteger highWaterMarkCounter = new AtomicInteger();
    boolean inWriteNowLoop;
    boolean writeSuspended;

    MessageEvent currentWriteEvent;
    SendBuffer currentWriteBuffer;

    public SctpChannelImpl(Channel parent, ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink,
                           com.sun.nio.sctp.SctpChannel underlayingChannel, SctpWorker worker) {
        super(parent, factory, pipeline, sink);

        this.underlayingChannel = underlayingChannel;
        this.worker = worker;
        config = new DefaultNioSctpChannelConfig(underlayingChannel);

        getCloseFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                state = ST_CLOSED;
            }
        });
    }

    @Override
    public NioSctpChannelConfig getConfig() {
        return config;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        InetSocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                final Iterator<SocketAddress> iterator = underlayingChannel.getAllLocalAddresses().iterator();
                if (iterator.hasNext()) {
                    this.localAddress = localAddress = (InetSocketAddress) iterator.next();
                }
            } catch (Throwable t) {
                return null;
            }
        }
        return localAddress;
    }

    @Override
    public Set<InetSocketAddress> getAllLocalAddresses() {
            try {
                final Set<SocketAddress> allLocalAddresses = underlayingChannel.getAllLocalAddresses();
                final Set<InetSocketAddress> addresses = new HashSet<InetSocketAddress>(allLocalAddresses.size());
                for(SocketAddress socketAddress: allLocalAddresses) {
                    addresses.add((InetSocketAddress) socketAddress);
                }
                return addresses;
            } catch (Throwable t) {
                return Collections.emptySet();
            }
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        InetSocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                final Iterator<SocketAddress> iterator = underlayingChannel.getRemoteAddresses().iterator();
                if (iterator.hasNext()) {
                    this.remoteAddress = remoteAddress = (InetSocketAddress) iterator.next();
                }
            } catch (Throwable t) {
                return null;
            }
        }
        return remoteAddress;
    }

    @Override
    public Set<InetSocketAddress> getRemoteAddresses() {
            try {
                final Set<SocketAddress> allLocalAddresses = underlayingChannel.getRemoteAddresses();
                final Set<InetSocketAddress> addresses = new HashSet<InetSocketAddress>(allLocalAddresses.size());
                for(SocketAddress socketAddress: allLocalAddresses) {
                    addresses.add((InetSocketAddress) socketAddress);
                }
                return addresses;
            } catch (Throwable t) {
                return Collections.emptySet();
            }
    }

    @Override
    public Association association() {
        try {
            return underlayingChannel.association();
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public boolean isOpen() {
        return state >= ST_OPEN;
    }

    @Override
    public boolean isBound() {
        return state >= ST_BOUND;
    }

    @Override
    public boolean isConnected() {
        return state == ST_CONNECTED;
    }

    final void setBound() {
        assert state == ST_OPEN : "Invalid state: " + state;
        state = ST_BOUND;
    }

    final void setConnected() {
        if (state != ST_CLOSED) {
            state = ST_CONNECTED;
        }
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
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

    private final class WriteRequestQueue extends LinkedTransferQueue<MessageEvent> {

        private static final long serialVersionUID = -246694024103520626L;

        private final ThreadLocalBoolean notifying = new ThreadLocalBoolean();

        WriteRequestQueue() {
            super();
        }

        @Override
        public boolean offer(MessageEvent e) {
            boolean success = super.offer(e);
            assert success;

            int messageSize = getMessageSize(e);
            int newWriteBufferSize = writeBufferSize.addAndGet(messageSize);
            int highWaterMark = getConfig().getWriteBufferHighWaterMark();

            if (newWriteBufferSize >= highWaterMark) {
                if (newWriteBufferSize - messageSize < highWaterMark) {
                    highWaterMarkCounter.incrementAndGet();
                    if (!notifying.get()) {
                        notifying.set(Boolean.TRUE);
                        fireChannelInterestChanged(SctpChannelImpl.this);
                        notifying.set(Boolean.FALSE);
                    }
                }
            }
            return true;
        }

        @Override
        public MessageEvent poll() {
            MessageEvent e = super.poll();
            if (e != null) {
                int messageSize = getMessageSize(e);
                int newWriteBufferSize = writeBufferSize.addAndGet(-messageSize);
                int lowWaterMark = getConfig().getWriteBufferLowWaterMark();

                if (newWriteBufferSize == 0 || newWriteBufferSize < lowWaterMark) {
                    if (newWriteBufferSize + messageSize >= lowWaterMark) {
                        highWaterMarkCounter.decrementAndGet();
                        if (isConnected() && !notifying.get()) {
                            notifying.set(Boolean.TRUE);
                            fireChannelInterestChanged(SctpChannelImpl.this);
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
            super();
        }

        @Override
        public void run() {
            writeTaskInTaskQueue.set(false);
            worker.writeFromTaskLoop(SctpChannelImpl.this);
        }
    }
}
