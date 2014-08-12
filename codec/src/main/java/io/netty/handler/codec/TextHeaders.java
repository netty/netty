/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A typical string multimap used by text protocols such as HTTP for the representation of arbitrary key-value data.
 * One thing to note is that it uses {@link CharSequence} as its primary key and value type rather than {@link String}.
 * When you invoke the operations that produce {@link String}s such as {@link #get(CharSequence)},
 * a {@link CharSequence} is implicitly converted to a {@link String}.  This is particularly useful for speed
 * optimization because this multimap can hold a special {@link CharSequence} implementation that a codec can
 * treat specially, such as {@link AsciiString}.
 */
public interface TextHeaders extends Iterable<Map.Entry<String, String>> {
    /**
     * Returns the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value if the header is found.
     *         {@code null} if there's no such header.
     */
    String get(CharSequence name);

    /**
     * Returns the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value if the header is found.
     *         {@code defaultValue} if there's no such header.
     */
    String get(CharSequence name, String defaultValue);

    /**
     * Returns the integer value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value if the header is found and its value is an integer.
     *         {@code null} if there's no such header or its value is not an integer.
     */
    Integer getInt(CharSequence name);

    /**
     * Returns the integer value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value if the header is found and its value is an integer.
     *         {@code defaultValue} if there's no such header or its value is not an integer.
     */
    int getInt(CharSequence name, int defaultValue);

    /**
     * Returns the long integer value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value if the header is found and its value is a long integer.
     *         {@code null} if there's no such header or its value is not a long integer.
     */
    Long getLong(CharSequence name);

    /**
     * Returns the long integer value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value if the header is found and its value is a long integer.
     *         {@code defaultValue} if there's no such header or its value is not a long integer.
     */
    long getLong(CharSequence name, long defaultValue);

    /**
     * Returns the date value of a header with the specified name as milliseconds.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name The name of the header to search
     * @return the first header value in milliseconds if the header is found and its value is a date.
     *         {@code null} if there's no such header or its value is not a date.
     */
    Long getTimeMillis(CharSequence name);

    /**
     * Returns the date value of a header with the specified name as milliseconds.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name The name of the header to search
     * @param defaultValue default value
     * @return the first header value in milliseconds if the header is found and its value is a date.
     *         {@code defaultValue} if there's no such header or its value is not a date.
     */
    long getTimeMillis(CharSequence name, long defaultValue);

    /**
     * Returns and removes the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value or {@code null} if there is no such header
     */
    String getAndRemove(CharSequence name);

    /**
     * Returns and removes the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value or {@code defaultValue} if there is no such header
     */
    String getAndRemove(CharSequence name, String defaultValue);

    /**
     * Returns and removes the integer value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value if the header is found and its value is an integer.
     *         {@code null} if there's no such header or its value is not an integer.
     */
    Integer getIntAndRemove(CharSequence name);

    /**
     * Returns and removes the integer value of a header with the specified name.  If there are more than one values for
     * the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value if the header is found and its value is an integer.
     *         {@code defaultValue} if there is no such header or its value of header is not an integer.
     */
    int getIntAndRemove(CharSequence name, int defaultValue);

    /**
     * Returns and removes the long value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value if the header is found and its value is an integer.
     *         {@code null} if there's no such header or its value is not an integer.
     */
    Long getLongAndRemove(CharSequence name);

    /**
     * Returns and removes the long value of a header with the specified name.  If there are more than one values for
     * the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value if the header is found and its value is an integer.
     *         {@code defaultValue} if there's no such header or its value is not an integer.
     */
    long getLongAndRemove(CharSequence name, long defaultValue);

    /**
     * Returns and removes the date value of a header with the specified name as milliseconds.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name The name of the header to search
     * @return the first header value in milliseconds if the header is found and its value is a date.
     *         {@code null} if there's no such header or its value is not a date.
     */
    Long getTimeMillisAndRemove(CharSequence name);

    /**
     * Returns and removes the date value of a header with the specified name as milliseconds.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name The name of the header to search
     * @param defaultValue default value
     * @return the first header value in milliseconds if the header is found and its value is a date.
     *         {@code defaultValue} if there's no such header or its value is not a date.
     */
    long getTimeMillisAndRemove(CharSequence name, long defaultValue);

    /**
     * Returns the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name The name of the header to search
     * @return The first header value or {@code null} if there is no such header
     */
    CharSequence getUnconverted(CharSequence name);

    /**
     * Returns and Removes the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name The name of the header to search
     * @return The first header value or {@code null} if there is no such header
     */
    CharSequence getUnconvertedAndRemove(CharSequence name);

    /**
     * Returns the values of headers with the specified name
     *
     * @param name The name of the headers to search
     * @return A {@link List} of header values which will be empty if no values are found
     */
    List<String> getAll(CharSequence name);

    /**
     * Returns the values of headers with the specified name
     *
     * @param name The name of the headers to search
     * @return A {@link List} of header values which will be empty if no values are found
     */
    List<CharSequence> getAllUnconverted(CharSequence name);

    /**
     * Returns and Removes the values of headers with the specified name
     *
     * @param name The name of the headers to search
     * @return A {@link List} of header values which will be empty if no values are found
     */
    List<String> getAllAndRemove(CharSequence name);

    /**
     * Returns and Removes the values of headers with the specified name
     *
     * @param name The name of the headers to search
     * @return A {@link List} of header values which will be empty if no values are found
     */
    List<CharSequence> getAllUnconvertedAndRemove(CharSequence name);

    /**
     * Returns a new {@link List} that contains all headers in this object.  Note that modifying the
     * returned {@link List} will not affect the state of this object.  If you intend to enumerate over the header
     * entries only, use {@link #iterator()} instead, which has much less overhead.
     */
    List<Entry<String, String>> entries();

    /**
     * Returns a new {@link List} that contains all headers in this object.  Note that modifying the
     * returned {@link List} will not affect the state of this object.  If you intend to enumerate over the header
     * entries only, use {@link #iterator()} instead, which has much less overhead.
     */
    List<Entry<CharSequence, CharSequence>> unconvertedEntries();

    /**
     * Returns {@code true} if and only if this collection contains the header with the specified name.
     *
     * @param name The name of the header to search for
     * @return {@code true} if at least one header is found
     */
    boolean contains(CharSequence name);

    /**
     * Returns the number of header entries in this collection.
     */
    int size();

    /**
     * Returns {@code true} if and only if this collection contains no header entries.
     */
    boolean isEmpty();

    /**
     * Returns a new {@link Set} that contains the names of all headers in this object.  Note that modifying the
     * returned {@link Set} will not affect the state of this object.  If you intend to enumerate over the header
     * entries only, use {@link #iterator()} instead, which has much less overhead.
     */
    Set<String> names();

    /**
     * Returns a new {@link Set} that contains the names of all headers in this object.  Note that modifying the
     * returned {@link Set} will not affect the state of this object.  If you intend to enumerate over the header
     * entries only, use {@link #iterator()} instead, which has much less overhead.
     */
    Set<CharSequence> unconvertedNames();

    /**
     * Adds a new header with the specified name and value.
     *
     * If the specified value is not a {@link String}, it is converted
     * into a {@link String} by {@link Object#toString()}, except in the cases
     * of {@link java.util.Date} and {@link java.util.Calendar}, which are formatted to the date
     * format defined in <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>.
     *
     * @param name the name of the header being added
     * @param value the value of the header being added
     *
     * @return {@code this}
     */
    TextHeaders add(CharSequence name, Object value);

    /**
     * Adds a new header with the specified name and values.
     *
     * This getMethod can be represented approximately as the following code:
     * <pre>
     * for (Object v: values) {
     *     if (v == null) {
     *         break;
     *     }
     *     headers.add(name, v);
     * }
     * </pre>
     *
     * @param name the name of the headepublic abstract rs being set
     * @param values the values of the headers being set
     * @return {@code this}
     */
    TextHeaders add(CharSequence name, Iterable<?> values);

    /**
     * Adds a new header with the specified name and values.
     *
     * This getMethod can be represented approximately as the following code:
     * <pre>
     * for (Object v: values) {
     *     if (v == null) {
     *         break;
     *     }
     *     headers.add(name, v);
     * }
     * </pre>
     *
     * @param name the name of the headepublic abstract rs being set
     * @param values the values of the headers being set
     * @return {@code this}
     */
    TextHeaders add(CharSequence name, Object... values);

    /**
     * Adds all header entries of the specified {@code headers}.
     *
     * @return {@code this}
     */
    TextHeaders add(TextHeaders headers);

    /**
     * Sets a header with the specified name and value.
     *
     * If there is an existing header with the same name, it is removed.
     * If the specified value is not a {@link String}, it is converted into a
     * {@link String} by {@link Object#toString()}, except for {@link java.util.Date}
     * and {@link java.util.Calendar}, which are formatted to the date format defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>.
     *
     * @param name The name of the header being set
     * @param value The value of the header being set
     * @return {@code this}
     */
    TextHeaders set(CharSequence name, Object value);

    /**
     * Sets a header with the specified name and values.
     *
     * If there is an existing header with the same name, it is removed.
     * This getMethod can be represented approximately as the following code:
     * <pre>
     * headers.remove(name);
     * for (Object v: values) {
     *     if (v == null) {
     *         break;
     *     }
     *     headers.add(name, v);
     * }
     * </pre>
     *
     * @param name the name of the headers being set
     * @param values the values of the headers being set
     * @return {@code this}
     */
    TextHeaders set(CharSequence name, Iterable<?> values);

    /**
     * Sets a header with the specified name and values.
     *
     * If there is an existing header with the same name, it is removed.
     * This getMethod can be represented approximately as the following code:
     * <pre>
     * headers.remove(name);
     * for (Object v: values) {
     *     if (v == null) {
     *         break;
     *     }
     *     headers.add(name, v);
     * }
     * </pre>
     *
     * @param name the name of the headers being set
     * @param values the values of the headers being set
     * @return {@code this}
     */
    TextHeaders set(CharSequence name, Object... values);

    /**
     * Cleans the current header entries and copies all header entries of the specified {@code headers}.
     *
     * @return {@code this}
     */
    TextHeaders set(TextHeaders headers);

    /**
     * Removes the header with the specified name.
     *
     * @param name The name of the header to remove
     * @return {@code true} if and only if at least one entry has been removed
     */
    boolean remove(CharSequence name);

    /**
     * Removes all headers.
     *
     * @return {@code this}
     */
    TextHeaders clear();

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name              the header name
     * @param value             the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean contains(CharSequence name, Object value);

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name              the header name
     * @param value             the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean contains(CharSequence name, Object value, boolean ignoreCase);

    @Override
    Iterator<Entry<String, String>> iterator();

    Iterator<Entry<CharSequence, CharSequence>> unconvertedIterator();

    TextHeaders forEachEntry(TextHeaderProcessor processor);
}
