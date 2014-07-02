/*
 * Copyright 2013 The Netty Project
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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ThreadPerChannelEventLoopGroupTest {

    private static final ChannelHandler NOOP_HANDLER = new ChannelHandlerAdapter() {
        @Override
        public boolean isSharable() {
            return true;
        }
    };

    @Test
    public void testTerminationFutureSuccessInLog() throws Exception {
        for (int i = 0; i < 2; i++) {
            ThreadPerChannelEventLoopGroup loopGroup = new ThreadPerChannelEventLoopGroup(64);
            runTest(loopGroup);
        }
    }

    @Test
    public void testTerminationFutureSuccessReflectively() throws Exception {
        Field terminationFutureField =
                ThreadPerChannelEventLoopGroup.class.getDeclaredField("terminationFuture");
        terminationFutureField.setAccessible(true);
        final Exception[] exceptionHolder = new Exception[1];
        for (int i = 0; i < 2; i++) {
            ThreadPerChannelEventLoopGroup loopGroup = new ThreadPerChannelEventLoopGroup(64);
            Promise<?> promise = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE) {
                @Override
                public Promise<Void> setSuccess(Void result) {
                    try {
                        return super.setSuccess(result);
                    } catch (IllegalStateException e) {
                        exceptionHolder[0] = e;
                        throw e;
                    }
                }
            };
            terminationFutureField.set(loopGroup, promise);
            runTest(loopGroup);
        }
        // The global event executor will not terminate, but this will give the test a chance to fail.
        GlobalEventExecutor.INSTANCE.awaitTermination(100, TimeUnit.MILLISECONDS);
        assertNull(exceptionHolder[0]);
    }

    private static void runTest(ThreadPerChannelEventLoopGroup loopGroup) throws InterruptedException {
        int taskCount = 100;
        EventExecutor testExecutor = new TestEventExecutor();
        ChannelGroup channelGroup = new DefaultChannelGroup(testExecutor);
        while (taskCount-- > 0) {
            Channel channel = new EmbeddedChannel(NOOP_HANDLER);
            loopGroup.register(channel, new DefaultChannelPromise(channel, testExecutor));
            channelGroup.add(channel);
        }
        channelGroup.close().sync();
        loopGroup.shutdownGracefully(100, 200, TimeUnit.MILLISECONDS).sync();
        assertTrue(loopGroup.isTerminated());
    }

    private static class TestEventExecutor extends SingleThreadEventExecutor {

        TestEventExecutor() {
            super(null, new DefaultThreadFactory("test"), false);
        }

        @Override
        protected void run() {
            for (;;) {
                Runnable task = takeTask();
                if (task != null) {
                    task.run();
                    updateLastExecutionTime();
                }

                if (confirmShutdown()) {
                    break;
                }
            }
        }
    }
}
