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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

/**
 * A typical {@code AsciiString} multimap used by protocols that use binary headers (such as HTTP/2)
 * for the representation of arbitrary key-value data. {@link AsciiString} is just a wrapper around
 * a byte array but provides some additional utility when handling text data.
 */
public interface BinaryHeaders extends Iterable<Map.Entry<AsciiString, AsciiString>> {

    /**
     * A visitor that helps reduce GC pressure while iterating over a collection of {@link BinaryHeaders}.
     */
    public interface BinaryHeaderVisitor {
        /**
         * @return
         * <ul>
         * <li>{@code true} if the processor wants to continue the loop and handle the entry.</li>
         * <li>{@code false} if the processor wants to stop handling headers and abort the loop.</li>
         * </ul>
         */
        boolean visit(AsciiString name, AsciiString value) throws Exception;
    }

    /**
     * Returns the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value if the header is found.
     *         {@code null} if there's no such header.
     */
    AsciiString get(AsciiString name);

    /**
     * Returns the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value if the header is found.
     *         {@code defaultValue} if there's no such header.
     */
    AsciiString get(AsciiString name, AsciiString defaultValue);

    /**
     * Returns and removes the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value or {@code null} if there is no such header
     */
    AsciiString getAndRemove(AsciiString name);

    /**
     * Returns and removes the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value or {@code defaultValue} if there is no such header
     */
    AsciiString getAndRemove(AsciiString name, AsciiString defaultValue);

    /**
     * Returns the values of headers with the specified name
     *
     * @param name The name of the headers to search
     * @return A {@link List} of header values which will be empty if no values are found
     */
    List<AsciiString> getAll(AsciiString name);

    /**
     * Returns and Removes the values of headers with the specified name
     *
     * @param name The name of the headers to search
     * @return A {@link List} of header values which will be empty if no values are found
     */
    List<AsciiString> getAllAndRemove(AsciiString name);

    /**
     * Returns a new {@link List} that contains all headers in this object.  Note that modifying the
     * returned {@link List} will not affect the state of this object.  If you intend to enumerate over the header
     * entries only, use {@link #iterator()} instead, which has much less overhead.
     */
    List<Entry<AsciiString, AsciiString>> entries();

    /**
     * Returns {@code true} if and only if this collection contains the header with the specified name.
     *
     * @param name The name of the header to search for
     * @return {@code true} if at least one header is found
     */
    boolean contains(AsciiString name);

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
    Set<AsciiString> names();

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
    BinaryHeaders add(AsciiString name, AsciiString value);

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
     * @param name the name of the headers being set
     * @param values the values of the headers being set
     * @return {@code this}
     */
    BinaryHeaders add(AsciiString name, Iterable<AsciiString> values);

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
     * @param name the name of the headers being set
     * @param values the values of the headers being set
     * @return {@code this}
     */
    BinaryHeaders add(AsciiString name, AsciiString... values);

    /**
     * Adds all header entries of the specified {@code headers}.
     *
     * @return {@code this}
     */
    BinaryHeaders add(BinaryHeaders headers);

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
    BinaryHeaders set(AsciiString name, AsciiString value);

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
    BinaryHeaders set(AsciiString name, Iterable<AsciiString> values);

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
    BinaryHeaders set(AsciiString name, AsciiString... values);

    /**
     * Cleans the current header entries and copies all header entries of the specified {@code headers}.
     *
     * @return {@code this}
     */
    BinaryHeaders set(BinaryHeaders headers);

    /**
     * Retains all current headers but calls {@link #set(AsciiString, Object)} for each entry in {@code headers}
     * @param headers The headers used to {@link #set(AsciiString, Object)} values in this instance
     * @return {@code this}
     */
    BinaryHeaders setAll(BinaryHeaders headers);

    /**
     * Removes the header with the specified name.
     *
     * @param name The name of the header to remove
     * @return {@code true} if and only if at least one entry has been removed
     */
    boolean remove(AsciiString name);

    /**
     * Removes all headers.
     *
     * @return {@code this}
     */
    BinaryHeaders clear();

    /**
     * Returns {@code true} if a header with the name and value exists.
     *
     * @param name              the header name
     * @param value             the header value
     * @return {@code true} if it contains it {@code false} otherwise
     */
    boolean contains(AsciiString name, AsciiString value);

    @Override
    Iterator<Entry<AsciiString, AsciiString>> iterator();

    BinaryHeaders forEachEntry(BinaryHeaderVisitor visitor);

    /**
     * Common utilities for {@link BinaryHeaders}.
     */
    public static final class Utils {
        private static final int HASH_CODE_PRIME = 31;

        /**
         * Generates a hash code for a {@link BinaryHeaders} object.
         */
        public static int hashCode(BinaryHeaders headers) {
            int result = 1;
            for (AsciiString name : headers.names()) {
                result = HASH_CODE_PRIME * result + name.hashCode();
                Set<AsciiString> values = new TreeSet<AsciiString>(headers.getAll(name));
                for (AsciiString value : values) {
                    result = HASH_CODE_PRIME * result + value.hashCode();
                }
            }
            return result;
        }

        /**
         * Compares the contents of two {@link BinaryHeaders} objects.
         */
        public static boolean equals(BinaryHeaders h1, BinaryHeaders h2) {
            // First, check that the set of names match.
            Set<AsciiString> names = h1.names();
            if (!names.equals(h2.names())) {
                return false;
            }

            // Compare the values for each name.
            for (AsciiString name : names) {
                List<AsciiString> values = h1.getAll(name);
                List<AsciiString> otherValues = h2.getAll(name);
                if (values.size() != otherValues.size()) {
                    return false;
                }

                // Convert the values to a set and remove values from the other object to see if
                // they match.
                Set<AsciiString> valueSet = new HashSet<AsciiString>(values);
                valueSet.removeAll(otherValues);
                if (!valueSet.isEmpty()) {
                    return false;
                }
            }

            return true;
        }

        /**
         * Generates a {@link String} representation of the {@link BinaryHeaders}, assuming all of
         * the names and values are {@code UTF-8} strings.
         */
        public static String toStringUtf8(BinaryHeaders headers) {
            StringBuilder builder =
                    new StringBuilder(headers.getClass().getSimpleName()).append('[');
            boolean first = true;
            Set<AsciiString> names = new TreeSet<AsciiString>(headers.names());
            for (AsciiString name : names) {
                Set<AsciiString> valueSet = new TreeSet<AsciiString>(headers.getAll(name));
                for (AsciiString value : valueSet) {
                    if (!first) {
                        builder.append(", ");
                    }
                    first = false;
                    builder.append(name).append(": ").append(value);
                }
            }
            return builder.append("]").toString();
        }
    }
}
