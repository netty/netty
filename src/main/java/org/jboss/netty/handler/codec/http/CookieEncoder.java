/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.handler.codec.http.cookie.ClientCookieEncoder;
import org.jboss.netty.handler.codec.http.cookie.ServerCookieEncoder;

import java.util.Set;
import java.util.TreeSet;

/**
 * Encodes {@link Cookie}s into an HTTP header value.  This encoder can encode
 * the HTTP cookie version 0, 1, and 2.
 * <p>
 * This encoder is stateful.  It maintains an internal data structure that
 * holds the {@link Cookie}s added by the {@link #addCookie(String, String)}
 * method.  Once {@link #encode()} is called, all added {@link Cookie}s are
 * encoded into an HTTP header value and all {@link Cookie}s in the internal
 * data structure are removed so that the encoder can start over.
 * <pre>
 * // Client-side example
 * {@link HttpRequest} req = ...;
 * {@link CookieEncoder} encoder = new {@link CookieEncoder}(false);
 * encoder.addCookie("JSESSIONID", "1234");
 * res.setHeader("Cookie", encoder.encode());
 *
 * // Server-side example
 * {@link HttpResponse} res = ...;
 * {@link CookieEncoder} encoder = new {@link CookieEncoder}(true);
 * encoder.addCookie("JSESSIONID", "1234");
 * res.setHeader("Set-Cookie", encoder.encode());
 * </pre>
 *
 * @see CookieDecoder
 *
 * @apiviz.stereotype utility
 * @apiviz.has        org.jboss.netty.handler.codec.http.Cookie oneway - - encodes
 */
public class CookieEncoder {

    private final Set<Cookie> cookies = new TreeSet<Cookie>();
    private final boolean server;
    private final boolean strict;

    /**
     * Creates a new encoder.
     *
     * @param server {@code true} if and only if this encoder is supposed to
     *               encode server-side cookies.  {@code false} if and only if
     *               this encoder is supposed to encode client-side cookies.
     */
    public CookieEncoder(boolean server) {
        this(server, false);
    }

    /**
     * Creates a new encoder.
     *
     * @param server {@code true} if and only if this encoder is supposed to
     *               encode server-side cookies.  {@code false} if and only if
     *               this encoder is supposed to encode client-side cookies.
     * @param strict {@code true} if and only if this encoder is supposed to
     *               validate characters according to RFC6265.
     */
    public CookieEncoder(boolean server, boolean strict) {
        this.server = server;
        this.strict = strict;
    }

    /**
     * Adds a new {@link Cookie} created with the specified name and value to
     * this encoder.
     */
    public void addCookie(String name, String value) {
        cookies.add(new DefaultCookie(name, value));
    }

    /**
     * Adds the specified {@link Cookie} to this encoder.
     */
    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    /**
     * Encodes the {@link Cookie}s which were added by {@link #addCookie(Cookie)}
     * so far into an HTTP header value.  If no {@link Cookie}s were added,
     * an empty string is returned.
     *
     * <strong>Be aware that calling this method will clear the content of the {@link CookieEncoder}</strong>
     */
    public String encode() {
        String answer;
        if (server) {
            answer = encodeServerSide();
        } else {
            answer = encodeClientSide();
        }
        cookies.clear();
        return answer;
    }

    private String encodeServerSide() {
        if (cookies.size() > 1) {
            throw new IllegalStateException(
                    "encode() can encode only one cookie on server mode: " + cookies.size() + " cookies added");
        }

        Cookie cookie = cookies.isEmpty() ? null : cookies.iterator().next();
        ServerCookieEncoder encoder = strict ? ServerCookieEncoder.STRICT : ServerCookieEncoder.LAX;
        return encoder.encode(cookie);
    }

    private String encodeClientSide() {
        ClientCookieEncoder encoder = strict ? ClientCookieEncoder.STRICT : ClientCookieEncoder.LAX;
        return encoder.encode(cookies);
    }
}
