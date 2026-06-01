/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2.internal;

import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

import static io.netty.util.AsciiString.EMPTY_STRING;

/**
 * Internal methods for {@link io.netty.handler.codec.http2.HttpConversionUtil}
 */
public final class InternalHttpConversionUtil {

    public static int queryOrFragmentStart(String uri, int searchStart) {
        int queryStart = uri.indexOf('?', searchStart);
        int fragmentStart = uri.indexOf('#', searchStart);
        return queryStart == -1 ? fragmentStart :
                fragmentStart == -1 ? queryStart : Math.min(queryStart, fragmentStart);
    }

    // Netty addition: detect authority for HTTP/2 :scheme/:authority extraction.
    public static boolean hasSchemeAndAuthority(String requestTarget) {
        int schemeEnd = requestTarget.indexOf("://");
        return isValidScheme(requestTarget, schemeEnd);
    }

    // Netty addition: validate the text before :// as a scheme.
    public static boolean isValidScheme(String uri, int schemeEnd) {
        if (schemeEnd <= 0) {
            return false;
        }
        char first = uri.charAt(0);
        if (!isAlpha(first)) {
            return false;
        }
        for (int i = 1; i < schemeEnd; ++i) {
            char c = uri.charAt(i);
            if (!isAlpha(c) && (c < '0' || c > '9') && c != '+' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    // package-private for testing only
    public static void setHttp2Authority(String authority, Http2Headers out) {
        // The authority MUST NOT include the deprecated "userinfo" subcomponent
        if (authority != null) {
            if (authority.isEmpty()) {
                out.authority(EMPTY_STRING);
            } else {
                int start = authority.indexOf('@') + 1;
                int length = authority.length() - start;
                if (length == 0) {
                    throw new IllegalArgumentException("authority: " + authority);
                }
                out.authority(new AsciiString(authority, start, length));
            }
        }
    }
}
