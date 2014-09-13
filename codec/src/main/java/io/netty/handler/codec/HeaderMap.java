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
package io.netty.handler.codec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

/**
 * Basic map of header names to values. This is meant to be a central storage mechanism by all
 * headers implementations. All keys and values are stored as {@link CharSequence}.
 */
public class HeaderMap implements Iterable<Entry<CharSequence, CharSequence>> {
    private static final int BUCKET_SIZE = 17;
    private static final int HASH_CODE_PRIME = 31;

    public static final NameConverter IDENTITY_NAME_CONVERTER = new NameConverter() {
        @Override
        public CharSequence convertName(CharSequence name) {
            return name;
        }
    };

    public interface EntryVisitor {
        boolean visit(Entry<CharSequence, CharSequence> entry);
    }

    public interface NameVisitor {
        boolean visit(CharSequence name);
    }

    public interface NameConverter {
        CharSequence convertName(CharSequence name);
    }

    public interface ValueMarshaller {
        CharSequence marshal(Object value);
    }

    public interface ValueUnmarshaller<T> {
        T unmarshal(CharSequence value);
    }

    private final HeaderEntry[] entries = new HeaderEntry[BUCKET_SIZE];
    private final HeaderEntry head = new HeaderEntry();
    private final NameConverter nameConverter;
    private final boolean ignoreCase;
    int size;

    public HeaderMap() {
        this(true);
    }

    public HeaderMap(boolean ignoreCase) {
        this(ignoreCase, IDENTITY_NAME_CONVERTER);
    }

    public HeaderMap(boolean ignoreCase, NameConverter nameConverter) {
        this.nameConverter = checkNotNull(nameConverter, "nameConverter");
        head.before = head.after = head;
        this.ignoreCase = ignoreCase;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public HeaderMap add(CharSequence name, CharSequence value) {
        name = convertName(name);
        checkNotNull(value, "value");
        int h = hashCode(name);
        int i = index(h);
        add0(h, i, name, value);
        return this;
    }

    public HeaderMap add(CharSequence name, Iterable<? extends CharSequence> values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCode(name);
        int i = index(h);
        for (CharSequence v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }
        return this;
    }

    public HeaderMap add(CharSequence name, CharSequence... values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCode(name);
        int i = index(h);
        for (CharSequence v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }
        return this;
    }

    public HeaderMap addConvertedValues(CharSequence name, ValueMarshaller converter, Iterable<?> values) {
        name = convertName(name);
        checkNotNull(values, "values");
        checkNotNull(converter, "converter");

        int h = hashCode(name);
        int i = index(h);
        for (Object v : values) {
            if (v == null) {
                break;
            }
            CharSequence convertedVal = converter.marshal(v);
            add0(h, i, name, convertedVal);
        }
        return this;
    }

    public HeaderMap addConvertedValues(CharSequence name, ValueMarshaller converter, Object... values) {
        name = convertName(name);
        checkNotNull(values, "values");
        checkNotNull(converter, "converter");

        int h = hashCode(name);
        int i = index(h);
        for (Object v : values) {
            if (v == null) {
                break;
            }
            CharSequence convertedVal = converter.marshal(v);
            add0(h, i, name, convertedVal);
        }
        return this;
    }

    private void add0(int h, int i, CharSequence name, CharSequence value) {
        // Update the hash table.
        HeaderEntry e = entries[i];
        HeaderEntry newEntry;
        entries[i] = newEntry = new HeaderEntry(h, name, value);
        newEntry.next = e;

        // Update the linked list.
        newEntry.addBefore(head);
    }

    public HeaderMap add(HeaderMap headers) {
        checkNotNull(headers, "headers");

        add0(headers);
        return this;
    }

    private void add0(HeaderMap headers) {
        if (headers.isEmpty()) {
            return;
        }

        HeaderMap m = (HeaderMap) headers;
        HeaderEntry e = m.head.after;
        while (e != m.head) {
            add(e.name, e.value);
            e = e.after;
        }
    }

    public boolean remove(CharSequence name) {
        checkNotNull(name, "name");
        int h = hashCode(name);
        int i = index(h);
        return remove0(h, i, name);
    }

    private boolean remove0(int h, int i, CharSequence name) {
        HeaderEntry e = entries[i];
        if (e == null) {
            return false;
        }

        boolean removed = false;
        for (;;) {
            if (e.hash == h && nameEquals(e.name, name)) {
                e.remove();
                HeaderEntry next = e.next;
                if (next != null) {
                    entries[i] = next;
                    e = next;
                } else {
                    entries[i] = null;
                    return true;
                }
                removed = true;
            } else {
                break;
            }
        }

        for (;;) {
            HeaderEntry next = e.next;
            if (next == null) {
                break;
            }
            if (next.hash == h && nameEquals(next.name, name)) {
                e.next = next.next;
                next.remove();
                removed = true;
            } else {
                e = next;
            }
        }

        return removed;
    }

    public HeaderMap set(CharSequence name, CharSequence value) {
        name = convertName(name);
        checkNotNull(value, "value");
        int h = hashCode(name);
        int i = index(h);
        remove0(h, i, name);
        add0(h, i, name, value);
        return this;
    }

    public HeaderMap set(CharSequence name, Iterable<? extends CharSequence> values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (CharSequence v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }

        return this;
    }

    public HeaderMap set(CharSequence name, CharSequence... values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (CharSequence v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }

        return this;
    }

    public HeaderMap set(CharSequence name, ValueMarshaller converter, Iterable<?> values) {
        name = convertName(name);
        checkNotNull(converter, "converter");
        checkNotNull(values, "values");

        int h = hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            CharSequence convertedVal = converter.marshal(v);
            add0(h, i, name, convertedVal);
        }

        return this;
    }

