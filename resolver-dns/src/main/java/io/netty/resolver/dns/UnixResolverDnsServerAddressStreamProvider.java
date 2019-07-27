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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider.DNS_PORT;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.StringUtil.indexOfNonWhiteSpace;

/**
 * Able to parse files such as <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a> and
 * <a href="https://developer.apple.com/legacy/library/documentation/Darwin/Reference/ManPages/man5/resolver.5.html">
 * /etc/resolver</a> to respect the system default domain servers.
 */
@UnstableApi
public final class UnixResolverDnsServerAddressStreamProvider implements DnsServerAddressStreamProvider {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(UnixResolverDnsServerAddressStreamProvider.class);
    private static final String ETC_RESOLV_CONF_FILE = "/etc/resolv.conf";
    private static final String ETC_RESOLVER_DIR = "/etc/resolver";
    private static final String NAMESERVER_ROW_LABEL = "nameserver";
    private static final String SORTLIST_ROW_LABEL = "sortlist";
    private static final String OPTIONS_ROW_LABEL = "options";
    private static final String DOMAIN_ROW_LABEL = "domain";
    private static final String SEARCH_ROW_LABEL = "search";
    private static final String PORT_ROW_LABEL = "port";
    private static final String NDOTS_LABEL = "ndots:";
    static final int DEFAULT_NDOTS = 1;
    private final DnsServerAddresses defaultNameServerAddresses;
    private final Map<String, DnsServerAddresses> domainToNameServerStreamMap;
    private static final Pattern SEARCH_DOMAIN_PATTERN = Pattern.compile("\\s+");

    /**
     * Attempt to parse {@code /etc/resolv.conf} and files in the {@code /etc/resolver} directory by default.
     * A failure to parse will return {@link DefaultDnsServerAddressStreamProvider}.
     */
    static DnsServerAddressStreamProvider parseSilently() {
        try {
            UnixResolverDnsServerAddressStreamProvider nameServerCache =
                    new UnixResolverDnsServerAddressStreamProvider(ETC_RESOLV_CONF_FILE, ETC_RESOLVER_DIR);
            return nameServerCache.mayOverrideNameServers() ? nameServerCache
                                                            : DefaultDnsServerAddressStreamProvider.INSTANCE;
        } catch (Exception e) {
            logger.debug("failed to parse {} and/or {}", ETC_RESOLV_CONF_FILE, ETC_RESOLVER_DIR, e);
            return DefaultDnsServerAddressStreamProvider.INSTANCE;
        }
    }

    /**
     * Parse a file of the format <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a> which may contain
     * the default DNS server to use, and also overrides for individual domains. Also parse list of files of the format
     * <a href="
     * https://developer.apple.com/legacy/library/documentation/Darwin/Reference/ManPages/man5/resolver.5.html">
     * /etc/resolver</a> which may contain multiple files to override the name servers used for multimple domains.
     * @param etcResolvConf <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a>.
     * @param etcResolverFiles List of files of the format defined in
     * <a href="
     * https://developer.apple.com/legacy/library/documentation/Darwin/Reference/ManPages/man5/resolver.5.html">
     * /etc/resolver</a>.
     * @throws IOException If an error occurs while parsing the input files.
     */
    public UnixResolverDnsServerAddressStreamProvider(File etcResolvConf, File... etcResolverFiles) throws IOException {
        Map<String, DnsServerAddresses> etcResolvConfMap = parse(checkNotNull(etcResolvConf, "etcResolvConf"));
        final boolean useEtcResolverFiles = etcResolverFiles != null && etcResolverFiles.length != 0;
        domainToNameServerStreamMap = useEtcResolverFiles ? parse(etcResolverFiles) : etcResolvConfMap;

        DnsServerAddresses defaultNameServerAddresses = etcResolvConfMap.get(etcResolvConf.getName());
        if (defaultNameServerAddresses == null) {
            Collection<DnsServerAddresses> values = etcResolvConfMap.values();
            if (values.isEmpty()) {
                throw new IllegalArgumentException(etcResolvConf + " didn't provide any name servers");
            }
            this.defaultNameServerAddresses = values.iterator().next();
        } else {
            this.defaultNameServerAddresses = defaultNameServerAddresses;
        }

        if (useEtcResolverFiles) {
            domainToNameServerStreamMap.putAll(etcResolvConfMap);
        }
    }

