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
     * Returns a {@link DnsQueryInflightHandle} that will be called everytime we might want to consolidate or
     * {@code null} if no consolidation should be used at all.
     *
     * @param question          the {@link DnsQuestion} or {@code null} if no consolidation should take place.
     * @return                  the {@link DnsQueryInflightHandle}.
     */
    DnsQueryInflightHandle handle(DnsQuestion question);

    /**
     * A handle for a current consolidation.
     */
    interface DnsQueryInflightHandle {
        /**
         * Returns {@code true} if consolidation should take place, {@code false} otherwise.
         *
         * @param queryStartStamp   the {@link System#nanoTime()} when the original query was done.
         * @return                  {@code true} if consolidation should be done, {@code false} otherwise and so an
         *                          extra query will be performed.
         */
        boolean consolidate(long queryStartStamp);

        /**
         * Called once the original inflight query was completed and there will be no more consolidations for the
         * original {@link DnsQuestion}.
         */
        default void complete() {
            // NOOP.
        }
    }
}
