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

import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.AUTHORITY;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.METHOD;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.SCHEME;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

/**
 * An immutable collection of headers sent or received via HTTP/2.
 */
public final class DefaultHttp2Headers extends Http2Headers {
    private static final int MAX_VALUE_LENGTH = 0xFFFF; // Length is a 16-bit field
    private static final int BUCKET_SIZE = 17;

    private final HeaderEntry[] entries;
    private final HeaderEntry head;
    private final int size;

    private DefaultHttp2Headers(Builder builder) {
        entries = builder.entries;
        head = builder.head;
        size = builder.size;
    }

    @Override
    public String get(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        int h = hash(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                return e.value;
            }

            e = e.next;
        }
        return null;
    }

    @Override
    public List<String> getAll(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        LinkedList<String> values = new LinkedList<String>();

        int h = hash(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                values.addFirst(e.value);
            }
            e = e.next;
        }
        return values;
    }

    @Override
    public List<Entry<String, String>> entries() {
        List<Map.Entry<String, String>> all = new LinkedList<Map.Entry<String, String>>();

        HeaderEntry e = head.after;
        while (e != head) {
            all.add(e);
            e = e.after;
        }
        return all;
    }

    @Override
    public boolean contains(String name) {
        return get(name) != null;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Set<String> names() {
        Set<String> names = new TreeSet<String>();

        HeaderEntry e = head.after;
        while (e != head) {
            names.add(e.key);
            e = e.after;
        }
        return names;
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return new HeaderIterator();
    }

    /**
     * Short cut for {@code new DefaultHttp2Headers.Builder()}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builds instances of {@link DefaultHttp2Headers}.
     */
    public static class Builder {
        private HeaderEntry[] entries;
        private HeaderEntry head;
        private Http2Headers buildResults;
        private int size;

        public Builder() {
            clear();
        }

        /**
         * Clears all existing headers from this collection and replaces them with the given header
         * set.
         */
        public void set(Http2Headers headers) {
            // No need to lazy copy the previous results, since we're starting from scratch.
            clear();
            for (Map.Entry<String, String> entry : headers) {
                add(entry.getKey(), entry.getValue());
            }
        }

        /**
         * Adds the given header to the collection.
         *
         * @throws IllegalArgumentException if the name or value of this header is invalid for any reason.
         */
        public Builder add(final String name, final Object value) {
            // If this is the first call on the builder since the last build, copy the previous
            // results.
            lazyCopy();

            String lowerCaseName = name.toLowerCase();
            validateHeaderName(lowerCaseName);
            String strVal = toString(value);
            validateHeaderValue(strVal);
            int nameHash = hash(lowerCaseName);
            int hashTableIndex = index(nameHash);
            add0(nameHash, hashTableIndex, lowerCaseName, strVal);
            return this;
        }

        /**
         * Removes the header with the given name from this collection.
         */
        public Builder remove(final String name) {
            if (name == null) {
                throw new NullPointerException("name");
            }

            // If this is the first call on the builder since the last build, copy the previous
            // results.
            lazyCopy();

            String lowerCaseName = name.toLowerCase();
            int nameHash = hash(lowerCaseName);
            int hashTableIndex = index(nameHash);
            remove0(nameHash, hashTableIndex, lowerCaseName);
            return this;
        }

        /**
         * Sets the given header in the collection, replacing any previous values.
         *
         * @throws IllegalArgumentException if the name or value of this header is invalid for any reason.
         */
        public Builder set(final String name, final Object value) {
            // If this is the first call on the builder since the last build, copy the previous
            // results.
            lazyCopy();

            String lowerCaseName = name.toLowerCase();
            validateHeaderName(lowerCaseName);
            String strVal = toString(value);
            validateHeaderValue(strVal);
            int nameHash = hash(lowerCaseName);
            int hashTableIndex = index(nameHash);
            remove0(nameHash, hashTableIndex, lowerCaseName);
            add0(nameHash, hashTableIndex, lowerCaseName, strVal);
            return this;
        }

        /**
         * Sets the given header in the collection, replacing any previous values.
         *
         * @throws IllegalArgumentException if the name or value of this header is invalid for any reason.
         */
        public Builder set(final String name, final Iterable<?> values) {
            if (values == null) {
                throw new NullPointerException("values");
            }

            // If this is the first call on the builder since the last build, copy the previous
            // results.
            lazyCopy();

            String lowerCaseName = name.toLowerCase();
            validateHeaderName(lowerCaseName);

            int nameHash = hash(lowerCaseName);
            int hashTableIndex = index(nameHash);

            remove0(nameHash, hashTableIndex, lowerCaseName);
            for (Object v : values) {
                if (v == null) {
                    break;
                }
                String strVal = toString(v);
                validateHeaderValue(strVal);
                add0(nameHash, hashTableIndex, lowerCaseName, strVal);
            }
            return this;
        }

        /**
         * Clears all values from this collection.
         */
        public Builder clear() {
            // No lazy copy required, since we're just creating a new array.
            entries = new HeaderEntry[BUCKET_SIZE];
            head = new HeaderEntry(-1, null, null);
            head.before = head.after = head;
            buildResults = null;
            size = 0;
            return this;
        }

        /**
         * Sets the {@link PseudoHeaderName#METHOD} header.
         */
        public Builder method(String method) {
            return set(METHOD.value(), method);
        }

        /**
         * Sets the {@link PseudoHeaderName#SCHEME} header.
         */
        public Builder scheme(String scheme) {
            return set(SCHEME.value(), scheme);
        }

        /**
         * Sets the {@link PseudoHeaderName#AUTHORITY} header.
         */
        public Builder authority(String authority) {
            return set(AUTHORITY.value(), authority);
        }

        /**
         * Sets the {@link PseudoHeaderName#PATH} header.
         */
        public Builder path(String path) {
            return set(PseudoHeaderName.PATH.value(), path);
        }

        /**
         * Sets the {@link PseudoHeaderName#STATUS} header.
         */
        public Builder status(String status) {
            return set(PseudoHeaderName.STATUS.value(), status);
        }

        /**
         * Builds a new instance of {@link DefaultHttp2Headers}.
         */
        public DefaultHttp2Headers build() {
            // If this is the first call on the builder since the last build, copy the previous
            // results.
            lazyCopy();

            // Give the multimap over to the headers instance and save the build results for
            // future lazy copies if this builder is used again later.
            DefaultHttp2Headers headers = new DefaultHttp2Headers(this);
            buildResults = headers;
            return headers;
        }

        /**
         * Performs a lazy copy of the last build results, if there are any. For the typical use
         * case, headers will only be built once so no copy will be required. If the any method is
         * called on the builder after that, it will force a copy of the most recently created
         * headers object.
         */
        private void lazyCopy() {
            if (buildResults != null) {
                set(buildResults);
                buildResults = null;
            }
        }

        private void add0(int hash, int hashTableIndex, final String name, final String value) {
            // Update the hash table.
            HeaderEntry e = entries[hashTableIndex];
            HeaderEntry newEntry;
            entries[hashTableIndex] = newEntry = new HeaderEntry(hash, name, value);
            newEntry.next = e;

            // Update the linked list.
            newEntry.addBefore(head);
            size++;
        }

        private void remove0(int hash, int hashTableIndex, String name) {
            HeaderEntry e = entries[hashTableIndex];
            if (e == null) {
                return;
            }

            for (;;) {
                if (e.hash == hash && eq(name, e.key)) {
                    e.remove();
                    size--;
                    HeaderEntry next = e.next;
                    if (next != null) {
                        entries[hashTableIndex] = next;
                        e = next;
                    } else {
                        entries[hashTableIndex] = null;
                        return;
                    }
                } else {
                    break;
                }
            }

            for (;;) {
                HeaderEntry next = e.next;
                if (next == null) {
                    break;
                }
                if (next.hash == hash && eq(name, next.key)) {
                    e.next = next.next;
                    next.remove();
                    size--;
                } else {
                    e = next;
                }
            }
        }

        private static String toString(Object value) {
            if (value == null) {
                return null;
            }
            return value.toString();
        }

        /**
         * Validate a HTTP2 header value. Does not validate max length.
         */
        private static void validateHeaderValue(String value) {
            if (value == null) {
                throw new NullPointerException("value");
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == 0) {
                    throw new IllegalArgumentException("value contains null character: " + value);
                }
            }
        }

        /**
         * Validate a HTTP/2 header name.
         */
        private static void validateHeaderName(String name) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            if (name.isEmpty()) {
                throw new IllegalArgumentException("name cannot be length zero");
            }
            // Since name may only contain ascii characters, for valid names
            // name.length() returns the number of bytes when UTF-8 encoded.
            if (name.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("name exceeds allowable length: " + name);
            }
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == 0) {
                    throw new IllegalArgumentException("name contains null character: " + name);
                }
                if (c > 127) {
                    throw new IllegalArgumentException("name contains non-ascii character: " + name);
                }
            }
            // If the name looks like an HTTP/2 pseudo-header, validate it against the list of
            // valid pseudo-headers.
            if (name.startsWith(PSEUDO_HEADER_PREFIX)) {
                if (!Http2Headers.PseudoHeaderName.isPseudoHeader(name)) {
                    throw new IllegalArgumentException("Invalid HTTP/2 Pseudo-header: " + name);
                }
            }
        }
    }

    private static int hash(String name) {
        int h = 0;
        for (int i = name.length() - 1; i >= 0; i--) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c += 32;
            }
            h = 31 * h + c;
        }

        if (h > 0) {
            return h;
        } else if (h == Integer.MIN_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return -h;
        }
    }

    private static boolean eq(String name1, String name2) {
        int nameLen = name1.length();
        if (nameLen != name2.length()) {
            return false;
        }

        for (int i = nameLen - 1; i >= 0; i--) {
            char c1 = name1.charAt(i);
            char c2 = name2.charAt(i);
            if (c1 != c2) {
                if (c1 >= 'A' && c1 <= 'Z') {
                    c1 += 32;
                }
                if (c2 >= 'A' && c2 <= 'Z') {
                    c2 += 32;
                }
                if (c1 != c2) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int index(int hash) {
        return hash % BUCKET_SIZE;
    }

    private final class HeaderIterator implements Iterator<Map.Entry<String, String>> {

        private HeaderEntry current = head;

        @Override
        public boolean hasNext() {
            return current.after != head;
        }

        @Override
        public Entry<String, String> next() {
            current = current.after;

            if (current == head) {
                throw new NoSuchElementException();
            }

            return current;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class HeaderEntry implements Map.Entry<String, String> {
        final int hash;
        final String key;
        final String value;
        HeaderEntry next;
        HeaderEntry before, after;

        HeaderEntry(int hash, String key, String value) {
            this.hash = hash;
            this.key = key;
            this.value = value;
        }

        void remove() {
            before.after = after;
            after.before = before;
        }

        void addBefore(HeaderEntry e) {
            after = e;
            before = e.before;
            before.after = this;
            after.before = this;
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

        @Override
        public String toString() {
            return key + '=' + value;
        }
    }
}
