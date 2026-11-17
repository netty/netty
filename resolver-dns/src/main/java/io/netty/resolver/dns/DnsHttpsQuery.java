/*
 * Copyright 2026 The Netty Project
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

import io.netty.util.internal.UnstableApi;

import java.util.Locale;

import static io.netty.util.internal.ObjectUtil.checkInRange;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The origin of an {@code http} or {@code https} URL to resolve with its HTTPS records
 * (<a href="https://www.rfc-editor.org/rfc/rfc9460.html">RFC 9460</a>): its scheme, host and port.
 */
@UnstableApi
public final class DnsHttpsQuery {

    private final String scheme;
    private final String host;
    private final int port;

    /**
     * Creates a query.
     *
     * @param scheme the scheme, {@code "http"} or {@code "https"}, case-insensitively
     * @param host the host, as in the URL
     * @param port the port, the default one of the scheme when the URL has none
     */
    public DnsHttpsQuery(String scheme, String host, int port) {
        checkNotNull(scheme, "scheme");
        this.scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(this.scheme) && !"https".equals(this.scheme)) {
            throw new IllegalArgumentException("scheme: " + scheme + " (expected: http or https)");
        }
        this.host = checkNotNull(host, "host");
        this.port = checkInRange(port, 1, 65535, "port");
    }

    /**
     * Returns the scheme, {@code "http"} or {@code "https"}.
     */
    public String scheme() {
        return scheme;
    }

    /**
     * Returns the host.
     */
    public String host() {
        return host;
    }

    /**
     * Returns the port.
     */
    public int port() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DnsHttpsQuery)) {
            return false;
        }
        DnsHttpsQuery that = (DnsHttpsQuery) o;
        return port == that.port && scheme.equals(that.scheme) && host.equalsIgnoreCase(that.host);
    }

    @Override
    public int hashCode() {
        return (scheme.hashCode() * 31 + host.toLowerCase(Locale.ROOT).hashCode()) * 31 + port;
    }

    @Override
    public String toString() {
        return scheme + "://" + host + ':' + port;
    }
}
