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
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.netty5.handler.codec.http.headers.HeaderUtils.CookiesByNameIterator;
import io.netty5.util.AsciiString;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static io.netty5.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty5.handler.codec.http.HttpHeaderNames.SET_COOKIE;
import static io.netty5.handler.codec.http.headers.DefaultHttpSetCookie.parseSetCookie;
import static io.netty5.handler.codec.http.headers.HeaderUtils.DEFAULT_HEADER_FILTER;
import static io.netty5.handler.codec.http.headers.HeaderUtils.domainMatches;
import static io.netty5.handler.codec.http.headers.HeaderUtils.parseCookiePair;
import static io.netty5.handler.codec.http.headers.HeaderUtils.pathMatches;
import static io.netty5.util.AsciiString.contentEquals;
import static io.netty5.util.AsciiString.contentEqualsIgnoreCase;
import static java.util.Collections.emptyIterator;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

final class ReadOnlyHttpHeaders implements HttpHeaders {
    private final CharSequence[] keyValuePairs;
    private final boolean validateCookies;

    ReadOnlyHttpHeaders(final CharSequence... keyValuePairs) {
        this(true, keyValuePairs);
    }

    ReadOnlyHttpHeaders(boolean validateCookies, final CharSequence... keyValuePairs) {
        if ((keyValuePairs.length & 1) != 0) {
            throw new IllegalArgumentException("keyValuePairs length must be even but was: " + keyValuePairs.length);
        }
        this.keyValuePairs = requireNonNull(keyValuePairs);
        this.validateCookies = validateCookies;
    }

    private static int hashCode(final CharSequence name) {
        return AsciiString.hashCode(name);
    }

    private static boolean equals(final CharSequence name1, final CharSequence name2) {
        return contentEqualsIgnoreCase(name1, name2);
    }

    private static boolean equalsValues(final CharSequence name1, final CharSequence name2) {
        return contentEquals(name1, name2);
    }

