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

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.xnio.Connector;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@SuppressWarnings("unchecked")
final class XnioClientChannel extends BaseXnioChannel {

    final Object connectLock = new Object();
    final Connector xnioConnector;
    volatile boolean connecting;

    XnioClientChannel(
            XnioClientChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink, Connector xnioConnector) {
        super(null, factory, pipeline, sink, new DefaultXnioChannelConfig());
        this.xnioConnector = xnioConnector;
        fireChannelOpen(this);
    }

    @Override
    public XnioClientChannelFactory getFactory() {
        return (XnioClientChannelFactory) super.getFactory();
    }
}
