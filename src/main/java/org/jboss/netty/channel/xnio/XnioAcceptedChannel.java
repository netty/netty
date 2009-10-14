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

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
final class XnioAcceptedChannel extends BaseXnioChannel {

    XnioAcceptedChannel(
            XnioServerChannel parent,
            XnioServerChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink) {
        super(parent, factory, pipeline, sink, new DefaultXnioChannelConfig());
        fireChannelOpen(this);
    }

    @Override
    public XnioClientChannelFactory getFactory() {
        return (XnioClientChannelFactory) super.getFactory();
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return getUnsupportedOperationFuture();
    }

    @Override
    public ChannelFuture bind(SocketAddress remoteAddress) {
        return getUnsupportedOperationFuture();
    }
}
