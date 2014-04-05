/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2.draft10;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * An immutable collection of headers sent or received via HTTP/2.
 */
public final class Http2Headers implements Iterable<Entry<String, String>> {
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
    private static final Iterator<String> EMPTY_ITERATOR =
            Collections.<String>emptyList().iterator();

    /**
     * HTTP2 header names.
     */
    public enum HttpName {
        /**
         * {@code :method}.
         */
        METHOD(":method"),

        /**
         * {@code :scheme}.
         */
        SCHEME(":scheme"),

        /**
         * {@code :authority}.
         */
        AUTHORITY(":authority"),

        /**
         * {@code :path}.
         */
        PATH(":path"),

        /**
         * {@code :status}.
         */
        STATUS(":status");

        private final String value;

        private HttpName(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    private final Map<String, List<String>> headers;

    private Http2Headers(Builder builder) {
        // Assume ownership of the builder's map.
        this.headers = builder.map;
    }

    public String getHeader(String name) {
        List<String> col = headers.get(name);
        return col.isEmpty() ? null : col.get(0);
    }

    public Collection<String> getHeaders(String name) {
        return Collections.unmodifiableCollection(headers.get(name));
    }

    public String getMethod() {
        return getHeader(HttpName.METHOD.value());
    }

    public String getScheme() {
        return getHeader(HttpName.SCHEME.value());
    }

    public String getAuthority() {
        return getHeader(HttpName.AUTHORITY.value());
    }

    public String getPath() {
        return getHeader(HttpName.PATH.value());
    }

    public String getStatus() {
        return getHeader(HttpName.STATUS.value());
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return new HeadersIterator();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((headers == null) ? 0 : headers.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Http2Headers other = (Http2Headers) obj;
        if (headers == null) {
            if (other.headers != null) {
                return false;
            }
        } else if (!headers.equals(other.headers)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return headers.toString();
    }

    /**
     * Short cut for {@code new Http2Headers.Builder()}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builds instances of {@link Http2Headers}. This class is not thread-safe.
     */
    public static class Builder {
        private Map<String, List<String>> map = new HashMap<String, List<String>>();
        private boolean needToCopy;

        public Builder clear() {
            copyIfNeeded();
            map.clear();
            return this;
        }

        public Builder addHeaders(Http2Headers headers) {
            if (headers == null) {
                throw new IllegalArgumentException("headers must not be null.");
            }
            copyIfNeeded();
            for (Map.Entry<String, List<String>> entry : headers.headers.entrySet()) {
                getOrCreateValues(entry.getKey()).addAll(entry.getValue());
            }
            return this;
        }

        public Builder addHeader(String name, String value) {
            copyIfNeeded();
            getOrCreateValues(name).add(value);
            return this;
        }

        public Builder addHeader(byte[] name, byte[] value) {
            addHeader(new String(name, DEFAULT_CHARSET), new String(value, DEFAULT_CHARSET));
            return this;
        }

        public Builder setMethod(String value) {
            return addHeader(HttpName.METHOD.value(), value);
        }

        public Builder setScheme(String value) {
            return addHeader(HttpName.SCHEME.value(), value);
        }

        public Builder setAuthority(String value) {
            return addHeader(HttpName.AUTHORITY.value(), value);
        }

        public Builder setPath(String value) {
            return addHeader(HttpName.PATH.value(), value);
        }

        public Builder setStatus(String value) {
            return addHeader(HttpName.STATUS.value(), value);
        }

        public Http2Headers build() {
            // We're giving the map over to the headers object. Future mutations
            // to the builder will require a copy.
            needToCopy = true;
            return new Http2Headers(this);
        }

        private void copyIfNeeded() {
            if (needToCopy) {
                needToCopy = false;

                // Copy the map.
                Map<String, List<String>> oldMap = map;
                map = new HashMap<String, List<String>>();
                for (Map.Entry<String, List<String>> entry : oldMap.entrySet()) {
                    map.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
                }
            }
        }

        private List<String> getOrCreateValues(String key) {
            List<String> values = map.get(key);
            if (values == null) {
                values = new ArrayList<String>();
                map.put(key, values);
            }
            return values;
        }
    }

    /**
     * A facade to allow iterating over the headers map as though it's a simple collection of
     * header values.
     */
    private final class HeadersIterator implements Iterator<Entry<String, String>> {
        private Iterator<Entry<String, List<String>>> keyIterator;
        private String key;
        private Iterator<String> valueIterator = EMPTY_ITERATOR;

        private HeadersIterator() {
            keyIterator = headers.entrySet().iterator();
            if (keyIterator.hasNext()) {
                // Initialize the value iterator to point at the first list of values.
                nextKey();
            }
        }

        @Override
        public boolean hasNext() {
            return valueIterator.hasNext() || keyIterator.hasNext();
        }

        @Override
        public Entry<String, String> next() {
            if (valueIterator.hasNext()) {
                return new ImmutableEntry(key, valueIterator.next());
            }

            // Nothing left in the current values list. Advance to the next key in the map.
            // This will either locate the next list or throw if no collections remain.
            nextKey();

            // Recurse. The recursion should terminate after one call since either it will find
            // the next header or the end of the list is reached.
            return next();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void nextKey() {
            Map.Entry<String, List<String>> entry = keyIterator.next();
            key = entry.getKey();
            valueIterator = entry.getValue().iterator();
        }
    }

    /**
     * Not using the JDK implementation in order to stay compatible with Java 5.
     */
    private static final class ImmutableEntry implements Map.Entry<String, String> {
        private final String key;
        private final String value;

        public ImmutableEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String setValue(String value) {
            throw new UnsupportedOperationException();
        }
    }
}
