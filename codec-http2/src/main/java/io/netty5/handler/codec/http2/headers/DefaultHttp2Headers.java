/*
 * Copyright 2022 The Netty Project
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
package io.netty5.handler.codec.http2.headers;

import io.netty5.handler.codec.http.headers.DefaultHttpHeaders;
import io.netty5.handler.codec.http.headers.HeaderValidationException;
import io.netty5.handler.codec.http.headers.HttpCookiePair;
import io.netty5.handler.codec.http.headers.HttpHeaderValidationUtil;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http.headers.HttpSetCookie;
import io.netty5.handler.codec.http.headers.MultiMap;
import io.netty5.util.AsciiString;
import io.netty5.util.ByteProcessor;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.netty5.util.AsciiString.isUpperCase;

/**
 * Default implementation of {@link Http2Headers}.
 *
 * @apiNote It is an implementation detail that this class extends {@link MultiMap}. The multi-map itself is not part
 * of the public API for this class. Only the methods declared in {@link HttpHeaders} are considered public API.
 */
public class DefaultHttp2Headers extends DefaultHttpHeaders implements Http2Headers {
    private static final ByteProcessor HTTP2_NAME_VALIDATOR_PROCESSOR = value -> !isUpperCase(value);

    /**
     * Create a new instance.
     *
     * @param arraySizeHint   A hint as to how large the hash data structure should be. The next positive power of two
     *                        will be used. An upper bound may be enforced.
     * @param validateNames   {@code true} to validate header names.
     * @param validateCookies {@code true} to validate cookie contents when parsing.
     * @param validateValues  {@code true} to validate header values.
     */
    public DefaultHttp2Headers(int arraySizeHint, boolean validateNames, boolean validateCookies,
                                  boolean validateValues) {
        super(arraySizeHint, validateNames, validateCookies, validateValues);
    }

    @Override
    protected CharSequence validateKey(@Nullable CharSequence name, boolean forAdd) {
        if (name == null || name.length() == 0) {
            throw new HeaderValidationException("empty headers are not allowed");
        }
        if (validateNames) {
            if (PseudoHeaderName.hasPseudoHeaderFormat(name)) {
                if (!PseudoHeaderName.isPseudoHeader(name)) {
                    throw new HeaderValidationException("'" + name + "' is not a standard pseudo-header.");
                }
                if (forAdd && contains(name)) {
                    throw new HeaderValidationException("Duplicate HTTP/2 pseudo-header '" + name + "' encountered.");
                }
            } else {
                validateHeaderName(name);
                if (name instanceof AsciiString) {
                    int index = ((AsciiString) name).forEachByte(HTTP2_NAME_VALIDATOR_PROCESSOR);
                    if (index != -1) {
                        throw new HeaderValidationException("'" + name + "' is an invalid header name.");
                    }
                } else {
                    for (int i = 0; i < name.length(); ++i) {
                        if (isUpperCase(name.charAt(i))) {
                            throw new HeaderValidationException("'" + name + "' is an invalid header name.");
                        }
                    }
                }
                if (HttpHeaderValidationUtil.isConnectionHeader(name, true)) {
                    throw new HeaderValidationException(
                            "Illegal connection-specific header '" + name + "' encountered.");
                }
            }
        }
        return name;
    }

    @Override
    protected CharSequence validateValue(CharSequence key, CharSequence value) {
        if (validateValues && HttpHeaderValidationUtil.isTeNotTrailers(key, value)) {
            throw new HeaderValidationException(
                    "Illegal value specified for the 'TE' header (only 'trailers' is allowed).");
        }
        return super.validateValue(key, value);
    }

    @Override
    public Http2Headers copy() {
        DefaultHttp2Headers copy = new DefaultHttp2Headers(
                size(), validateNames, validateCookies, validateValues);
        copy.add(this);
        return copy;
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence value) {
        super.add(name, value);
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, Iterable<? extends CharSequence> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, Iterator<? extends CharSequence> valuesItr) {
        super.add(name, valuesItr);
        return this;
    }

