/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
 * @author Trustin Lee (tlee@redhat.com)
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
