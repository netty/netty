/*
 * Copyright 2026 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.channel.DefaultEventLoop;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class CacheTest {

    @ParameterizedTest
    @ValueSource(strings = { "address", "negative" })
    void testEvictWhenExpirationEventLoopTerminates(String value) {
        Cache<String> cache = newCache();
        DefaultEventLoop originalLoop = new DefaultEventLoop();
        DefaultEventLoop replacementLoop = new DefaultEventLoop();
        try {
            cache.cache("netty.io", value, 86400, originalLoop);
            assertThat(cache.get("netty.io")).isEqualTo(singletonList(value));

            originalLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();

            // A cancelled expiration must not leave entries cached indefinitely, even without further lookups.
            assertThat(cache.size()).isZero();
            assertThat(cache.get("netty.io")).isNull();

            cache.cache("netty.io", "fresh", 86400, replacementLoop);
            assertThat(cache.get("netty.io")).isEqualTo(singletonList("fresh"));
        } finally {
            originalLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
            replacementLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
            cache.clear();
        }
    }

    @Test
    void testReplacingExpirationKeepsEntriesUntilNewEventLoopTerminates() {
        Cache<String> cache = newCache();
        DefaultEventLoop originalLoop = new DefaultEventLoop();
        DefaultEventLoop replacementLoop = new DefaultEventLoop();
        try {
            cache.cache("netty.io", "first", 86400, originalLoop);
            cache.cache("netty.io", "second", 3600, replacementLoop);
            originalLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();

            // Cancelling the superseded timer must not evict entries now owned by the shorter timer.
            assertThat(cache.get("netty.io")).isEqualTo(asList("first", "second"));

            replacementLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
            assertThat(cache.size()).isZero();
            assertThat(cache.get("netty.io")).isNull();
        } finally {
            originalLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
            replacementLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
            cache.clear();
        }
    }

    @Test
    void testExpirationCancelledBeforeScheduleReturns() {
        Cache<String> cache = newCache();
        DefaultEventLoop loop = new DefaultEventLoop() {
            @Override
            public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                ScheduledFuture<?> future = super.schedule(command, delay, unit);
                submit(new Runnable() {
                    @Override
                    public void run() {
                        // NOOP
                    }
                }).syncUninterruptibly();
                shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
                return future;
            }
        };
        try {
            cache.cache("netty.io", "address", 86400, loop);
            assertThat(cache.size()).isZero();
            assertThat(cache.get("netty.io")).isNull();
        } finally {
            loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).syncUninterruptibly();
            cache.clear();
        }
    }

    private static Cache<String> newCache() {
        return new Cache<String>() {
            @Override
            protected boolean shouldReplaceAll(String entry) {
                return "negative".equals(entry);
            }

            @Override
            protected boolean equals(String entry, String otherEntry) {
                return entry.equals(otherEntry);
            }
        };
    }
}
