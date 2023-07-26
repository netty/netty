/*
 * Copyright 2022 The Netty Project
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
/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty5.handler.codec.http.headers;

import io.netty5.util.AsciiString;

import static io.netty5.handler.codec.http.headers.HeaderUtils.validateCookieNameAndValue;
import static io.netty5.util.AsciiString.contentEquals;
import static io.netty5.util.AsciiString.indexOf;

/**
 * Default implementation of {@link HttpCookiePair}.
 */
public final class DefaultHttpCookiePair implements HttpCookiePair {
    private final CharSequence name;
    private final CharSequence value;
    private final boolean isWrapped;

    /**
     * Create a new instance.
     *
     * @param cookieName The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>.
     * @param cookieValue The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     */
    public DefaultHttpCookiePair(final CharSequence cookieName, final CharSequence cookieValue) {
        this(cookieName, cookieValue, false);
    }

    /**
     * Create a new instance.
     *
     * @param cookieName The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>.
     * @param cookieValue The <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>.
     * @param isWrapped {@code true} if the value should be wrapped in DQUOTE as described in
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-value</a>
     */
    public DefaultHttpCookiePair(final CharSequence cookieName, final CharSequence cookieValue, boolean isWrapped) {
        validateCookieNameAndValue(cookieName, cookieValue);
        name = cookieName;
        value = cookieValue;
        this.isWrapped = isWrapped;
    }

    /**
     * Parse a <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-pair</a> from {@code sequence}.
     * @param sequence The {@link CharSequence} that contains a
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-pair</a>.
     *
     * @param nameStart The index were the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>
     * starts in {@code sequence}.
     * @param valueEnd The end index (exclusive) of the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-pair</a> in {@code sequence}.
     * @return a {@link HttpCookiePair} parsed from {@code sequence}.
     */
    static HttpCookiePair parseCookiePair(final CharSequence sequence, int nameStart, int valueEnd) {
        return parseCookiePair(sequence, nameStart, indexOf(sequence, '=', nameStart) - nameStart, valueEnd);
    }

    /**
     * Parse a <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-pair</a> from {@code sequence}.
     * @param sequence The {@link CharSequence} that contains a
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-pair</a>.
     *
     * @param nameStart The index were the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>
     * starts in {@code sequence}.
     * @param nameLength The length of the <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-name</a>.
     * @param valueEnd The end index (exclusive) of the
     * <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">cookie-pair</a> in {@code sequence}.
     * @return a {@link HttpCookiePair} parsed from {@code sequence}.
     */
    static HttpCookiePair parseCookiePair(final CharSequence sequence, int nameStart, int nameLength, int valueEnd) {
        return parseCookiePair0(sequence, nameStart, nameLength, valueEnd < 0 ? sequence.length() : valueEnd);
    }

    private static HttpCookiePair parseCookiePair0(final CharSequence sequence, int nameStart, int nameLength,
                                                   int valueEnd) {
        final int valueStart = nameStart + nameLength + 1;
        if (valueEnd <= valueStart || valueStart < 0) {
            throw new IllegalArgumentException("value indexes are invalid. valueStart: " + valueStart
                    + " valueEnd: " + valueEnd);
        }
        if (sequence.charAt(valueStart) == '"' && sequence.charAt(valueEnd - 1) == '"') {
            if (valueEnd - 2 <= valueStart) {
                throw new IllegalArgumentException("double quote exists but value empty");
            }
            return new DefaultHttpCookiePair(sequence.subSequence(nameStart, valueStart - 1),
                    sequence.subSequence(valueStart + 1, valueEnd - 1), true);
        }
        return new DefaultHttpCookiePair(sequence.subSequence(nameStart, valueStart - 1),
                sequence.subSequence(valueStart, valueEnd), false);
    }

    @Override
    public CharSequence name() {
        return name;
    }

    @Override
    public CharSequence value() {
        return value;
    }

    @Override
    public boolean isWrapped() {
        return isWrapped;
    }

    @Override
    public CharSequence encodedCookie() {
        StringBuilder sb = new StringBuilder(name.length() + value.length() + 1 + (isWrapped ? 2 : 0));
        sb.append(name).append('=');
        if (isWrapped) {
            sb.append('"').append(value).append('"');
        } else {
            sb.append(value);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpCookiePair)) {
            return false;
        }
        final HttpCookiePair rhs = (HttpCookiePair) o;
        return contentEquals(name, rhs.name()) && contentEquals(value, rhs.value());
    }

    @Override
    public int hashCode() {
        int hash = 31 + AsciiString.hashCode(name);
        hash = 31 * hash + AsciiString.hashCode(value);
        return hash;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '[' + name + ']';
    }
}
