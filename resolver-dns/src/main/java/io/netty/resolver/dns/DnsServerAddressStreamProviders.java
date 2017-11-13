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

/**
 * Utility methods related to {@link DnsServerAddressStreamProvider}.
 */
@UnstableApi
public final class DnsServerAddressStreamProviders {
    // TODO(scott): how is this done on Windows? This may require a JNI call to GetNetworkParams
    // https://msdn.microsoft.com/en-us/library/aa365968(VS.85).aspx.
    private static final DnsServerAddressStreamProvider DEFAULT_DNS_SERVER_ADDRESS_STREAM_PROVIDER =
            // If on windows just use the DefaultDnsServerAddressStreamProvider.INSTANCE as otherwise
            // we will log some error which may be confusing.
            PlatformDependent.isWindows() ? DefaultDnsServerAddressStreamProvider.INSTANCE :
                    UnixResolverDnsServerAddressStreamProvider.parseSilently();

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
