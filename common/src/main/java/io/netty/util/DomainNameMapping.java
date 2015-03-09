/*
 * Copyright 2014 The Netty Project
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

package io.netty.util;

import io.netty.util.internal.StringUtil;

import java.net.IDN;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps a domain name to its associated value object.
 * <p>
 * DNS wildcard is supported as hostname, so you can use {@code *.netty.io} to match both {@code netty.io}
 * and {@code downloads.netty.io}.
 * </p>
 */
public class DomainNameMapping<V> implements Mapping<String, V> {

    private static final Pattern DNS_WILDCARD_PATTERN = Pattern.compile("^\\*\\..*");

    private final Map<String, V> map;

    private final V defaultValue;

    /**
     * Creates a default, order-sensitive mapping. If your hostnames are in conflict, the mapping
     * will choose the one you add first.
     *
     * @param defaultValue the default value for {@link #map(String)} to return when nothing matches the input
     */
    public DomainNameMapping(V defaultValue) {
        this(4, defaultValue);
    }

    /**
     * Creates a default, order-sensitive mapping. If your hostnames are in conflict, the mapping
     * will choose the one you add first.
     *
     * @param initialCapacity initial capacity for the internal map
     * @param defaultValue the default value for {@link #map(String)} to return when nothing matches the input
     */
    public DomainNameMapping(int initialCapacity, V defaultValue) {
        if (defaultValue == null) {
            throw new NullPointerException("defaultValue");
        }
        map = new LinkedHashMap<String, V>(initialCapacity);
        this.defaultValue = defaultValue;
    }

    /**
     * Adds a mapping that maps the specified (optionally wildcard) host name to the specified output value.
     * <p>
     * <a href="http://en.wikipedia.org/wiki/Wildcard_DNS_record">DNS wildcard</a> is supported as hostname.
     * For example, you can use {@code *.netty.io} to match {@code netty.io} and {@code downloads.netty.io}.
     * </p>
     *
     * @param hostname the host name (optionally wildcard)
     * @param output the output value that will be returned by {@link #map(String)} when the specified host name
     *               matches the specified input host name
     */
    public DomainNameMapping<V> add(String hostname, V output) {
        if (hostname == null) {
            throw new NullPointerException("input");
        }

        if (output == null) {
            throw new NullPointerException("output");
        }

        map.put(normalizeHostname(hostname), output);
        return this;
    }

    /**
     * Simple function to match <a href="http://en.wikipedia.org/wiki/Wildcard_DNS_record">DNS wildcard</a>.
     */
    private static boolean matches(String hostNameTemplate, String hostName) {
        // note that inputs are converted and lowercased already
        if (DNS_WILDCARD_PATTERN.matcher(hostNameTemplate).matches()) {
            return hostNameTemplate.substring(2).equals(hostName) ||
                    hostName.endsWith(hostNameTemplate.substring(1));
        } else {
            return hostNameTemplate.equals(hostName);
        }
    }

    /**
     * IDNA ASCII conversion and case normalization
     */
    private static String normalizeHostname(String hostname) {
        if (needsNormalization(hostname)) {
            hostname = IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED);
        }
        return hostname.toLowerCase(Locale.US);
    }

    private static boolean needsNormalization(String hostname) {
        final int length = hostname.length();
        for (int i = 0; i < length; i ++) {
            int c = hostname.charAt(i);
            if (c > 0x7F) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V map(String input) {
        if (input != null) {
            input = normalizeHostname(input);

            for (Map.Entry<String, V> entry : map.entrySet()) {
                if (matches(entry.getKey(), input)) {
                    return entry.getValue();
                }
            }
        }

        return defaultValue;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(default: " + defaultValue + ", map: " + map + ')';
    }
}
