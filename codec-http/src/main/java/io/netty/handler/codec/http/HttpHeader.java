/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;


import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An interface that defines a HTTP message, providing common properties for
 * {@link HttpRequestHeader} and {@link HttpResponseHeader}.
 * @see HttpResponseHeader
 * @see HttpRequestHeader
 * @see HttpHeaders
 *
 * @apiviz.landmark
 * @apiviz.has io.netty.handler.codec.http.HttpChunk oneway - - is followed by
 */
public interface HttpHeader extends HttpObject {

    /**
     * Returns the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name The name of the header to search
     * @return The first header value or {@code null} if there is no such header
     */
    String getHeader(String name);

    /**
     * Returns the values of headers with the specified name
     *
     * @param name The name of the headers to search
     * @return A {@link List} of header values which will be empty if no values
     *         are found
     */
    List<String> getHeaders(String name);

    /**
     * Returns the all headers that this message contains.
     *
     * @return A {@link List} of the header name-value entries, which will be
     *         empty if no pairs are found
     */
    List<Map.Entry<String, String>> getHeaders();

    /**
     * Checks to see if there is a header with the specified name
     *
     * @param name The name of the header to search for
     * @return True if at least one header is found
     */
    boolean containsHeader(String name);

    /**
     * Gets a {@link Set} of all header names that this message contains
     *
     * @return A {@link Set} of all header names
     */
    Set<String> getHeaderNames();

    /**
     * Returns the protocol version of this {@link HttpHeader}
     *
     * @return The protocol version
     */
    HttpVersion getProtocolVersion();

    /**
     * Sets the protocol version of this {@link HttpHeader}
     *
     * @param version The version to set
     */
    void setProtocolVersion(HttpVersion version);

    /**
     * Adds a new header with the specified name and value.
     *
     * If the specified value is not a {@link String}, it is converted
     * into a {@link String} by {@link Object#toString()}, except in the cases
     * of {@link Date} and {@link Calendar}, which are formatted to the date
     * format defined in <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>.
     *
     * @param name The name of the header being added
     * @param value The value of the header being added
     */
    void addHeader(String name, Object value);

    /**
     * Sets a header with the specified name and value.
     *
     * If there is an existing header with the same name, it is removed.
     * If the specified value is not a {@link String}, it is converted into a
     * {@link String} by {@link Object#toString()}, except for {@link Date}
     * and {@link Calendar}, which are formatted to the date format defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>.
     *
     * @param name The name of the header being set
     * @param value The value of the header being set
     */
    void setHeader(String name, Object value);

    /**
     * Sets a header with the specified name and values.
     *
     * If there is an existing header with the same name, it is removed.
     * This method can be represented approximately as the following code:
     * <pre>
     * m.removeHeader(name);
     * for (Object v: values) {
     *     if (v == null) {
     *         break;
     *     }
     *     m.addHeader(name, v);
     * }
     * </pre>
     *
     * @param name The name of the headers being set
     * @param values The values of the headers being set
     */
    void setHeader(String name, Iterable<?> values);

    /**
     * Removes the header with the specified name.
     *
     * @param name The name of the header to remove
     */
    void removeHeader(String name);

    /**
     * Removes all headers from this {@link HttpHeader}.
     */
    void clearHeaders();
}
