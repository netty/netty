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
package io.netty.channel.rxtx;


import java.util.concurrent.ExecutorService;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.internal.ExecutorUtil;

/**
 * A {@link ChannelFactory} for creating {@link RRXTXChannel} instances.
 */
public class RRXTXChannelFactory implements ChannelFactory {

    private final ChannelGroup channels = new DefaultChannelGroup("RXTXChannelFactory-ChannelGroup");

    private final ExecutorService executor;

    public RRXTXChannelFactory(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public Channel newChannel(final ChannelPipeline pipeline) {
        RRXTXChannelSink sink = new RRXTXChannelSink(executor);
        RRXTXChannel channel = new RRXTXChannel(null, this, pipeline, sink);
        sink.setChannel(channel);
        channels.add(channel);
        return channel;
    }

    @Override
    public void releaseExternalResources() {
        ChannelGroupFuture close = channels.close();
        close.awaitUninterruptibly();
        ExecutorUtil.terminate(executor);
    }
}
