/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.util.AsciiString;
import io.netty.util.internal.UnstableApi;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static io.netty.handler.codec.CharSequenceValueConverter.INSTANCE;
import static io.netty.handler.codec.http.DefaultHttpHeaders.HttpNameValidator;
import static io.netty.util.AsciiString.contentEquals;
import static io.netty.util.AsciiString.contentEqualsIgnoreCase;

/**
 * A variant of {@link HttpHeaders} which only supports read-only methods.
 * <p>
 * Any array passed to this class may be used directly in the underlying data structures of this class. If these
 * arrays may be modified it is the caller's responsibility to supply this class with a copy of the array.
 * <p>
 * This may be a good alternative to {@link DefaultHttpHeaders} if your have a fixed set of headers which will not
 * change.
 */
@UnstableApi
public final class ReadOnlyHttpHeaders extends HttpHeaders {
    private final CharSequence[] nameValuePairs;

    /**
     * Create a new instance.
     * @param validateHeaders {@code true} to validate the contents of each header name.
     * @param nameValuePairs An array of the structure {@code [<name,value>,<name,value>,...]}.
     *                      A copy will <strong>NOT</strong> be made of this array. If the contents of this array
     *                      may be modified externally you are responsible for passing in a copy.
     */
    public ReadOnlyHttpHeaders(boolean validateHeaders, CharSequence... nameValuePairs) {
        if ((nameValuePairs.length & 1) != 0) {
            throw newInvalidArraySizeException();
        }
        if (validateHeaders) {
            validateHeaders(nameValuePairs);
        }
        this.nameValuePairs = nameValuePairs;
    }

    private static IllegalArgumentException newInvalidArraySizeException() {
        return new IllegalArgumentException("nameValuePairs must be arrays of [name, value] pairs");
    }

