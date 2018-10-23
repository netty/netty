/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.codec.http.cache;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.EnumSet;

import static io.netty.handler.codec.http.HttpHeaderValues.*;

final class CacheControlDecoder {
    // https://blogs.msdn.microsoft.com/ie/2010/07/14/caching-improvements-in-internet-explorer-9
    public static final int MAXIMUM_AGE = Integer.MAX_VALUE;

    private CacheControlDecoder() {
    }

    public static CacheControlDirectives decode(HttpHeaders headers) {
        int maxAgeSeconds = -1;
        int sMaxAgeSeconds = -1;
        int maxStaleSeconds = -1;
        int minFreshSeconds = -1;
        int staleWhileRevalidate = -1;
        int staleIfError = -1;
        EnumSet<CacheControlDirectives.CacheControlFlags> flags =
                EnumSet.noneOf(CacheControlDirectives.CacheControlFlags.class);

        for (String value : headers.getAll(HttpHeaderNames.CACHE_CONTROL)) {
            int pos = 0;
            while (pos < value.length()) {
                int tokenStart = pos;
                pos = skipUntil(value, pos, "=,;");
                String directive = value.substring(tokenStart, pos).trim();
                String parameter;

                if (pos == value.length() || value.charAt(pos) == ',' || value.charAt(pos) == ';') {
                    pos++; // consume ',' or ';' (if necessary)
                    parameter = null;
                } else {
                    pos++; // consume '='
                    pos = skipWhitespace(value, pos);

                    // quoted string
                    if (pos < value.length() && value.charAt(pos) == '\"') {
                        pos++; // consume '"' open quote
                        int parameterStart = pos;
                        pos = skipUntil(value, pos, "\"");
                        parameter = value.substring(parameterStart, pos);
                        pos++; // consume '"' close quote (if necessary)

                        // unquoted string
                    } else {
                        int parameterStart = pos;
                        pos = skipUntil(value, pos, ",;");
                        parameter = value.substring(parameterStart, pos).trim();
                    }
                }

                if (NO_CACHE.contentEqualsIgnoreCase(directive)) {
                    flags.add(CacheControlDirectives.CacheControlFlags.NO_CACHE);
                } else if (NO_STORE.contentEqualsIgnoreCase(directive)) {
                    flags.add(CacheControlDirectives.CacheControlFlags.NO_STORE);
                } else if (MAX_AGE.contentEqualsIgnoreCase(directive)) {
                    maxAgeSeconds = parseSeconds(parameter, -1);
                } else if (S_MAXAGE.contentEqualsIgnoreCase(directive)) {
                    sMaxAgeSeconds = parseSeconds(parameter, -1);
                } else if (PRIVATE.contentEqualsIgnoreCase(directive)) {
                    flags.add(CacheControlDirectives.CacheControlFlags.PRIVATE);
                } else if (PUBLIC.contentEqualsIgnoreCase(directive)) {
                    flags.add(CacheControlDirectives.CacheControlFlags.PUBLIC);
                } else if (PROXY_REVALIDATE.contentEqualsIgnoreCase(directive)) {
                    flags.add(CacheControlDirectives.CacheControlFlags.PROXY_REVALIDATE);
                } else if (MUST_REVALIDATE.contentEqualsIgnoreCase(directive)) {
                    flags.add(CacheControlDirectives.CacheControlFlags.MUST_REVALIDATE);
                } else if (MAX_STALE.contentEqualsIgnoreCase(directive)) {
                    maxStaleSeconds = parseSeconds(parameter, MAXIMUM_AGE);
                } else if (MIN_FRESH.contentEqualsIgnoreCase(directive)) {
                    minFreshSeconds = parseSeconds(parameter, -1);
                } else if (ONLY_IF_CACHED.contentEqualsIgnoreCase(directive)) {
                    flags.add(CacheControlDirectives.CacheControlFlags.ONLY_IF_CACHED);
                } else if (NO_TRANSFORM.contentEqualsIgnoreCase(directive)) {
                    flags.add(CacheControlDirectives.CacheControlFlags.NO_TRANSFORM);
                } else if (IMMUTABLE.contentEqualsIgnoreCase(directive)) {
                    flags.add(CacheControlDirectives.CacheControlFlags.IMMUTABLE);
                } else if ("stale-while-revalidate".equalsIgnoreCase(directive)) {
                    staleWhileRevalidate = parseSeconds(parameter, -1);
                } else if ("stale-if-error".equalsIgnoreCase(directive)) {
                    staleIfError = parseSeconds(parameter, -1);
                }
            }
        }

        return new CacheControlDirectives(flags, maxAgeSeconds, sMaxAgeSeconds, maxStaleSeconds,
                                          minFreshSeconds, staleWhileRevalidate, staleIfError);
    }

    private static int skipUntil(String input, int pos, String characters) {
        for (; pos < input.length(); pos++) {
            if (characters.indexOf(input.charAt(pos)) != -1) {
                break;
            }
        }
        return pos;
    }

    private static int skipWhitespace(String input, int pos) {
        for (; pos < input.length(); pos++) {
            char c = input.charAt(pos);
            if (c != ' ' && c != '\t') {
                break;
            }
        }
        return pos;
    }

    private static int parseSeconds(String value, int defaultValue) {
        try {
            long seconds = Long.parseLong(value);
            if (seconds > MAXIMUM_AGE) {
                return MAXIMUM_AGE;
            } else if (seconds < 0) {
                return 0;
            } else {
                return (int) seconds;
            }
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
