/*
 * Copyright 2018 The Netty Project
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

import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Hashtable;
import java.util.List;

final class DirContextUtils {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(DirContextUtils.class);

    private DirContextUtils() { }

    /**
     * On IBM i the JNDI lookup ends up in {@code LoadDnsConfig}, which needs special authority
     * ({@code *IOSYSCFG}) and can terminate the whole JVM when it is missing. See
     * <a href="https://github.com/netty/netty/issues/17396">#17396</a>.
     */
    static boolean isIbmI(String osName) {
        return "OS/400".equalsIgnoreCase(osName);
    }

    static void addNameServers(List<InetSocketAddress> defaultNameServers, int defaultPort) {
        if (isIbmI(SystemPropertyUtil.get("os.name", ""))) {
            logger.debug("Not obtaining nameservers via InitialDirContext on IBM i");
            return;
        }
        // Using jndi-dns to obtain the default name servers.
        //
        // See:
        // - https://docs.oracle.com/javase/8/docs/technotes/guides/jndi/jndi-dns.html
        // - https://mail.openjdk.java.net/pipermail/net-dev/2017-March/010695.html
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
                        String host = uri.getHost();
                        if (host == null || host.isEmpty()) {
                            logger.debug(
                                    "Skipping a nameserver URI as host portion could not be extracted: {}", server);
                            // If the host portion can not be parsed we should just skip this entry.
                            continue;
                        }
                        int port  = uri.getPort();
                        defaultNameServers.add(SocketUtils.socketAddress(uri.getHost(), port == -1 ?
                                defaultPort : port));
                    } catch (URISyntaxException e) {
                        logger.debug("Skipping a malformed nameserver URI: {}", server, e);
                    }
                }
            }
        } catch (Exception ex) {
            // Will try reflection if this fails.
            logger.debug("Unable to obtain nameservers via InitialDirContext", ex);
        }
    }
}
