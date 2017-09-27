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
package io.netty.handler.codec.http;

import io.netty.util.AsciiString;

/**
 * Defines the common schemes used for the HTTP protocol as defined by
 * <a href="https://tools.ietf.org/html/rfc7230">rfc7230</a>.
 */
public final class HttpScheme {

    /**
     * Scheme for non-secure HTTP connection.
     */
    public static final HttpScheme HTTP = new HttpScheme(80, "http");

    /**
     * Scheme for secure HTTP connection.
     */
    public static final HttpScheme HTTPS = new HttpScheme(443, "https");

    private final int port;
    private final AsciiString name;

    private HttpScheme(int port, String name) {
        this.port = port;
        this.name = AsciiString.cached(name);
    }

    public AsciiString name() {
        return name;
    }

    public int port() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpScheme)) {
            return false;
        }
        HttpScheme other = (HttpScheme) o;
        return other.port() == port && other.name().equals(name);
    }

    @Override
    public int hashCode() {
        return port * 31 + name.hashCode();
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
