/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec;

import io.netty.util.HashingStrategy;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

import static io.netty.util.HashingStrategy.JAVA_HASHER;
import static io.netty.util.internal.MathUtil.findNextPositivePowerOfTwo;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Default implementation of {@link Headers};
 *
 * @param <K> the type of the header name.
 * @param <V> the type of the header value.
 * @param <T> the type to use for return values when the intention is to return {@code this} object.
 */
public class DefaultHeaders<K, V, T extends Headers<K, V, T>> implements Headers<K, V, T> {
    /**
     * Constant used to seed the hash code generation. Could be anything but this was borrowed from murmur3.
     */
    static final int HASH_CODE_SEED = 0xc2b2ae35;

    private final HeaderEntry<K, V>[] entries;
    protected final HeaderEntry<K, V> head;

    private final byte hashMask;
    private final ValueConverter<V> valueConverter;
    private final NameValidator<K> nameValidator;
    private final HashingStrategy<K> hashingStrategy;
    int size;

    public interface NameValidator<K> {
        /**
         * Verify that {@code name} is valid.
         * @param name The name to validate.
         * @throws RuntimeException if {@code name} is not valid.
         */
        void validateName(K name);

        @SuppressWarnings("rawtypes")
        NameValidator NOT_NULL = new NameValidator() {
            @Override
            public void validateName(Object name) {
                checkNotNull(name, "name");
            }
        };
    }

    @SuppressWarnings("unchecked")
    public DefaultHeaders(ValueConverter<V> valueConverter) {
        this(JAVA_HASHER, valueConverter);
    }

    @SuppressWarnings("unchecked")
    public DefaultHeaders(ValueConverter<V> valueConverter, NameValidator<K> nameValidator) {
        this(JAVA_HASHER, valueConverter, nameValidator);
    }

    @SuppressWarnings("unchecked")
    public DefaultHeaders(HashingStrategy<K> nameHashingStrategy, ValueConverter<V> valueConverter) {
        this(nameHashingStrategy, valueConverter, NameValidator.NOT_NULL);
    }

    public DefaultHeaders(HashingStrategy<K> nameHashingStrategy,
            ValueConverter<V> valueConverter, NameValidator<K> nameValidator) {
        this(nameHashingStrategy, valueConverter, nameValidator, 16);
    }

    /**
     * Create a new instance.
     * @param nameHashingStrategy Used to hash and equality compare names.
     * @param valueConverter Used to convert values to/from native types.
     * @param nameValidator Used to validate name elements.
     * @param arraySizeHint A hint as to how large the hash data structure should be.
     * The next positive power of two will be used. An upper bound may be enforced.
     */
    @SuppressWarnings("unchecked")
    public DefaultHeaders(HashingStrategy<K> nameHashingStrategy,
            ValueConverter<V> valueConverter, NameValidator<K> nameValidator, int arraySizeHint) {
        this.valueConverter = checkNotNull(valueConverter, "valueConverter");
        this.nameValidator = checkNotNull(nameValidator, "nameValidator");
        this.hashingStrategy = checkNotNull(nameHashingStrategy, "nameHashingStrategy");
        // Enforce a bound of [2, 128] because hashMask is a byte. The max possible value of hashMask is one less
        // than the length of this array, and we want the mask to be > 0.
        entries = new DefaultHeaders.HeaderEntry[findNextPositivePowerOfTwo(max(2, min(arraySizeHint, 128)))];
        hashMask = (byte) (entries.length - 1);
        head = new HeaderEntry<K, V>();
    }

