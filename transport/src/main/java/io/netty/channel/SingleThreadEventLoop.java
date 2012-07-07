/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import java.util.concurrent.ThreadFactory;

public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {

    protected SingleThreadEventLoop(ThreadFactory threadFactory) {
        super(threadFactory);
    }

    @Override
    public ChannelFuture register(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        return register(channel, channel.newFuture());
    }

    @Override
    public ChannelFuture register(final Channel channel, final ChannelFuture future) {
        if (inEventLoop()) {
            channel.unsafe().register(this, future);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    channel.unsafe().register(SingleThreadEventLoop.this, future);
                }
            });
        }
        return future;
    }
}
