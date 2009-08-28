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
package org.jboss.netty.channel.xnio;

import static org.jboss.netty.channel.Channels.*;

import java.net.SocketAddress;
import java.nio.channels.GatheringByteChannel;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.internal.LinkedTransferQueue;
import org.jboss.netty.util.internal.ThreadLocalBoolean;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.ConnectedChannel;
import org.jboss.xnio.channels.MultipointWritableMessageChannel;
import org.jboss.xnio.channels.WritableMessageChannel;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev: 937 $, $Date: 2009-02-25 19:43:03 +0900 (Wed, 25 Feb 2009) $
 */
@SuppressWarnings("unchecked")
class BaseXnioChannel extends AbstractChannel implements XnioChannel {

    private final XnioChannelConfig config;
    volatile java.nio.channels.Channel xnioChannel;
    private volatile SocketAddress localAddress;
    private volatile SocketAddress remoteAddress;

    final Object writeLock = new Object();
    final Queue<MessageEvent> writeBuffer = new WriteBuffer();
    final AtomicInteger writeBufferSize = new AtomicInteger();
    final AtomicInteger highWaterMarkCounter = new AtomicInteger();
    MessageEvent currentWriteEvent;
    int currentWriteIndex;

    BaseXnioChannel(
            Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink,
            XnioChannelConfig config) {
        super(parent, factory, pipeline, sink);
        this.config = config;
    }

    public XnioChannelConfig getConfig() {
        return config;
    }

    public SocketAddress getLocalAddress() {
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            java.nio.channels.Channel xnioChannel = this.xnioChannel;
            if (!isOpen() || !(xnioChannel instanceof BoundChannel)) {
                return null;
            }

            this.localAddress = localAddress =
                (SocketAddress) ((BoundChannel) xnioChannel).getLocalAddress();
        }
        return localAddress;
    }

    public SocketAddress getRemoteAddress() {
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            java.nio.channels.Channel xnioChannel = this.xnioChannel;
            if (!isOpen() || !(xnioChannel instanceof ConnectedChannel)) {
                return null;
            }

            this.remoteAddress = remoteAddress =
                (SocketAddress) ((ConnectedChannel) xnioChannel).getPeerAddress();
        }
        return remoteAddress;
    }

    public boolean isBound() {
        return getLocalAddress() != null;
    }

    public boolean isConnected() {
        return getRemoteAddress() != null;
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
    public ChannelFuture write(Object message) {
        java.nio.channels.Channel xnioChannel = this.xnioChannel;
        if (xnioChannel instanceof MultipointWritableMessageChannel) {
            SocketAddress remoteAddress = getRemoteAddress();
            if (remoteAddress != null) {
                return write(message, remoteAddress);
            } else {
                return getUnsupportedOperationFuture();
            }
        }

        if (xnioChannel instanceof GatheringByteChannel ||
            xnioChannel instanceof WritableMessageChannel) {
            return super.write(message);
        } else {
            return getUnsupportedOperationFuture();
        }
    }

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null) {
            return write(message);
        }

        java.nio.channels.Channel xnioChannel = this.xnioChannel;
        if (xnioChannel instanceof MultipointWritableMessageChannel) {
            return super.write(message);
        } else {
            return getUnsupportedOperationFuture();
        }
    }

    void closeNow(ChannelFuture future) {
        SocketAddress localAddress = getLocalAddress();
        SocketAddress remoteAddress = getRemoteAddress();

        if (!setClosed()) {
            future.setSuccess();
            return;
        }

        try {
            IoUtils.safeClose(xnioChannel);
            xnioChannel = null;
            XnioChannelRegistry.unregisterChannelMapping(this);

            future.setSuccess();
            if (remoteAddress != null) {
                fireChannelDisconnected(this);
            }
            if (localAddress != null) {
                fireChannelUnbound(this);
            }

            fireChannelClosed(this);
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(this, t);
        }
    }

    private final class WriteBuffer extends LinkedTransferQueue<MessageEvent> {

        private final ThreadLocalBoolean notifying = new ThreadLocalBoolean();

        WriteBuffer() {
            super();
        }

        @Override
        public boolean offer(MessageEvent e) {
            boolean success = super.offer(e);
            assert success;

            int messageSize = ((ChannelBuffer) e.getMessage()).readableBytes();
            int newWriteBufferSize = writeBufferSize.addAndGet(messageSize);
            int highWaterMark = getConfig().getWriteBufferHighWaterMark();

            if (newWriteBufferSize >= highWaterMark) {
                if (newWriteBufferSize - messageSize < highWaterMark) {
                    highWaterMarkCounter.incrementAndGet();
                    if (!notifying.get()) {
                        notifying.set(Boolean.TRUE);
                        fireChannelInterestChanged(BaseXnioChannel.this);
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
                int messageSize = ((ChannelBuffer) e.getMessage()).readableBytes();
                int newWriteBufferSize = writeBufferSize.addAndGet(-messageSize);
                int lowWaterMark = getConfig().getWriteBufferLowWaterMark();

                if (newWriteBufferSize == 0 || newWriteBufferSize < lowWaterMark) {
                    if (newWriteBufferSize + messageSize >= lowWaterMark) {
                        highWaterMarkCounter.decrementAndGet();
                        if (!notifying.get()) {
                            notifying.set(Boolean.TRUE);
                            fireChannelInterestChanged(BaseXnioChannel.this);
                            notifying.set(Boolean.FALSE);
                        }
                    }
                }
            }
            return e;
        }
    }
}
