/*
 * Copyright 2017 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KQueueEventLoopTest {

    @Test(timeout = 5000L)
    public void testScheduleBigDelayOverMax() {
        EventLoopGroup group = new KQueueEventLoopGroup(1);

        final EventLoop el = group.next();
        try {
            el.schedule(new Runnable() {
                @Override
                public void run() {
                    // NOOP
                }
            }, Integer.MAX_VALUE, TimeUnit.DAYS);
            fail();
        } catch (IllegalArgumentException expected) {
            // expected
        }

        group.shutdownGracefully();
    }

    @Test
    public void testScheduleBigDelay() {
        EventLoopGroup group = new KQueueEventLoopGroup(1);

        final EventLoop el = group.next();
        Future<?> future = el.schedule(new Runnable() {
            @Override
            public void run() {
                // NOOP
            }
        }, KQueueEventLoop.MAX_SCHEDULED_DAYS, TimeUnit.DAYS);

        assertFalse(future.awaitUninterruptibly(1000));
        assertTrue(future.cancel(true));
        group.shutdownGracefully();
    }
}
