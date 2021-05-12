/*
 * Copyright 2021 The Netty Project
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
package io.netty.resolver;

import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A container of hosts file entries
 */
public final class HostsFileEntriesProvider {

    public interface Parser {

        /**
         * Parses the hosts file at standard OS location using the system default {@link Charset} for decoding.
         *
         * @return a new {@link HostsFileEntriesProvider}
         * @throws IOException file could not be read
         */
        HostsFileEntriesProvider parse() throws IOException;

        /**
         * Parses the hosts file at standard OS location using the given {@link Charset}s one after another until
         * parse something or none is left.
         *
         * @param charsets the {@link Charset}s to try as file encodings when parsing
         * @return a new {@link HostsFileEntriesProvider}
         * @throws IOException file could not be read
         */
        HostsFileEntriesProvider parse(Charset... charsets) throws IOException;

        /**
         * Parses the provided hosts file using the given {@link Charset}s one after another until
         * parse something or none is left. In case {@link Charset}s are not provided,
         * the system default {@link Charset} is used for decoding.
         *
         * @param file the file to be parsed
         * @param charsets the {@link Charset}s to try as file encodings when parsing, in case {@link Charset}s
         * are not provided, the system default {@link Charset} is used for decoding
         * @return a new {@link HostsFileEntriesProvider}
         * @throws IOException file could not be read
         */
        HostsFileEntriesProvider parse(File file, Charset... charsets) throws IOException;

        /**
         * Performs the parsing operation using the provided reader of hosts file format.
         *
         * @param reader the reader of hosts file format
         * @return a new {@link HostsFileEntriesProvider}
         */
        HostsFileEntriesProvider parse(Reader reader) throws IOException;

        /**
         * Parses the hosts file at standard OS location using the system default {@link Charset} for decoding.
         *
         * @return a new {@link HostsFileEntriesProvider}
         */
        HostsFileEntriesProvider parseSilently();

        /**
         * Parses the hosts file at standard OS location using the given {@link Charset}s one after another until
         * parse something or none is left.
         *
         * @param charsets the {@link Charset}s to try as file encodings when parsing
         * @return a new {@link HostsFileEntriesProvider}
         */
        HostsFileEntriesProvider parseSilently(Charset... charsets);

        /**
         * Parses the provided hosts file using the given {@link Charset}s one after another until
         * parse something or none is left. In case {@link Charset}s are not provided,
         * the system default {@link Charset} is used for decoding.
         *
         * @param file the file to be parsed
         * @param charsets the {@link Charset}s to try as file encodings when parsing, in case {@link Charset}s
         * are not provided, the system default {@link Charset} is used for decoding
         * @return a new {@link HostsFileEntriesProvider}
         */
        HostsFileEntriesProvider parseSilently(File file, Charset... charsets);
    }

    /**
     * Creates a parser for {@link HostsFileEntriesProvider}.
     *
     * @return a new {@link HostsFileEntriesProvider.Parser}
     */
    public static Parser parser() {
        return new ParserImpl();
    }

    static final HostsFileEntriesProvider EMPTY =
            new HostsFileEntriesProvider(
                    Collections.<String, List<InetAddress>>emptyMap(),
                    Collections.<String, List<InetAddress>>emptyMap());

    private final Map<String, List<InetAddress>> ipv4Entries;
    private final Map<String, List<InetAddress>> ipv6Entries;

    HostsFileEntriesProvider(Map<String, List<InetAddress>> ipv4Entries, Map<String, List<InetAddress>> ipv6Entries) {
        this.ipv4Entries = Collections.unmodifiableMap(new HashMap<String, List<InetAddress>>(ipv4Entries));
        this.ipv6Entries = Collections.unmodifiableMap(new HashMap<String, List<InetAddress>>(ipv6Entries));
    }

    /**
     * The IPv4 entries.
     *
     * @return the IPv4 entries
     */
    public Map<String, List<InetAddress>> ipv4Entries() {
        return ipv4Entries;
    }

    /**
     * The IPv6 entries.
     *
     * @return the IPv6 entries
     */
    public Map<String, List<InetAddress>> ipv6Entries() {
        return ipv6Entries;
    }

    private static final class ParserImpl implements Parser {

        private static final String WINDOWS_DEFAULT_SYSTEM_ROOT = "C:\\Windows";
        private static final String WINDOWS_HOSTS_FILE_RELATIVE_PATH = "\\system32\\drivers\\etc\\hosts";
        private static final String X_PLATFORMS_HOSTS_FILE_PATH = "/etc/hosts";

        private static final Pattern WHITESPACES = Pattern.compile("[ \t]+");

        private static final InternalLogger logger = InternalLoggerFactory.getInstance(Parser.class);

