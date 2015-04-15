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

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public interface Headers<T> extends Iterable<Entry<T, T>> {

    /**
     * Converts to/from a generic object to the type of the headers.
     */
    interface ValueConverter<T> {
        T convertObject(Object value);

        T convertBoolean(boolean value);

        boolean convertToBoolean(T value);

        T convertByte(byte value);

        byte convertToByte(T value);

        T convertChar(char value);

        char convertToChar(T value);

        T convertShort(short value);

        short convertToShort(T value);

        T convertInt(int value);

        int convertToInt(T value);

        T convertLong(long value);

        long convertToLong(T value);

        T convertTimeMillis(long value);

        long convertToTimeMillis(T value);

        T convertFloat(float value);

        float convertToFloat(T value);

        T convertDouble(double value);

        double convertToDouble(T value);
    }

    /**
     * Returns the value of a header with the specified name. If there is more than one value for the specified name,
     * the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the first header value if the header is found. {@code null} if there's no such header
     */
    T get(T name);

    /**
     * Returns the value of a header with the specified name. If there is more than one value for the specified name,
     * the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the first header value or {@code defaultValue} if there is no such header
     */
    T get(T name, T defaultValue);

    /**
     * Returns the value of a header with the specified name and removes it from this object. If there is more than
     * one value for the specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the first header value or {@code null} if there is no such header
     */
    T getAndRemove(T name);

    /**
     * Returns the value of a header with the specified name and removes it from this object. If there is more than
     * one value for the specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the first header value or {@code defaultValue} if there is no such header
     */
    T getAndRemove(T name, T defaultValue);

    /**
     * Returns all values for the header with the specified name. The returned {@link List} can't be modified.
     *
     * @param name the name of the header to retrieve
     * @return a {@link List} of header values or an empty {@link List} if no values are found.
     */
    List<T> getAll(T name);

    /**
     * Returns all values for the header with the specified name and removes them from this object.
     * The returned {@link List} can't be modified.
     *
     * @param name the name of the header to retrieve
     * @return a {@link List} of header values or an empty {@link List} if no values are found.
     */
    List<T> getAllAndRemove(T name);

    /**
     * Returns the {@code boolean} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code boolean} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code boolean}.
     */
    Boolean getBoolean(T name);

    /**
     * Returns the {@code boolean} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code boolean} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code boolean}.
     */
    boolean getBoolean(T name, boolean defaultValue);

    /**
     * Returns the {@code byte} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code byte} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code byte}.
     */
    Byte getByte(T name);

    /**
     * Returns the {@code byte} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code byte} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code byte}.
     */
    byte getByte(T name, byte defaultValue);

    /**
     * Returns the {@code char} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code char} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code char}.
     */
    Character getChar(T name);

    /**
     * Returns the {@code char} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code char} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code char}.
     */
    char getChar(T name, char defaultValue);

    /**
     * Returns the {@code short} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code short} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code short}.
     */
    Short getShort(T name);

    /**
     * Returns the {@code short} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code short} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code short}.
     */
    short getShort(T name, short defaultValue);

    /**
     * Returns the {@code int} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code int} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code int}.
     */
    Integer getInt(T name);

    /**
     * Returns the {@code int} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code int} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code int}.
     */
    int getInt(T name, int defaultValue);

    /**
     * Returns the {@code long} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code long} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code long}.
     */
    Long getLong(T name);

    /**
     * Returns the {@code long} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code long} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code long}.
     */
    long getLong(T name, long defaultValue);

    /**
     * Returns the {@code float} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code float} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code float}.
     */
    Float getFloat(T name);

    /**
     * Returns the {@code float} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code float} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code float}.
     */
    float getFloat(T name, float defaultValue);

    /**
     * Returns the {@code double} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code double} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code double}.
     */
    Double getDouble(T name);

    /**
     * Returns the {@code double} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code double} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code double}.
     */
    double getDouble(T name, double defaultValue);

    /**
     * Returns the value of a header with the specified name in milliseconds. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the milliseconds value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to milliseconds.
     */
    Long getTimeMillis(T name);

    /**
     * Returns the value of a header with the specified name in milliseconds. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the milliseconds value of the first value in insertion order or {@code defaultValue} if there is no such
     *         value or it can't be converted to milliseconds.
     */
    long getTimeMillis(T name, long defaultValue);

    /**
     * Returns the {@code boolean} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to retrieve
     * @return the {@code boolean} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code boolean}.
     */
    Boolean getBooleanAndRemove(T name);

    /**
     * Returns the {@code boolean} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code boolean} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code boolean}.
     */
    boolean getBooleanAndRemove(T name, boolean defaultValue);

    /**
     * Returns the {@code byte} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @return the {@code byte} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code byte}.
     */
    Byte getByteAndRemove(T name);

    /**
     * Returns the {@code byte} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code byte} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code byte}.
     */
    byte getByteAndRemove(T name, byte defaultValue);

    /**
     * Returns the {@code char} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @return the {@code char} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code char}.
     */
    Character getCharAndRemove(T name);

    /**
     * Returns the {@code char} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code char} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code char}.
     */
    char getCharAndRemove(T name, char defaultValue);

    /**
     * Returns the {@code short} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @return the {@code short} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code short}.
     */
    Short getShortAndRemove(T name);

    /**
     * Returns the {@code short} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code short} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code short}.
     */
    short getShortAndRemove(T name, short defaultValue);

    /**
     * Returns the {@code int} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @return the {@code int} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code int}.
     */
    Integer getIntAndRemove(T name);

    /**
     * Returns the {@code int} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code int} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code int}.
     */
    int getIntAndRemove(T name, int defaultValue);

    /**
     * Returns the {@code long} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @return the {@code long} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code long}.
     */
    Long getLongAndRemove(T name);

    /**
     * Returns the {@code long} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code long} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code long}.
     */
    long getLongAndRemove(T name, long defaultValue);

    /**
     * Returns the {@code float} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @return the {@code float} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code float}.
     */
    Float getFloatAndRemove(T name);

    /**
     * Returns the {@code float} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code float} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code float}.
     */
    float getFloatAndRemove(T name, float defaultValue);

    /**
     * Returns the {@code double} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @return the {@code double} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code double}.
     */
    Double getDoubleAndRemove(T name);

    /**
     * Returns the {@code double} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code double} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code double}.
     */
    double getDoubleAndRemove(T name, double defaultValue);

    /**
     * Returns the value of a header with the specified {@code name} in milliseconds and removes the header from this
     * object. If there is more than one value for the specified {@code name}, the first value in insertion order is
     * returned. In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to retrieve
     * @return the milliseconds value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to milliseconds.
     */
    Long getTimeMillisAndRemove(T name);

    /**
     * Returns the value of a header with the specified {@code name} in milliseconds and removes the header from this
     * object. If there is more than one value for the specified {@code name}, the first value in insertion order is
     * returned. In any case all values for {@code name} are removed.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the milliseconds value of the first value in insertion order or {@code defaultValue} if there is no such
     *         value or it can't be converted to milliseconds.
     */
    long getTimeMillisAndRemove(T name, long defaultValue);

    /**
     * Returns {@code true} if a header with the {@code name} exists, {@code false} otherwise.
     *
     * @param name the header name
     */
    boolean contains(T name);

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists, {@code false} otherwise.
     * <p>
     * The {@link Object#equals(Object)} method is used to test for equality of {@code value}.
     * </p>
     * @param name the header name
     */
    boolean contains(T name, T value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsObject(T name, Object value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsBoolean(T name, boolean value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsByte(T name, byte value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsChar(T name, char value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsShort(T name, short value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsInt(T name, int value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsLong(T name, long value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsFloat(T name, float value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsDouble(T name, double value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsTimeMillis(T name, long value);

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists.
     *
     * @param name the header name
     * @param value the header value
     * @param valueComparator The comparator to use when comparing {@code value} to entries in this map
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean contains(T name, T value, Comparator<? super T> valueComparator);

    /**
     * Returns the number of headers in this object.
     */
    int size();

    /**
     * Returns {@code true} if {@link #size()} equals {@code 0}.
     */
    boolean isEmpty();

    /**
     * Returns a {@link Set} of all header names in this object. The returned {@link Set} cannot be modified.
     */
    Set<T> names();

    /**
     * Adds a new header with the specified {@code name} and {@code value}.
     *
     * @param name the name of the header
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> add(T name, T value);

    /**
     * Adds new headers with the specified {@code name} and {@code values}. This method is semantically equivalent to
     *
     * <pre>
     * for (T value : values) {
     *     headers.add(name, value);
     * }
     * </pre>
     *
     * @param name the header name
     * @param values the values of the header
     * @return {@code this}
     */
    Headers<T> add(T name, Iterable<? extends T> values);

    /**
     * Adds new headers with the specified {@code name} and {@code values}. This method is semantically equivalent to
     *
     * <pre>
     * for (T value : values) {
     *     headers.add(name, value);
     * }
     * </pre>
     *
     * @param name the header name
     * @param values the values of the header
     * @return {@code this}
     */
    Headers<T> add(T name, T... values);

    /**
     * Adds a new header. Before the {@code value} is add, it's converted to type {@code T} by a call to
     * {@link ValueConverter#convertObject(java.lang.Object)}.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> addObject(T name, Object value);

    /**
     * Adds a new header with the specified name and values. This method is equivalent to
     *
     * <pre>
     * for (Object v : values) {
     *     headers.addObject(name, v);
     * }
     * </pre>
     *
     * @param name the header name
     * @param values the value of the header
     * @return {@code this}
     */
    Headers<T> addObject(T name, Iterable<?> values);

    /**
     * Adds a new header with the specified name and values. This method is equivalent to
     *
     * <pre>
     * for (Object v : values) {
     *     headers.addObject(name, v);
     * }
     * </pre>
     *
     * @param name the header name
     * @param values the value of the header
     * @return {@code this}
     */
    Headers<T> addObject(T name, Object... values);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> addBoolean(T name, boolean value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> addByte(T name, byte value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> addChar(T name, char value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> addShort(T name, short value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> addInt(T name, int value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> addLong(T name, long value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> addFloat(T name, float value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> addDouble(T name, double value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> addTimeMillis(T name, long value);

    /**
     * Adds all header names and values of {@code headers} to this object.
     *
     * @throws IllegalArgumentException if {@code headers == this}.
     * @return {@code this}
     */
    Headers<T> add(Headers<? extends T> headers);

    /**
     * Sets a header with the specified name and value. Any existing headers with the same name are overwritten.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    Headers<T> set(T name, T value);

    /**
     * Sets a new header with the specified name and values. This method is equivalent to
     *
     * <pre>
     * for (T v : values) {
     *     headers.addObject(name, v);
     * }
     * </pre>
     *
     * @param name the header name
     * @param values the value of the header
     * @return {@code this}
     */
    Headers<T> set(T name, Iterable<? extends T> values);

    /**
     * Sets a header with the specified name and values. Any existing headers with this name are removed. This method
     * is equivalent to:
     *
     * <pre>
     * headers.remove(name);
     * for (T v : values) {
     *     headers.add(name, v);
     * }
     * </pre>
     *
     * @param name the header name
     * @param values the value of the header
     * @return {@code this}
     */
    Headers<T> set(T name, T... values);

    /**
     * Sets a new header. Any existing headers with this name are removed. Before the {@code value} is add, it's
     * converted to type {@code T} by a call to {@link ValueConverter#convertObject(java.lang.Object)}.
     *
     * @param name the header name
     * @param value the value of the header
     * @throws NullPointerException if either {@code name} or {@code value} before or after its conversion is
     *                              {@code null}.
     * @return {@code this}
     */
    Headers<T> setObject(T name, Object value);

    /**
     * Sets a header with the specified name and values. Any existing headers with this name are removed. This method
     * is equivalent to:
     *
     * <pre>
     * headers.remove(name);
     * for (Object v : values) {
     *     headers.addObject(name, v);
     * }
     * </pre>
     *
     * @param name the header name
     * @param values the values of the header
     * @return {@code this}
     */
    Headers<T> setObject(T name, Iterable<?> values);

    /**
     * Sets a header with the specified name and values. Any existing headers with this name are removed. This method
     * is equivalent to:
     *
     * <pre>
     * headers.remove(name);
     * for (Object v : values) {
     *     headers.addObject(name, v);
     * }
     * </pre>
     *
     * @param name the header name
     * @param values the values of the header
     * @return {@code this}
     */
    Headers<T> setObject(T name, Object... values);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    Headers<T> setBoolean(T name, boolean value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    Headers<T> setByte(T name, byte value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    Headers<T> setChar(T name, char value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    Headers<T> setShort(T name, short value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    Headers<T> setInt(T name, int value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    Headers<T> setLong(T name, long value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    Headers<T> setFloat(T name, float value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    Headers<T> setDouble(T name, double value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    Headers<T> setTimeMillis(T name, long value);

    /**
     * Clears the current header entries and copies all header entries of the specified {@code headers}.
     *
     * @return {@code this}
     */
    Headers<T> set(Headers<? extends T> headers);

    /**
     * Retains all current headers but calls {@link #set(T, T)} for each entry in {@code headers}.
     *
     * @param headers The headers used to {@link #set(T, T)} values in this instance
     * @return {@code this}
     */
    Headers<T> setAll(Headers<? extends T> headers);

    /**
     * Removes all headers with the specified {@code name}.
     *
     * @param name the header name
     * @return {@code true} if at least one entry has been removed.
     */
    boolean remove(T name);

    /**
     * Removes all headers. After a call to this method {@link #size()} equals {@code 0}.
     *
     * @return {@code this}
     */
    Headers<T> clear();

    @Override
    Iterator<Entry<T, T>> iterator();
}
