/*
 * Copyright 2016 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.channel.EventLoop;

/**
 * Default {@link DnsNameResolverBuilder} that build a standard {@link DnsNameResolver}.
 */
public final class DnsNameResolverBuilder extends AbstractDnsNameResolverBuilder<DnsNameResolverBuilder> {

    /**
     * Creates a new builder.
     *
     * @param eventLoop the {@link EventLoop} the {@link EventLoop} which will perform the communication with the DNS
     *                  servers.
     */
    public DnsNameResolverBuilder(EventLoop eventLoop) {
        super(eventLoop);
    }

    @Override
    protected DnsNameResolver build0(DnsCache cache) {
        return new DnsNameResolver(
                eventLoop,
                channelFactory,
                localAddress,
                nameServerAddresses,
                cache,
                queryTimeoutMillis,
                resolvedAddressTypes,
                recursionDesired,
                maxQueriesPerResolve,
                traceEnabled,
                maxPayloadSize,
                optResourceEnabled,
                hostsFileEntriesResolver);
    }
}
