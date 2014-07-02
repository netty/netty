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

package io.netty.handler.codec.http2;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

/**
 * An immutable collection of headers sent or received via HTTP/2.
 */
public abstract class Http2Headers implements Iterable<Entry<String, String>> {

    public static final Http2Headers EMPTY_HEADERS = new Http2Headers() {

        @Override
        public String get(String name) {
            return null;
        }

        @Override
        public List<String> getAll(String name) {
            return Collections.emptyList();
        }

        @Override
        public List<Entry<String, String>> entries() {
            return Collections.emptyList();
        }

        @Override
        public boolean contains(String name) {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public Set<String> names() {
            return Collections.emptySet();
        }

        @Override
        public Iterator<Entry<String, String>> iterator() {
            return entries().iterator();
        }
    };

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

        HttpName(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * Returns the {@link Set} of all header names.
     */
    public abstract Set<String> names();

    /**
     * Returns the header value with the specified header name. If there is more than one header
     * value for the specified header name, the first value is returned.
     *
     * @return the header value or {@code null} if there is no such header
     */
    public abstract String get(String name);

    /**
     * Returns the header values with the specified header name.
     *
     * @return the {@link List} of header values. An empty list if there is no such header.
     */
    public abstract List<String> getAll(String name);

    /**
     * Returns all header names and values that this frame contains.
     *
     * @return the {@link List} of the header name-value pairs. An empty list if there is no header
     *         in this message.
     */
    public abstract List<Map.Entry<String, String>> entries();

    /**
     * Returns {@code true} if and only if there is a header with the specified header name.
     */
    public abstract boolean contains(String name);

    /**
     * Checks if no header exists.
     */
    public abstract boolean isEmpty();

    /**
     * Gets the {@link HttpName#METHOD} header or {@code null} if there is no such header
     */
    public final String method() {
        return get(HttpName.METHOD.value());
    }

    /**
     * Gets the {@link HttpName#SCHEME} header or {@code null} if there is no such header
     */
    public final String scheme() {
        return get(HttpName.SCHEME.value());
    }

    /**
     * Gets the {@link HttpName#AUTHORITY} header or {@code null} if there is no such header
     */
    public final String authority() {
        return get(HttpName.AUTHORITY.value());
    }

    /**
     * Gets the {@link HttpName#PATH} header or {@code null} if there is no such header
     */
    public final String path() {
        return get(HttpName.PATH.value());
    }

    /**
     * Gets the {@link HttpName#STATUS} header or {@code null} if there is no such header
     */
    public final String status() {
        return get(HttpName.STATUS.value());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        for (String name : names()) {
            result = prime * result + name.hashCode();
            Set<String> values = new TreeSet<String>(getAll(name));
            for (String value : values) {
                result = prime * result + value.hashCode();
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Http2Headers)) {
            return false;
        }
        Http2Headers other = (Http2Headers) o;

        // First, check that the set of names match.
        Set<String> names = names();
        if (!names.equals(other.names())) {
            return false;
        }

        // Compare the values for each name.
        for (String name : names) {
            List<String> values = getAll(name);
            List<String> otherValues = other.getAll(name);
            if (values.size() != otherValues.size()) {
                return false;
            }
            // Convert the values to a set and remove values from the other object to see if
            // they match.
            Set<String> valueSet = new HashSet<String>(values);
            valueSet.removeAll(otherValues);
            if (!valueSet.isEmpty()) {
                return false;
            }
        }

        // They match.
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Http2Headers[");
        for (Map.Entry<String, String> header : this) {
            builder.append(header.getKey());
            builder.append(':');
            builder.append(header.getValue());
            builder.append(',');
        }
        builder.append(']');
        return builder.toString();
    }
}