    @Override
    public V get(K name) {
        checkNotNull(name, "name");

        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        HeaderEntry<K, V> e = entries[i];
        V value = null;
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
    public V get(K name, V defaultValue) {
        V value = get(name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public V getAndRemove(K name) {
        int h = hashingStrategy.hashCode(name);
        return remove0(h, index(h), checkNotNull(name, "name"));
    }

    @Override
    public V getAndRemove(K name, V defaultValue) {
        V value = getAndRemove(name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public List<V> getAll(K name) {
        checkNotNull(name, "name");

        LinkedList<V> values = new LinkedList<V>();

        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        HeaderEntry<K, V> e = entries[i];
        while (e != null) {
            if (e.hash == h && hashingStrategy.equals(name, e.key)) {
                values.addFirst(e.getValue());
            }
            e = e.next;
        }
        return values;
    }

    /**
     * Equivalent to {@link #getAll(Object)} but no intermediate list is generated.
     * @param name the name of the header to retrieve
     * @return an {@link Iterator} of header values corresponding to {@code name}.
     */
    public Iterator<V> valueIterator(K name) {
        return new ValueIterator(name);
    }

    @Override
    public List<V> getAllAndRemove(K name) {
        List<V> all = getAll(name);
        remove(name);
        return all;
    }

    @Override
    public boolean contains(K name) {
        return get(name) != null;
    }

    @Override
    public boolean containsObject(K name, Object value) {
        return contains(name, valueConverter.convertObject(checkNotNull(value, "value")));
    }

    @Override
    public boolean containsBoolean(K name, boolean value) {
        return contains(name, valueConverter.convertBoolean(value));
    }

    @Override
    public boolean containsByte(K name, byte value) {
        return contains(name, valueConverter.convertByte(value));
    }

    @Override
    public boolean containsChar(K name, char value) {
        return contains(name, valueConverter.convertChar(value));
    }

    @Override
    public boolean containsShort(K name, short value) {
        return contains(name, valueConverter.convertShort(value));
    }

    @Override
    public boolean containsInt(K name, int value) {
        return contains(name, valueConverter.convertInt(value));
    }

    @Override
    public boolean containsLong(K name, long value) {
        return contains(name, valueConverter.convertLong(value));
    }

    @Override
    public boolean containsFloat(K name, float value) {
        return contains(name, valueConverter.convertFloat(value));
    }

    @Override
    public boolean containsDouble(K name, double value) {
        return contains(name, valueConverter.convertDouble(value));
    }

    @Override
    public boolean containsTimeMillis(K name, long value) {
        return contains(name, valueConverter.convertTimeMillis(value));
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(K name, V value) {
        return contains(name, value, JAVA_HASHER);
    }

    public final boolean contains(K name, V value, HashingStrategy<? super V> valueHashingStrategy) {
        checkNotNull(name, "name");

        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        HeaderEntry<K, V> e = entries[i];
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
    public Set<K> names() {
        if (isEmpty()) {
            return Collections.emptySet();
        }
        Set<K> names = new LinkedHashSet<K>(size());
        HeaderEntry<K, V> e = head.after;
        while (e != head) {
            names.add(e.getKey());
            e = e.after;
        }
        return names;
    }

    @Override
    public T add(K name, V value) {
        nameValidator.validateName(name);
        checkNotNull(value, "value");
        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        add0(h, i, name, value);
        return thisT();
    }

    @Override
    public T add(K name, Iterable<? extends V> values) {
        nameValidator.validateName(name);
        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        for (V v: values) {
            add0(h, i, name, v);
        }
        return thisT();
    }

    @Override
    public T add(K name, V... values) {
        nameValidator.validateName(name);
        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        for (V v: values) {
            add0(h, i, name, v);
        }
        return thisT();
    }

    @Override
    public T addObject(K name, Object value) {
        return add(name, valueConverter.convertObject(checkNotNull(value, "value")));
    }

    @Override
    public T addObject(K name, Iterable<?> values) {
        for (Object value : values) {
            addObject(name, value);
        }
        return thisT();
    }

    @Override
    public T addObject(K name, Object... values) {
        for (Object value: values) {
            addObject(name, value);
        }
        return thisT();
    }

    @Override
    public T addInt(K name, int value) {
        return add(name, valueConverter.convertInt(value));
    }

    @Override
    public T addLong(K name, long value) {
        return add(name, valueConverter.convertLong(value));
    }

    @Override
    public T addDouble(K name, double value) {
        return add(name, valueConverter.convertDouble(value));
    }

    @Override
    public T addTimeMillis(K name, long value) {
        return add(name, valueConverter.convertTimeMillis(value));
    }

    @Override
    public T addChar(K name, char value) {
        return add(name, valueConverter.convertChar(value));
    }

    @Override
    public T addBoolean(K name, boolean value) {
        return add(name, valueConverter.convertBoolean(value));
    }

    @Override
    public T addFloat(K name, float value) {
        return add(name, valueConverter.convertFloat(value));
    }

    @Override
    public T addByte(K name, byte value) {
        return add(name, valueConverter.convertByte(value));
    }

    @Override
    public T addShort(K name, short value) {
        return add(name, valueConverter.convertShort(value));
    }

    @Override
    public T add(Headers<? extends K, ? extends V, ?> headers) {
        if (headers == this) {
            throw new IllegalArgumentException("can't add to itself.");
        }
        addImpl(headers);
        return thisT();
    }

    protected void addImpl(Headers<? extends K, ? extends V, ?> headers) {
        if (headers instanceof DefaultHeaders) {
            @SuppressWarnings("unchecked")
            final DefaultHeaders<? extends K, ? extends V, T> defaultHeaders =
                    (DefaultHeaders<? extends K, ? extends V, T>) headers;
            HeaderEntry<? extends K, ? extends V> e = defaultHeaders.head.after;
            if (defaultHeaders.hashingStrategy == hashingStrategy &&
                    defaultHeaders.nameValidator == nameValidator) {
                // Fastest copy
                while (e != defaultHeaders.head) {
                    add0(e.hash, index(e.hash), e.key, e.value);
                    e = e.after;
                }
            } else {
                // Fast copy
                while (e != defaultHeaders.head) {
                    add(e.key, e.value);
                    e = e.after;
                }
            }
        } else {
            // Slow copy
            for (Entry<? extends K, ? extends V> header : headers) {
                add(header.getKey(), header.getValue());
            }
        }
    }

    @Override
    public T set(K name, V value) {
        nameValidator.validateName(name);
        checkNotNull(value, "value");
        int h = hashingStrategy.hashCode(name);
        int i = index(h);
        remove0(h, i, name);
        add0(h, i, name, value);
        return thisT();
    }

    @Override
    public T set(K name, Iterable<? extends V> values) {
        nameValidator.validateName(name);
        checkNotNull(values, "values");

        int h = hashingStrategy.hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (V v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }

        return thisT();
    }

    @Override
    public T set(K name, V... values) {
        nameValidator.validateName(name);
        checkNotNull(values, "values");

        int h = hashingStrategy.hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (V v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, v);
        }

        return thisT();
    }

    @Override
    public T setObject(K name, Object value) {
        checkNotNull(value, "value");
        V convertedValue = checkNotNull(valueConverter.convertObject(value), "convertedValue");
        return set(name, convertedValue);
    }

    @Override
    public T setObject(K name, Iterable<?> values) {
        nameValidator.validateName(name);

        int h = hashingStrategy.hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, valueConverter.convertObject(v));
        }

        return thisT();
    }

    @Override
    public T setObject(K name, Object... values) {
        nameValidator.validateName(name);

        int h = hashingStrategy.hashCode(name);
        int i = index(h);

        remove0(h, i, name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            add0(h, i, name, valueConverter.convertObject(v));
        }

        return thisT();
    }

    @Override
    public T setInt(K name, int value) {
        return set(name, valueConverter.convertInt(value));
    }

    @Override
    public T setLong(K name, long value) {
        return set(name, valueConverter.convertLong(value));
    }

    @Override
    public T setDouble(K name, double value) {
        return set(name, valueConverter.convertDouble(value));
    }

    @Override
    public T setTimeMillis(K name, long value) {
        return set(name, valueConverter.convertTimeMillis(value));
    }

    @Override
    public T setFloat(K name, float value) {
        return set(name, valueConverter.convertFloat(value));
    }

    @Override
    public T setChar(K name, char value) {
        return set(name, valueConverter.convertChar(value));
    }

    @Override
    public T setBoolean(K name, boolean value) {
        return set(name, valueConverter.convertBoolean(value));
    }

    @Override
    public T setByte(K name, byte value) {
        return set(name, valueConverter.convertByte(value));
    }

    @Override
    public T setShort(K name, short value) {
        return set(name, valueConverter.convertShort(value));
    }

    @Override
    public T set(Headers<? extends K, ? extends V, ?> headers) {
        if (headers != this) {
            clear();
            addImpl(headers);
        }
        return thisT();
    }

    @Override
    public T setAll(Headers<? extends K, ? extends V, ?> headers) {
        if (headers != this) {
            for (K key : headers.names()) {
                remove(key);
            }
            addImpl(headers);
        }
        return thisT();
    }

    @Override
    public boolean remove(K name) {
        return getAndRemove(name) != null;
    }

    @Override
    public T clear() {
        Arrays.fill(entries, null);
        head.before = head.after = head;
        size = 0;
        return thisT();
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        return new HeaderIterator();
    }

    @Override
    public Boolean getBoolean(K name) {
        V v = get(name);
        try {
            return v != null ? valueConverter.convertToBoolean(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public boolean getBoolean(K name, boolean defaultValue) {
        Boolean v = getBoolean(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Byte getByte(K name) {
        V v = get(name);
        try {
            return v != null ? valueConverter.convertToByte(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public byte getByte(K name, byte defaultValue) {
        Byte v = getByte(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Character getChar(K name) {
        V v = get(name);
        try {
            return v != null ? valueConverter.convertToChar(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public char getChar(K name, char defaultValue) {
        Character v = getChar(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Short getShort(K name) {
        V v = get(name);
        try {
            return v != null ? valueConverter.convertToShort(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public short getShort(K name, short defaultValue) {
        Short v = getShort(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Integer getInt(K name) {
        V v = get(name);
        try {
            return v != null ? valueConverter.convertToInt(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public int getInt(K name, int defaultValue) {
        Integer v = getInt(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Long getLong(K name) {
        V v = get(name);
        try {
            return v != null ? valueConverter.convertToLong(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public long getLong(K name, long defaultValue) {
        Long v = getLong(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Float getFloat(K name) {
        V v = get(name);
        try {
            return v != null ? valueConverter.convertToFloat(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public float getFloat(K name, float defaultValue) {
        Float v = getFloat(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Double getDouble(K name) {
        V v = get(name);
        try {
            return v != null ? valueConverter.convertToDouble(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public double getDouble(K name, double defaultValue) {
        Double v = getDouble(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Long getTimeMillis(K name) {
        V v = get(name);
        try {
            return v != null ? valueConverter.convertToTimeMillis(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public long getTimeMillis(K name, long defaultValue) {
        Long v = getTimeMillis(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Boolean getBooleanAndRemove(K name) {
        V v = getAndRemove(name);
        try {
            return v != null ? valueConverter.convertToBoolean(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public boolean getBooleanAndRemove(K name, boolean defaultValue) {
        Boolean v = getBooleanAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Byte getByteAndRemove(K name) {
        V v = getAndRemove(name);
        try {
            return v != null ? valueConverter.convertToByte(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public byte getByteAndRemove(K name, byte defaultValue) {
        Byte v = getByteAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Character getCharAndRemove(K name) {
        V v = getAndRemove(name);
        try {
            return v != null ? valueConverter.convertToChar(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public char getCharAndRemove(K name, char defaultValue) {
        Character v = getCharAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Short getShortAndRemove(K name) {
        V v = getAndRemove(name);
        try {
            return v != null ? valueConverter.convertToShort(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public short getShortAndRemove(K name, short defaultValue) {
        Short v = getShortAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Integer getIntAndRemove(K name) {
        V v = getAndRemove(name);
        try {
            return v != null ? valueConverter.convertToInt(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public int getIntAndRemove(K name, int defaultValue) {
        Integer v = getIntAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Long getLongAndRemove(K name) {
        V v = getAndRemove(name);
        try {
            return v != null ? valueConverter.convertToLong(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public long getLongAndRemove(K name, long defaultValue) {
        Long v = getLongAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Float getFloatAndRemove(K name) {
        V v = getAndRemove(name);
        try {
            return v != null ? valueConverter.convertToFloat(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public float getFloatAndRemove(K name, float defaultValue) {
        Float v = getFloatAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Double getDoubleAndRemove(K name) {
        V v = getAndRemove(name);
        try {
            return v != null ? valueConverter.convertToDouble(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public double getDoubleAndRemove(K name, double defaultValue) {
        Double v = getDoubleAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public Long getTimeMillisAndRemove(K name) {
        V v = getAndRemove(name);
        try {
            return v != null ? valueConverter.convertToTimeMillis(v) : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Override
    public long getTimeMillisAndRemove(K name, long defaultValue) {
        Long v = getTimeMillisAndRemove(name);
        return v != null ? v : defaultValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Headers)) {
            return false;
        }

        return equals((Headers<K, V, ?>) o, JAVA_HASHER);
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
    public final boolean equals(Headers<K, V, ?> h2, HashingStrategy<V> valueHashingStrategy) {
        if (h2.size() != size()) {
            return false;
        }

        if (this == h2) {
            return true;
        }

        for (K name : names()) {
            List<V> otherValues = h2.getAll(name);
            List<V> values = getAll(name);
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
    public final int hashCode(HashingStrategy<V> valueHashingStrategy) {
        int result = HASH_CODE_SEED;
        for (K name : names()) {
            result = 31 * result + hashingStrategy.hashCode(name);
            List<V> values = getAll(name);
            for (int i = 0; i < values.size(); ++i) {
                result = 31 * result + valueHashingStrategy.hashCode(values.get(i));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return HeadersUtils.toString(getClass(), iterator(), size());
    }

    protected HeaderEntry<K, V> newHeaderEntry(int h, K name, V value, HeaderEntry<K, V> next) {
        return new HeaderEntry<K, V>(h, name, value, next, head);
    }

    protected ValueConverter<V> valueConverter() {
        return valueConverter;
    }

    private int index(int hash) {
        return hash & hashMask;
    }

    private void add0(int h, int i, K name, V value) {
        // Update the hash table.
        entries[i] = newHeaderEntry(h, name, value, entries[i]);
        ++size;
    }

    /**
     * @return the first value inserted whose hash code equals {@code h} and whose name is equal to {@code name}.
     */
    private V remove0(int h, int i, K name) {
        HeaderEntry<K, V> e = entries[i];
        if (e == null) {
            return null;
        }

        V value = null;
        HeaderEntry<K, V> next = e.next;
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

    private HeaderEntry<K, V> remove0(HeaderEntry<K, V> entry, HeaderEntry<K, V> previous) {
        int i = index(entry.hash);
        HeaderEntry<K, V> e = entries[i];
        if (e == entry) {
            entries[i] = entry.next;
            previous = entries[i];
        } else {
            previous.next = entry.next;
        }
        entry.remove();
        --size;
        return previous;
    }

    @SuppressWarnings("unchecked")
    private T thisT() {
        return (T) this;
    }

    /**
     * Returns a deep copy of this instance.
     */
    public DefaultHeaders<K, V, T> copy() {
        DefaultHeaders<K, V, T> copy = new DefaultHeaders<K, V, T>(
                hashingStrategy, valueConverter, nameValidator, entries.length);
        copy.addImpl(this);
        return copy;
    }

    private final class HeaderIterator implements Iterator<Map.Entry<K, V>> {
        private HeaderEntry<K, V> current = head;

        @Override
        public boolean hasNext() {
            return current.after != head;
        }

        @Override
        public Entry<K, V> next() {
            current = current.after;

            if (current == head) {
                throw new NoSuchElementException();
            }

            return current;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("read only");
        }
    }

    private final class ValueIterator implements Iterator<V> {
        private final K name;
        private final int hash;
        private HeaderEntry<K, V> removalPrevious;
        private HeaderEntry<K, V> previous;
        private HeaderEntry<K, V> next;

        ValueIterator(K name) {
            this.name = checkNotNull(name, "name");
            hash = hashingStrategy.hashCode(name);
            calculateNext(entries[index(hash)]);
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public V next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            if (previous != null) {
                removalPrevious = previous;
            }
            previous = next;
            calculateNext(next.next);
            return previous.value;
        }

        @Override
        public void remove() {
            if (previous == null) {
                throw new IllegalStateException();
            }
            removalPrevious = remove0(previous, removalPrevious);
            previous = null;
        }

        private void calculateNext(HeaderEntry<K, V> entry) {
            while (entry != null) {
                if (entry.hash == hash && hashingStrategy.equals(name, entry.key)) {
                    next = entry;
                    return;
                }
                entry = entry.next;
            }
            next = null;
        }
    }

    protected static class HeaderEntry<K, V> implements Map.Entry<K, V> {
        protected final int hash;
        protected final K key;
        protected V value;
        /**
         * In bucket linked list
         */
        protected HeaderEntry<K, V> next;
        /**
         * Overall insertion order linked list
         */
        protected HeaderEntry<K, V> before, after;

        protected HeaderEntry(int hash, K key) {
            this.hash = hash;
            this.key = key;
        }

        HeaderEntry(int hash, K key, V value, HeaderEntry<K, V> next, HeaderEntry<K, V> head) {
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
            before = after = this;
        }

        protected final void pointNeighborsToThis() {
            before.after = this;
            after.before = this;
        }

        public final HeaderEntry<K, V> before() {
            return before;
        }

        public final HeaderEntry<K, V> after() {
            return after;
        }

        protected void remove() {
            before.after = after;
            after.before = before;
        }

        @Override
        public final K getKey() {
            return key;
        }

        @Override
        public final V getValue() {
            return value;
        }

        @Override
        public final V setValue(V value) {
            checkNotNull(value, "value");
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public final String toString() {
            return key.toString() + '=' + value.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> other = (Map.Entry<?, ?>) o;
            return (getKey() == null ? other.getKey() == null : getKey().equals(other.getKey()))  &&
                   (getValue() == null ? other.getValue() == null : getValue().equals(other.getValue()));
        }

        @Override
        public int hashCode() {
            return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
        }
    }
}
