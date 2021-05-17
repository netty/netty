/*
 * Copyright 2019 The Netty Project
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
package io.netty.resolver.dns.macos;

import io.netty.resolver.dns.DnsServerAddressStream;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.resolver.dns.DnsServerAddresses;
import io.netty.util.internal.ClassInitializerUtil;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link DnsServerAddressStreamProvider} implementation which makes use of the same mechanism as
 * <a href="https://opensource.apple.com/tarballs/mDNSResponder/">Apple's open source mDNSResponder</a> to retrieve the
 * current nameserver configuration of the system.
 */
public final class MacOSDnsServerAddressStreamProvider implements DnsServerAddressStreamProvider {

    private static final Comparator<DnsResolver> RESOLVER_COMPARATOR =
            new Comparator<DnsResolver>() {
                @Override
                public int compare(DnsResolver r1, DnsResolver r2) {
                    // Note: order is descending (from higher to lower) so entries with lower search order override
                    // entries with higher search order.
                    return r1.searchOrder() < r2.searchOrder() ? 1 : (r1.searchOrder() == r2.searchOrder() ? 0 : -1);
                }
            };

    private static final Throwable UNAVAILABILITY_CAUSE;

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(MacOSDnsServerAddressStreamProvider.class);

    // Let's refresh every 10 seconds.
    private static final long REFRESH_INTERVAL = TimeUnit.SECONDS.toNanos(10);

    static {
        // Preload all classes that will be used in the OnLoad(...) function of JNI to eliminate the possiblity of a
        // class-loader deadlock. This is a workaround for https://github.com/netty/netty/issues/11209.

        // This needs to match all the classes that are loaded via NETTY_JNI_UTIL_LOAD_CLASS or looked up via
        // NETTY_JNI_UTIL_FIND_CLASS.
        ClassInitializerUtil.tryLoadClasses(MacOSDnsServerAddressStreamProvider.class,
                // netty_resolver_dns_macos
                byte[].class, String.class
        );

        Throwable cause = null;
        try {
            loadNativeLibrary();
        } catch (Throwable error) {
            cause = error;
        }
        UNAVAILABILITY_CAUSE = cause;
    }

    private static void loadNativeLibrary() {
        if (!PlatformDependent.isOsx()) {
            throw new IllegalStateException("Only supported on MacOS/OSX");
        }
        String staticLibName = "netty_resolver_dns_native_macos";
        String sharedLibName = staticLibName + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(MacOSDnsServerAddressStreamProvider.class);
        try {
            NativeLibraryLoader.load(sharedLibName, cl);
        } catch (UnsatisfiedLinkError e1) {
            try {
                NativeLibraryLoader.load(staticLibName, cl);
                logger.debug("Failed to load {}", sharedLibName, e1);
            } catch (UnsatisfiedLinkError e2) {
                ThrowableUtil.addSuppressed(e1, e2);
                throw e1;
            }
        }
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

    public MacOSDnsServerAddressStreamProvider() {
        ensureAvailability();
    }

    private volatile Map<String, DnsServerAddresses> currentMappings = retrieveCurrentMappings();
    private final AtomicLong lastRefresh = new AtomicLong(System.nanoTime());

    private static Map<String, DnsServerAddresses> retrieveCurrentMappings() {
        DnsResolver[] resolvers = resolvers();

        if (resolvers == null || resolvers.length == 0) {
            return Collections.emptyMap();
        }
        Arrays.sort(resolvers, RESOLVER_COMPARATOR);
        Map<String, DnsServerAddresses> resolverMap = new HashMap<String, DnsServerAddresses>(resolvers.length);
        for (DnsResolver resolver: resolvers) {
            // Skip mdns
            if ("mdns".equalsIgnoreCase(resolver.options())) {
                continue;
            }
            InetSocketAddress[] nameservers = resolver.nameservers();
            if (nameservers == null || nameservers.length == 0) {
                continue;
            }
            String domain = resolver.domain();
            if (domain == null) {
                // Default mapping.
                domain = StringUtil.EMPTY_STRING;
            }
            InetSocketAddress[] servers = resolver.nameservers();
            for (int a = 0; a < servers.length; a++) {
                InetSocketAddress address = servers[a];
                // Check if the default port should be used
                if (address.getPort() == 0) {
                    int port = resolver.port();
                    if (port == 0) {
                        port = 53;
                    }
                    servers[a] = new InetSocketAddress(address.getAddress(), port);
                }
            }

            resolverMap.put(domain, DnsServerAddresses.sequential(servers));
        }
        return resolverMap;
    }

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

    private static native DnsResolver[] resolvers();
}
