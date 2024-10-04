/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec;

import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Provides utility methods related to {@link Headers}.
 */
public final class HeadersUtils {

    private HeadersUtils() {
    }

    /**
     * {@link Headers#get(Object)} and convert each element of {@link List} to a {@link String}.
     * @param name the name of the header to retrieve
     * @return a {@link List} of header values or an empty {@link List} if no values are found.
     */
    public static <K, V> List<String> getAllAsString(Headers<K, V, ?> headers, K name) {
        final List<V> allNames = headers.getAll(name);
        return new AbstractList<String>() {
            @Override
            public String get(int index) {
                V value = allNames.get(index);
                return value != null ? value.toString() : null;
            }

            @Override
            public int size() {
                return allNames.size();
            }
        };
    }

    /**
     * {@link Headers#get(Object)} and convert the result to a {@link String}.
     * @param headers the headers to get the {@code name} from
     * @param name the name of the header to retrieve
     * @return the first header value if the header is found. {@code null} if there's no such entry.
     */
    public static <K, V> String getAsString(Headers<K, V, ?> headers, K name) {
        V orig = headers.get(name);
        return orig != null ? orig.toString() : null;
    }

    /**
     * {@link Headers#iterator()} which converts each {@link Entry}'s key and value to a {@link String}.
     */
    public static Iterator<Entry<String, String>> iteratorAsString(
            Iterable<Entry<CharSequence, CharSequence>> headers) {
        return new StringEntryIterator(headers.iterator());
    }

    /**
     * Helper for implementing toString for {@link DefaultHeaders} and wrappers such as DefaultHttpHeaders.
     * @param headersClass the class of headers
     * @param headersIt the iterator on the actual headers
     * @param size the size of the iterator
     * @return a String representation of the headers
     */
    public static <K, V> String toString(Class<?> headersClass, Iterator<Entry<K, V>> headersIt, int size) {
        String simpleName = headersClass.getSimpleName();
        if (size == 0) {
            return simpleName + "[]";
        } else {
            // original capacity assumes 20 chars per headers
            StringBuilder sb = new StringBuilder(simpleName.length() + 2 + size * 20)
                    .append(simpleName)
                    .append('[');
            while (headersIt.hasNext()) {
                Entry<?, ?> header = headersIt.next();
                sb.append(header.getKey()).append(": ").append(header.getValue()).append(", ");
            }
            sb.setLength(sb.length() - 2);
            return sb.append(']').toString();
        }
    }

    /**
     * {@link Headers#names()} and convert each element of {@link Set} to a {@link String}.
     * @param headers the headers to get the names from
     * @return a {@link Set} of header values or an empty {@link Set} if no values are found.
     */
    public static Set<String> namesAsString(Headers<CharSequence, CharSequence, ?> headers) {
        return new DelegatingNameSet(headers);
    }

    private static final class StringEntryIterator implements Iterator<Entry<String, String>> {
        private final Iterator<Entry<CharSequence, CharSequence>> iter;

        StringEntryIterator(Iterator<Entry<CharSequence, CharSequence>> iter) {
            this.iter = iter;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public Entry<String, String> next() {
            return new StringEntry(iter.next());
        }

        @Override
        public void remove() {
            iter.remove();
        }
    }

    private static final class StringEntry implements Entry<String, String> {
        private final Entry<CharSequence, CharSequence> entry;
        private String name;
        private String value;

        StringEntry(Entry<CharSequence, CharSequence> entry) {
            this.entry = entry;
        }

        @Override
        public String getKey() {
            if (name == null) {
                name = entry.getKey().toString();
            }
            return name;
        }

        @Override
        public String getValue() {
            if (value == null && entry.getValue() != null) {
                value = entry.getValue().toString();
            }
            return value;
        }

        @Override
        public String setValue(String value) {
            String old = getValue();
            entry.setValue(value);
            return old;
        }

        @Override
        public String toString() {
            return entry.toString();
        }
    }

    private static final class StringIterator<T> implements Iterator<String> {
        private final Iterator<T> iter;

        StringIterator(Iterator<T> iter) {
            this.iter = iter;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public String next() {
            T next = iter.next();
            return next != null ? next.toString() : null;
        }

        @Override
        public void remove() {
            iter.remove();
        }
    }

    private static final class DelegatingNameSet extends AbstractCollection<String> implements Set<String> {
        private final Headers<CharSequence, CharSequence, ?> headers;

        DelegatingNameSet(Headers<CharSequence, CharSequence, ?> headers) {
            this.headers = checkNotNull(headers, "headers");
        }

        @Override
        public int size() {
            return headers.names().size();
        }

        @Override
        public boolean isEmpty() {
            return headers.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return headers.contains(o.toString());
        }

        @Override
        public Iterator<String> iterator() {
            return new StringIterator<CharSequence>(headers.names().iterator());
        }
    }
}
