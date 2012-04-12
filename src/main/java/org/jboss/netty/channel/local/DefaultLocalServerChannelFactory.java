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
package org.jboss.netty.channel.local;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.group.DefaultChannelGroup;

/**
 * The default {@link LocalServerChannelFactory} implementation.
 * @apiviz.landmark
 */
public class DefaultLocalServerChannelFactory implements LocalServerChannelFactory {

    private final DefaultChannelGroup group = new DefaultChannelGroup();
    private final ChannelSink sink = new LocalServerChannelSink();

    /**
     * Creates a new instance.
     */
    public DefaultLocalServerChannelFactory() {
        super();
    }

    public LocalServerChannel newChannel(ChannelPipeline pipeline) {
        LocalServerChannel channel = new DefaultLocalServerChannel(this, pipeline, sink);
        group.add(channel);
        return channel;
    }


    /**
     * Release all the previous created channels. This takes care of calling {@link LocalChannelRegistry#unregister(LocalAddress)}
     * for each if them.
     */
    public void releaseExternalResources() {
        group.close().awaitUninterruptibly();
    }
}
