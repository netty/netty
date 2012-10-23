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
package io.netty.channel.socket.aio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelTaskScheduler;
import io.netty.channel.SingleThreadEventLoop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadFactory;

final class AioEventLoop extends SingleThreadEventLoop {

    private final Set<Channel> channels = Collections.newSetFromMap(new IdentityHashMap<Channel, Boolean>());

    private final ChannelFutureListener registrationListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isSuccess()) {
                return;
            }

            Channel ch = future.channel();
            channels.add(ch);
            ch.closeFuture().addListener(deregistrationListener);
        }
    };

    private final ChannelFutureListener deregistrationListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            channels.remove(future.channel());
        }
    };

    AioEventLoop(AioEventLoopGroup parent, ThreadFactory threadFactory, ChannelTaskScheduler scheduler) {
        super(parent, threadFactory, scheduler);
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return super.register(channel).addListener(registrationListener);
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelFuture future) {
        return super.register(channel, future).addListener(registrationListener);
    }

    @Override
    protected void run() {
        for (;;) {
            Runnable task;
            try {
                task = takeTask();
                task.run();
            } catch (InterruptedException e) {
                // Waken up by interruptThread()
            }

            if (isShutdown()) {
                closeAll();
                task = pollTask();
                if (task == null) {
                    break;
                }
                task.run();
            }
        }
    }

    private void closeAll() {
        Collection<Channel> channels = new ArrayList<Channel>(this.channels.size());
        for (Channel ch: this.channels) {
            channels.add(ch);
        }

        for (Channel ch: channels) {
            ch.unsafe().close(ch.unsafe().voidFuture());
        }
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop && isShutdown()) {
            interruptThread();
        }
    }
}