    /**
     * Parse a file of the format <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a> which may contain
     * the default DNS server to use, and also overrides for individual domains. Also parse a directory of the format
     * <a href="
     * https://developer.apple.com/legacy/library/documentation/Darwin/Reference/ManPages/man5/resolver.5.html">
     * /etc/resolver</a> which may contain multiple files to override the name servers used for multimple domains.
     * @param etcResolvConf <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a>.
     * @param etcResolverDir Directory containing files of the format defined in
     * <a href="
     * https://developer.apple.com/legacy/library/documentation/Darwin/Reference/ManPages/man5/resolver.5.html">
     * /etc/resolver</a>.
     * @throws IOException If an error occurs while parsing the input files.
     */
    public UnixResolverDnsServerAddressStreamProvider(String etcResolvConf, String etcResolverDir) throws IOException {
        this(etcResolvConf == null ? null : new File(etcResolvConf),
             etcResolverDir == null ? null : new File(etcResolverDir).listFiles());
    }

    @Override
    public DnsServerAddressStream nameServerAddressStream(String hostname) {
        for (;;) {
            int i = hostname.indexOf('.', 1);
            if (i < 0 || i == hostname.length() - 1) {
                return defaultNameServerAddresses.stream();
            }

            DnsServerAddresses addresses = domainToNameServerStreamMap.get(hostname);
            if (addresses != null) {
                return addresses.stream();
            }

            hostname = hostname.substring(i + 1);
        }
    }

    private boolean mayOverrideNameServers() {
        return !domainToNameServerStreamMap.isEmpty() || defaultNameServerAddresses.stream().next() != null;
    }

