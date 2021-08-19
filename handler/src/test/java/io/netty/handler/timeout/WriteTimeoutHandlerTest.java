/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.timeout;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WriteTimeoutHandlerTest {

    @Test
    public void testPromiseUseDifferentExecutor() throws Exception {
        EventExecutorGroup group1 = new DefaultEventExecutorGroup(1);
        EmbeddedChannel channel = new EmbeddedChannel(false, false);
        try {
            channel.pipeline().addLast(new WriteTimeoutHandler(10000));
            final CountDownLatch latch = new CountDownLatch(1);
            channel.register();
            Promise<Void> promise = new DefaultPromise<>(group1.next());
            channel.writeAndFlush("something", promise);

            promise.addListener(f -> latch.countDown());

            latch.await();
            assertTrue(channel.finishAndReleaseAll());
        } finally {
            group1.shutdownGracefully();
        }
    }
}
