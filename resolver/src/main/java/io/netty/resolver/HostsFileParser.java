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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * A parser for hosts files.
 * The produced mappings contain only the first entry per hostname.
 * Consider using {@link HostsFileEntriesProvider} when mappings with all entries per hostname are needed.
 */
public final class HostsFileParser {

    /**
     * Parse hosts file at standard OS location using the systems default {@link Charset} for decoding.
     *
     * @return a {@link HostsFileEntries}
     */
    public static HostsFileEntries parseSilently() {
        return hostsFileEntries(HostsFileEntriesProvider.parser().parseSilently());
    }

    /**
     * Parse hosts file at standard OS location using the given {@link Charset}s one after each other until
     * we were able to parse something or none is left.
     *
     * @param charsets the {@link Charset}s to try as file encodings when parsing.
     * @return a {@link HostsFileEntries}
     */
    public static HostsFileEntries parseSilently(Charset... charsets) {
        return hostsFileEntries(HostsFileEntriesProvider.parser().parseSilently(charsets));
    }

    /**
     * Parse hosts file at standard OS location using the system default {@link Charset} for decoding.
     *
     * @return a {@link HostsFileEntries}
     * @throws IOException file could not be read
     */
    public static HostsFileEntries parse() throws IOException {
        return hostsFileEntries(HostsFileEntriesProvider.parser().parse());
    }

    /**
     * Parse a hosts file using the system default {@link Charset} for decoding.
     *
     * @param file the file to be parsed
     * @return a {@link HostsFileEntries}
     * @throws IOException file could not be read
     */
    public static HostsFileEntries parse(File file) throws IOException {
        return hostsFileEntries(HostsFileEntriesProvider.parser().parse(file));
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
        return hostsFileEntries(HostsFileEntriesProvider.parser().parse(file, charsets));
    }

    /**
     * Parse a reader of hosts file format.
     *
     * @param reader the file to be parsed
     * @return a {@link HostsFileEntries}
     * @throws IOException file could not be read
     */
    public static HostsFileEntries parse(Reader reader) throws IOException {
        return hostsFileEntries(HostsFileEntriesProvider.parser().parse(reader));
    }

    /**
     * Can't be instantiated.
     */
    private HostsFileParser() {
    }

    @SuppressWarnings("unchecked")
    private static HostsFileEntries hostsFileEntries(HostsFileEntriesProvider provider) {
        return provider == HostsFileEntriesProvider.EMPTY ? HostsFileEntries.EMPTY :
                new HostsFileEntries((Map<String, Inet4Address>) toMapWithSingleValue(provider.ipv4Entries()),
                        (Map<String, Inet6Address>) toMapWithSingleValue(provider.ipv6Entries()));
    }

    private static Map<String, ?> toMapWithSingleValue(Map<String, List<InetAddress>> fromMapWithListValue) {
        Map<String, InetAddress> result = new HashMap<String, InetAddress>();
        for (Map.Entry<String, List<InetAddress>> entry : fromMapWithListValue.entrySet()) {
            List<InetAddress> value = entry.getValue();
            if (!value.isEmpty()) {
                result.put(entry.getKey(), value.get(0));
            }
        }
        return result;
    }
}
