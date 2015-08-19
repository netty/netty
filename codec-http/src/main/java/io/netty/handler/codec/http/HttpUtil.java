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

import java.net.URI;

/**
 * Utility methods useful in the HTTP context.
 */
public final class HttpUtil {
    private HttpUtil() { }

    /**
     * Determine if a uri is in origin-form according to
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.3">rfc7230, 5.3</a>.
     */
    public static boolean isOriginForm(URI uri) {
        return uri.getScheme() == null && uri.getSchemeSpecificPart() == null &&
               uri.getHost() == null && uri.getAuthority() == null;
    }

    /**
     * Determine if a uri is in asteric-form according to
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.3">rfc7230, 5.3</a>.
     */
    public static boolean isAsteriskForm(URI uri) {
        return "*".equals(uri.getPath()) &&
                uri.getScheme() == null && uri.getSchemeSpecificPart() == null &&
                uri.getHost() == null && uri.getAuthority() == null && uri.getQuery() == null &&
                uri.getFragment() == null;
    }
}
