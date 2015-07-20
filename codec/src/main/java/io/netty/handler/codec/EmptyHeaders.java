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

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class EmptyHeaders<T> implements Headers<T> {
    @Override
    public T get(T name) {
        return null;
    }

    @Override
    public T get(T name, T defaultValue) {
        return null;
    }

    @Override
    public T getAndRemove(T name) {
        return null;
    }

    @Override
    public T getAndRemove(T name, T defaultValue) {
        return null;
    }

    @Override
    public List<T> getAll(T name) {
        return Collections.emptyList();
    }

    @Override
    public List<T> getAllAndRemove(T name) {
        return Collections.emptyList();
    }

    @Override
    public Boolean getBoolean(T name) {
        return null;
    }

    @Override
    public boolean getBoolean(T name, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public Byte getByte(T name) {
        return null;
    }

    @Override
    public byte getByte(T name, byte defaultValue) {
        return defaultValue;
    }

    @Override
    public Character getChar(T name) {
        return null;
    }

    @Override
    public char getChar(T name, char defaultValue) {
        return defaultValue;
    }

    @Override
    public Short getShort(T name) {
        return null;
    }

    @Override
    public short getShort(T name, short defaultValue) {
        return defaultValue;
    }

    @Override
    public Integer getInt(T name) {
        return null;
    }

    @Override
    public int getInt(T name, int defaultValue) {
        return defaultValue;
    }

    @Override
    public Long getLong(T name) {
        return null;
    }

    @Override
    public long getLong(T name, long defaultValue) {
        return defaultValue;
    }

    @Override
    public Float getFloat(T name) {
        return null;
    }

    @Override
    public float getFloat(T name, float defaultValue) {
        return defaultValue;
    }

    @Override
    public Double getDouble(T name) {
        return null;
    }

    @Override
    public double getDouble(T name, double defaultValue) {
        return defaultValue;
    }

    @Override
    public Long getTimeMillis(T name) {
        return null;
    }

    @Override
    public long getTimeMillis(T name, long defaultValue) {
        return defaultValue;
    }

    @Override
    public Boolean getBooleanAndRemove(T name) {
        return null;
    }

    @Override
    public boolean getBooleanAndRemove(T name, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public Byte getByteAndRemove(T name) {
        return null;
    }

    @Override
    public byte getByteAndRemove(T name, byte defaultValue) {
        return defaultValue;
    }

    @Override
    public Character getCharAndRemove(T name) {
        return null;
    }

    @Override
    public char getCharAndRemove(T name, char defaultValue) {
        return defaultValue;
    }

    @Override
    public Short getShortAndRemove(T name) {
        return null;
    }

    @Override
    public short getShortAndRemove(T name, short defaultValue) {
        return defaultValue;
    }

    @Override
    public Integer getIntAndRemove(T name) {
        return null;
    }

    @Override
    public int getIntAndRemove(T name, int defaultValue) {
        return defaultValue;
    }

    @Override
    public Long getLongAndRemove(T name) {
        return null;
    }

    @Override
    public long getLongAndRemove(T name, long defaultValue) {
        return defaultValue;
    }

    @Override
    public Float getFloatAndRemove(T name) {
        return null;
    }

    @Override
    public float getFloatAndRemove(T name, float defaultValue) {
        return defaultValue;
    }

    @Override
    public Double getDoubleAndRemove(T name) {
        return null;
    }

    @Override
    public double getDoubleAndRemove(T name, double defaultValue) {
        return defaultValue;
    }

    @Override
    public Long getTimeMillisAndRemove(T name) {
        return null;
    }

    @Override
    public long getTimeMillisAndRemove(T name, long defaultValue) {
        return defaultValue;
    }

    @Override
    public boolean contains(T name) {
        return false;
    }

    @Override
    public boolean contains(T name, T value) {
        return false;
    }

    @Override
    public boolean containsObject(T name, Object value) {
        return false;
    }

    @Override
    public boolean containsBoolean(T name, boolean value) {
        return false;
    }

    @Override
    public boolean containsByte(T name, byte value) {
        return false;
    }

    @Override
    public boolean containsChar(T name, char value) {
        return false;
    }

    @Override
    public boolean containsShort(T name, short value) {
        return false;
    }

    @Override
    public boolean containsInt(T name, int value) {
        return false;
    }

    @Override
    public boolean containsLong(T name, long value) {
        return false;
    }

    @Override
    public boolean containsFloat(T name, float value) {
        return false;
    }

    @Override
    public boolean containsDouble(T name, double value) {
        return false;
    }

    @Override
    public boolean containsTimeMillis(T name, long value) {
        return false;
    }

    @Override
    public boolean contains(T name, T value, Comparator<? super T> valueComparator) {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Set<T> names() {
        return Collections.emptySet();
    }

    @Override
    public Headers<T> add(T name, T value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> add(T name, Iterable<? extends T> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> add(T name, T... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addObject(T name, Object value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addObject(T name, Iterable<?> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addObject(T name, Object... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addBoolean(T name, boolean value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addByte(T name, byte value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addChar(T name, char value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addShort(T name, short value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addInt(T name, int value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addLong(T name, long value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addFloat(T name, float value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addDouble(T name, double value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> addTimeMillis(T name, long value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> add(Headers<? extends T> headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> set(T name, T value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> set(T name, Iterable<? extends T> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> set(T name, T... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setObject(T name, Object value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setObject(T name, Iterable<?> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setObject(T name, Object... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setBoolean(T name, boolean value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setByte(T name, byte value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setChar(T name, char value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setShort(T name, short value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setInt(T name, int value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setLong(T name, long value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setFloat(T name, float value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setDouble(T name, double value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setTimeMillis(T name, long value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> set(Headers<? extends T> headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public Headers<T> setAll(Headers<? extends T> headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public boolean remove(T name) {
        return false;
    }

    @Override
    public Headers<T> clear() {
        return this;
    }

    @Override
    public Iterator<Entry<T, T>> iterator() {
        List<Entry<T, T>> empty = Collections.emptyList();
        return empty.iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Headers)) {
            return false;
        }

        Headers<?> rhs = (Headers<?>) o;
        return isEmpty() && rhs.isEmpty();
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName()).append('[').append(']').toString();
    }
}
