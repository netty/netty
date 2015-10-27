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

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Common interface for {@link Headers} which represents a mapping of key to value.
 * Duplicate keys may be allowed by implementations.
 *
 * @param <K> the type of the header name.
 * @param <V> the type of the header value.
 * @param <T> the type to use for return values when the intention is to return {@code this} object.
 */
public interface Headers<K, V, T extends Headers<K, V, T>> extends Iterable<Entry<K, V>> {
    /**
     * Returns the value of a header with the specified name. If there is more than one value for the specified name,
     * the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the first header value if the header is found. {@code null} if there's no such header
     */
    V get(K name);

    /**
     * Returns the value of a header with the specified name. If there is more than one value for the specified name,
     * the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the first header value or {@code defaultValue} if there is no such header
     */
    V get(K name, V defaultValue);

    /**
     * Returns the value of a header with the specified name and removes it from this object. If there is more than
     * one value for the specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the first header value or {@code null} if there is no such header
     */
    V getAndRemove(K name);

    /**
     * Returns the value of a header with the specified name and removes it from this object. If there is more than
     * one value for the specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the first header value or {@code defaultValue} if there is no such header
     */
    V getAndRemove(K name, V defaultValue);

    /**
     * Returns all values for the header with the specified name. The returned {@link List} can't be modified.
     *
     * @param name the name of the header to retrieve
     * @return a {@link List} of header values or an empty {@link List} if no values are found.
     */
    List<V> getAll(K name);

    /**
     * Returns all values for the header with the specified name and removes them from this object.
     * The returned {@link List} can't be modified.
     *
     * @param name the name of the header to retrieve
     * @return a {@link List} of header values or an empty {@link List} if no values are found.
     */
    List<V> getAllAndRemove(K name);

    /**
     * Returns the {@code boolean} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code boolean} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code boolean}.
     */
    Boolean getBoolean(K name);

    /**
     * Returns the {@code boolean} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code boolean} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code boolean}.
     */
    boolean getBoolean(K name, boolean defaultValue);

    /**
     * Returns the {@code byte} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code byte} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code byte}.
     */
    Byte getByte(K name);

    /**
     * Returns the {@code byte} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code byte} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code byte}.
     */
    byte getByte(K name, byte defaultValue);

    /**
     * Returns the {@code char} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code char} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code char}.
     */
    Character getChar(K name);

    /**
     * Returns the {@code char} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code char} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code char}.
     */
    char getChar(K name, char defaultValue);

    /**
     * Returns the {@code short} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code short} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code short}.
     */
    Short getShort(K name);

    /**
     * Returns the {@code short} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code short} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code short}.
     */
    short getShort(K name, short defaultValue);

    /**
     * Returns the {@code int} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code int} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code int}.
     */
    Integer getInt(K name);

    /**
     * Returns the {@code int} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code int} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code int}.
     */
    int getInt(K name, int defaultValue);

    /**
     * Returns the {@code long} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code long} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code long}.
     */
    Long getLong(K name);

    /**
     * Returns the {@code long} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code long} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code long}.
     */
    long getLong(K name, long defaultValue);

    /**
     * Returns the {@code float} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code float} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code float}.
     */
    Float getFloat(K name);

    /**
     * Returns the {@code float} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code float} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code float}.
     */
    float getFloat(K name, float defaultValue);

    /**
     * Returns the {@code double} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the {@code double} value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to {@code double}.
     */
    Double getDouble(K name);

    /**
     * Returns the {@code double} value of a header with the specified name. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the {@code double} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code double}.
     */
    double getDouble(K name, double defaultValue);

    /**
     * Returns the value of a header with the specified name in milliseconds. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @return the milliseconds value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to milliseconds.
     */
    Long getTimeMillis(K name);

    /**
     * Returns the value of a header with the specified name in milliseconds. If there is more than one value for the
     * specified name, the first value in insertion order is returned.
     *
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the milliseconds value of the first value in insertion order or {@code defaultValue} if there is no such
     *         value or it can't be converted to milliseconds.
     */
    long getTimeMillis(K name, long defaultValue);

    /**
     * Returns the {@code boolean} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to retrieve
     * @return the {@code boolean} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code boolean}.
     */
    Boolean getBooleanAndRemove(K name);

    /**
     * Returns the {@code boolean} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code boolean} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code boolean}.
     */
    boolean getBooleanAndRemove(K name, boolean defaultValue);

    /**
     * Returns the {@code byte} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @return the {@code byte} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code byte}.
     */
    Byte getByteAndRemove(K name);

    /**
     * Returns the {@code byte} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code byte} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code byte}.
     */
    byte getByteAndRemove(K name, byte defaultValue);

    /**
     * Returns the {@code char} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @return the {@code char} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code char}.
     */
    Character getCharAndRemove(K name);

    /**
     * Returns the {@code char} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code char} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code char}.
     */
    char getCharAndRemove(K name, char defaultValue);

    /**
     * Returns the {@code short} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @return the {@code short} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code short}.
     */
    Short getShortAndRemove(K name);

    /**
     * Returns the {@code short} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code short} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code short}.
     */
    short getShortAndRemove(K name, short defaultValue);

    /**
     * Returns the {@code int} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @return the {@code int} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code int}.
     */
    Integer getIntAndRemove(K name);

    /**
     * Returns the {@code int} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code int} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code int}.
     */
    int getIntAndRemove(K name, int defaultValue);

    /**
     * Returns the {@code long} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @return the {@code long} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code long}.
     */
    Long getLongAndRemove(K name);

    /**
     * Returns the {@code long} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code long} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code long}.
     */
    long getLongAndRemove(K name, long defaultValue);

