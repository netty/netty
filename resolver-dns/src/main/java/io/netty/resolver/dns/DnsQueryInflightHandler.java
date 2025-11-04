/*
 * Copyright 2025 The Netty Project
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

import io.netty.handler.codec.dns.DnsQuestion;

/**
 * Allows to control for which {@link DnsQuestion}s we will try to consolidate the result and so reduce the number
 * of inflight queries.
 */
public interface DnsQueryInflightHandler {

    /**
     * Returns a {@link Runnable} that will be called once the query completes or {@code null} if no consolidation
     * should be used at all.
     *
     * @param question          the {@link DnsQuestion}
     * @param queryStartStamp   the timestamp {@link System#nanoTime()} of when the original query was be performed or
     *                          {@code 0} if no other query for this {@link DnsQuestion} is inflight.
     * @return                  a {@link Runnable} that will be executed once the original query was completed or
     *                          {@code null} if no consolidation should take place.
     */
    Runnable handle(DnsQuestion question, long queryStartStamp);
}
