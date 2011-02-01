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
package org.jboss.netty.channel.socket.nio;

import static org.jboss.netty.channel.Channels.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.socket.nio.SocketSendBufferPool.SendBuffer;
import org.jboss.netty.util.internal.LinkedTransferQueue;
import org.jboss.netty.util.internal.ThreadLocalBoolean;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2202 $, $Date: 2010-02-23 16:18:58 +0900 (Tue, 23 Feb 2010) $
 *
 */
class NioSocketChannel extends AbstractChannel
                                implements org.jboss.netty.channel.socket.SocketChannel {

    private static final int ST_OPEN = 0;
    private static final int ST_BOUND = 1;
    private static final int ST_CONNECTED = 2;
    private static final int ST_CLOSED = -1;
    volatile int state = ST_OPEN;

    final SocketChannel socket;
    final NioWorker worker;
    private final NioSocketChannelConfig config;
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

    public NioSocketChannel(
            Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink,
            SocketChannel socket, NioWorker worker) {
        super(parent, factory, pipeline, sink);

        this.socket = socket;
        this.worker = worker;
        config = new DefaultNioSocketChannelConfig(socket.socket());

        // TODO Move the state variable to AbstractChannel so that we don't need
        //      to add many listeners.
        getCloseFuture().addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                state = ST_CLOSED;
            }
        });
    }

    public NioSocketChannelConfig getConfig() {
        return config;
    }

    public InetSocketAddress getLocalAddress() {
        InetSocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress =
                    (InetSocketAddress) socket.socket().getLocalSocketAddress();
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
                    (InetSocketAddress) socket.socket().getRemoteSocketAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    @Override
    public boolean isOpen() {
        return state >= ST_OPEN;
    }

    public boolean isBound() {
        return state >= ST_BOUND;
    }

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
                        fireChannelInterestChanged(NioSocketChannel.this);
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
                            fireChannelInterestChanged(NioSocketChannel.this);
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

        public void run() {
            writeTaskInTaskQueue.set(false);
            worker.writeFromTaskLoop(NioSocketChannel.this);
        }
    }
}
