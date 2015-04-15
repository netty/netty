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

import io.netty.util.concurrent.FastThreadLocal;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.Date;
import java.util.TreeMap;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;


/**
 * Default implementation of {@link Headers};
 *
 * @param <T> the type of the header name and value.
 */
public class DefaultHeaders<T> implements Headers<T> {

    private final Map<T, Object> map;
    private final NameValidator<T> nameValidator;
    private final ValueConverter<T> valueConverter;
    int size;

    boolean hasMultipleValues;

    public DefaultHeaders(Map<T, Object> map, NameValidator<T> nameValidator, ValueConverter<T> valueConverter) {
        this.map = checkNotNull(map, "map");
        this.nameValidator = checkNotNull(nameValidator, "nameValidator");
        this.valueConverter = checkNotNull(valueConverter, "valueConverter");
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(T name) {
        Object value = map.get(name);
        if (isList(value)) {
            return ((List<T>) value).get(0);
        }
        return (T) value;
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
    @SuppressWarnings("unchecked")
    public T getAndRemove(T name) {
        Object value = map.remove(name);
        if (value == null) {
            return null;
        }
        if (isList(value)) {
            List<T> value0 = (List<T>) value;
            size -= value0.size();
            return value0.get(0);
        }
        size--;
        return (T) value;
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
    @SuppressWarnings("unchecked")
    public List<T> getAll(T name) {
        Object value = map.get(name);
        if (isList(value)) {
            return unmodifiableList((List<T>) value);
        }
        if (value == null) {
            return emptyList();
        }
        return singletonList((T) value);
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
    @SuppressWarnings("unchecked")
    public boolean contains(T name, T value) {
        Object values = map.get(name);
        if (isList(values)) {
            return ((List<T>) values).contains(value);
        }
        return values != null && values.equals(value);
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
    @SuppressWarnings("unchecked")
    public boolean contains(T name, T value, Comparator<? super T> valueComparator) {
        Object values = map.get(name);
        if (isList(values)) {
            List<T> values0 = (List<T>) values;
            for (int i = 0; i < values0.size(); i++) {
                if (valueComparator.compare(value, values0.get(i)) == 0) {
                    return true;
                }
            }
            return false;
        }
        return values != null && valueComparator.compare((T) values, value) == 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public Set<T> names() {
        return unmodifiableSet(map.keySet());
    }

    @Override
    public Headers<T> add(T name, T value) {
        validateName(name);
        checkNotNull(value, "value");
        Object prevValue = map.put(name, value);
        size++;
        if (prevValue != null) {
            appendValue(name, value, prevValue);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    private void appendValue(T name, T value, Object prevValue) {
        hasMultipleValues = true;
        if (isList(prevValue)) {
            ((List<T>) prevValue).add(value);
            map.put(name, prevValue);
        } else {
            List<T> values = newList();
            values.add((T) prevValue);
            values.add(value);
            map.put(name, values);
        }
    }

    @Override
    public Headers<T> add(T name, Iterable<? extends T> values) {
        checkNotNull(values, "values");
        for (T value : values) {
            add(name, value);
        }
        return this;
    }

    @Override
    public Headers<T> add(T name, T... values) {
        checkNotNull(values, "values");
        for (int i = 0; i < values.length; i++) {
            add(name, values[i]);
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
        for (Entry<? extends T, ? extends T> header : headers) {
            add(header.getKey(), header.getValue());
        }
        return this;
    }

    @Override
    public Headers<T> set(T name, T value) {
        validateName(name);
        checkNotNull(value, "value");
        Object oldValue = map.put(name, value);
        updateSizeAfterSet(oldValue, 1);
        return this;
    }

    @Override
    public Headers<T> set(T name, Iterable<? extends T> values) {
        validateName(name);
        checkNotNull(values, "values");
        List<T> list = newList();
        for (T value : values) {
            list.add(checkNotNull(value, "value"));
        }
        Object oldValue = map.put(name, list);
        updateSizeAfterSet(oldValue, list.size());
        hasMultipleValues = true;
        return this;
    }

    @Override
    public Headers<T> set(T name, T... values) {
        validateName(name);
        checkNotNull(values, "values");
        List<T> list = newList(values.length);
        for (int i = 0; i < values.length; i++) {
            list.add(checkNotNull(values[i], "value"));
        }
        Object oldValue = map.put(name, list);
        updateSizeAfterSet(oldValue, values.length);
        hasMultipleValues = true;
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
        validateName(name);
        checkNotNull(values, "values");
        List<T> list = newList();
        for (Object value : values) {
            value = checkNotNull(value, "value");
            T convertedValue = checkNotNull(valueConverter.convertObject(value), "convertedValue");
            list.add(convertedValue);
        }
        Object oldValue = map.put(name, list);
        updateSizeAfterSet(oldValue, list.size());
        hasMultipleValues = true;
        return this;
    }

    @Override
    public Headers<T> setObject(T name, Object... values) {
        validateName(name);
        checkNotNull(values, "values");
        List<T> list = newList(values.length);
        for (int i = 0; i < values.length; i++) {
            Object value = checkNotNull(values[i], "value");
            T convertedValue = checkNotNull(valueConverter.convertObject(value), "convertedValue");
            list.add(convertedValue);
        }
        Object oldValue = map.put(name, list);
        updateSizeAfterSet(oldValue, list.size());
        hasMultipleValues = true;
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
        add(headers);
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
        map.clear();
        hasMultipleValues = false;
        size = 0;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Entry<T, T>> iterator() {
        Object iter = map.entrySet().iterator();
        return !hasMultipleValues ? (Iterator<Entry<T, T>>) iter
                                  : new NameValueIterator((Iterator<Entry<T, Object>>) iter);
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
    public short getShort(T name, short defaultValue) {
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
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHeaders)) {
            return false;
        }
        DefaultHeaders<T> other = (DefaultHeaders<T>) o;
        return size() == other.size() && map.equals(other.map);
    }

    /**
     * This method is purposefully kept simple and returns {@link #size()} as the hash code.
     * There are two compelling reasons for keeping {@link #hashCode()} simple:
     *   1) It's difficult to get it right as the hash code mostly depends on the {@link Map}
     *      implementation. Simply using {@link Map#hashCode()} doesn't work as for
     *      example {@link TreeMap#hashCode()} does not fulfill the contract between {@link Object#hashCode()} and
     *      {@link Object#equals(Object)} when it's used with a {@link Comparator} that is not consistent with
     *      {@link Object#equals(Object)}.
     *   2) The {@link #hashCode()} function doesn't appear to be important for {@link Headers} implementations. It's
     *      solely there because we override {@link Object#equals(Object)}, which makes it easier to use {@link Headers}
     *      in tests, but also has little practical use outside of tests.
     */
    @Override
    public int hashCode() {
        return size();
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

    protected ValueConverter<T> valueConverter() {
        return valueConverter;
    }

    @SuppressWarnings("unchecked")
    private void updateSizeAfterSet(Object oldValue, int newSize) {
        if (isList(oldValue)) {
            size -= ((List<T>) oldValue).size();
        } else if (oldValue != null) {
            size--;
        }
        size += newSize;
    }

    private void validateName(T name) {
        checkNotNull(name, "name");
        nameValidator.validate(name);
    }

    private static boolean isList(Object value) {
        return value != null && value.getClass().equals(ValuesList.class);
    }

    private List<T> newList() {
        return newList(4);
    }

    private List<T> newList(int initialSize) {
        return new ValuesList<T>(initialSize);
    }

    private final class NameValueIterator implements Iterator<Entry<T, T>> {
        private final Iterator<Entry<T, Object>> iter;
        private T name;
        private List<T> values;
        private int valuesIdx;

        NameValueIterator(Iterator<Entry<T, Object>> iter) {
            this.iter = iter;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext() || values != null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Entry<T, T> next() {
            if (name == null) {
                Entry<T, Object> entry = iter.next();
                if (!isList(entry.getValue())) {
                    return (Entry<T, T>) entry;
                }
                initListIterator(entry);
            }
            return fetchNextEntryFromList();
        }

        @SuppressWarnings("unchecked")
        private void initListIterator(Entry<T, Object> entry) {
            name = entry.getKey();
            values = (List<T>) entry.getValue();
            valuesIdx = 0;
        }

        private Entry<T, T> fetchNextEntryFromList() {
            Entry<T, T> next = new SimpleListEntry<T>(name, values, valuesIdx++);
            if (values.size() == valuesIdx) {
                name = null;
                values = null;
            }
            return next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class SimpleListEntry<T> extends SimpleEntry<T, T> {
        private static final long serialVersionUID = 542062057061955051L;
        private final List<T> values;
        private final int idx;

        SimpleListEntry(T name, List<T> values, int idx) {
            super(name, values.get(idx));
            this.values = values;
            this.idx = idx;
        }

        @Override
        public T setValue(T value) {
            T oldValue = super.setValue(value);
            values.set(idx, value);
            return oldValue;
        }
    }

    private static final class ValuesList<T> extends ArrayList<T> {
        private static final long serialVersionUID = -2434101246756843900L;

        ValuesList(int initialSize) {
            super(initialSize);
        }
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
    static final class HeaderDateFormat {
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

    public interface NameValidator<T> {
        void validate(T name);
    }

    public static final class NoNameValidator<T> implements NameValidator<T> {

        @SuppressWarnings("rawtypes")
        private static final NameValidator INSTANCE = new NoNameValidator();

        private NoNameValidator() {
        }

        @SuppressWarnings("unchecked")
        public static <T> NameValidator<T> instance() {
            return (NameValidator<T>) INSTANCE;
        }

        @Override
        public void validate(T name) {
        }
    }

    public static <V> boolean comparatorEquals(Headers<V> a, Headers<V> b, Comparator<V> valueComparator) {
        if (a.size() != b.size()) {
            return false;
        }
        for (V name : a.names()) {
            List<V> otherValues = b.getAll(name);
            List<V> values = a.getAll(name);
            if (otherValues.size() != values.size()) {
                return false;
            }
            for (int i = 0; i < otherValues.size(); i++) {
                if (valueComparator.compare(otherValues.get(i), values.get(i)) != 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
