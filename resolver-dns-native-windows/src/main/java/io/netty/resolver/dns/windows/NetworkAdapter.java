/*
 * Copyright 2022 The Netty Project
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
package io.netty.resolver.dns.windows;

import java.net.InetSocketAddress;
import java.util.List;

final class NetworkAdapter {
    private final List<InetSocketAddress> nameservers;
    private final List<String> searchDomains;

    NetworkAdapter(List<InetSocketAddress> nameservers, List<String> searchDomains) {
        this.nameservers = nameservers;
        this.searchDomains = searchDomains;
    }

    public List<InetSocketAddress> getNameservers() {
        return nameservers;
    }

    public List<String> getSearchDomains() {
        return searchDomains;
    }
}
