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
    requires io.netty.buffer;
    requires io.netty.codec;
    requires io.netty.codec.xml;
    requires io.netty.codec.smtp;
    requires io.netty.codec.mqtt;
    requires io.netty.codec.memcache;
    requires io.netty.codec.haproxy;
    requires io.netty.codec.redis;
    requires io.netty.codec.stomp;
    requires io.netty.codec.socks;
    requires io.netty.codec.protobuf;
    requires io.netty.codec.marshalling;
    requires io.netty.codec.compression;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.handler.ssl.ocsp;
    requires io.netty.pkitesting;
    requires io.netty.transport;
    requires io.netty.transport.classes.kqueue;
    requires io.netty.transport.classes.epoll;
    requires io.netty.transport.classes.io_uring;
    requires io.netty.resolver.dns.classes.macos;
    requires io.netty.resolver.dns;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
    requires jboss.marshalling;
    requires org.bouncycastle.pkix;

    requires static org.slf4j;
    requires static ch.qos.logback.core;
    requires static ch.qos.logback.classic;
    requires static org.apache.logging.log4j;
    requires static org.apache.logging.log4j.core;
    requires static org.apache.commons.logging;
}