    private static void validateHeaders(CharSequence... keyValuePairs) {
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            HttpNameValidator.validateName(keyValuePairs[i]);
        }
    }

    private CharSequence get0(CharSequence name) {
        final int nameHash = AsciiString.hashCode(name);
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            CharSequence roName = nameValuePairs[i];
            if (AsciiString.hashCode(roName) == nameHash && contentEqualsIgnoreCase(roName, name)) {
                return nameValuePairs[i + 1];
            }
        }
        return null;
    }

    @Override
    public String get(String name) {
        CharSequence value = get0(name);
        return value == null ? null : value.toString();
    }

    @Override
    public Integer getInt(CharSequence name) {
        CharSequence value = get0(name);
        return value == null ? null : INSTANCE.convertToInt(value);
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        CharSequence value = get0(name);
        return value == null ? defaultValue : INSTANCE.convertToInt(value);
    }

    @Override
    public Short getShort(CharSequence name) {
        CharSequence value = get0(name);
        return value == null ? null : INSTANCE.convertToShort(value);
    }

    @Override
    public short getShort(CharSequence name, short defaultValue) {
        CharSequence value = get0(name);
        return value == null ? defaultValue : INSTANCE.convertToShort(value);
    }

    @Override
    public Long getTimeMillis(CharSequence name) {
        CharSequence value = get0(name);
        return value == null ? null : INSTANCE.convertToTimeMillis(value);
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        CharSequence value = get0(name);
        return value == null ? defaultValue : INSTANCE.convertToTimeMillis(value);
    }

    @Override
    public List<String> getAll(String name) {
        if (isEmpty()) {
            return Collections.emptyList();
        }
        final int nameHash = AsciiString.hashCode(name);
        List<String> values = new ArrayList<String>(4);
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            CharSequence roName = nameValuePairs[i];
            if (AsciiString.hashCode(roName) == nameHash && contentEqualsIgnoreCase(roName, name)) {
                values.add(nameValuePairs[i + 1].toString());
            }
        }
        return values;
    }

    @Override
    public List<Map.Entry<String, String>> entries() {
        if (isEmpty()) {
            return Collections.emptyList();
        }
        List<Map.Entry<String, String>> entries = new ArrayList<Map.Entry<String, String>>(size());
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            entries.add(new SimpleImmutableEntry<String, String>(nameValuePairs[i].toString(),
                    nameValuePairs[i + 1].toString()));
        }
        return entries;
    }

    @Override
    public boolean contains(String name) {
        return get0(name) != null;
    }

    @Override
    public boolean contains(String name, String value, boolean ignoreCase) {
        return containsValue(name, value, ignoreCase);
    }

    @Override
    public boolean containsValue(CharSequence name, CharSequence value, boolean ignoreCase) {
        if (ignoreCase) {
            for (int i = 0; i < nameValuePairs.length; i += 2) {
                if (contentEqualsIgnoreCase(nameValuePairs[i], name) &&
                        contentEqualsIgnoreCase(nameValuePairs[i + 1], value)) {
                    return true;
                }
            }
        } else {
            for (int i = 0; i < nameValuePairs.length; i += 2) {
                if (contentEqualsIgnoreCase(nameValuePairs[i], name) &&
                        contentEquals(nameValuePairs[i + 1], value)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Iterator<String> valueStringIterator(CharSequence name) {
        return new ReadOnlyStringValueIterator(name);
    }

    @Override
    public Iterator<CharSequence> valueCharSequenceIterator(CharSequence name) {
        return new ReadOnlyValueIterator(name);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return new ReadOnlyStringIterator();
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iteratorCharSequence() {
        return new ReadOnlyIterator();
    }

    @Override
    public boolean isEmpty() {
        return nameValuePairs.length == 0;
    }

    @Override
    public int size() {
        return nameValuePairs.length >>> 1;
    }

    @Override
    public Set<String> names() {
        if (isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> names = new LinkedHashSet<String>(size());
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            names.add(nameValuePairs[i].toString());
        }
        return names;
    }

    @Override
    public HttpHeaders add(String name, Object value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public HttpHeaders addInt(CharSequence name, int value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public HttpHeaders addShort(CharSequence name, short value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public HttpHeaders set(String name, Object value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public HttpHeaders set(String name, Iterable<?> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public HttpHeaders setInt(CharSequence name, int value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public HttpHeaders setShort(CharSequence name, short value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public HttpHeaders remove(String name) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public HttpHeaders clear() {
        throw new UnsupportedOperationException("read only");
    }

    private final class ReadOnlyIterator implements Map.Entry<CharSequence, CharSequence>,
            Iterator<Map.Entry<CharSequence, CharSequence>> {
        private CharSequence key;
        private CharSequence value;
        private int nextNameIndex;

        @Override
        public boolean hasNext() {
            return nextNameIndex != nameValuePairs.length;
        }

        @Override
        public Map.Entry<CharSequence, CharSequence> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            key = nameValuePairs[nextNameIndex];
            value = nameValuePairs[nextNameIndex + 1];
            nextNameIndex += 2;
            return this;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public CharSequence getKey() {
            return key;
        }

        @Override
        public CharSequence getValue() {
            return value;
        }

        @Override
        public CharSequence setValue(CharSequence value) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public String toString() {
            return key.toString() + '=' + value.toString();
        }
    }

    private final class ReadOnlyStringIterator implements Map.Entry<String, String>,
            Iterator<Map.Entry<String, String>> {
        private String key;
        private String value;
        private int nextNameIndex;

        @Override
        public boolean hasNext() {
            return nextNameIndex != nameValuePairs.length;
        }

        @Override
        public Map.Entry<String, String> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            key = nameValuePairs[nextNameIndex].toString();
            value = nameValuePairs[nextNameIndex + 1].toString();
            nextNameIndex += 2;
            return this;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("read only");
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
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public String toString() {
            return key + '=' + value;
        }
    }

    private final class ReadOnlyStringValueIterator implements Iterator<String> {
        private final CharSequence name;
        private final int nameHash;
        private int nextNameIndex;

        ReadOnlyStringValueIterator(CharSequence name) {
            this.name = name;
            nameHash = AsciiString.hashCode(name);
            nextNameIndex = findNextValue();
        }

        @Override
        public boolean hasNext() {
            return nextNameIndex != -1;
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String value = nameValuePairs[nextNameIndex + 1].toString();
            nextNameIndex = findNextValue();
            return value;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("read only");
        }

        private int findNextValue() {
            for (int i = nextNameIndex; i < nameValuePairs.length; i += 2) {
                final CharSequence roName = nameValuePairs[i];
                if (nameHash == AsciiString.hashCode(roName) && contentEqualsIgnoreCase(name, roName)) {
                    return i;
                }
            }
            return -1;
        }
    }

    private final class ReadOnlyValueIterator implements Iterator<CharSequence> {
        private final CharSequence name;
        private final int nameHash;
        private int nextNameIndex;

        ReadOnlyValueIterator(CharSequence name) {
            this.name = name;
            nameHash = AsciiString.hashCode(name);
            nextNameIndex = findNextValue();
        }

        @Override
        public boolean hasNext() {
            return nextNameIndex != -1;
        }

        @Override
        public CharSequence next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            CharSequence value = nameValuePairs[nextNameIndex + 1];
            nextNameIndex = findNextValue();
            return value;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("read only");
        }

        private int findNextValue() {
            for (int i = nextNameIndex; i < nameValuePairs.length; i += 2) {
                final CharSequence roName = nameValuePairs[i];
                if (nameHash == AsciiString.hashCode(roName) && contentEqualsIgnoreCase(name, roName)) {
                    return i;
                }
            }
            return -1;
        }
    }
}