    @Nullable
    @Override
    public CharSequence get(final CharSequence name) {
        final int nameHash = hashCode(name);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            if (nameHash == hashCode(currentName) && equals(currentName, name)) {
                return keyValuePairs[i + 1];
            }
        }
        return null;
    }

    @Nullable
    @Override
    public CharSequence getAndRemove(final CharSequence name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<CharSequence> valuesIterator(final CharSequence name) {
        return new ReadOnlyValueIterator(name);
    }

    @Override
    public boolean contains(final CharSequence name, final CharSequence value) {
        final int nameHash = hashCode(name);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            if (nameHash == hashCode(currentName) && equals(currentName, name) &&
                    equalsValues(keyValuePairs[i + 1], value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsIgnoreCase(final CharSequence name, final CharSequence value) {
        final int nameHash = hashCode(name);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            if (nameHash == hashCode(currentName) && equals(currentName, name) &&
                    equals(keyValuePairs[i + 1], value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int size() {
        return keyValuePairs.length >>> 1;
    }

    @Override
    public boolean isEmpty() {
        return keyValuePairs.length == 0;
    }

    @Override
    public Set<CharSequence> names() {
        final Set<CharSequence> nameSet = new HashSet<>(size());
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            nameSet.add(keyValuePairs[i]);
        }
        return unmodifiableSet(nameSet);
    }

    @Override
    public HttpHeaders add(final CharSequence name, final CharSequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(final CharSequence name, final Iterable<? extends CharSequence> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(final CharSequence name, final CharSequence... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(final HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(final CharSequence name, final Iterable<? extends CharSequence> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(final CharSequence name, final CharSequence... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(final HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders replace(final HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final CharSequence name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(CharSequence name, CharSequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIgnoreCase(CharSequence name, CharSequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iterator() {
        return new ReadOnlyIterator();
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof HttpHeaders && HeaderUtils.equals(this, (HttpHeaders) o);
    }

    @Override
    public int hashCode() {
        return HeaderUtils.hashCode(this);
    }

    @Override
    public String toString() {
        return toString(DEFAULT_HEADER_FILTER);
    }

    @Nullable
    @Override
    public HttpCookiePair getCookie(final CharSequence name) {
        final int nameHash = hashCode(COOKIE);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, COOKIE)) {
                HttpCookiePair cookiePair = parseCookiePair(currentValue, name);
                if (cookiePair != null) {
                    return cookiePair;
                }
            }
        }
        return null;
    }

    @Nullable
    @Override
    public HttpSetCookie getSetCookie(final CharSequence name) {
        final int nameHash = hashCode(SET_COOKIE);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, SET_COOKIE)) {
                HttpSetCookie setCookie = HeaderUtils.parseSetCookie(currentValue, name, validateCookies);
                if (setCookie != null) {
                    return setCookie;
                }
            }
        }
        return null;
    }

    @Override
    public Iterator<HttpCookiePair> getCookiesIterator() {
        Iterator<CharSequence> valueItr = valuesIterator(COOKIE);
        return valueItr.hasNext() ? new ReadOnlyCookiesIterator(valueItr) : emptyIterator();
    }

    @Override
    public Iterator<HttpCookiePair> getCookiesIterator(final CharSequence name) {
        return new ReadOnlyCookiesByNameIterator(valuesIterator(COOKIE), name);
    }

    @Override
    public Iterator<HttpSetCookie> getSetCookiesIterator() {
        final int nameHash = hashCode(SET_COOKIE);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            if (nameHash == hashCode(currentName) && equals(currentName, SET_COOKIE)) {
                return new ReadOnlySetCookieIterator(i, nameHash, validateCookies);
            }
        }
        return emptyIterator();
    }

    @Override
    public Iterator<HttpSetCookie> getSetCookiesIterator(final CharSequence name) {
        final int nameHash = hashCode(SET_COOKIE);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, SET_COOKIE)) {
                HttpSetCookie setCookie = HeaderUtils.parseSetCookie(currentValue, name, validateCookies);
                if (setCookie != null) {
                    return new ReadOnlySetCookieNameIterator(i, nameHash, setCookie);
                }
            }
        }
        return emptyIterator();
    }

    @Override
    public Iterator<HttpSetCookie> getSetCookiesIterator(final CharSequence name, final CharSequence domain,
                                                           final CharSequence path) {
        final int nameHash = hashCode(SET_COOKIE);
        final int end = keyValuePairs.length - 1;
        for (int i = 0; i < end; i += 2) {
            final CharSequence currentName = keyValuePairs[i];
            final CharSequence currentValue = keyValuePairs[i + 1];
            if (nameHash == hashCode(currentName) && equals(currentName, SET_COOKIE)) {
                // In the future we could attempt to delay full parsing of the cookie until after the domain/path have
                // been matched, but for simplicity just do the parsing ahead of time.
                HttpSetCookie setCookie = HeaderUtils.parseSetCookie(currentValue, name, validateCookies);
                if (setCookie != null && domainMatches(domain, setCookie.domain()) &&
                        pathMatches(path, setCookie.path())) {
                    return new ReadOnlySetCookieNameDomainPathIterator(i, nameHash, setCookie, domain, path);
                }
            }
        }
        return emptyIterator();
    }

    @Override
    public HttpHeaders addCookie(final HttpCookiePair cookie) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders addSetCookie(final HttpSetCookie cookie) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeCookies(final CharSequence name) {
        return false;
    }

    @Override
    public boolean removeSetCookies(final CharSequence name) {
        return false;
    }

    @Override
    public boolean removeSetCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        return false;
    }

    private static final class ReadOnlyCookiesIterator extends HeaderUtils.CookiesIterator {
        private final Iterator<? extends CharSequence> valueItr;
        @Nullable
        private CharSequence headerValue;

        ReadOnlyCookiesIterator(final Iterator<? extends CharSequence> valueItr) {
            this.valueItr = valueItr;
            if (valueItr.hasNext()) {
                headerValue = valueItr.next();
                initNext(headerValue);
            }
        }

        @Nullable
        @Override
        protected CharSequence cookieHeaderValue() {
            return headerValue;
        }

        @Override
        protected void advanceCookieHeaderValue() {
            headerValue = valueItr.hasNext() ? valueItr.next() : null;
        }
    }

    private static final class ReadOnlyCookiesByNameIterator extends CookiesByNameIterator {
        private final Iterator<? extends CharSequence> valueItr;
        @Nullable
        private CharSequence headerValue;

        ReadOnlyCookiesByNameIterator(final Iterator<? extends CharSequence> valueItr, final CharSequence name) {
            super(name);
            this.valueItr = valueItr;
            if (valueItr.hasNext()) {
                headerValue = valueItr.next();
                initNext(headerValue);
            }
        }

        @Nullable
        @Override
        protected CharSequence cookieHeaderValue() {
            return headerValue;
        }

        @Override
        protected void advanceCookieHeaderValue() {
            headerValue = valueItr.hasNext() ? valueItr.next() : null;
        }
    }

    private final class ReadOnlySetCookieIterator implements Iterator<HttpSetCookie> {
        private final int nameHash;
        private final boolean validate;
        private int i;

        ReadOnlySetCookieIterator(int keyIndex, int nameHash, boolean validate) {
            i = keyIndex;
            this.nameHash = nameHash;
            this.validate = validate;
        }

        @Override
        public boolean hasNext() {
            return i < keyValuePairs.length;
        }

        @Override
        public HttpSetCookie next() {
            final int end = keyValuePairs.length - 1;
            if (i >= end) {
                throw new NoSuchElementException();
            }
            final HttpSetCookie next = parseSetCookie(keyValuePairs[i + 1], validate);
            i += 2;
            for (; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                if (nameHash == AsciiString.hashCode(currentName) &&
                        contentEqualsIgnoreCase(currentName, SET_COOKIE)) {
                    break;
                }
            }
            return next;
        }
    }

    private final class ReadOnlySetCookieNameIterator implements Iterator<HttpSetCookie> {
        private final int nameHash;
        @Nullable
        private HttpSetCookie next;
        private int i;

        ReadOnlySetCookieNameIterator(int keyIndex, int nameHash, final HttpSetCookie next) {
            this.i = keyIndex;
            this.nameHash = nameHash;
            this.next = next;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public HttpSetCookie next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            HttpSetCookie currentCookie = next;
            next = null;
            i += 2;
            final int end = keyValuePairs.length - 1;
            for (; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                final CharSequence currentValue = keyValuePairs[i + 1];
                if (nameHash == AsciiString.hashCode(currentName) &&
                        contentEqualsIgnoreCase(currentName, SET_COOKIE)) {
                    next = HeaderUtils.parseSetCookie(currentValue, currentCookie.name(), validateCookies);
                    if (next != null) {
                        break;
                    }
                }
            }
            return currentCookie;
        }
    }

    private final class ReadOnlySetCookieNameDomainPathIterator implements Iterator<HttpSetCookie> {
        private final int nameHash;
        private final CharSequence domain;
        private final CharSequence path;
        @Nullable
        private HttpSetCookie next;
        private int i;

        ReadOnlySetCookieNameDomainPathIterator(int keyIndex, int nameHash, HttpSetCookie next,
                                                final CharSequence domain, final CharSequence path) {
            this.i = keyIndex;
            this.nameHash = nameHash;
            this.domain = domain;
            this.path = path;
            this.next = next;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public HttpSetCookie next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            HttpSetCookie currentCookie = next;
            next = null;
            i += 2;
            final int end = keyValuePairs.length - 1;
            for (; i < end; i += 2) {
                final CharSequence currentName = keyValuePairs[i];
                final CharSequence currentValue = keyValuePairs[i + 1];
                if (nameHash == AsciiString.hashCode(currentName) &&
                        contentEqualsIgnoreCase(currentName, SET_COOKIE)) {
                    // In the future we could attempt to delay full parsing of the cookie until after the domain/path
                    // have been matched, but for simplicity just do the parsing ahead of time.
                    HttpSetCookie setCookie = HeaderUtils.parseSetCookie(currentValue, currentCookie.name(),
                            validateCookies);
                    if (setCookie != null && domainMatches(domain, setCookie.domain()) &&
                            pathMatches(path, setCookie.path())) {
                        next = setCookie;
                        break;
                    }
                }
            }
            return currentCookie;
        }
    }

    private final class ReadOnlyIterator implements Map.Entry<CharSequence, CharSequence>,
                                                    Iterator<Map.Entry<CharSequence, CharSequence>> {
        private int keyIndex;
        @Nullable
        private CharSequence key;
        @Nullable
        private CharSequence value;

        @Override
        public boolean hasNext() {
            return keyIndex != keyValuePairs.length;
        }

        @Override
        public Map.Entry<CharSequence, CharSequence> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            key = keyValuePairs[keyIndex];
            value = keyValuePairs[keyIndex + 1];
            keyIndex += 2;
            return this;
        }

        @Override
        @Nullable
        public CharSequence getKey() {
            return key;
        }

        @Override
        @Nullable
        public CharSequence getValue() {
            return value;
        }

        @Override
        public CharSequence setValue(final CharSequence value) {
            throw new UnsupportedOperationException();
        }
    }

    private final class ReadOnlyValueIterator implements Iterator<CharSequence> {
        private final CharSequence name;
        private final int nameHash;
        private int keyIndex;
        @Nullable
        private CharSequence nextValue;

        ReadOnlyValueIterator(final CharSequence name) {
            this.name = name;
            nameHash = ReadOnlyHttpHeaders.hashCode(name);
            calculateNext();
        }

        @Override
        public boolean hasNext() {
            return nextValue != null;
        }

        @Override
        public CharSequence next() {
            if (nextValue == null) {
                throw new NoSuchElementException();
            }
            final CharSequence current = nextValue;
            calculateNext();
            return current;
        }

        private void calculateNext() {
            final int end = keyValuePairs.length - 1;
            for (; keyIndex < end; keyIndex += 2) {
                final CharSequence currentName = keyValuePairs[keyIndex];
                if (nameHash == ReadOnlyHttpHeaders.hashCode(currentName) &&
                        ReadOnlyHttpHeaders.equals(name, currentName)) {
                    nextValue = keyValuePairs[keyIndex + 1];
                    keyIndex += 2;
                    return;
                }
            }
            nextValue = null;
        }
    }
}
