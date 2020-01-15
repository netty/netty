/*
 * Copyright 2015 The Netty Project
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
package io.netty.resolver;

import java.net.InetAddress;

/**
 * Resolves a hostname against the hosts file entries.
 */
public interface HostsFileEntriesResolver {

    /**
     * Default instance: a {@link DefaultHostsFileEntriesResolver}.
     */
    HostsFileEntriesResolver DEFAULT = new DefaultHostsFileEntriesResolver();

    /**
     * Resolve the address of a hostname against the entries in a hosts file, depending on some address types.
     * @param inetHost the hostname to resolve
     * @param resolvedAddressTypes the address types to resolve
     * @return the first matching address
     */
    InetAddress address(String inetHost, ResolvedAddressTypes resolvedAddressTypes);
}
