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
package io.netty.resolver.dns;

import java.net.InetSocketAddress;

/**
 * An infinite stream of DNS server addresses, that requests feedback to be returned to it.
 *
 * If query is successful timing information is provided, else a failure notification is given.
 */
public interface DnsServerResponseFeedbackAddressStream extends DnsServerAddressStream {

    /**
     * A way to provide success feedback to {@link DnsServerAddressStream} so that {@link #next()} can be tuned
     * to return the best performing DNS server address
     *
     * NOTE: This is called regardless of the RCode returned by the DNS server
     *
     * @param address The address returned by {@link #next()} that feedback needs to be applied to
     * @param queryResponseTimeNanos The response time of a query against the given DNS server
     */
    void feedbackSuccess(InetSocketAddress address, long queryResponseTimeNanos);

    /**
     * A way to provide failure feedback to {@link DnsServerAddressStream} so that {@link #next()} cab be tuned
     * to return the best performing DNS server address
     *
     * @param address The address returned by {@link #next()} that feedback needs to be applied to
     * @param failureCause The reason the DNS query failed, can be used to penalize failures differently
     * @param queryResponseTimeNanos The response time of a query against the given DNS server
     */
    void feedbackFailure(InetSocketAddress address, Throwable failureCause, long queryResponseTimeNanos);
}
