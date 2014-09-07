/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.concurrent.TimeUnit;

/**
 * Utilities for the integration tests.
 */
final class Http2TestUtil {
    public static final int MESSAGE_AWAIT_SECONDS = 5;

    /**
     * Generate {@code count} new {@link EventLoopGroup} objects
     * @param count The number of {@link EventLoopGroup}s to instantiate
     * @return Array of {@code count} new {@link NioEventLoopGroup} objects
     */
    public static EventLoopGroup[] newEventLoopGroups(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        EventLoopGroup[] groups = new EventLoopGroup[count];
        for (int i = 0; i < groups.length; ++i) {
            groups[i] = new NioEventLoopGroup();
        }
        return groups;
    }

    /**
     * Shutdown each {@link EventLoopGroup}
     * @param groups The groups to shutdown
     */
    public static void teardownGroups(EventLoopGroup[] groups) {
        for (int i = 0; i < groups.length; ++i) {
            groups[i].shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Interface that allows for running a operation that throws a {@link Http2Exception}.
     */
    interface Http2Runnable {
        void run() throws Http2Exception;
    }

    /**
     * Runs the given operation within the event loop thread of the given {@link Channel}.
     */
    static void runInChannel(Channel channel, final Http2Runnable runnable) {
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Http2Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private Http2TestUtil() { }
}
