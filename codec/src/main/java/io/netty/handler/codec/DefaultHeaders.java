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

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import static io.netty.util.internal.ObjectUtil.*;

public class DefaultHeaders<T> implements Headers<T> {
    /**
     * Allows users of this interface to specify a hash code other than the default {@link Object#hashCode()}
     */
    public interface HashCodeGenerator<T> {
        /**
         * Obtain the hash code for the given {@code name}
         *
         * @param name The name to generate the hash code for
         * @return The hash code for {@code name}
         */
        int generateHashCode(T name);
    }

    /**
     * Allows users to convert the {@code name} elements before being processed by this map
     */
    public interface NameConverter<T> {
        /**
         * Convert the {@code name} to some other form of the same object type
         *
         * @param name The object to convert
         * @return The results of the conversion
         */
        T convertName(T name);
    }

    /**
     * Uses the {@link #hashCode()} method to generate the hash code.
     */
    public static final class JavaHashCodeGenerator<T> implements HashCodeGenerator<T> {
        @Override
        public int generateHashCode(T name) {
            return name.hashCode();
        }
    }

    /**
     * A name converted which does not covert but instead just returns this {@code name} unchanged
     */
    public static final class IdentityNameConverter<T> implements NameConverter<T> {
        @Override
        public T convertName(T name) {
            return name;
        }
    }

    private static final int HASH_CODE_PRIME = 31;
    private static final int DEFAULT_BUCKET_SIZE = 17;
    private static final int DEFAULT_MAP_SIZE = 4;
    private static final NameConverter<Object> DEFAULT_NAME_CONVERTER = new IdentityNameConverter<Object>();

    private final IntObjectMap<HeaderEntry> entries;
    private final IntObjectMap<HeaderEntry> tailEntries;
    private final HeaderEntry head;
    private final Comparator<? super T> keyComparator;
    private final Comparator<? super T> valueComparator;
    private final HashCodeGenerator<T> hashCodeGenerator;
    private final ValueConverter<T> valueConverter;
    private final NameConverter<T> nameConverter;
    private final int bucketSize;
    int size;

    @SuppressWarnings("unchecked")
    public DefaultHeaders(Comparator<? super T> keyComparator, Comparator<? super T> valueComparator,
            HashCodeGenerator<T> hashCodeGenerator, ValueConverter<T> typeConverter) {
        this(keyComparator, valueComparator, hashCodeGenerator, typeConverter,
                (NameConverter<T>) DEFAULT_NAME_CONVERTER);
    }

    public DefaultHeaders(Comparator<? super T> keyComparator, Comparator<? super T> valueComparator,
            HashCodeGenerator<T> hashCodeGenerator, ValueConverter<T> typeConverter, NameConverter<T> nameConverter) {
        this(keyComparator, valueComparator, hashCodeGenerator, typeConverter, nameConverter, DEFAULT_BUCKET_SIZE,
                DEFAULT_MAP_SIZE);
    }

    public DefaultHeaders(Comparator<? super T> keyComparator, Comparator<? super T> valueComparator,
            HashCodeGenerator<T> hashCodeGenerator, ValueConverter<T> valueConverter, NameConverter<T> nameConverter,
            int bucketSize, int initialMapSize) {
        if (keyComparator == null) {
            throw new NullPointerException("keyComparator");
        }
        if (valueComparator == null) {
            throw new NullPointerException("valueComparator");
        }
        if (hashCodeGenerator == null) {
            throw new NullPointerException("hashCodeGenerator");
        }
        if (valueConverter == null) {
            throw new NullPointerException("valueConverter");
        }
        if (nameConverter == null) {
            throw new NullPointerException("nameConverter");
        }
        if (bucketSize < 1) {
            throw new IllegalArgumentException("bucketSize must be a positive integer");
        }
        head = new HeaderEntry();
        head.before = head.after = head;
        this.keyComparator = keyComparator;
        this.valueComparator = valueComparator;
        this.hashCodeGenerator = hashCodeGenerator;
        this.valueConverter = valueConverter;
        this.nameConverter = nameConverter;
        this.bucketSize = bucketSize;
        entries = new IntObjectHashMap<HeaderEntry>(initialMapSize);
        tailEntries = new IntObjectHashMap<HeaderEntry>(initialMapSize);
    }

