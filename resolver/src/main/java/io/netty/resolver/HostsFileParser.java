/*
 * Copyright 2015 The Netty Project
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
package io.netty.resolver;

import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
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
     * Parse hosts file at standard OS location.
     *
     * @return a map of hostname or alias to {@link InetAddress}
     */
    public static Map<String, InetAddress> parseSilently() {
        File hostsFile = locateHostsFile();
        try {
            return parse(hostsFile);
        } catch (IOException e) {
            logger.warn("Failed to load and parse hosts file at " + hostsFile.getPath(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Parse hosts file at standard OS location.
     *
     * @return a map of hostname or alias to {@link InetAddress}
     * @throws IOException file could not be read
     */
    public static Map<String, InetAddress> parse() throws IOException {
        return parse(locateHostsFile());
    }

    /**
     * Parse a hosts file.
     *
     * @param file the file to be parsed
     * @return a map of hostname or alias to {@link InetAddress}
     * @throws IOException file could not be read
     */
    public static Map<String, InetAddress> parse(File file) throws IOException {
        checkNotNull(file, "file");
        if (file.exists() && file.isFile()) {
            return parse(new BufferedReader(new FileReader(file)));
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Parse a reader of hosts file format.
     *
     * @param reader the file to be parsed
     * @return a map of hostname or alias to {@link InetAddress}
     * @throws IOException file could not be read
     */
    public static Map<String, InetAddress> parse(Reader reader) throws IOException {
        checkNotNull(reader, "reader");
        BufferedReader buff = new BufferedReader(reader);
        try {
            Map<String, InetAddress> entries = new HashMap<String, InetAddress>();
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
                    if (!entries.containsKey(hostnameLower)) {
                        // trying to map a host to multiple IPs is wrong
                        // only the first entry is honored
                        entries.put(hostnameLower, InetAddress.getByAddress(hostname, ipBytes));
                    }
                }
            }
            return entries;
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
