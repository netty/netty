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

import io.netty.util.HashingStrategy;
import io.netty.util.concurrent.FastThreadLocal;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TimeZone;

import static io.netty.util.HashingStrategy.JAVA_HASHER;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Default implementation of {@link Headers};
 *
 * @param <T> the type of the header name and value.
 */
public class DefaultHeaders<T> implements Headers<T> {
    /**
     * How big the underlying array is for the hash data structure.
     * <p>
     * This should be a power of 2 so the {@link #index(int)} method can full address the memory.
     */
    private static final int ARRAY_SIZE = 1 << 4;
    private static final int HASH_MASK = ARRAY_SIZE - 1;

    private static int index(int hash) {
        // Fold the upper 16 bits onto the 16 lower bits so more of the hash code is represented
        // when translating to an index.
        return ((hash >>> 16) ^ hash) & HASH_MASK;
    }

    @SuppressWarnings("unchecked")
    private final HeaderEntry<T>[] entries = new DefaultHeaders.HeaderEntry[ARRAY_SIZE];
    protected final HeaderEntry<T> head = new HeaderEntry<T>();

    private final ValueConverter<T> valueConverter;
    private final NameValidator<T> nameValidator;
    private final HashingStrategy<T> hashingStrategy;
    int size;

    public interface NameValidator<T> {
        void validateName(T name);

        @SuppressWarnings("rawtypes")
        NameValidator NOT_NULL = new NameValidator() {
            @Override
            public void validateName(Object name) {
                checkNotNull(name, "name");
            }
        };
    }

    @SuppressWarnings("unchecked")
    public DefaultHeaders(ValueConverter<T> valueConverter) {
        this(JAVA_HASHER, valueConverter, NameValidator.NOT_NULL);
    }

    public DefaultHeaders(HashingStrategy<T> nameHashingStrategy,
            ValueConverter<T> valueConverter, NameValidator<T> nameValidator) {
        this.valueConverter = checkNotNull(valueConverter, "valueConverter");
        this.nameValidator = checkNotNull(nameValidator, "nameValidator");
        this.hashingStrategy = checkNotNull(nameHashingStrategy, "nameHashingStrategy");
        head.before = head.after = head;
    }

    @Override
    public T get(T name) {
        checkNotNull(name, "name");

        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        HeaderEntry<T> e = entries[i];
        T value = null;
        // loop until the first header was found
        while (e != null) {
            if (e.hash == h && hashingStrategy.equals(name, e.key)) {
                value = e.value;
            }

            e = e.next;
        }
        return value;
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
        int h = hashingStrategy.hashCode(name);
        return remove0(h, index(h), checkNotNull(name, "name"));
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

        LinkedList<T> values = new LinkedList<T>();

        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        HeaderEntry<T> e = entries[i];
        while (e != null) {
            if (e.hash == h && hashingStrategy.equals(name, e.key)) {
                values.addFirst(e.getValue());
            }
            e = e.next;
        }
        return values;
    }

    @Override
    public List<T> getAllAndRemove(T name) {
        List<T> all = getAll(name);
        remove(name);
        return all;
    }

    @Override
    public boolean contains(T name) {
        return get(name) != null;
    }

