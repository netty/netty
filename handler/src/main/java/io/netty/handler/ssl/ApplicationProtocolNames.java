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

package io.netty.handler.ssl;

/**
 * Provides a set of protocol names used in ALPN and NPN.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7540#section-11.1">RFC7540 (HTTP/2)</a>
 * @see <a href="https://tools.ietf.org/html/rfc7301#section-6">RFC7301 (TLS ALPN Extension)</a>
 * @see <a href="https://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04#section-7">TLS NPN Extension Draft</a>
 */
public final class ApplicationProtocolNames {

    /**
     * {@code "h2"}: HTTP version 2
     */
    public static final String HTTP_2 = "h2";

    /**
     * {@code "http/1.1"}: HTTP version 1.1
     */
    public static final String HTTP_1_1 = "http/1.1";

    /**
     * {@code "spdy/3.1"}: SPDY version 3.1
     */
    public static final String SPDY_3_1 = "spdy/3.1";

    /**
     * {@code "spdy/3"}: SPDY version 3
     */
    public static final String SPDY_3 = "spdy/3";

    /**
     * {@code "spdy/2"}: SPDY version 2
     */
    public static final String SPDY_2 = "spdy/2";

    /**
     * {@code "spdy/1"}: SPDY version 1
     */
    public static final String SPDY_1 = "spdy/1";

    private ApplicationProtocolNames() { }
}
