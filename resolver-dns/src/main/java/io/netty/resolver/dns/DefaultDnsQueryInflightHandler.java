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
import io.netty.util.internal.ObjectUtil;

public final class DefaultDnsQueryInflightHandler implements DnsQueryInflightHandler {
    private final int maxConsolidated;
    private int inflight;
    private final Runnable decrementer = () -> inflight--;
    private static final Runnable NOOP = () -> { };

    public DefaultDnsQueryInflightHandler(int maxConsolidated) {
        this.maxConsolidated = ObjectUtil.checkPositive(maxConsolidated, "maxConsolidated");
    }

    @Override
    public Runnable handle(DnsQuestion question, long queryStartStamp) {
        if (queryStartStamp == 0) {
            // A new query let's see how many we have already inflight that are considered for consolidation.
            if (inflight < maxConsolidated) {
                inflight++;
                return decrementer;
            }
            return null;
        }
        // Use consolidation if possible.
        return NOOP;
    }
}
