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
package io.netty.handler.codec.http.cookie;

import io.netty.util.AsciiString;

public final class CookieHeaderNames {
    @Deprecated
    public static final String PATH = "Path";

    @Deprecated
    public static final String EXPIRES = "Expires";

    @Deprecated
    public static final String MAX_AGE = "Max-Age";

    @Deprecated
    public static final String DOMAIN = "Domain";

    @Deprecated
    public static final String SECURE = "Secure";

    @Deprecated
    public static final String HTTPONLY = "HTTPOnly";

    public static final AsciiString PATH_ASCII = new AsciiString("Path");

    public static final AsciiString EXPIRES_ASCII = new AsciiString("Expires");

    public static final AsciiString MAX_AGE_ASCII = new AsciiString("Max-Age");

    public static final AsciiString DOMAIN_ASCII = new AsciiString("Domain");

    public static final AsciiString SECURE_ASCII = new AsciiString("Secure");

    public static final AsciiString HTTPONLY_ASCII = new AsciiString("HTTPOnly");

    private CookieHeaderNames() {
        // Unused.
    }
}
