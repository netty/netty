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
package io.netty.handler.ssl;

import io.netty.util.Mapping;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.IDN;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <p>This class maps a domain name to a configured {@link SslContext}.</p>
 *
 * <p>DNS wildcard is supported as hostname, so you can use {@code *.netty.io} to match both {@code netty.io}
 * and {@code downloads.netty.io}.</p>
 */
public class DomainNameMapping implements Mapping<String, SslContext> {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(DomainNameMapping.class);

    private static final Pattern DNS_WILDCARD_PATTERN = Pattern.compile("^\\*\\..*");

    private final Map<String, SslContext> userProvidedContexts;

    private final SslContext defaultContext;

    /**
     * Create a default, order-sensitive mapping. If your hostnames are in conflict, the mapping
     * will choose the one you add first.
     *
     * @param defaultContext default {@link SslContext} when the nothing matches input.
     */
    public DomainNameMapping(SslContext defaultContext) {
        this(4, defaultContext);
    }

    /**
     * Create a default, order-sensitive mapping. If your hostnames are in conflict, the mapping
     * will choose the one you add first.
     *
     * @param initialCapacity initial capacity for internal map
     * @param defaultContext default {@link SslContext} when the handler fails to detect SNI extension
     */
    public DomainNameMapping(int initialCapacity, SslContext defaultContext) {
        if (defaultContext == null) {
            throw new NullPointerException("defaultContext");
        }
        userProvidedContexts = new LinkedHashMap<String, SslContext>(initialCapacity);
        this.defaultContext = defaultContext;
    }

    /**
     * Add a {@link SslContext} to the handler.
     *
     * <a href="http://en.wikipedia.org/wiki/Wildcard_DNS_record">DNS wildcard</a> is supported as hostname.
     * For example, you can use {@code *.netty.io} to match {@code netty.io} and {@code downloads.netty.io}.
     *
     * @param hostname hostname for the certificate.
     * @param context the {@link SslContext}
     */
    public DomainNameMapping addContext(String hostname, SslContext context) {
        if (hostname == null) {
            throw new NullPointerException("hostname");
        }

        if (context == null) {
            throw new NullPointerException("context");
        }

        userProvidedContexts.put(normalizeHostname(hostname), context);
        return this;
    }

    /**
     * <p>Simple function to match <a href="http://en.wikipedia.org/wiki/Wildcard_DNS_record">DNS wildcard</a>.
     * </p>
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
    static String normalizeHostname(String hostname) {
        return IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED).toLowerCase();
    }

    @Override
    public SslContext map(String hostname) {
        if (hostname != null) {
            for (Map.Entry<String, SslContext> entry : userProvidedContexts.entrySet()) {
                if (matches(entry.getKey(), hostname)) {
                    return entry.getValue();
                }
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Using default SslContext");
        }
        return defaultContext;
    }
}
