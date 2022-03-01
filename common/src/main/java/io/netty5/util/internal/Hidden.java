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

package io.netty5.util.internal;

import io.netty5.util.concurrent.FastThreadLocalThread;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;


/**
 * Contains classes that must have public visibility but are not public API.
 */
class Hidden {

    /**
     * This class integrates Netty with BlockHound.
     * <p>
     * It is public but only because of the ServiceLoader's limitations
     * and SHOULD NOT be considered a public API.
     */
    @UnstableApi
    public static final class NettyBlockHoundIntegration implements BlockHoundIntegration {

        @Override
        public void applyTo(BlockHound.Builder builder) {
            builder.allowBlockingCallsInside(
                    "io.netty5.channel.nio.NioEventLoop",
                    "handleLoopException"
            );

            builder.allowBlockingCallsInside(
                    "io.netty5.channel.kqueue.KQueueEventLoop",
                    "handleLoopException"
            );

            builder.allowBlockingCallsInside(
                    "io.netty5.channel.epoll.EpollEventLoop",
                    "handleLoopException"
            );

            builder.allowBlockingCallsInside(
                    "io.netty5.util.HashedWheelTimer",
                    "start"
            );

            builder.allowBlockingCallsInside(
                    "io.netty5.util.HashedWheelTimer",
                    "stop"
            );

            builder.allowBlockingCallsInside(
                    "io.netty5.util.HashedWheelTimer$Worker",
                    "waitForNextTick"
            );

            builder.allowBlockingCallsInside(
                    "io.netty5.util.concurrent.SingleThreadEventExecutor",
                    "confirmShutdown"
            );
            builder.allowBlockingCallsInside("io.netty5.util.concurrent.GlobalEventExecutor",
                    "addTask");

            builder.allowBlockingCallsInside("io.netty5.util.concurrent.GlobalEventExecutor",
                    "takeTask");

            builder.allowBlockingCallsInside(
                    "io.netty5.util.concurrent.SingleThreadEventExecutor",
                    "addTask");

            builder.allowBlockingCallsInside(
                    "io.netty5.util.concurrent.SingleThreadEventExecutor",
                    "takeTask");

            builder.allowBlockingCallsInside(
                    "io.netty5.handler.ssl.SslHandler",
                    "handshake"
            );

            builder.allowBlockingCallsInside(
                    "io.netty5.handler.ssl.SslHandler",
                    "runAllDelegatedTasks"
            );
            builder.allowBlockingCallsInside(
                    "io.netty5.handler.ssl.SslHandler",
                    "runDelegatedTasks"
            );

            builder.allowBlockingCallsInside(
                    "io.netty5.handler.ssl.ReferenceCountedOpenSslClientContext$ExtendedTrustManagerVerifyCallback",
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
                    "io.netty5.resolver.dns.UnixResolverDnsServerAddressStreamProvider",
                    "parse");

            builder.allowBlockingCallsInside(
                    "io.netty5.resolver.dns.UnixResolverDnsServerAddressStreamProvider",
                    "parseEtcResolverSearchDomains");

            builder.allowBlockingCallsInside(
                    "io.netty5.resolver.dns.UnixResolverDnsServerAddressStreamProvider",
                    "parseEtcResolverOptions");

            builder.allowBlockingCallsInside(
                    "io.netty5.resolver.HostsFileEntriesProvider$ParserImpl",
                    "parse");

            builder.allowBlockingCallsInside(
                    "io.netty5.util.NetUil$SoMaxConnAction",
                    "run");

            builder.nonBlockingThreadPredicate(p -> thread ->
                    p.test(thread) || thread instanceof FastThreadLocalThread);
        }
    }
}
