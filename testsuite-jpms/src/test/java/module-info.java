/*
 * Copyright 2024 The Netty Project
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
open module io.netty.testsuite_jpms.test {
    requires org.junit.jupiter.api;
    requires jdk.jfr;
    requires io.netty5.buffer;
    requires io.netty5.codec;
    requires io.netty5.codec.xml;
    requires io.netty5.codec.smtp;
    requires io.netty5.codec.mqtt;
    requires io.netty5.codec.memcache;
    requires io.netty5.codec.haproxy;
    requires io.netty5.codec.redis;
    requires io.netty5.codec.stomp;
    requires io.netty5.codec.socks;
    requires io.netty5.codec.protobuf;
    requires io.netty5.codec.compression;
    requires io.netty5.common;
    requires io.netty5.handler;
    requires io.netty5.handler.ssl.ocsp;
    requires io.netty5.pkitesting;
    requires io.netty5.transport;
    requires io.netty5.transport.classes.kqueue;
    requires io.netty5.transport.classes.epoll;
    requires io.netty5.transport.classes.io_uring;
    requires io.netty5.resolver.dns.macos;
    requires io.netty5.resolver.dns;
    requires io.netty5.codec.http;
    requires io.netty5.codec.http2;
    requires io.netty5.codec.http3;
    requires io.netty5.codec.classes.quic;
    requires org.bouncycastle.pkix;

    requires static org.slf4j;
    requires static ch.qos.logback.core;
    requires static ch.qos.logback.classic;
    requires static org.apache.logging.log4j;
    requires static org.apache.logging.log4j.core;
    requires static org.apache.commons.logging;
}