    public HeaderMap set(CharSequence name, ValueMarshaller converter, Object... values) {
        name = convertName(name);
        checkNotNull(converter, "converter");
        checkNotNull(values, "values");

        int h = hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            CharSequence convertedVal = converter.marshal(v);
            add0(h, i, name, convertedVal);
        }

        return this;
    }

    public HeaderMap set(HeaderMap headers) {
        checkNotNull(headers, "headers");

        clear();
        add0(headers);
        return this;
    }

    public HeaderMap setAll(HeaderMap headers) {
        checkNotNull(headers, "headers");

        HeaderEntry e = headers.head.after;
        while (e != headers.head) {
            set(e.name, e.value);
            e = e.after;
        }

        return this;
    }

    public HeaderMap clear() {
        Arrays.fill(entries, null);
        head.before = head.after = head;
        size = 0;
        return this;
    }

    public CharSequence get(CharSequence name) {
        checkNotNull(name, "name");

        int h = hashCode(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        CharSequence value = null;
        // loop until the first header was found
        while (e != null) {
            if (e.hash == h && nameEquals(e.name, name)) {
                value = e.value;
            }

            e = e.next;
        }
        return value;
    }

    public CharSequence get(CharSequence name, CharSequence defaultValue) {
        CharSequence v = get(name);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    public CharSequence getAndRemove(CharSequence name) {
        checkNotNull(name, "name");
        int h = hashCode(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        if (e == null) {
            return null;
        }

        CharSequence value = null;
        for (;;) {
            if (e.hash == h && nameEquals(e.name, name)) {
                value = e.value;
                e.remove();
                HeaderEntry next = e.next;
                if (next != null) {
                    entries[i] = next;
                    e = next;
                } else {
                    entries[i] = null;
                    return value;
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
            if (next.hash == h && nameEquals(next.name, name)) {
                value = next.value;
                e.next = next.next;
                next.remove();
            } else {
                e = next;
            }
        }

        return value;
    }

    public CharSequence getAndRemove(CharSequence name, CharSequence defaultValue) {
        CharSequence v = getAndRemove(name);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    public List<CharSequence> getAll(CharSequence name) {
        checkNotNull(name, "name");

        List<CharSequence> values = new ArrayList<CharSequence>(4);
        int h = hashCode(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && nameEquals(e.name, name)) {
                values.add(e.getValue());
            }
            e = e.next;
        }

        Collections.reverse(values);
        return values;
    }

    public List<CharSequence> getAllAndRemove(CharSequence name) {
        checkNotNull(name, "name");
        int h = hashCode(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        if (e == null) {
            return null;
        }

        List<CharSequence> values = new ArrayList<CharSequence>(4);
        for (;;) {
            if (e.hash == h && nameEquals(e.name, name)) {
                values.add(e.getValue());
                e.remove();
                HeaderEntry next = e.next;
                if (next != null) {
                    entries[i] = next;
                    e = next;
                } else {
                    entries[i] = null;
                    Collections.reverse(values);
                    return values;
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
            if (next.hash == h && nameEquals(next.name, name)) {
                values.add(next.getValue());
                e.next = next.next;
                next.remove();
            } else {
                e = next;
            }
        }

        Collections.reverse(values);
        return values;
    }

    public <T> List<T> getAll(CharSequence name, ValueUnmarshaller<T> unmarshaller) {
        checkNotNull(name, "name");
        checkNotNull(unmarshaller, "unmarshaller");

        List<T> values = new ArrayList<T>(4);
        int h = hashCode(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && nameEquals(e.name, name)) {
                values.add(unmarshaller.unmarshal(e.value));
            }
            e = e.next;
        }

        Collections.reverse(values);
        return values;
    }

    public <T> List<T> getAllAndRemove(CharSequence name, ValueUnmarshaller<T> unmarshaller) {
        checkNotNull(name, "name");
        checkNotNull(unmarshaller, "unmarshaller");

        int h = hashCode(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        if (e == null) {
            return null;
        }

        List<T> values = new ArrayList<T>(4);
        for (;;) {
            if (e.hash == h && nameEquals(e.name, name)) {
                values.add(unmarshaller.unmarshal(e.value));
                e.remove();
                HeaderEntry next = e.next;
                if (next != null) {
                    entries[i] = next;
                    e = next;
                } else {
                    entries[i] = null;
                    Collections.reverse(values);
                    return values;
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
            if (next.hash == h && nameEquals(next.name, name)) {
                values.add(unmarshaller.unmarshal(next.getValue()));
                e.next = next.next;
                next.remove();
            } else {
                e = next;
            }
        }

        Collections.reverse(values);
        return values;
    }

    public List<Map.Entry<CharSequence, CharSequence>> entries() {
        int cnt = 0;
        int size = size();
        @SuppressWarnings("unchecked")
        Map.Entry<CharSequence, CharSequence>[] all = new Map.Entry[size];

        HeaderEntry e = head.after;
        while (e != head) {
            all[cnt ++] = e;
            e = e.after;
        }

        assert size == cnt;
        return Arrays.asList(all);
    }

    @Override
    public Iterator<Entry<CharSequence, CharSequence>> iterator() {
        return new HeaderIterator();
    }

    public boolean contains(CharSequence name) {
        return get(name) != null;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return head == head.after;
    }

    public boolean contains(CharSequence name, CharSequence value) {
        return contains(name, value, false);
    }

    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        checkNotNull(name, "name");
        checkNotNull(value, "value");
        int h = hashCode(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && nameEquals(e.name, name)) {
                if (valueEquals(e.value, value, ignoreCase)) {
                    return true;
                }
            }
            e = e.next;
        }
        return false;
    }

    public Set<CharSequence> names() {
        return names(ignoreCase);
    }

    /**
     * Get the set of names for all text headers
     * @param caseInsensitive {@code true} if names should be added in a case insensitive
     * @return The set of names for all text headers
     */
    public Set<CharSequence> names(boolean caseInsensitive) {
        final Set<CharSequence> names =
                caseInsensitive ? new TreeSet<CharSequence>(
                        AsciiString.CHARSEQUENCE_CASE_INSENSITIVE_ORDER)
                        : new LinkedHashSet<CharSequence>(size());
        forEachName(new NameVisitor() {
            @Override
            public boolean visit(CharSequence name) {
                names.add(name);
                return true;
            }
        });
        return names;
    }

    public HeaderMap forEachEntry(EntryVisitor visitor) {
        HeaderEntry e = head.after;
        while (e != head) {
            if (!visitor.visit(e)) {
                break;
            }
            e = e.after;
        }
        return this;
    }

    public void forEachName(NameVisitor visitor) {
        HeaderEntry e = head.after;
        while (e != head) {
            if (!visitor.visit(e.getKey())) {
                return;
            }
            e = e.after;
        }
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (CharSequence name : names()) {
            result = HASH_CODE_PRIME * result + name.hashCode();
            Set<CharSequence> values = new TreeSet<CharSequence>(getAll(name));
            for (CharSequence value : values) {
                result = HASH_CODE_PRIME * result + value.hashCode();
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HeaderMap)) {
            return false;
        }

        // First, check that the set of names match.
        HeaderMap h2 = (HeaderMap) o;
        Set<CharSequence> names = names();
        if (!names.equals(h2.names())) {
            return false;
        }

        // Compare the values for each name.
        for (CharSequence name : names) {
            List<CharSequence> values = getAll(name);
            List<CharSequence> otherValues = h2.getAll(name);
            if (values.size() != otherValues.size()) {
                return false;
            }

            // Convert the values to a set and remove values from the other object to see if
            // they match.
            Set<CharSequence> valueSet = new HashSet<CharSequence>(values);
            valueSet.removeAll(otherValues);
            if (!valueSet.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder =
                new StringBuilder('[');
        Set<CharSequence> names = names(true);
        for (CharSequence name : names) {
            Set<CharSequence> valueSet = new TreeSet<CharSequence>(getAll(name));
            for (CharSequence value : valueSet) {
                builder.append(name).append(": ").append(value).append(", ");
            }
        }
        // Now remove the last ", " if there is one.
        if (builder.length() >= 3) {
            builder.setLength(builder.length() - 2);
        }
        return builder.append("]").toString();
    }

    private boolean nameEquals(CharSequence a, CharSequence b) {
        return equals(a, b, ignoreCase);
    }

    private static boolean valueEquals(CharSequence a, CharSequence b, boolean ignoreCase) {
        return equals(a, b, ignoreCase);
    }

    private static boolean equals(CharSequence a, CharSequence b, boolean ignoreCase) {
        if (ignoreCase) {
            return AsciiString.equalsIgnoreCase(a, b);
        } else {
            return AsciiString.equals(a, b);
        }
    }

    private static int index(int hash) {
        return Math.abs(hash % BUCKET_SIZE);
    }

    private CharSequence convertName(CharSequence name) {
        return nameConverter.convertName(checkNotNull(name, "name"));
    }

    private static <T> T checkNotNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        return value;
    }

    private static int hashCode(CharSequence name) {
        return AsciiString.caseInsensitiveHashCode(name);
    }

    private final class HeaderEntry implements Map.Entry<CharSequence, CharSequence> {
        final int hash;
        final CharSequence name;
        CharSequence value;
        HeaderEntry next;
        HeaderEntry before, after;

        HeaderEntry(int hash, CharSequence name, CharSequence value) {
            this.hash = hash;
            this.name = name;
            this.value = value;
        }

        HeaderEntry() {
            hash = -1;
            name = null;
            value = null;
        }

        void remove() {
            before.after = after;
            after.before = before;
            --size;
        }

        void addBefore(HeaderEntry e) {
            after  = e;
            before = e.before;
            before.after = this;
            after.before = this;
            ++size;
        }

        @Override
        public CharSequence getKey() {
            return name;
        }

        @Override
        public CharSequence getValue() {
            return value;
        }

        @Override
        public CharSequence setValue(CharSequence value) {
            checkNotNull(value, "value");
            checkNotNull(value, "value");
            CharSequence oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public String toString() {
            return new StringBuilder(name).append('=').append(value).toString();
        }
    }

    protected final class HeaderIterator implements Iterator<Entry<CharSequence, CharSequence>> {

        private HeaderEntry current = head;

        @Override
        public boolean hasNext() {
            return current.after != head;
        }

        @Override
        public Entry<CharSequence, CharSequence> next() {
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
}
