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

package io.netty.util.internal;

import io.netty.util.concurrent.FastThreadLocalThread;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Contains classes that must be have public visibility but are not public API.
 */
class Hidden {

    /**
     * This class integrates Netty with BlockHound.
     * <p>
     * It is public but only because of the ServiceLoader's limitations
     * and SHOULD NOT be considered a public API.
     */
    @UnstableApi
    @SuppressJava6Requirement(reason = "BlockHound is Java 8+, but this class is only loaded by it's SPI")
    public static final class NettyBlockHoundIntegration implements BlockHoundIntegration {

        @Override
        public void applyTo(BlockHound.Builder builder) {
            builder.allowBlockingCallsInside(
                    "io.netty.channel.nio.NioEventLoop",
                    "handleLoopException"
            );

            builder.allowBlockingCallsInside(
                    "io.netty.channel.kqueue.KQueueEventLoop",
                    "handleLoopException"
            );

            builder.allowBlockingCallsInside(
                    "io.netty.channel.epoll.EpollEventLoop",
                    "handleLoopException"
            );

            builder.allowBlockingCallsInside(
                    "io.netty.util.HashedWheelTimer",
                    "start"
            );

            builder.allowBlockingCallsInside(
                    "io.netty.util.HashedWheelTimer",
                    "stop"
            );

            builder.allowBlockingCallsInside(
                    "io.netty.util.HashedWheelTimer$Worker",
                    "waitForNextTick"
            );

            builder.allowBlockingCallsInside(
                    "io.netty.util.concurrent.SingleThreadEventExecutor",
                    "confirmShutdown"
            );

            builder.allowBlockingCallsInside(
                    "io.netty.handler.ssl.SslHandler",
                    "handshake"
            );

            builder.allowBlockingCallsInside(
                    "io.netty.handler.ssl.SslHandler",
                    "runAllDelegatedTasks"
            );

            builder.allowBlockingCallsInside(
                    "io.netty.util.concurrent.GlobalEventExecutor",
                    "takeTask");

            builder.allowBlockingCallsInside(
                    "io.netty.util.concurrent.GlobalEventExecutor",
                    "addTask");

            builder.allowBlockingCallsInside(
                    "io.netty.util.concurrent.SingleThreadEventExecutor",
                    "takeTask");

            builder.allowBlockingCallsInside(
                    "io.netty.util.concurrent.SingleThreadEventExecutor",
                    "addTask");

            builder.allowBlockingCallsInside(
                    "io.netty.handler.ssl.ReferenceCountedOpenSslClientContext$ExtendedTrustManagerVerifyCallback",
                    "verify");

            // Let's whitelist SSLEngineImpl.unwrap(...) for now as it may fail otherwise for TLS 1.3.
            // See https://mail.openjdk.java.net/pipermail/security-dev/2020-August/022271.html
            builder.allowBlockingCallsInside(
                    "sun.security.ssl.SSLEngineImpl",
                    "unwrap");

            builder.allowBlockingCallsInside(
                    "sun.security.ssl.SSLEngineImpl",
                    "wrap");

            builder.allowBlockingCallsInside(
                    "io.netty.resolver.dns.UnixResolverDnsServerAddressStreamProvider",
                    "parse");

            builder.nonBlockingThreadPredicate(new Function<Predicate<Thread>, Predicate<Thread>>() {
                @Override
                public Predicate<Thread> apply(final Predicate<Thread> p) {
                    return new Predicate<Thread>() {
                        @Override
                        @SuppressJava6Requirement(reason = "Predicate#test")
                        public boolean test(Thread thread) {
                            return p.test(thread) || thread instanceof FastThreadLocalThread;
                        }
                    };
                }
            });
        }

        @Override
        public int compareTo(BlockHoundIntegration o) {
            return 0;
        }
    }
}
