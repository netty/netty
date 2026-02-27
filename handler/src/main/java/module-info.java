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
module io.netty5.handler {
    requires io.netty5.common;
    requires io.netty5.buffer;
    requires io.netty5.transport;
    requires io.netty5.resolver;
    requires io.netty5.codec;
    requires io.netty5.transport.unix.common;
    requires java.naming;

    requires static io.netty.tcnative.classes.openssl;
    requires static org.bouncycastle.pkix;
    requires static org.bouncycastle.provider;
    requires static org.conscrypt;
    requires static transitive io.netty5.pkitesting;

    exports io.netty.handler.address;
    exports io.netty.handler.flow;
    exports io.netty.handler.flush;
    exports io.netty.handler.ipfilter;
    exports io.netty.handler.logging;
    exports io.netty.handler.pcap;
    exports io.netty.handler.ssl;
    exports io.netty.handler.ssl.util;
    exports io.netty.handler.stream;
    exports io.netty.handler.timeout;
    exports io.netty.handler.traffic;
}