    @Override
    public boolean containsObject(T name, Object value) {
        return contains(name, valueConverter.convertObject(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsBoolean(T name, boolean value) {
        return contains(name, valueConverter.convertBoolean(value));
    }

    @Override
    public boolean containsByte(T name, byte value) {
        return contains(name, valueConverter.convertByte(value));
    }

    @Override
    public boolean containsChar(T name, char value) {
        return contains(name, valueConverter.convertChar(value));
    }

    @Override
    public boolean containsShort(T name, short value) {
        return contains(name, valueConverter.convertShort(value));
    }

    @Override
    public boolean containsInt(T name, int value) {
        return contains(name, valueConverter.convertInt(value));
    }

    @Override
    public boolean containsLong(T name, long value) {
        return contains(name, valueConverter.convertLong(value));
    }

    @Override
    public boolean containsFloat(T name, float value) {
        return contains(name, valueConverter.convertFloat(value));
    }

    @Override
    public boolean containsDouble(T name, double value) {
        return contains(name, valueConverter.convertDouble(value));
    }

    @Override
    public boolean containsTimeMillis(T name, long value) {
        return contains(name, valueConverter.convertTimeMillis(value));
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(T name, T value) {
        return contains(name, value, JAVA_HASHER);
    }

    public final boolean contains(T name, T value, HashingStrategy<? super T> valueHashingStrategy) {
        checkNotNull(name, "name");

        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        HeaderEntry<T> e = entries[i];
        while (e != null) {
            if (e.hash == h && hashingStrategy.equals(name, e.key) && valueHashingStrategy.equals(value, e.value)) {
                return true;
            }
            e = e.next;
        }
        return false;
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
        if (isEmpty()) {
            return Collections.emptySet();
        }
        Set<T> names = new LinkedHashSet<T>(size());
        HeaderEntry<T> e = head.after;
        while (e != head) {
            names.add(e.getKey());
            e = e.after;
        }
        return names;
    }

    @Override
    public Headers<T> add(T name, T value) {
        nameValidator.validateName(name);
        checkNotNull(value, "value");
        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        add0(h, i, name, value);
        return this;
    }

    @Override
    public Headers<T> add(T name, Iterable<? extends T> values) {
        nameValidator.validateName(name);
        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        for (T v: values) {
            add0(h, i, name, v);
        }
        return this;
    }

    @Override
    public Headers<T> add(T name, T... values) {
        nameValidator.validateName(name);
        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        for (T v: values) {
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
        checkNotNull(values, "values");
        for (Object value : values) {
            addObject(name, value);
        }
        return this;
    }

    @Override
    public Headers<T> addObject(T name, Object... values) {
        checkNotNull(values, "values");
        for (int i = 0; i < values.length; i++) {
            addObject(name, values[i]);
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
    public Headers<T> add(Headers<? extends T> headers) {
        checkNotNull(headers, "headers");
        if (headers == this) {
            throw new IllegalArgumentException("can't add to itself.");
        }
        if (headers instanceof DefaultHeaders) {
            @SuppressWarnings("unchecked")
            DefaultHeaders<T> defaultHeaders = (DefaultHeaders<T>) headers;
            HeaderEntry<T> e = defaultHeaders.head.after;
            while (e != defaultHeaders.head) {
                add(e.key, e.value);
                e = e.after;
            }
            return this;
        } else {
            for (Entry<? extends T, ? extends T> header : headers) {
                add(header.getKey(), header.getValue());
            }
        }
        return this;
    }

    @Override
    public Headers<T> set(T name, T value) {
        nameValidator.validateName(name);
        checkNotNull(value, "value");
        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        remove0(h, i, name);
        add0(h, i, name, value);
        return this;
    }

    @Override
    public Headers<T> set(T name, Iterable<? extends T> values) {
        nameValidator.validateName(name);
        checkNotNull(values, "values");

        int h = hashingStrategy.hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (T v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }

        return this;
    }

    @Override
    public Headers<T> set(T name, T... values) {
        nameValidator.validateName(name);
        checkNotNull(values, "values");

        int h = hashingStrategy.hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (T v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }

        return this;
    }

    @Override
    public Headers<T> setObject(T name, Object value) {
        checkNotNull(value, "value");
        T convertedValue = checkNotNull(valueConverter.convertObject(value), "convertedValue");
        return set(name, convertedValue);
    }

    @Override
    public Headers<T> setObject(T name, Iterable<?> values) {
        nameValidator.validateName(name);
        checkNotNull(values, "values");

        int h = hashingStrategy.hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, valueConverter.convertObject(v));
        }

        return this;
    }

    @Override
    public Headers<T> setObject(T name, Object... values) {
        nameValidator.validateName(name);
        checkNotNull(values, "values");

        int h = hashingStrategy.hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, valueConverter.convertObject(v));
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
    public Headers<T> set(Headers<? extends T> headers) {
        checkNotNull(headers, "headers");
        if (headers == this) {
            return this;
        }
        clear();
        if (headers instanceof DefaultHeaders) {
            @SuppressWarnings("unchecked")
            DefaultHeaders<T> defaultHeaders = (DefaultHeaders<T>) headers;
            HeaderEntry<T> e = defaultHeaders.head.after;
            while (e != defaultHeaders.head) {
                add(e.key, e.value);
                e = e.after;
            }
        } else {
            add(headers);
        }
        return this;
    }

    @Override
    public Headers<T> setAll(Headers<? extends T> headers) {
        checkNotNull(headers, "headers");
        if (headers == this) {
            return this;
        }
        for (T key : headers.names()) {
            remove(key);
        }
        for (Entry<? extends T, ? extends T> entry : headers) {
            add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    @Override
    public boolean remove(T name) {
        return getAndRemove(name) != null;
    }

    @Override
    public Headers<T> clear() {
        Arrays.fill(entries, null);
        head.before = head.after = head;
        size = 0;
        return this;
    }

    @Override
    public Iterator<Entry<T, T>> iterator() {
        return new HeaderIterator();
    }

    @Override
    public Boolean getBoolean(T name) {
        T v = get(name);
        return v != null ? valueConverter.convertToBoolean(v) : null;
    }

    @Override
    public boolean getBoolean(T name, boolean defaultValue) {
        Boolean v = getBoolean(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Byte getByte(T name) {
        T v = get(name);
        return v != null ? valueConverter.convertToByte(v) : null;
    }

    @Override
    public byte getByte(T name, byte defaultValue) {
        Byte v = getByte(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Character getChar(T name) {
        T v = get(name);
        return v != null ? valueConverter.convertToChar(v) : null;
    }

    @Override
    public char getChar(T name, char defaultValue) {
        Character v = getChar(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Short getShort(T name) {
        T v = get(name);
        return v != null ? valueConverter.convertToShort(v) : null;
    }

    @Override
    public short getShort(T name, short defaultValue) {
        Short v = getShort(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Integer getInt(T name) {
        T v = get(name);
        return v != null ? valueConverter.convertToInt(v) : null;
    }

    @Override
    public int getInt(T name, int defaultValue) {
        Integer v = getInt(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Long getLong(T name) {
        T v = get(name);
        return v != null ? valueConverter.convertToLong(v) : null;
    }

    @Override
    public long getLong(T name, long defaultValue) {
        Long v = getLong(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Float getFloat(T name) {
        T v = get(name);
        return v != null ? valueConverter.convertToFloat(v) : null;
    }

    @Override
    public float getFloat(T name, float defaultValue) {
        Float v = getFloat(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Double getDouble(T name) {
        T v = get(name);
        return v != null ? valueConverter.convertToDouble(v) : null;
    }

    @Override
    public double getDouble(T name, double defaultValue) {
        Double v = getDouble(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Long getTimeMillis(T name) {
        T v = get(name);
        return v != null ? valueConverter.convertToTimeMillis(v) : null;
    }

    @Override
    public long getTimeMillis(T name, long defaultValue) {
        Long v = getTimeMillis(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Boolean getBooleanAndRemove(T name) {
        T v = getAndRemove(name);
        return v != null ? valueConverter.convertToBoolean(v) : null;
    }

    @Override
    public boolean getBooleanAndRemove(T name, boolean defaultValue) {
        Boolean v = getBooleanAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Byte getByteAndRemove(T name) {
        T v = getAndRemove(name);
        return v != null ? valueConverter.convertToByte(v) : null;
    }

    @Override
    public byte getByteAndRemove(T name, byte defaultValue) {
        Byte v = getByteAndRemove(name);
        return v != null ? v : defaultValue;
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
        return v != null ? v : defaultValue;
    }

    @Override
    public Short getShortAndRemove(T name) {
        T v = getAndRemove(name);
        return v != null ? valueConverter.convertToShort(v) : null;
    }

    @Override
    public short getShortAndRemove(T name, short defaultValue) {
        Short v = getShortAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Integer getIntAndRemove(T name) {
        T v = getAndRemove(name);
        return v != null ? valueConverter.convertToInt(v) : null;
    }

    @Override
    public int getIntAndRemove(T name, int defaultValue) {
        Integer v = getIntAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Long getLongAndRemove(T name) {
        T v = getAndRemove(name);
        return v != null ? valueConverter.convertToLong(v) : null;
    }

    @Override
    public long getLongAndRemove(T name, long defaultValue) {
        Long v = getLongAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Float getFloatAndRemove(T name) {
        T v = getAndRemove(name);
        return v != null ? valueConverter.convertToFloat(v) : null;
    }

    @Override
    public float getFloatAndRemove(T name, float defaultValue) {
        Float v = getFloatAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Double getDoubleAndRemove(T name) {
        T v = getAndRemove(name);
        return v != null ? valueConverter.convertToDouble(v) : null;
    }

    @Override
    public double getDoubleAndRemove(T name, double defaultValue) {
        Double v = getDoubleAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Long getTimeMillisAndRemove(T name) {
        T v = getAndRemove(name);
        return v != null ? valueConverter.convertToTimeMillis(v) : null;
    }

    @Override
    public long getTimeMillisAndRemove(T name, long defaultValue) {
        Long v = getTimeMillisAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Headers)) {
            return false;
        }

        return equals((Headers<T>) o, JAVA_HASHER);
    }

    @SuppressWarnings("unchecked")
    @Override
    public int hashCode() {
        return hashCode(JAVA_HASHER);
    }

    /**
     * Test this object for equality against {@code h2}.
     * @param h2 The object to check equality for.
     * @param valueHashingStrategy Defines how values will be compared for equality.
     * @return {@code true} if this object equals {@code h2} given {@code valueHashingStrategy}.
     * {@code false} otherwise.
     */
    public final boolean equals(Headers<T> h2, HashingStrategy<T> valueHashingStrategy) {
        if (h2.size() != size()) {
            return false;
        }

        if (this == h2) {
            return true;
        }

        for (T name : names()) {
            List<T> otherValues = h2.getAll(name);
            List<T> values = getAll(name);
            if (otherValues.size() != values.size()) {
                return false;
            }
            for (int i = 0; i < otherValues.size(); i++) {
                if (!valueHashingStrategy.equals(otherValues.get(i), values.get(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Generate a hash code for this object given a {@link HashingStrategy} to generate hash codes for
     * individual values.
     * @param valueHashingStrategy Defines how values will be hashed.
     */
    public final int hashCode(HashingStrategy<T> valueHashingStrategy) {
        int result = 1;
        for (T name : names()) {
            result = 31 * result + hashingStrategy.hashCode(name);
            List<T> values = getAll(name);
            for (int i = 0; i < values.size(); ++i) {
                result = 31 * result + valueHashingStrategy.hashCode(values.get(i));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append('[');
        String separator = "";
        for (T name : names()) {
            List<T> values = getAll(name);
            for (int i = 0; i < values.size(); ++i) {
                builder.append(separator);
                builder.append(name).append(": ").append(values.get(i));
            }
            separator = ", ";
        }
        return builder.append(']').toString();
    }

    protected HeaderEntry<T> newHeaderEntry(int h, T name, T value, HeaderEntry<T> next) {
        return new HeaderEntry<T>(h, name, value, next, head);
    }

    protected ValueConverter<T> valueConverter() {
        return valueConverter;
    }

    private void add0(int h, int i, T name, T value) {
        // Update the hash table.
        entries[i] = newHeaderEntry(h, name, value, entries[i]);
        ++size;
    }

    /**
     * @return the first value inserted whose hash code equals {@code h} and whose name is equal to {@code name}.
     */
    private T remove0(int h, int i, T name) {
        HeaderEntry<T> e = entries[i];
        if (e == null) {
            return null;
        }

        T value = null;
        HeaderEntry<T> next = e.next;
        while (next != null) {
            if (next.hash == h && hashingStrategy.equals(name, next.key)) {
                value = next.value;
                e.next = next.next;
                next.remove();
                --size;
            } else {
                e = next;
            }

            next = e.next;
        }

        e = entries[i];
        if (e.hash == h && hashingStrategy.equals(name, e.key)) {
            if (value == null) {
                value = e.value;
            }
            entries[i] = e.next;
            e.remove();
            --size;
        }

        return value;
    }

    /**
     * This {@link DateFormat} decodes 3 formats of {@link Date}.
     *
     * <ul>
     * <li>Sun, 06 Nov 1994 08:49:37 GMT: standard specification, the only one with valid generation</li>
     * <li>Sun, 06 Nov 1994 08:49:37 GMT: obsolete specification</li>
     * <li>Sun Nov 6 08:49:37 1994: obsolete specification</li>
     * </ul>
     */
    public static final class HeaderDateFormat {
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
            Date date = dateFormat1.parse(text);
            if (date == null) {
                date = dateFormat2.parse(text);
            }
            if (date == null) {
                date = dateFormat3.parse(text);
            }
            if (date == null) {
                throw new ParseException(text, 0);
            }
            return date.getTime();
        }
    }

    private final class HeaderIterator implements Iterator<Map.Entry<T, T>> {
        private HeaderEntry<T> current = head;

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
            throw new UnsupportedOperationException("read-only iterator");
        }
    }

    protected static class HeaderEntry<T> implements Map.Entry<T, T> {
        protected final int hash;
        protected final T key;
        protected T value;
        /**
         * In bucket linked list
         */
        protected HeaderEntry<T> next;
        /**
         * Overall insertion order linked list
         */
        protected HeaderEntry<T> before, after;

        protected HeaderEntry(int hash, T key) {
            this.hash = hash;
            this.key = key;
        }

        HeaderEntry(int hash, T key, T value, HeaderEntry<T> next, HeaderEntry<T> head) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;

            after = head;
            before = head.before;
            pointNeighborsToThis();
        }

        HeaderEntry() {
            hash = -1;
            key = null;
        }

        protected final void pointNeighborsToThis() {
            before.after = this;
            after.before = this;
        }

        public final HeaderEntry<T> before() {
            return before;
        }

        public final HeaderEntry<T> after() {
            return after;
        }

        protected void remove() {
            before.after = after;
            after.before = before;
        }

        @Override
        public final T getKey() {
            return key;
        }

        @Override
        public final T getValue() {
            return value;
        }

        @Override
        public final T setValue(T value) {
            checkNotNull(value, "value");
            T oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public final String toString() {
            return key.toString() + '=' + value.toString();
        }
    }
}
