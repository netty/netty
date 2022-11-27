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
package io.netty.resolver.dns.windows;

import io.netty.resolver.dns.DnsServerAddressStream;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.resolver.dns.DnsServerAddresses;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class WindowsResolverDnsServerAddressStreamProvider implements DnsServerAddressStreamProvider {
    private static final Throwable UNAVAILABILITY_CAUSE;
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(WindowsResolverDnsServerAddressStreamProvider.class);

    // Let's refresh every 10 seconds.
    private static final long REFRESH_INTERVAL = TimeUnit.SECONDS.toNanos(10);

    static {
        Throwable cause = null;
        try {
            loadNativeLibrary();
        } catch (Throwable error) {
            cause = error;
        }
        UNAVAILABILITY_CAUSE = cause;
    }

    private volatile Map<String, DnsServerAddresses> currentMappings;
    private final AtomicLong lastRefresh;

    public WindowsResolverDnsServerAddressStreamProvider() {
        ensureAvailability();
        currentMappings = retrieveCurrentMappings();
        lastRefresh = new AtomicLong(System.nanoTime());
    }

    private static void loadNativeLibrary() {
        if (!PlatformDependent.isWindows()) {
            throw new IllegalStateException("Only supported on Windows");
        }

        String sharedLibName = "netty_resolver_dns_native_windows" + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(WindowsResolverDnsServerAddressStreamProvider.class);
        NativeLibraryLoader.load(sharedLibName, cl);
    }

    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    public static void ensureAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            throw (Error) new UnsatisfiedLinkError(
                    "failed to load the required native library").initCause(UNAVAILABILITY_CAUSE);
        }
    }

    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }
    public static native NetworkAdapter[] adapters();

    @Override
    public DnsServerAddressStream nameServerAddressStream(String hostname) {
        long last = lastRefresh.get();
        Map<String, DnsServerAddresses> resolverMap = currentMappings;
        if (System.nanoTime() - last > REFRESH_INTERVAL) {
            // This is slightly racy which means it will be possible still use the old configuration for a small
            // amount of time, but that's ok.
            if (lastRefresh.compareAndSet(last, System.nanoTime())) {
                resolverMap = currentMappings = retrieveCurrentMappings();
            }
        }

        final String originalHostname = hostname;
        for (;;) {
            int i = hostname.indexOf('.', 1);
            if (i < 0 || i == hostname.length() - 1) {
                // Try access default mapping.
                DnsServerAddresses addresses = resolverMap.get(StringUtil.EMPTY_STRING);
                if (addresses != null) {
                    return addresses.stream();
                }
                return DnsServerAddressStreamProviders.unixDefault().nameServerAddressStream(originalHostname);
            }

            DnsServerAddresses addresses = resolverMap.get(hostname);
            if (addresses != null) {
                return addresses.stream();
            }

            hostname = hostname.substring(i + 1);
        }
    }

    private static Map<String, DnsServerAddresses> retrieveCurrentMappings() {
        NetworkAdapter[] resolvers = adapters();

        if (resolvers == null || resolvers.length == 0) {
            return Collections.emptyMap();
        }

        Map<String, DnsServerAddresses> resolverMap = new HashMap<>(resolvers.length);
        for (NetworkAdapter resolver: resolvers) {

            InetSocketAddress[] nameservers = resolver.nameservers();
            if (nameservers == null || nameservers.length == 0) {
                continue;
            }

            for(String domain : resolver.searchDomains()) {
                if (domain == null) {
                    // Default mapping.
                    domain = StringUtil.EMPTY_STRING;
                }
                InetSocketAddress[] servers = resolver.nameservers();
                for (int a = 0; a < servers.length; a++) {
                    InetSocketAddress address = servers[a];
                    // Check if the default port should be used
                    if (address.getPort() == 0) {
                        servers[a] = new InetSocketAddress(address.getAddress(), 53);
                    }
                }

                resolverMap.put(domain, DnsServerAddresses.sequential(servers));
            }
        }
        return resolverMap;
    }
}
