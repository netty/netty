/*
 * Copyright 2017 The Netty Project
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

/**
 * Provides an opportunity to override which {@link DnsServerAddressStream} is used to resolve a specific hostname.
 * <p>
 * For example this can be used to represent <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a> and
 * <a href="https://developer.apple.com/legacy/library/documentation/Darwin/Reference/ManPages/man5/resolver.5.html">
 * /etc/resolver</a>.
 */
public interface DnsServerAddressStreamProvider {
    /**
     * Ask this provider for the name servers to query for {@code hostname}.
     * @param hostname The hostname for which to lookup the DNS server addressed to use.
     *                 If this is the final {@link DnsServerAddressStreamProvider} to be queried then generally empty
     *                 string or {@code '.'} correspond to the default {@link DnsServerAddressStream}.
     * @return The {@link DnsServerAddressStream} which should be used to resolve {@code hostname}.
     */
    DnsServerAddressStream nameServerAddressStream(String hostname);
}
