/*
 * Copyright 2026 The Netty Project
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

import io.netty.handler.codec.dns.DnsSvcbRecord;

import java.util.List;

/**
 * Represents the result of a previous HTTPS resolution which can be cached, like {@link DnsCacheEntry} does for
 * addresses.
 */
interface DnsHttpsCacheEntry {

    /**
     * Get the HTTPS records, sorted by priority, before the compatibility filter that depends on the client.
     * <p>
     * This may be null if the resolution failed, and in that case {@link #cause()} will describe the failure.
     * @return the HTTPS records.
     */
    List<DnsSvcbRecord> records();

    /**
     * If the resolution failed this will provide the rational.
     * @return the rational for why the resolution failed, or {@code null} if it hasn't failed.
     */
    Throwable cause();

    /*** Returns when this entry expires, like {@link DnsCacheEntry#deadlineNanos()}.
     *
     * @return the deadline of this entry, or {@code -1} if unknown
     */
    long deadlineNanos();
}
