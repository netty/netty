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
package io.netty.channel.socket.oio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.SingleThreadEventLoop;


class OioChildEventLoop extends SingleThreadEventLoop {

    private final OioEventLoop parent;
    private AbstractOioChannel ch;

    OioChildEventLoop(OioEventLoop parent) {
        super(parent.threadFactory);
        this.parent = parent;
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelFuture future) {
        return super.register(channel, future).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    ch = (AbstractOioChannel) future.channel();
                } else {
                    deregister();
                }
            }
        });
    }

    @Override
    protected void run() {
        for (;;) {
            AbstractOioChannel ch = this.ch;
            if (ch == null || !ch.isActive()) {
                Runnable task;
                try {
                    task = takeTask();
                    task.run();
                } catch (InterruptedException e) {
                    // Waken up by interruptThread()
                }
            } else {
                long startTime = System.nanoTime();
                for (;;) {
                    final Runnable task = pollTask();
                    if (task == null) {
                        break;
                    }

                    task.run();

                    // Ensure running tasks doesn't take too much time.
                    if (System.nanoTime() - startTime > AbstractOioChannel.SO_TIMEOUT * 1000000L) {
                        break;
                    }
                }

                ch.unsafe().read();

                // Handle deregistration
                if (!ch.isRegistered()) {
                    runAllTasks();
                    deregister();
                }
            }

            if (isShutdown()) {
                if (ch != null) {
                    ch.unsafe().close(ch.unsafe().voidFuture());
                }
                if (peekTask() == null) {
                    break;
                }
            }
        }
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        interruptThread();
    }

    private void deregister() {
        ch = null;
        parent.activeChildren.remove(this);
        parent.idleChildren.add(this);
    }
}