    private static Map<String, DnsServerAddresses> parse(File... etcResolverFiles) throws IOException {
        Map<String, DnsServerAddresses> domainToNameServerStreamMap =
                new HashMap<String, DnsServerAddresses>(etcResolverFiles.length << 1);
        for (File etcResolverFile : etcResolverFiles) {
            if (!etcResolverFile.isFile()) {
                continue;
            }
            FileReader fr = new FileReader(etcResolverFile);
            BufferedReader br = null;
            try {
                br = new BufferedReader(fr);
                List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>(2);
                String domainName = etcResolverFile.getName();
                int port = DNS_PORT;
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    char c;
                    if (line.isEmpty() || (c = line.charAt(0)) == '#' || c == ';') {
                        continue;
                    }
                    if (line.startsWith(NAMESERVER_ROW_LABEL)) {
                        int i = indexOfNonWhiteSpace(line, NAMESERVER_ROW_LABEL.length());
                        if (i < 0) {
                            throw new IllegalArgumentException("error parsing label " + NAMESERVER_ROW_LABEL +
                                    " in file " + etcResolverFile + ". value: " + line);
                        }
                        String maybeIP = line.substring(i);
                        // There may be a port appended onto the IP address so we attempt to extract it.
                        if (!NetUtil.isValidIpV4Address(maybeIP) && !NetUtil.isValidIpV6Address(maybeIP)) {
                            i = maybeIP.lastIndexOf('.');
                            if (i + 1 >= maybeIP.length()) {
                                throw new IllegalArgumentException("error parsing label " + NAMESERVER_ROW_LABEL +
                                        " in file " + etcResolverFile + ". invalid IP value: " + line);
                            }
                            port = Integer.parseInt(maybeIP.substring(i + 1));
                            maybeIP = maybeIP.substring(0, i);
                        }
                        addresses.add(SocketUtils.socketAddress(maybeIP, port));
                    } else if (line.startsWith(DOMAIN_ROW_LABEL)) {
                        int i = indexOfNonWhiteSpace(line, DOMAIN_ROW_LABEL.length());
                        if (i < 0) {
                            throw new IllegalArgumentException("error parsing label " + DOMAIN_ROW_LABEL +
                                    " in file " + etcResolverFile + " value: " + line);
                        }
                        domainName = line.substring(i);
                        if (!addresses.isEmpty()) {
                            putIfAbsent(domainToNameServerStreamMap, domainName, addresses);
                        }
                        addresses = new ArrayList<InetSocketAddress>(2);
                    } else if (line.startsWith(PORT_ROW_LABEL)) {
                        int i = indexOfNonWhiteSpace(line, PORT_ROW_LABEL.length());
                        if (i < 0) {
                            throw new IllegalArgumentException("error parsing label " + PORT_ROW_LABEL +
                                    " in file " + etcResolverFile + " value: " + line);
                        }
                        port = Integer.parseInt(line.substring(i));
                    } else if (line.startsWith(SORTLIST_ROW_LABEL)) {
                        logger.info("row type {} not supported. ignoring line: {}", SORTLIST_ROW_LABEL, line);
                    }
                }
                if (!addresses.isEmpty()) {
                    putIfAbsent(domainToNameServerStreamMap, domainName, addresses);
                }
            } finally {
                if (br == null) {
                    fr.close();
                } else {
                    br.close();
                }
            }
        }
        return domainToNameServerStreamMap;
    }

    private static void putIfAbsent(Map<String, DnsServerAddresses> domainToNameServerStreamMap,
                                    String domainName,
                                    List<InetSocketAddress> addresses) {
        // TODO(scott): sortlist is being ignored.
        putIfAbsent(domainToNameServerStreamMap, domainName, DnsServerAddresses.sequential(addresses));
    }

    private static void putIfAbsent(Map<String, DnsServerAddresses> domainToNameServerStreamMap,
                                    String domainName,
                                    DnsServerAddresses addresses) {
        DnsServerAddresses existingAddresses = domainToNameServerStreamMap.put(domainName, addresses);
        if (existingAddresses != null) {
            domainToNameServerStreamMap.put(domainName, existingAddresses);
            logger.debug("Domain name {} already maps to addresses {} so new addresses {} will be discarded",
                    domainName, existingAddresses, addresses);
        }
    }

    /**
     * Parse a file of the format <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a> and return the
     * value corresponding to the first ndots in an options configuration.
     * @return the value corresponding to the first ndots in an options configuration, or {@link #DEFAULT_NDOTS} if not
     * found.
     * @throws IOException If a failure occurs parsing the file.
     */
    static int parseEtcResolverFirstNdots() throws IOException {
        return parseEtcResolverFirstNdots(new File(ETC_RESOLV_CONF_FILE));
    }

    /**
     * Parse a file of the format <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a> and return the
     * value corresponding to the first ndots in an options configuration.
     * @param etcResolvConf a file of the format <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a>.
     * @return the value corresponding to the first ndots in an options configuration, or {@link #DEFAULT_NDOTS} if not
     * found.
     * @throws IOException If a failure occurs parsing the file.
     */
    static int parseEtcResolverFirstNdots(File etcResolvConf) throws IOException {
        FileReader fr = new FileReader(etcResolvConf);
        BufferedReader br = null;
        try {
            br = new BufferedReader(fr);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(OPTIONS_ROW_LABEL)) {
                    int i = line.indexOf(NDOTS_LABEL);
                    if (i >= 0) {
                        i += NDOTS_LABEL.length();
                        final int j = line.indexOf(' ', i);
                        return Integer.parseInt(line.substring(i, j < 0 ? line.length() : j));
                    }
                    break;
                }
            }
        } finally {
            if (br == null) {
                fr.close();
            } else {
                br.close();
            }
        }
        return DEFAULT_NDOTS;
    }

    /**
     * Parse a file of the format <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a> and return the
     * list of search domains found in it or an empty list if not found.
     * @return List of search domains.
     * @throws IOException If a failure occurs parsing the file.
     */
    static List<String> parseEtcResolverSearchDomains() throws IOException {
        return parseEtcResolverSearchDomains(new File(ETC_RESOLV_CONF_FILE));
    }

    /**
     * Parse a file of the format <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a> and return the
     * list of search domains found in it or an empty list if not found.
     * @param etcResolvConf a file of the format <a href="https://linux.die.net/man/5/resolver">/etc/resolv.conf</a>.
     * @return List of search domains.
     * @throws IOException If a failure occurs parsing the file.
     */
    static List<String> parseEtcResolverSearchDomains(File etcResolvConf) throws IOException {
        String localDomain = null;
        List<String> searchDomains = new ArrayList<String>();

        FileReader fr = new FileReader(etcResolvConf);
        BufferedReader br = null;
        try {
            br = new BufferedReader(fr);
            String line;
            while ((line = br.readLine()) != null) {
                if (localDomain == null && line.startsWith(DOMAIN_ROW_LABEL)) {
                    int i = indexOfNonWhiteSpace(line, DOMAIN_ROW_LABEL.length());
                    if (i >= 0) {
                        localDomain = line.substring(i);
                    }
                } else if (line.startsWith(SEARCH_ROW_LABEL)) {
                    int i = indexOfNonWhiteSpace(line, SEARCH_ROW_LABEL.length());
                    if (i >= 0) {
                        // May contain more then one entry, either seperated by whitespace or tab.
                        // See https://linux.die.net/man/5/resolver
                        String[] domains = SEARCH_DOMAIN_PATTERN.split(line.substring(i));
                        Collections.addAll(searchDomains, domains);
                    }
                }
            }
        } finally {
            if (br == null) {
                fr.close();
            } else {
                br.close();
            }
        }

        // return what was on the 'domain' line only if there were no 'search' lines
        return localDomain != null && searchDomains.isEmpty()
                ? Collections.singletonList(localDomain)
                : searchDomains;
    }
}