    /**
     * Returns the {@code float} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @return the {@code float} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code float}.
     */
    Float getFloatAndRemove(K name);

    /**
     * Returns the {@code float} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code float} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code float}.
     */
    float getFloatAndRemove(K name, float defaultValue);

    /**
     * Returns the {@code double} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @return the {@code double} value of the first value in insertion order or {@code null} if there is no
     *         such value or it can't be converted to {@code double}.
     */
    Double getDoubleAndRemove(K name);

    /**
     * Returns the {@code double} value of a header with the specified {@code name} and removes the header from this
     * object. If there is more than one value for the specified name, the first value in insertion order is returned.
     * In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the {@code double} value of the first value in insertion order or {@code defaultValue} if there is no
     *         such value or it can't be converted to {@code double}.
     */
    double getDoubleAndRemove(K name, double defaultValue);

    /**
     * Returns the value of a header with the specified {@code name} in milliseconds and removes the header from this
     * object. If there is more than one value for the specified {@code name}, the first value in insertion order is
     * returned. In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to retrieve
     * @return the milliseconds value of the first value in insertion order or {@code null} if there is no such
     *         value or it can't be converted to milliseconds.
     */
    Long getTimeMillisAndRemove(K name);

    /**
     * Returns the value of a header with the specified {@code name} in milliseconds and removes the header from this
     * object. If there is more than one value for the specified {@code name}, the first value in insertion order is
     * returned. In any case all values for {@code name} are removed.
     * <p>
     * If an exception occurs during the translation from type {@code T} all entries with {@code name} may still
     * be removed.
     * @param name the name of the header to retrieve
     * @param defaultValue the default value
     * @return the milliseconds value of the first value in insertion order or {@code defaultValue} if there is no such
     *         value or it can't be converted to milliseconds.
     */
    long getTimeMillisAndRemove(K name, long defaultValue);

    /**
     * Returns {@code true} if a header with the {@code name} exists, {@code false} otherwise.
     *
     * @param name the header name
     */
    boolean contains(K name);

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists, {@code false} otherwise.
     * <p>
     * The {@link Object#equals(Object)} method is used to test for equality of {@code value}.
     * </p>
     * @param name the header name
     * @param value the header value of the header to find
     */
    boolean contains(K name, V value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsObject(K name, Object value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsBoolean(K name, boolean value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsByte(K name, byte value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsChar(K name, char value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsShort(K name, short value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsInt(K name, int value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsLong(K name, long value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsFloat(K name, float value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsDouble(K name, double value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name the header name
     * @param value the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean containsTimeMillis(K name, long value);

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
    Set<K> names();

    /**
     * Adds a new header with the specified {@code name} and {@code value}.
     *
     * @param name the name of the header
     * @param value the value of the header
     * @return {@code this}
     */
    T add(K name, V value);

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
    T add(K name, Iterable<? extends V> values);

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
    T add(K name, V... values);

    /**
     * Adds a new header. Before the {@code value} is added, it's converted to type {@code T}.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T addObject(K name, Object value);

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
    T addObject(K name, Iterable<?> values);

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
    T addObject(K name, Object... values);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T addBoolean(K name, boolean value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T addByte(K name, byte value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T addChar(K name, char value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T addShort(K name, short value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T addInt(K name, int value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T addLong(K name, long value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T addFloat(K name, float value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T addDouble(K name, double value);

    /**
     * Adds a new header.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T addTimeMillis(K name, long value);

    /**
     * Adds all header names and values of {@code headers} to this object.
     *
     * @throws IllegalArgumentException if {@code headers == this}.
     * @return {@code this}
     */
    T add(Headers<? extends K, ? extends V, ?> headers);

    /**
     * Sets a header with the specified name and value. Any existing headers with the same name are overwritten.
     *
     * @param name the header name
     * @param value the value of the header
     * @return {@code this}
     */
    T set(K name, V value);

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
    T set(K name, Iterable<? extends V> values);

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
    T set(K name, V... values);

    /**
     * Sets a new header. Any existing headers with this name are removed. Before the {@code value} is add, it's
     * converted to type {@code T}.
     *
     * @param name the header name
     * @param value the value of the header
     * @throws NullPointerException if either {@code name} or {@code value} before or after its conversion is
     *                              {@code null}.
     * @return {@code this}
     */
    T setObject(K name, Object value);

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
    T setObject(K name, Iterable<?> values);

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
    T setObject(K name, Object... values);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    T setBoolean(K name, boolean value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    T setByte(K name, byte value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    T setChar(K name, char value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    T setShort(K name, short value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    T setInt(K name, int value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    T setLong(K name, long value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    T setFloat(K name, float value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    T setDouble(K name, double value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    T setTimeMillis(K name, long value);

    /**
     * Clears the current header entries and copies all header entries of the specified {@code headers}.
     *
     * @return {@code this}
     */
    T set(Headers<? extends K, ? extends V, ?> headers);

    /**
     * Retains all current headers but calls {@link #set(T, T)} for each entry in {@code headers}.
     *
     * @param headers The headers used to {@link #set(T, T)} values in this instance
     * @return {@code this}
     */
    T setAll(Headers<? extends K, ? extends V, ?> headers);

    /**
     * Removes all headers with the specified {@code name}.
     *
     * @param name the header name
     * @return {@code true} if at least one entry has been removed.
     */
    boolean remove(K name);

    /**
     * Removes all headers. After a call to this method {@link #size()} equals {@code 0}.
     *
     * @return {@code this}
     */
    T clear();

    @Override
    Iterator<Entry<K, V>> iterator();
}
