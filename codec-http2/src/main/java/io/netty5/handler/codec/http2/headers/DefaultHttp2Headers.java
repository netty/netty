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
import io.netty5.util.internal.UnstableApi;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.function.Supplier;

@UnstableApi
public class DefaultHttp2Headers extends DefaultHttpHeaders implements Http2Headers {
    /**
     * Create a new instance.
     *
     * @param arraySizeHint   A hint as to how large the hash data structure should be. The next positive power of two
     *                        will be used. An upper bound may be enforced.
     * @param validateNames   {@code true} to validate header names.
     * @param validateCookies {@code true} to validate cookie contents when parsing.
     * @param validateValues  {@code true} to validate header values.
     */
    protected DefaultHttp2Headers(int arraySizeHint, boolean validateNames, boolean validateCookies,
                                  boolean validateValues) {
        super(arraySizeHint, validateNames, validateCookies, validateValues);
    }

    @Override
    protected CharSequence validateKey(@Nullable CharSequence name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Empty header names are not allowed");
        }
        if (validateNames) {
            if (PseudoHeaderName.hasPseudoHeaderFormat(name)) {
                if (!PseudoHeaderName.isPseudoHeader(name)) {
                    throw new IllegalArgumentException(name + " is not a standard pseudo-header.");
                }
            } else {
                validateHeaderName(name);
            }
        }
        return name;
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
