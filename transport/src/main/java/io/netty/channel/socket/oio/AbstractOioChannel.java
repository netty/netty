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
package io.netty.channel.socket.oio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.socket.Worker;

abstract class AbstractOioChannel extends AbstractChannel {
    private volatile InetSocketAddress localAddress;
    volatile InetSocketAddress remoteAddress;
    volatile Thread workerThread;
    volatile Worker worker;
    
    final Object interestOpsLock = new Object();

    AbstractOioChannel(
            Channel parent,
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink) {
        super(parent, factory, pipeline, sink);
    }
    
    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    @Override
    protected void setInterestOpsNow(int interestOps) {
        super.setInterestOpsNow(interestOps);
    }

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return super.write(message, remoteAddress);
        }
    }
    
    @Override
    public boolean isBound() {
        return isOpen() && isSocketBound();
    }

    @Override
    public boolean isConnected() {
        return isOpen() && isSocketConnected();
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
    
    abstract boolean isSocketBound();
    
    abstract boolean isSocketConnected();
    
    abstract boolean isSocketClosed();
    
    abstract InetSocketAddress getLocalSocketAddress() throws Exception;
    
    abstract InetSocketAddress getRemoteSocketAddress() throws Exception;
    
    abstract void closeSocket() throws IOException;
    
}
