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

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.BoundServer;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
@SuppressWarnings("unchecked")
final class DefaultXnioServerChannel extends BaseXnioChannel implements XnioServerChannel {

    private static final Object bindLock = new Object();

    final BoundServer xnioServer;

    DefaultXnioServerChannel(
            XnioServerChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink, BoundServer xnioServer) {
        super(null, factory, pipeline, sink, new DefaultXnioChannelConfig());
        this.xnioServer = xnioServer;
        fireChannelOpen(this);
    }

    @Override
    public XnioServerChannelFactory getFactory() {
        return (XnioServerChannelFactory) super.getFactory();
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return getUnsupportedOperationFuture();
    }

    @Override
    public ChannelFuture disconnect() {
        return getUnsupportedOperationFuture();
    }

    @Override
    public int getInterestOps() {
        return OP_NONE;
    }

    @Override
    public ChannelFuture setInterestOps(int interestOps) {
        return getUnsupportedOperationFuture();
    }

    @Override
    protected void setInterestOpsNow(int interestOps) {
        // Ignore.
    }

    void bindNow(ChannelFuture future, SocketAddress localAddress) {
        try {
            synchronized (bindLock) {
                IoFuture<BoundChannel> bindFuture = xnioServer.bind(localAddress);
                for (;;) {
                    IoFuture.Status bindStatus = bindFuture.await();
                    switch (bindStatus) {
                    case WAITING:
                        // Keep waiting for the result.
                        continue;
                    case CANCELLED:
                        throw new Error("should not reach here");
                    case DONE:
                        break;
                    case FAILED:
                        throw bindFuture.getException();
                    default:
                        throw new Error("should not reach here: " + bindStatus);
                    }

                    // Break the loop if done.
                    break;
                }

                BoundChannel xnioChannel = bindFuture.get();
                this.xnioChannel = xnioChannel;
                XnioChannelRegistry.registerServerChannel(this);
            }

            future.setSuccess();
            fireChannelBound(this, getLocalAddress());
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(this, t);
        }
    }

    @Override
    void closeNow(ChannelFuture future) {
        SocketAddress localAddress = getLocalAddress();
        boolean bound = localAddress != null;
        try {
            if (setClosed()) {
                future.setSuccess();
                synchronized (bindLock) {
                    IoUtils.safeClose(xnioChannel);
                    XnioChannelRegistry.unregisterServerChannel(localAddress);
                    XnioChannelRegistry.unregisterChannelMapping(this);
                }

                if (bound) {
                    fireChannelUnbound(this);
                }
                fireChannelClosed(this);
            } else {
                future.setSuccess();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(this, t);
        }
    }
}
