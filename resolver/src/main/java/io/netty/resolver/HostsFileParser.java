/*
 * Copyright 2015 The Netty Project
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
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * A parser for hosts files.
 */
public final class HostsFileParser {

    private static final String WINDOWS_DEFAULT_SYSTEM_ROOT = "C:\\Windows";
    private static final String WINDOWS_HOSTS_FILE_RELATIVE_PATH = "\\system32\\drivers\\etc\\hosts";
    private static final String X_PLATFORMS_HOSTS_FILE_PATH = "/etc/hosts";

    private static final Pattern WHITESPACES = Pattern.compile("[ \t]+");

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HostsFileParser.class);

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

    /**
     * Parse hosts file at standard OS location using the systems default {@link Charset} for decoding.
     *
     * @return a {@link HostsFileEntries}
     */
    public static HostsFileEntries parseSilently() {
        return parseSilently(Charset.defaultCharset());
    }

    /**
     * Parse hosts file at standard OS location using the given {@link Charset}s one after each other until
     * we were able to parse something or none is left.
     *
     * @param charsets the {@link Charset}s to try as file encodings when parsing.
     * @return a {@link HostsFileEntries}
     */
    public static HostsFileEntries parseSilently(Charset... charsets) {
        File hostsFile = locateHostsFile();
        try {
            return parse(hostsFile, charsets);
        } catch (IOException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to load and parse hosts file at " + hostsFile.getPath(), e);
            }
            return HostsFileEntries.EMPTY;
        }
    }

    /**
     * Parse hosts file at standard OS location using the system default {@link Charset} for decoding.
     *
     * @return a {@link HostsFileEntries}
     * @throws IOException file could not be read
     */
    public static HostsFileEntries parse() throws IOException {
        return parse(locateHostsFile());
    }

    /**
     * Parse a hosts file using the system default {@link Charset} for decoding.
     *
     * @param file the file to be parsed
     * @return a {@link HostsFileEntries}
     * @throws IOException file could not be read
     */
    public static HostsFileEntries parse(File file) throws IOException {
        return parse(file, Charset.defaultCharset());
    }

    /**
     * Parse a hosts file.
     *
     * @param file the file to be parsed
     * @param charsets the {@link Charset}s to try as file encodings when parsing.
     * @return a {@link HostsFileEntries}
     * @throws IOException file could not be read
     */
    public static HostsFileEntries parse(File file, Charset... charsets) throws IOException {
        checkNotNull(file, "file");
        checkNotNull(charsets, "charsets");
        if (file.exists() && file.isFile()) {
            for (Charset charset: charsets) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), charset));
                try {
                    HostsFileEntries entries = parse(reader);
                    if (entries != HostsFileEntries.EMPTY) {
                        return entries;
                    }
                } finally {
                    reader.close();
                }
            }
        }
        return HostsFileEntries.EMPTY;
    }

    /**
     * Parse a reader of hosts file format.
     *
     * @param reader the file to be parsed
     * @return a {@link HostsFileEntries}
     * @throws IOException file could not be read
     */
    public static HostsFileEntries parse(Reader reader) throws IOException {
        checkNotNull(reader, "reader");
        BufferedReader buff = new BufferedReader(reader);
        try {
            Map<String, Inet4Address> ipv4Entries = new HashMap<String, Inet4Address>();
            Map<String, Inet6Address> ipv6Entries = new HashMap<String, Inet6Address>();
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
                for (String s: WHITESPACES.split(line)) {
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
                for (int i = 1; i < lineParts.size(); i ++) {
                    String hostname = lineParts.get(i);
                    String hostnameLower = hostname.toLowerCase(Locale.ENGLISH);
                    InetAddress address = InetAddress.getByAddress(hostname, ipBytes);
                    if (address instanceof Inet4Address) {
                        Inet4Address previous = ipv4Entries.put(hostnameLower, (Inet4Address) address);
                        if (previous != null) {
                            // restore, we want to keep the first entry
                            ipv4Entries.put(hostnameLower, previous);
                        }
                    } else {
                        Inet6Address previous = ipv6Entries.put(hostnameLower, (Inet6Address) address);
                        if (previous != null) {
                            // restore, we want to keep the first entry
                            ipv6Entries.put(hostnameLower, previous);
                        }
                    }
                }
            }
            return ipv4Entries.isEmpty() && ipv6Entries.isEmpty() ?
                    HostsFileEntries.EMPTY :
                    new HostsFileEntries(ipv4Entries, ipv6Entries);
        } finally {
            try {
                buff.close();
            } catch (IOException e) {
                logger.warn("Failed to close a reader", e);
            }
        }
    }

    /**
     * Can't be instantiated.
     */
    private HostsFileParser() {
    }
}
