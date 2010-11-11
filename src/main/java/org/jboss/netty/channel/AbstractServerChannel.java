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
package org.jboss.netty.channel;

import java.net.SocketAddress;

/**
 * A skeletal server-side {@link Channel} implementation.  A server-side
 * {@link Channel} does not allow the following operations:
 * <ul>
 * <li>{@link #connect(SocketAddress)}</li>
 * <li>{@link #disconnect()}</li>
 * <li>{@link #getInterestOps()}</li>
 * <li>{@link #setInterestOps(int)}</li>
 * <li>{@link #write(Object)}</li>
 * <li>{@link #write(Object, SocketAddress)}</li>
 * <li>and the shortcut methods which calls the methods mentioned above
 * </ul>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public abstract class AbstractServerChannel extends AbstractChannel implements ServerChannel {

    /**
     * Creates a new instance.
     *
     * @param factory
     *        the factory which created this channel
     * @param pipeline
     *        the pipeline which is going to be attached to this channel
     * @param sink
     *        the sink which will receive downstream events from the pipeline
     *        and send upstream events to the pipeline
     */
    protected AbstractServerChannel(
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink) {
        super(null, factory, pipeline, sink);
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

    @Override
    public ChannelFuture write(Object message) {
        return getUnsupportedOperationFuture();
    }

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        return getUnsupportedOperationFuture();
    }

    public boolean isConnected() {
        return false;
    }
}
