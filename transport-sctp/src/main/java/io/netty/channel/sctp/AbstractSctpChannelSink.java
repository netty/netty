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

import io.netty.channel.AbstractChannelSink;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.ChannelRunnableWrapper;

public abstract class AbstractSctpChannelSink extends AbstractChannelSink {

    @Override
    public ChannelFuture execute(ChannelPipeline pipeline, final Runnable task) {
        Channel ch = pipeline.getChannel();
        if (ch instanceof SctpChannelImpl) {
            SctpChannelImpl channel = (SctpChannelImpl) ch;
            ChannelRunnableWrapper wrapper = new ChannelRunnableWrapper(channel, task);
            channel.worker.executeInIoThread(wrapper);
            return wrapper;
             
        } else {
            return super.execute(pipeline, task);
        }

    }
}
