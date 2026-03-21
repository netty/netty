import io.netty.util.internal.NettyBlockHoundIntegration;

/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
module io.netty5.common {
    provides reactor.blockhound.integration.BlockHoundIntegration with NettyBlockHoundIntegration;

    requires jdk.unsupported;
    requires java.logging;
    requires org.jctools.core;
    requires jdk.jfr;
    requires static org.apache.commons.logging;
    requires static org.apache.log4j;
    requires static org.apache.logging.log4j;
    requires static org.jetbrains.annotations;
    requires static org.slf4j;
    requires static org.graalvm.nativeimage;
    requires static reactor.blockhound;

    exports io.netty.util;
    exports io.netty.util.collection;
    exports io.netty.util.concurrent;

    exports io.netty.util.internal to
            io.netty5.buffer,
            io.netty5.resolver,
            io.netty5.transport,
            io.netty5.pkitesting,
            io.netty5.handler,
            io.netty5.codec,
            io.netty5.codec.compression,
            io.netty5.codec.protobuf,
            io.netty5.codec.dns,
            io.netty5.codec.haproxy,
            io.netty5.transport.unix.common,
            io.netty5.codec.http,
            io.netty5.codec.http2,
            io.netty5.codec.classes.quic,
            io.netty5.codec.http3,
            io.netty5.codec.memcache,
            io.netty5.codec.mqtt,
            io.netty5.codec.redis,
            io.netty5.codec.smtp,
            io.netty5.codec.socks,
            io.netty5.codec.stomp,
            io.netty5.codec.xml,
            io.netty5.handler.proxy,
            io.netty5.resolver.dns,
            io.netty5.handler.ssl.ocsp,
            io.netty5.transport.classes.epoll,
            io.netty5.transport.classes.io_uring,
            io.netty5.transport.classes.kqueue,
            io.netty5.resolver.dns.macos,
            io.netty5.testsuite_jpms.test,
            io.netty5.microbench,
            io.netty5.transport.blockhound;
    exports io.netty.util.internal.logging to
            io.netty5.buffer,
            io.netty5.resolver,
            io.netty5.transport,
            io.netty5.handler,
            io.netty5.codec.compression,
            io.netty5.codec.http,
            io.netty5.codec.http2,
            io.netty5.codec.classes.quic,
            io.netty5.codec.http3,
            io.netty5.codec.socks,
            io.netty5.handler.proxy,
            io.netty5.resolver.dns,
            io.netty5.handler.ssl.ocsp,
            io.netty5.transport.classes.epoll,
            io.netty5.transport.classes.io_uring,
            io.netty5.transport.classes.kqueue,
            io.netty5.resolver.dns.macos,
            io.netty5.testsuite_jpms.test,
            io.netty5.microbench;
}