        @Override
        public HostsFileEntriesProvider parse() throws IOException {
            return parse(locateHostsFile(), Charset.defaultCharset());
        }

        @Override
        public HostsFileEntriesProvider parse(Charset... charsets) throws IOException {
            return parse(locateHostsFile(), charsets);
        }

        @Override
        public HostsFileEntriesProvider parse(File file, Charset... charsets) throws IOException {
            checkNotNull(file, "file");
            checkNotNull(charsets, "charsets");
            if (charsets.length == 0) {
                charsets = new Charset[]{Charset.defaultCharset()};
            }
            if (file.exists() && file.isFile()) {
                for (Charset charset : charsets) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), charset));
                    try {
                        HostsFileEntriesProvider entries = parse(reader);
                        if (entries != HostsFileEntriesProvider.EMPTY) {
                            return entries;
                        }
                    } finally {
                        reader.close();
                    }
                }
            }
            return HostsFileEntriesProvider.EMPTY;
        }

        @Override
        public HostsFileEntriesProvider parse(Reader reader) throws IOException {
            checkNotNull(reader, "reader");
            BufferedReader buff = new BufferedReader(reader);
            try {
                Map<String, List<InetAddress>> ipv4Entries = new HashMap<String, List<InetAddress>>();
                Map<String, List<InetAddress>> ipv6Entries = new HashMap<String, List<InetAddress>>();
                String line;
                while ((line = buff.readLine()) != null) {
                    // remove comment
                    int commentPosition = line.indexOf('#');
                    if (commentPosition != -1) {
                        line = line.substring(0, commentPosition);
                    }
                    // skip empty lines
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    // split
                    List<String> lineParts = new ArrayList<String>();
                    for (String s : WHITESPACES.split(line)) {
                        if (!s.isEmpty()) {
                            lineParts.add(s);
                        }
                    }

                    // a valid line should be [IP, hostname, alias*]
                    if (lineParts.size() < 2) {
                        // skip invalid line
                        continue;
                    }

                    byte[] ipBytes = NetUtil.createByteArrayFromIpAddressString(lineParts.get(0));

                    if (ipBytes == null) {
                        // skip invalid IP
                        continue;
                    }

                    // loop over hostname and aliases
                    for (int i = 1; i < lineParts.size(); i++) {
                        String hostname = lineParts.get(i);
                        String hostnameLower = hostname.toLowerCase(Locale.ENGLISH);
                        InetAddress address = InetAddress.getByAddress(hostname, ipBytes);
                        List<InetAddress> addresses;
                        if (address instanceof Inet4Address) {
                            addresses = ipv4Entries.get(hostnameLower);
                            if (addresses == null) {
                                addresses = new ArrayList<InetAddress>();
                                ipv4Entries.put(hostnameLower, addresses);
                            }
                        } else {
                            addresses = ipv6Entries.get(hostnameLower);
                            if (addresses == null) {
                                addresses = new ArrayList<InetAddress>();
                                ipv6Entries.put(hostnameLower, addresses);
                            }
                        }
                        addresses.add(address);
                    }
                }
                return ipv4Entries.isEmpty() && ipv6Entries.isEmpty() ?
                        HostsFileEntriesProvider.EMPTY :
                        new HostsFileEntriesProvider(ipv4Entries, ipv6Entries);
            } finally {
                try {
                    buff.close();
                } catch (IOException e) {
                    logger.warn("Failed to close a reader", e);
                }
            }
        }

        @Override
        public HostsFileEntriesProvider parseSilently() {
            return parseSilently(locateHostsFile(), Charset.defaultCharset());
        }

        @Override
        public HostsFileEntriesProvider parseSilently(Charset... charsets) {
            return parseSilently(locateHostsFile(), charsets);
        }

        @Override
        public HostsFileEntriesProvider parseSilently(File file, Charset... charsets) {
            try {
                return parse(file, charsets);
            } catch (IOException e) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to load and parse hosts file at " + file.getPath(), e);
                }
                return HostsFileEntriesProvider.EMPTY;
            }
        }

        private static File locateHostsFile() {
            File hostsFile;
            if (PlatformDependent.isWindows()) {
                hostsFile = new File(System.getenv("SystemRoot") + WINDOWS_HOSTS_FILE_RELATIVE_PATH);
                if (!hostsFile.exists()) {
                    hostsFile = new File(WINDOWS_DEFAULT_SYSTEM_ROOT + WINDOWS_HOSTS_FILE_RELATIVE_PATH);
                }
            } else {
                hostsFile = new File(X_PLATFORMS_HOSTS_FILE_PATH);
            }
            return hostsFile;
        }
    }
}
