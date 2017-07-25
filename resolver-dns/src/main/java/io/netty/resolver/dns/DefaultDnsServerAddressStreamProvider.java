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

import io.netty.util.NetUtil;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import static io.netty.resolver.dns.DnsServerAddresses.sequential;

/**
 * A {@link DnsServerAddressStreamProvider} which will use predefined default DNS servers to use for DNS resolution.
 * These defaults do not respect your host's machines defaults.
 * <p>
 * This may use the JDK's blocking DNS resolution to bootstrap the default DNS server addresses.
 */
@UnstableApi
public final class DefaultDnsServerAddressStreamProvider implements DnsServerAddressStreamProvider {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(DefaultDnsServerAddressStreamProvider.class);
    public static final DefaultDnsServerAddressStreamProvider INSTANCE = new DefaultDnsServerAddressStreamProvider();

    private static final List<InetSocketAddress> DEFAULT_NAME_SERVER_LIST;
    private static final InetSocketAddress[] DEFAULT_NAME_SERVER_ARRAY;
    private static final DnsServerAddresses DEFAULT_NAME_SERVERS;
    static final int DNS_PORT = 53;

    static {
        final List<InetSocketAddress> defaultNameServers = new ArrayList<InetSocketAddress>(2);

        // Using jndi-dns to obtain the default name servers.
        //
        // See:
        // - http://docs.oracle.com/javase/8/docs/technotes/guides/jndi/jndi-dns.html
        // - http://mail.openjdk.java.net/pipermail/net-dev/2017-March/010695.html
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns://");
        try {
            DirContext ctx = new InitialDirContext(env);
            String dnsUrls = (String) ctx.getEnvironment().get("java.naming.provider.url");
            // Only try if not empty as otherwise we will produce an exception
            if (dnsUrls != null && !dnsUrls.isEmpty()) {
                String[] servers = dnsUrls.split(" ");
                for (String server : servers) {
                    try {
                        URI uri = new URI(server);
                        String host = new URI(server).getHost();

                        if (host == null || host.isEmpty()) {
                            logger.debug(
                                    "Skipping a nameserver URI as host portion could not be extracted: {}", server);
                            // If the host portion can not be parsed we should just skip this entry.
                            continue;
                        }
                        int port  = uri.getPort();
                        defaultNameServers.add(SocketUtils.socketAddress(uri.getHost(), port == -1 ? DNS_PORT : port));
                    } catch (URISyntaxException e) {
                        logger.debug("Skipping a malformed nameserver URI: {}", server, e);
                    }
                }
            }
        } catch (NamingException ignore) {
            // Will try reflection if this fails.
        }

        if (defaultNameServers.isEmpty()) {
            try {
                Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
                Method open = configClass.getMethod("open");
                Method nameservers = configClass.getMethod("nameservers");
                Object instance = open.invoke(null);

                @SuppressWarnings("unchecked")
                final List<String> list = (List<String>) nameservers.invoke(instance);
                for (String a: list) {
                    if (a != null) {
                        defaultNameServers.add(new InetSocketAddress(SocketUtils.addressByName(a), DNS_PORT));
                    }
                }
            } catch (Exception ignore) {
                // Failed to get the system name server list via reflection.
                // Will add the default name servers afterwards.
            }
        }

        if (!defaultNameServers.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Default DNS servers: {} (sun.net.dns.ResolverConfiguration)", defaultNameServers);
            }
        } else {
            // Depending if IPv6 or IPv4 is used choose the correct DNS servers provided by google:
            // https://developers.google.com/speed/public-dns/docs/using
            // https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html
            if (NetUtil.isIpV6AddressesPreferred() ||
                    (NetUtil.LOCALHOST instanceof Inet6Address && !NetUtil.isIpV4StackPreferred())) {
                Collections.addAll(
                        defaultNameServers,
                        SocketUtils.socketAddress("2001:4860:4860::8888", DNS_PORT),
                        SocketUtils.socketAddress("2001:4860:4860::8844", DNS_PORT));
            } else {
                Collections.addAll(
                        defaultNameServers,
                        SocketUtils.socketAddress("8.8.8.8", DNS_PORT),
                        SocketUtils.socketAddress("8.8.4.4", DNS_PORT));
            }

            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Default DNS servers: {} (Google Public DNS as a fallback)", defaultNameServers);
            }
        }

        DEFAULT_NAME_SERVER_LIST = Collections.unmodifiableList(defaultNameServers);
        DEFAULT_NAME_SERVER_ARRAY = defaultNameServers.toArray(new InetSocketAddress[defaultNameServers.size()]);
        DEFAULT_NAME_SERVERS = sequential(DEFAULT_NAME_SERVER_ARRAY);
    }

    private DefaultDnsServerAddressStreamProvider() {
    }

    @Override
    public DnsServerAddressStream nameServerAddressStream(String hostname) {
        return DEFAULT_NAME_SERVERS.stream();
    }

    /**
     * Returns the list of the system DNS server addresses. If it failed to retrieve the list of the system DNS server
     * addresses from the environment, it will return {@code "8.8.8.8"} and {@code "8.8.4.4"}, the addresses of the
     * Google public DNS servers.
     */
    public static List<InetSocketAddress> defaultAddressList() {
        return DEFAULT_NAME_SERVER_LIST;
    }

    /**
     * Returns the {@link DnsServerAddresses} that yields the system DNS server addresses sequentially. If it failed to
     * retrieve the list of the system DNS server addresses from the environment, it will use {@code "8.8.8.8"} and
     * {@code "8.8.4.4"}, the addresses of the Google public DNS servers.
     * <p>
     * This method has the same effect with the following code:
     * <pre>
     * DnsServerAddresses.sequential(DnsServerAddresses.defaultAddressList());
     * </pre>
     * </p>
     */
    public static DnsServerAddresses defaultAddresses() {
        return DEFAULT_NAME_SERVERS;
    }

    /**
     * Get the array form of {@link #defaultAddressList()}.
     * @return The array form of {@link #defaultAddressList()}.
     */
    static InetSocketAddress[] defaultAddressArray() {
        return DEFAULT_NAME_SERVER_ARRAY.clone();
    }
}
