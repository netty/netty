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

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

/**
 * Represents the results from a previous DNS query which can be cached.
 */
public interface DnsCacheEntry {
    /**
     * Get the resolved address.
     * <p>
     * This may be null if the resolution failed, and in that case {@link #cause()} will describe the failure.
     * @return the resolved address.
     */
    InetAddress address();

    /**
     * If the DNS query failed this will provide the rational.
     * @return the rational for why the DNS query failed, or {@code null} if the query hasn't failed.
     */
    Throwable cause();

    /**
     * Get the CNAME chain that led to this address resolution.
     * <p>
     * This method returns the sequence of CNAME records that were followed during DNS resolution.
     * If the address was resolved directly without following any CNAMEs, this returns an empty list.
     * <p>
     * The default implementation returns an empty list for backward compatibility with existing
     * cache implementations.
     *
     * @return the CNAME chain, never {@code null} but may be empty
     * @since 4.2.0
     */
    default List<String> cnameChain() {
        return Collections.emptyList();
    }
}
