/*
 * Copyright 2017 The Netty Project
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

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.UnstableApi;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility methods related to {@link DnsServerAddressStreamProvider}.
 */
@UnstableApi
public final class DnsServerAddressStreamProviders {
    // We use 5 minutes which is the same as what OpenJDK is using in sun.net.dns.ResolverConfigurationImpl.
    private static final long REFRESH_INTERVAL = TimeUnit.MINUTES.toNanos(5);

    // TODO(scott): how is this done on Windows? This may require a JNI call to GetNetworkParams
    // https://msdn.microsoft.com/en-us/library/aa365968(VS.85).aspx.
    private static final DnsServerAddressStreamProvider DEFAULT_DNS_SERVER_ADDRESS_STREAM_PROVIDER =
            new DnsServerAddressStreamProvider() {
        private volatile DnsServerAddressStreamProvider currentProvider = provider();
        private final AtomicLong lastRefresh = new AtomicLong(System.nanoTime());

        @Override
        public DnsServerAddressStream nameServerAddressStream(String hostname) {
            long last = lastRefresh.get();
            DnsServerAddressStreamProvider current = currentProvider;
            if (System.nanoTime() - last > REFRESH_INTERVAL) {
                // This is slightly racy which means it will be possible still use the old configuration for a small
                // amount of time, but that's ok.
                if (lastRefresh.compareAndSet(last, System.nanoTime())) {
                    current = currentProvider = provider();
                }
            }
            return current.nameServerAddressStream(hostname);
        }

        private DnsServerAddressStreamProvider provider() {
            // If on windows just use the DefaultDnsServerAddressStreamProvider.INSTANCE as otherwise
            // we will log some error which may be confusing.
            return PlatformDependent.isWindows() ? DefaultDnsServerAddressStreamProvider.INSTANCE :
                    UnixResolverDnsServerAddressStreamProvider.parseSilently();
        }
    };

    private DnsServerAddressStreamProviders() {
    }

    /**
     * A {@link DnsServerAddressStreamProvider} which inherits the DNS servers from your local host's configuration.
     * <p>
     * Note that only macOS and Linux are currently supported.
     * @return A {@link DnsServerAddressStreamProvider} which inherits the DNS servers from your local host's
     * configuration.
     */
    public static DnsServerAddressStreamProvider platformDefault() {
        return DEFAULT_DNS_SERVER_ADDRESS_STREAM_PROVIDER;
    }
}
