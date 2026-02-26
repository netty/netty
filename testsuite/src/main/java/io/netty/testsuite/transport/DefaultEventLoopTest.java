/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.transport;

import io.netty.channel.Channel;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultEventLoopTest extends AbstractSingleThreadEventLoopTest {

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testChannelsIteratorNotSupported() throws Exception {
        EventLoopGroup group = newEventLoopGroup();
        final SingleThreadEventLoop loop = (SingleThreadEventLoop) group.next();
        try {
            final Channel ch = newChannel();
            loop.register(ch).syncUninterruptibly();

            assertThrows(UnsupportedOperationException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    loop.registeredChannelsIterator();
                }
            });
        } finally {
            group.shutdownGracefully();
        }
    }

    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new DefaultEventLoopGroup();
    }

    @Override
    protected EventLoopGroup newAutoScalingEventLoopGroup() {
        return new AutoScalingDefaultEventLoopGroup(SCALING_MAX_THREADS, AUTO_SCALING_CHOOSER_FACTORY);
    }

    @Override
    protected Channel newChannel() {
        return new LocalChannel();
    }

    @Override
    protected Class<? extends ServerChannel> serverChannelClass() {
        return LocalServerChannel.class;
    }

    private static final class SuspendableDefaultEventLoop extends SingleThreadEventLoop {
        SuspendableDefaultEventLoop(EventLoopGroup parent, Executor executor) {
            super(parent, executor, true, true, DEFAULT_MAX_PENDING_TASKS,
                  RejectedExecutionHandlers.reject());
        }

        @Override
        protected void run() {
            for (;;) {
                Runnable task = takeTask();
                if (task != null) {
                    runTask(task);
                    updateLastExecutionTime();
                }

                // Check if a suspend is requested and we have no more tasks. If so,
                // exit the run() method to allow the suspension to complete.
                if (canSuspend()) {
                    break;
                }

                if (confirmShutdown()) {
                    break;
                }
            }
        }
    }

    private static final class AutoScalingDefaultEventLoopGroup extends MultithreadEventLoopGroup {
        AutoScalingDefaultEventLoopGroup(int nThreads, EventExecutorChooserFactory chooserFactory) {
            super(nThreads, (Executor) null, chooserFactory);
        }

        @Override
        protected EventLoop newChild(Executor executor, Object... args) throws Exception {
            return new SuspendableDefaultEventLoop(this, executor);
        }
    }
}
