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
package io.netty.channel.socket.sctp;

import com.sun.nio.sctp.SctpChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;

import static io.netty.channel.Channels.*;

/**
 *
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://github.com/jestan">Jestan Nirojan</a>
 */
final class SctpAcceptedChannel extends SctpChannelImpl {

    final Thread bossThread;

    SctpAcceptedChannel(
            ChannelFactory factory, ChannelPipeline pipeline,
            Channel parent, ChannelSink sink,
            SctpChannel socket, SctpWorker worker, Thread bossThread) {

        super(parent, factory, pipeline, sink, socket, worker);

        this.bossThread = bossThread;

        setConnected();
        fireChannelOpen(this);
        fireChannelBound(this, getLocalAddress());
        fireChannelConnected(this, getRemoteAddress());
    }
}