    @Override
    public Http2Headers add(CharSequence name, CharSequence... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public Http2Headers add(HttpHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence value) {
        super.set(name, value);
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, Iterable<? extends CharSequence> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, Iterator<? extends CharSequence> valueItr) {
        super.set(name, valueItr);
        return this;
    }

    @Override
    public Http2Headers set(CharSequence name, CharSequence... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public Http2Headers set(final HttpHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public Http2Headers replace(final HttpHeaders headers) {
        super.replace(headers);
        return this;
    }

    @Override
    public Http2Headers clear() {
        super.clear();
        return this;
    }

    @Override
    public Http2Headers addCookie(HttpCookiePair cookie) {
        super.addCookie(cookie);
        return this;
    }

    @Override
    public Http2Headers addCookie(final CharSequence name, final CharSequence value) {
        super.addCookie(name, value);
        return this;
    }

    @Override
    public Http2Headers addSetCookie(HttpSetCookie cookie) {
        super.addSetCookie(cookie);
        return this;
    }

    @Override
    public Http2Headers addSetCookie(final CharSequence name, final CharSequence value) {
        super.addSetCookie(name, value);
        return this;
    }

    @Override
    public Http2Headers method(CharSequence value) {
        set(PseudoHeaderName.METHOD.value(), value);
        return this;
    }

    @Override
    public Http2Headers scheme(CharSequence value) {
        set(PseudoHeaderName.SCHEME.value(), value);
        return this;
    }

    @Override
    public Http2Headers authority(CharSequence value) {
        set(PseudoHeaderName.AUTHORITY.value(), value);
        return this;
    }

    @Override
    public Http2Headers path(CharSequence value) {
        set(PseudoHeaderName.PATH.value(), value);
        return this;
    }

    @Override
    public Http2Headers status(CharSequence value) {
        set(PseudoHeaderName.STATUS.value(), value);
        return this;
    }

    @Override
    public CharSequence method() {
        return get(PseudoHeaderName.METHOD.value());
    }

    @Override
    public CharSequence scheme() {
        return get(PseudoHeaderName.SCHEME.value());
    }

    @Override
    public CharSequence authority() {
        return get(PseudoHeaderName.AUTHORITY.value());
    }

    @Override
    public CharSequence path() {
        return get(PseudoHeaderName.PATH.value());
    }

    @Override
    public CharSequence status() {
        return get(PseudoHeaderName.STATUS.value());
    }

    @Override
    public Iterator<Entry<CharSequence, CharSequence>> iterator() {
        return concat(filter(super.iterator(), entry -> PseudoHeaderName.isPseudoHeader(entry.getKey())),
                      () -> filter(super.iterator(), entry -> !PseudoHeaderName.isPseudoHeader(entry.getKey())));
    }

    private static <T> Iterator<T> concat(Iterator<T> first, Supplier<Iterator<T>> secondSupplier) {
        return new Iterator<T>() {
            private Iterator<T> current = first;
            private Supplier<Iterator<T>> supplierOfSecond = secondSupplier;

            @Override
            public boolean hasNext() {
                while (!current.hasNext()) {
                    if (supplierOfSecond == null) {
                        return false;
                    }
                    current = supplierOfSecond.get();
                    supplierOfSecond = null;
                }
                return true;
            }

            @Override
            public void remove() {
                current.remove();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        };
    }

    private static <T> Iterator<T> filter(Iterator<T> iterator, Predicate<T> predicate) {
        if (!iterator.hasNext()) {
            return iterator;
        }
        EntryIterator<T> entryIterator = (EntryIterator<T>) iterator;
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                while (entryIterator.hasNext() && !predicate.test(entryIterator.peekNext())) {
                    entryIterator.next();
                }
                return entryIterator.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return entryIterator.next();
            }

            @Override
            public void remove() {
                entryIterator.remove();
            }
        };
    }
}