    @Override
    public T get(T name) {
        checkNotNull(name, "name");

        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        HeaderEntry e = entries.get(i);
        while (e != null) {
            if (e.hash == h && keyComparator.compare(e.name, name) == 0) {
                return e.value;
            }
            e = e.next;
        }
        return null;
    }

    @Override
    public T get(T name, T defaultValue) {
        T value = get(name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public T getAndRemove(T name) {
        checkNotNull(name, "name");
        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        HeaderEntry e = entries.get(i);
        if (e == null) {
            return null;
        }

        T value = null;
        for (;;) {
            if (e.hash == h && keyComparator.compare(e.name, name) == 0) {
                if (value == null) {
                    value = e.value;
                }
                e.remove();
                HeaderEntry next = e.next;
                if (next != null) {
                    entries.put(i, next);
                    e = next;
                } else {
                    entries.remove(i);
                    tailEntries.remove(i);
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
            if (next.hash == h && keyComparator.compare(e.name, name) == 0) {
                if (value == null) {
                    value = next.value;
                }
                e.next = next.next;
                if (e.next == null) {
                    tailEntries.put(i, e);
                }
                next.remove();
            } else {
                e = next;
            }
        }

        return value;
    }

    @Override
    public T getAndRemove(T name, T defaultValue) {
        T value = getAndRemove(name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public List<T> getAll(T name) {
        checkNotNull(name, "name");
        List<T> values = new ArrayList<T>(4);
        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        HeaderEntry e = entries.get(i);
        while (e != null) {
            if (e.hash == h && keyComparator.compare(e.name, name) == 0) {
                values.add(e.value);
            }
            e = e.next;
        }

        return values;
    }

    @Override
    public List<T> getAllAndRemove(T name) {
        checkNotNull(name, "name");
        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        HeaderEntry e = entries.get(i);
        if (e == null) {
            return null;
        }

        List<T> values = new ArrayList<T>(4);
        for (;;) {
            if (e.hash == h && keyComparator.compare(e.name, name) == 0) {
                values.add(e.value);
                e.remove();
                HeaderEntry next = e.next;
                if (next != null) {
                    entries.put(i, next);
                    e = next;
                } else {
                    entries.remove(i);
                    tailEntries.remove(i);
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
            if (next.hash == h && keyComparator.compare(next.name, name) == 0) {
                values.add(next.value);
                e.next = next.next;
                if (e.next == null) {
                    tailEntries.put(i, e);
                }
                next.remove();
            } else {
                e = next;
            }
        }

        return values;
    }

    @Override
    public List<Entry<T, T>> entries() {
        final int size = size();
        List<Map.Entry<T, T>> localEntries = new ArrayList<Map.Entry<T, T>>(size);

        HeaderEntry e = head.after;
        while (e != head) {
            localEntries.add(e);
            e = e.after;
        }

        assert size == localEntries.size();
        return localEntries;
    }

    @Override
    public boolean contains(T name) {
        return get(name) != null;
    }

    @Override
    public boolean contains(T name, T value) {
        return contains(name, value, keyComparator, valueComparator);
    }

    @Override
    public boolean containsObject(T name, Object value) {
        return contains(name, valueConverter.convertObject(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsBoolean(T name, boolean value) {
        return contains(name, valueConverter.convertBoolean(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsByte(T name, byte value) {
        return contains(name, valueConverter.convertByte(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsChar(T name, char value) {
        return contains(name, valueConverter.convertChar(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsShort(T name, short value) {
        return contains(name, valueConverter.convertShort(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsInt(T name, int value) {
        return contains(name, valueConverter.convertInt(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsLong(T name, long value) {
        return contains(name, valueConverter.convertLong(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsFloat(T name, float value) {
        return contains(name, valueConverter.convertFloat(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsDouble(T name, double value) {
        return contains(name, valueConverter.convertDouble(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsTimeMillis(T name, long value) {
        return contains(name, valueConverter.convertTimeMillis(checkNotNull(value, "value")));
    }

    @Override
    public boolean contains(T name, T value, Comparator<? super T> comparator) {
        return contains(name, value, comparator, comparator);
    }

    @Override
    public boolean contains(T name, T value,
            Comparator<? super T> keyComparator, Comparator<? super T> valueComparator) {
        checkNotNull(name, "name");
        checkNotNull(value, "value");
        checkNotNull(keyComparator, "keyComparator");
        checkNotNull(valueComparator, "valueComparator");
        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        HeaderEntry e = entries.get(i);
        while (e != null) {
            if (e.hash == h &&
                    keyComparator.compare(e.name, name) == 0 &&
                    valueComparator.compare(e.value, value) == 0) {
                return true;
            }
            e = e.next;
        }
        return false;
    }

    @Override
    public boolean containsObject(T name, Object value, Comparator<? super T> comparator) {
        return containsObject(name, value, comparator, comparator);
    }

    @Override
    public boolean containsObject(T name, Object value, Comparator<? super T> keyComparator,
            Comparator<? super T> valueComparator) {
        return contains(
                name, valueConverter.convertObject(checkNotNull(value, "value")), keyComparator, valueComparator);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return head == head.after;
    }

    @Override
    public Set<T> names() {
        final Set<T> names = new TreeSet<T>(keyComparator);

        HeaderEntry e = head.after;
        while (e != head) {
            names.add(e.name);
            e = e.after;
        }

        return names;
    }

    @Override
    public List<T> namesList() {
        final List<T> names = new ArrayList<T>(size());

        HeaderEntry e = head.after;
        while (e != head) {
            names.add(e.name);
            e = e.after;
        }

        return names;
    }

    @Override
    public Headers<T> add(T name, T value) {
        name = convertName(name);
        checkNotNull(value, "value");
        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        add0(h, i, name, value);
        return this;
    }

    @Override
    public Headers<T> add(T name, Iterable<? extends T> values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        for (T v : values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }
        return this;
    }

    @Override
    public Headers<T> add(T name, T... values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        for (T v : values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }
        return this;
    }

    @Override
    public Headers<T> addObject(T name, Object value) {
        return add(name, valueConverter.convertObject(checkNotNull(value, "value")));
    }

    @Override
    public Headers<T> addObject(T name, Iterable<?> values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        for (Object o : values) {
            if (o == null) {
                break;
            }
            T converted = valueConverter.convertObject(o);
            checkNotNull(converted, "converted");
            add0(h, i, name, converted);
        }
        return this;
    }

    @Override
    public Headers<T> addObject(T name, Object... values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        for (Object o : values) {
            if (o == null) {
                break;
            }
            T converted = valueConverter.convertObject(o);
            checkNotNull(converted, "converted");
            add0(h, i, name, converted);
        }
        return this;
    }

    @Override
    public Headers<T> addInt(T name, int value) {
        return add(name, valueConverter.convertInt(value));
    }

    @Override
    public Headers<T> addLong(T name, long value) {
        return add(name, valueConverter.convertLong(value));
    }

    @Override
    public Headers<T> addDouble(T name, double value) {
        return add(name, valueConverter.convertDouble(value));
    }

    @Override
    public Headers<T> addTimeMillis(T name, long value) {
        return add(name, valueConverter.convertTimeMillis(value));
    }

    @Override
    public Headers<T> addChar(T name, char value) {
        return add(name, valueConverter.convertChar(value));
    }

    @Override
    public Headers<T> addBoolean(T name, boolean value) {
        return add(name, valueConverter.convertBoolean(value));
    }

    @Override
    public Headers<T> addFloat(T name, float value) {
        return add(name, valueConverter.convertFloat(value));
    }

    @Override
    public Headers<T> addByte(T name, byte value) {
        return add(name, valueConverter.convertByte(value));
    }

    @Override
    public Headers<T> addShort(T name, short value) {
        return add(name, valueConverter.convertShort(value));
    }

    @Override
    public Headers<T> add(Headers<T> headers) {
        checkNotNull(headers, "headers");

        add0(headers);
        return this;
    }

    @Override
    public Headers<T> set(T name, T value) {
        name = convertName(name);
        checkNotNull(value, "value");
        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        remove0(h, i, name);
        add0(h, i, name, value);
        return this;
    }

    @Override
    public Headers<T> set(T name, Iterable<? extends T> values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        remove0(h, i, name);
        for (T v : values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }

        return this;
    }

    @Override
    public Headers<T> set(T name, T... values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        remove0(h, i, name);
        for (T v : values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }

        return this;
    }

    @Override
    public Headers<T> setObject(T name, Object value) {
        return set(name, valueConverter.convertObject(checkNotNull(value, "value")));
    }

    @Override
    public Headers<T> setObject(T name, Iterable<?> values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        remove0(h, i, name);
        for (Object o : values) {
            if (o == null) {
                break;
            }
            T converted = valueConverter.convertObject(o);
            checkNotNull(converted, "converted");
            add0(h, i, name, converted);
        }

        return this;
    }

    @Override
    public Headers<T> setObject(T name, Object... values) {
        name = convertName(name);
        checkNotNull(values, "values");

        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        remove0(h, i, name);
        for (Object o : values) {
            if (o == null) {
                break;
            }
            T converted = valueConverter.convertObject(o);
            checkNotNull(converted, "converted");
            add0(h, i, name, converted);
        }

        return this;
    }

    @Override
    public Headers<T> setInt(T name, int value) {
        return set(name, valueConverter.convertInt(value));
    }

    @Override
    public Headers<T> setLong(T name, long value) {
        return set(name, valueConverter.convertLong(value));
    }

    @Override
    public Headers<T> setDouble(T name, double value) {
        return set(name, valueConverter.convertDouble(value));
    }

    @Override
    public Headers<T> setTimeMillis(T name, long value) {
        return set(name, valueConverter.convertTimeMillis(value));
    }

    @Override
    public Headers<T> setFloat(T name, float value) {
        return set(name, valueConverter.convertFloat(value));
    }

    @Override
    public Headers<T> setChar(T name, char value) {
        return set(name, valueConverter.convertChar(value));
    }

    @Override
    public Headers<T> setBoolean(T name, boolean value) {
        return set(name, valueConverter.convertBoolean(value));
    }

    @Override
    public Headers<T> setByte(T name, byte value) {
        return set(name, valueConverter.convertByte(value));
    }

    @Override
    public Headers<T> setShort(T name, short value) {
        return set(name, valueConverter.convertShort(value));
    }

    @Override
    public Headers<T> set(Headers<T> headers) {
        checkNotNull(headers, "headers");

        clear();
        add0(headers);
        return this;
    }

    @Override
    public Headers<T> setAll(Headers<T> headers) {
        checkNotNull(headers, "headers");

        if (headers instanceof DefaultHeaders) {
            DefaultHeaders<T> m = (DefaultHeaders<T>) headers;
            HeaderEntry e = m.head.after;
            while (e != m.head) {
                set(e.name, e.value);
                e = e.after;
            }
        } else {
            try {
                headers.forEachEntry(setAllVisitor());
            } catch (Exception ex) {
                PlatformDependent.throwException(ex);
            }
        }

        return this;
    }

    @Override
    public boolean remove(T name) {
        checkNotNull(name, "name");
        int h = hashCodeGenerator.generateHashCode(name);
        int i = index(h);
        return remove0(h, i, name);
    }

    @Override
    public Headers<T> clear() {
        entries.clear();
        tailEntries.clear();
        head.before = head.after = head;
        size = 0;
        return this;
    }

    @Override
    public Iterator<Entry<T, T>> iterator() {
        return new KeyValueHeaderIterator();
    }

    @Override
    public Map.Entry<T, T> forEachEntry(EntryVisitor<T> visitor) throws Exception {
        HeaderEntry e = head.after;
        while (e != head) {
            if (!visitor.visit(e)) {
                return e;
            }
            e = e.after;
        }
        return null;
    }

    @Override
    public T forEachName(NameVisitor<T> visitor) throws Exception {
        HeaderEntry e = head.after;
        while (e != head) {
            if (!visitor.visit(e.name)) {
                return e.name;
            }
            e = e.after;
        }
        return null;
    }

    @Override
    public Boolean getBoolean(T name) {
        T v = get(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToBoolean(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public boolean getBoolean(T name, boolean defaultValue) {
        Boolean v = getBoolean(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Byte getByte(T name) {
        T v = get(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToByte(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public byte getByte(T name, byte defaultValue) {
        Byte v = getByte(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Character getChar(T name) {
        T v = get(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToChar(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public char getChar(T name, char defaultValue) {
        Character v = getChar(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Short getShort(T name) {
        T v = get(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToShort(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public short getInt(T name, short defaultValue) {
        Short v = getShort(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Integer getInt(T name) {
        T v = get(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToInt(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public int getInt(T name, int defaultValue) {
        Integer v = getInt(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Long getLong(T name) {
        T v = get(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToLong(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public long getLong(T name, long defaultValue) {
        Long v = getLong(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Float getFloat(T name) {
        T v = get(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToFloat(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public float getFloat(T name, float defaultValue) {
        Float v = getFloat(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Double getDouble(T name) {
        T v = get(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToDouble(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public double getDouble(T name, double defaultValue) {
        Double v = getDouble(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Long getTimeMillis(T name) {
        T v = get(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToTimeMillis(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public long getTimeMillis(T name, long defaultValue) {
        Long v = getTimeMillis(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Boolean getBooleanAndRemove(T name) {
        T v = getAndRemove(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToBoolean(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public boolean getBooleanAndRemove(T name, boolean defaultValue) {
        Boolean v = getBooleanAndRemove(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Byte getByteAndRemove(T name) {
        T v = getAndRemove(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToByte(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public byte getByteAndRemove(T name, byte defaultValue) {
        Byte v = getByteAndRemove(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Character getCharAndRemove(T name) {
        T v = getAndRemove(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToChar(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public char getCharAndRemove(T name, char defaultValue) {
        Character v = getCharAndRemove(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Short getShortAndRemove(T name) {
        T v = getAndRemove(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToShort(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public short getShortAndRemove(T name, short defaultValue) {
        Short v = getShortAndRemove(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Integer getIntAndRemove(T name) {
        T v = getAndRemove(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToInt(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public int getIntAndRemove(T name, int defaultValue) {
        Integer v = getIntAndRemove(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Long getLongAndRemove(T name) {
        T v = getAndRemove(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToLong(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public long getLongAndRemove(T name, long defaultValue) {
        Long v = getLongAndRemove(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Float getFloatAndRemove(T name) {
        T v = getAndRemove(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToFloat(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public float getFloatAndRemove(T name, float defaultValue) {
        Float v = getFloatAndRemove(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Double getDoubleAndRemove(T name) {
        T v = getAndRemove(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToDouble(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public double getDoubleAndRemove(T name, double defaultValue) {
        Double v = getDoubleAndRemove(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public Long getTimeMillisAndRemove(T name) {
        T v = getAndRemove(name);
        if (v == null) {
            return null;
        }
        try {
            return valueConverter.convertToTimeMillis(v);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public long getTimeMillisAndRemove(T name, long defaultValue) {
        Long v = getTimeMillisAndRemove(name);
        return v == null ? defaultValue : v;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHeaders)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        DefaultHeaders<T> h2 = (DefaultHeaders<T>) o;
        // First, check that the set of names match. Don't use a TreeSet for comparison
        // because we want to force the keyComparator to be used for all comparisons
        List<T> namesList = namesList();
        List<T> otherNamesList = h2.namesList();
        if (!equals(namesList, otherNamesList, keyComparator)) {
            return false;
        }

        // Compare the values for each name. Don't use a TreeSet for comparison
        // because we want to force the valueComparator to be used for all comparisons
        Set<T> names = new TreeSet<T>(keyComparator);
        names.addAll(namesList);
        for (T name : names) {
            if (!equals(getAll(name), h2.getAll(name), valueComparator)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compare two lists using the {@code comparator} for all comparisons (not using the equals() operator)
     * @param lhs Left hand side
     * @param rhs Right hand side
     * @param comparator Comparator which will be used for all comparisons (equals() on objects will not be used)
     * @return True if {@code lhs} == {@code rhs} according to {@code comparator}. False otherwise.
     */
    private static <T> boolean equals(List<T> lhs, List<T> rhs, Comparator<? super T> comparator) {
        final int lhsSize = lhs.size();
        if (lhsSize != rhs.size()) {
            return false;
        }

        // Don't use a TreeSet to do the comparison.  We want to force the comparator
        // to be used instead of the object's equals()
        Collections.sort(lhs, comparator);
        Collections.sort(rhs, comparator);
        for (int i = 0; i < lhsSize; ++i) {
            if (comparator.compare(lhs.get(i), rhs.get(i)) != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (T name : names()) {
            result = HASH_CODE_PRIME * result + name.hashCode();
            List<T> values = getAll(name);
            Collections.sort(values, valueComparator);
            for (int i = 0; i < values.size(); ++i) {
                result = HASH_CODE_PRIME * result + hashCodeGenerator.generateHashCode(values.get(i));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append('[');
        for (T name : names()) {
            List<T> values = getAll(name);
            Collections.sort(values, valueComparator);
            for (int i = 0; i < values.size(); ++i) {
                builder.append(name).append(": ").append(values.get(i)).append(", ");
            }
        }
        if (builder.length() > 2) { // remove the trailing ", "
            builder.setLength(builder.length() - 2);
        }
        return builder.append(']').toString();
    }

    protected ValueConverter<T> valueConverter() {
        return valueConverter;
    }

    private T convertName(T name) {
        return nameConverter.convertName(checkNotNull(name, "name"));
    }

    private int index(int hash) {
        return Math.abs(hash % bucketSize);
    }

    private void add0(Headers<T> headers) {
        if (headers.isEmpty()) {
            return;
        }

        if (headers instanceof DefaultHeaders) {
            DefaultHeaders<T> m = (DefaultHeaders<T>) headers;
            HeaderEntry e = m.head.after;
            while (e != m.head) {
                add(e.name, e.value);
                e = e.after;
            }
        } else {
            try {
                headers.forEachEntry(addAllVisitor());
            } catch (Exception ex) {
                PlatformDependent.throwException(ex);
            }
        }
    }

    private void add0(int h, int i, T name, T value) {
        // Update the per-bucket hash table linked list
        HeaderEntry newEntry = new HeaderEntry(h, name, value);
        HeaderEntry oldTail = tailEntries.get(i);
        if (oldTail == null) {
            entries.put(i, newEntry);
        } else {
            oldTail.next = newEntry;
        }
        tailEntries.put(i, newEntry);

        // Update the overall insertion order linked list
        newEntry.addBefore(head);
    }

    private boolean remove0(int h, int i, T name) {
        HeaderEntry e = entries.get(i);
        if (e == null) {
            return false;
        }

        boolean removed = false;
        for (;;) {
            if (e.hash == h && keyComparator.compare(e.name, name) == 0) {
                e.remove();
                HeaderEntry next = e.next;
                if (next != null) {
                    entries.put(i, next);
                    e = next;
                } else {
                    entries.remove(i);
                    tailEntries.remove(i);
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
            if (next.hash == h && keyComparator.compare(next.name, name) == 0) {
                e.next = next.next;
                if (e.next == null) {
                    tailEntries.put(i, e);
                }
                next.remove();
                removed = true;
            } else {
                e = next;
            }
        }

        return removed;
    }

    private EntryVisitor<T> setAllVisitor() {
        return new EntryVisitor<T>() {
            @Override
            public boolean visit(Entry<T, T> entry) {
                set(entry.getKey(), entry.getValue());
                return true;
            }
        };
    }

    private EntryVisitor<T> addAllVisitor() {
        return new EntryVisitor<T>() {
            @Override
            public boolean visit(Entry<T, T> entry) {
                add(entry.getKey(), entry.getValue());
                return true;
            }
        };
    }

    private final class HeaderEntry implements Map.Entry<T, T> {
        final int hash;
        final T name;
        T value;
        /**
         * In bucket linked list
         */
        HeaderEntry next;
        /**
         * Overall insertion order linked list
         */
        HeaderEntry before, after;

        HeaderEntry(int hash, T name, T value) {
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
            after = e;
            before = e.before;
            before.after = this;
            after.before = this;
            ++size;
        }

        @Override
        public T getKey() {
            return name;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public T setValue(T value) {
            checkNotNull(value, "value");
            T oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                .append(name)
                .append('=')
                .append(value)
                .toString();
        }
    }

    protected final class KeyValueHeaderIterator implements Iterator<Entry<T, T>> {

        private HeaderEntry current = head;

        @Override
        public boolean hasNext() {
            return current.after != head;
        }

        @Override
        public Entry<T, T> next() {
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

    /**
     * This DateFormat decodes 3 formats of {@link java.util.Date}, but only encodes the one, the first:
     * <ul>
     * <li>Sun, 06 Nov 1994 08:49:37 GMT: standard specification, the only one with valid generation</li>
     * <li>Sun, 06 Nov 1994 08:49:37 GMT: obsolete specification</li>
     * <li>Sun Nov 6 08:49:37 1994: obsolete specification</li>
     * </ul>
     */
    static final class HeaderDateFormat {
        private static final ParsePosition parsePos = new ParsePosition(0);
        private static final FastThreadLocal<HeaderDateFormat> dateFormatThreadLocal =
                new FastThreadLocal<HeaderDateFormat>() {
            @Override
            protected HeaderDateFormat initialValue() {
                return new HeaderDateFormat();
            }
        };

        static HeaderDateFormat get() {
            return dateFormatThreadLocal.get();
        }

        /**
         * Standard date format:
         *
         * <pre>
         * Sun, 06 Nov 1994 08:49:37 GMT -> E, d MMM yyyy HH:mm:ss z
         * </pre>
         */
        private final DateFormat dateFormat1 = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        /**
         * First obsolete format:
         *
         * <pre>
         * Sunday, 06-Nov-94 08:49:37 GMT -> E, d-MMM-y HH:mm:ss z
         * </pre>
         */
        private final DateFormat dateFormat2 = new SimpleDateFormat("E, dd-MMM-yy HH:mm:ss z", Locale.ENGLISH);
        /**
         * Second obsolete format
         *
         * <pre>
         * Sun Nov 6 08:49:37 1994 -> EEE, MMM d HH:mm:ss yyyy
         * </pre>
         */
        private final DateFormat dateFormat3 = new SimpleDateFormat("E MMM d HH:mm:ss yyyy", Locale.ENGLISH);

        private HeaderDateFormat() {
            TimeZone tz = TimeZone.getTimeZone("GMT");
            dateFormat1.setTimeZone(tz);
            dateFormat2.setTimeZone(tz);
            dateFormat3.setTimeZone(tz);
        }

        long parse(String text) throws ParseException {
            Date date = dateFormat1.parse(text, parsePos);
            if (date == null) {
                date = dateFormat2.parse(text, parsePos);
            }
            if (date == null) {
                date = dateFormat3.parse(text, parsePos);
            }
            if (date == null) {
                throw new ParseException(text, 0);
            }
            return date.getTime();
        }

        long parse(String text, long defaultValue) {
            Date date = dateFormat1.parse(text, parsePos);
            if (date == null) {
                date = dateFormat2.parse(text, parsePos);
            }
            if (date == null) {
                date = dateFormat3.parse(text, parsePos);
            }
            if (date == null) {
                return defaultValue;
            }
            return date.getTime();
        }
    }
}
