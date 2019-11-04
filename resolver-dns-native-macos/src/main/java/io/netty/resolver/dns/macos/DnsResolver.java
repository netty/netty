/*
 * Copyright 2019 The Netty Project
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
package io.netty.resolver.dns.macos;

import io.netty.channel.unix.NativeInetAddress;

import java.net.InetSocketAddress;

/**
 * Represent the {@code dns_resolver_t} struct.
 */
final class DnsResolver {

    private final String domain;
    private final InetSocketAddress[] nameservers;
    private final int port;
    private final String[] searches;
    private final String options;
    private final int timeout;
    private final int searchOrder;

    DnsResolver(String domain, byte[][] nameservers, int port,
                String[] searches, String options, int timeout, int searchOrder) {
        this.domain = domain;
        if (nameservers == null) {
            this.nameservers = new InetSocketAddress[0];
        } else {
            this.nameservers = new InetSocketAddress[nameservers.length];
            for (int i = 0; i < nameservers.length; i++) {
                byte[] addr = nameservers[i];
                this.nameservers[i] = NativeInetAddress.address(addr, 0, addr.length);
            }
        }
        this.port = port;
        this.searches = searches;
        this.options = options;
        this.timeout = timeout;
        this.searchOrder = searchOrder;
    }

    String domain() {
        return domain;
    }

    InetSocketAddress[] nameservers() {
        return nameservers;
    }

    int port() {
        return port;
    }

    String[] searches() {
        return searches;
    }

    String options() {
        return options;
    }

    int timeout() {
        return timeout;
    }

    int searchOrder() {
        return searchOrder;
    }
}
