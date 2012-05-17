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
package io.netty.channel.sctp;

import static io.netty.channel.Channels.future;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.MessageEvent;
import io.netty.channel.sctp.SctpSendBufferPool.SctpSendBuffer;
import io.netty.channel.socket.nio.AbstractNioChannel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import com.sun.nio.sctp.Association;

/**
 */
class SctpChannelImpl extends AbstractNioChannel implements SctpChannel {

    private static final int ST_OPEN = 0;
    private static final int ST_BOUND = 1;
    private static final int ST_CONNECTED = 2;
    private static final int ST_CLOSED = -1;
    volatile int state = ST_OPEN;

    private final NioSctpChannelConfig config;

    final SctpNotificationHandler notificationHandler = new SctpNotificationHandler(this);
    
    public SctpChannelImpl(Channel parent, ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink,
                           com.sun.nio.sctp.SctpChannel channel, SctpWorker worker) {
        super(parent, factory, pipeline, sink, worker, new SctpJdkChannel(channel));

        config = new DefaultNioSctpChannelConfig(channel);

        getCloseFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                state = ST_CLOSED;
            }
        });
    }

    Queue<MessageEvent> getWriteBufferQueue() {
        return writeBufferQueue;
    }
    
    Object getWriteLock() {
        return writeLock;
    }
    
    Object getInterestedOpsLock() {
        return interestOpsLock;
    }
    
    
    void setWriteSuspended(boolean writeSuspended) {
        this.writeSuspended = writeSuspended;
    }
    
    boolean getWriteSuspended() {
        return writeSuspended;
    }
    
    void setInWriteNowLoop(boolean inWriteNowLoop) {
        this.inWriteNowLoop = inWriteNowLoop;
    }
    
    MessageEvent getCurrentWriteEvent() {
        return currentWriteEvent;
    }
    
    void setCurrentWriteEvent(MessageEvent currentWriteEvent) {
        this.currentWriteEvent = currentWriteEvent;
    }
    
    int getRawInterestOps() {
        return super.getInterestOps();
    }

    void setRawInterestOpsNow(int interestOps) {
        super.setInterestOpsNow(interestOps);
    }
    
    SctpSendBuffer getCurrentWriteBuffer() {
        return (SctpSendBuffer) currentWriteBuffer;
    }
    
    void setCurrentWriteBuffer(SctpSendBuffer currentWriteBuffer) {
        this.currentWriteBuffer = currentWriteBuffer;
    }
    
    @Override
    public SctpWorker getWorker() {
        return (SctpWorker) super.getWorker();
    }
    
    
    @Override
    public NioSctpChannelConfig getConfig() {
        return config;
    }

    @Override
    public SctpJdkChannel getJdkChannel() {
        return (SctpJdkChannel) super.getJdkChannel();
    }
    

    @Override
    public Set<InetSocketAddress> getAllLocalAddresses() {
            try {
                final Set<SocketAddress> allLocalAddresses = getJdkChannel().getChannel().getAllLocalAddresses();
                final Set<InetSocketAddress> addresses = new HashSet<InetSocketAddress>(allLocalAddresses.size());
                for (SocketAddress socketAddress: allLocalAddresses) {
                    addresses.add((InetSocketAddress) socketAddress);
                }
                return addresses;
            } catch (Throwable t) {
                return Collections.emptySet();
            }
    }

    @Override
    public Set<InetSocketAddress> getAllRemoteAddresses() {
            try {
                final Set<SocketAddress> allLocalAddresses = getJdkChannel().getChannel().getRemoteAddresses();
                final Set<InetSocketAddress> addresses = new HashSet<InetSocketAddress>(allLocalAddresses.size());
                for (SocketAddress socketAddress: allLocalAddresses) {
                    addresses.add((InetSocketAddress) socketAddress);
                }
                return addresses;
            } catch (Throwable t) {
                return Collections.emptySet();
            }
    }

    @Override
    public ChannelFuture bindAddress(InetAddress localAddress) {
        ChannelFuture future = future(this);
        getPipeline().sendDownstream(new SctpBindAddressEvent(this, future, localAddress));
        return future;
    }

    @Override
    public ChannelFuture unbindAddress(InetAddress localAddress) {
        ChannelFuture future = future(this);
        getPipeline().sendDownstream(new SctpUnbindAddressEvent(this, future, localAddress));
        return future;
    }

    @Override
    public Association association() {
        try {
            return getJdkChannel().getChannel().association();
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

    protected final void setConnected() {
        if (state != ST_CLOSED) {
            state = ST_CONNECTED;
        }
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }
    
    @Override
    protected WriteRequestQueue createRequestQueue() {
        return new WriteRequestQueue() {
            
            @Override
            protected int getMessageSize(MessageEvent e) {
                Object m = e.getMessage();
                if (m instanceof SctpFrame) {
                    return ((SctpFrame) m).getPayloadBuffer().readableBytes();
                }
                return 0;
            }
        };
    }

}
