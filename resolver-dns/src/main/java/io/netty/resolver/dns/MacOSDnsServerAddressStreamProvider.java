/*
 * Copyright 2019 The Netty Project
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

import io.netty.util.NetUtil;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider.DNS_PORT;

/**
 * Able to parse output of <a href="https://www.unix.com/man-page/osx/8/scutil/">scutil --dns</a> to respect
 * nameserver configuration.
 */
@UnstableApi
public final class MacOSDnsServerAddressStreamProvider implements DnsServerAddressStreamProvider {
    private static final Pattern SCUTIL_PATTERN = Pattern.compile("^(.+):(.+)$");

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(MacOSDnsServerAddressStreamProvider.class);
    private final Map<String, DnsServerAddresses> mappings = buildAddressMap();
    private final DnsServerAddressStreamProvider fallbackProvider =
            UnixResolverDnsServerAddressStreamProvider.parseSilently();

    @Override
    public DnsServerAddressStream nameServerAddressStream(final String hostname) {
        if (mappings.isEmpty()) {
            return fallbackProvider.nameServerAddressStream(hostname);
        }

        String name = hostname;
        for (;;) {
            int i = name.indexOf('.', 1);
            if (i < 0 || i == name.length() - 1) {
                return fallbackProvider.nameServerAddressStream(hostname);
            }

            DnsServerAddresses addresses = mappings.get(hostname);
            if (addresses != null) {
                return addresses.stream();
            }

            name = hostname.substring(i + 1);
        }
    }

    private static Map<String, DnsServerAddresses> buildAddressMap() {
        Object result = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    return Runtime.getRuntime().exec("scutil --dns");
                } catch (IOException e) {
                    return e;
                }
            }
        });
        if (result instanceof Throwable) {
            logger.debug("Unable to execute 'scutil --dns'", (Throwable) result);
            return Collections.emptyMap();
        }

        try {
            return parse(((Process) result).getInputStream());
        } catch (IOException e) {
            logger.debug("Unable to parse output of 'scutil --dns'", e);
            return Collections.emptyMap();
        }
    }

    // package-private for testing.
    static Map<String, DnsServerAddresses> parse(InputStream in) throws IOException {
            Map<String, DnsServerAddresses> domainToNameServerStreamMap =
                    new HashMap<String, DnsServerAddresses>(10);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        try {
            List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>(2);
            String domainName = null;
            int port = DNS_PORT;
            boolean skip = false;
            String line;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("resolver #")) {
                    if (!skip && domainName != null && !addresses.isEmpty()) {
                        domainToNameServerStreamMap.put(domainName, DnsServerAddresses.sequential(addresses));
                    }

                    // Reset for next entry
                    addresses = new ArrayList<InetSocketAddress>(2);
                    domainName = null;
                    port = DNS_PORT;
                    skip = false;
                } else {
                    Matcher matcher = SCUTIL_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        String key = matcher.group(1).trim();
                        String value = matcher.group(2).trim();

                        if (key.startsWith("nameserver")) {
                            String maybeIP = value;
                            // There may be a port appended onto the IP address so we attempt to extract it.
                            if (!NetUtil.isValidIpV4Address(maybeIP) && !NetUtil.isValidIpV6Address(maybeIP)) {
                                int i = maybeIP.lastIndexOf('.');
                                if (i + 1 >= maybeIP.length()) {
                                    throw new IllegalArgumentException();
                                }
                                port = Integer.parseInt(maybeIP.substring(i + 1));
                                maybeIP = maybeIP.substring(0, i);
                            }
                            addresses.add(SocketUtils.socketAddress(maybeIP, port));
                        } else if (key.equals("domain")) {
                            domainName = value;
                        } else if (key.equals("options")) {
                            if (value.equals("mdns")) {
                                skip = true;
                            }
                        }
                    }
                }
            }
            if (!skip && domainName != null && !addresses.isEmpty()) {
                domainToNameServerStreamMap.put(domainName, DnsServerAddresses.sequential(addresses));
            }
        } finally {
            br.close();
        }
        return domainToNameServerStreamMap;
    }
}
