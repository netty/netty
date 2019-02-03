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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.HeadersUtils;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static io.netty.util.AsciiString.contentEquals;
import static io.netty.util.AsciiString.contentEqualsIgnoreCase;
import static io.netty.util.AsciiString.trim;
import static java.util.Objects.requireNonNull;

/**
 * Provides the constants for the standard HTTP header names and values and
 * commonly used utility methods that accesses an {@link HttpMessage}.
 */
public abstract class HttpHeaders implements Iterable<Map.Entry<String, String>> {

    /**
     * @deprecated Use {@link HttpUtil#isKeepAlive(HttpMessage)} instead.
     *
     * Returns {@code true} if and only if the connection can remain open and
     * thus 'kept alive'.  This methods respects the value of the
     * {@code "Connection"} header first and then the return value of
     * {@link HttpVersion#isKeepAliveDefault()}.
     */
    @Deprecated
    public static boolean isKeepAlive(HttpMessage message) {
        return HttpUtil.isKeepAlive(message);
    }

    /**
     * @deprecated Use {@link HttpUtil#setKeepAlive(HttpMessage, boolean)} instead.
     *
     * Sets the value of the {@code "Connection"} header depending on the
     * protocol version of the specified message.  This getMethod sets or removes
     * the {@code "Connection"} header depending on what the default keep alive
     * mode of the message's protocol version is, as specified by
     * {@link HttpVersion#isKeepAliveDefault()}.
     * <ul>
     * <li>If the connection is kept alive by default:
     *     <ul>
     *     <li>set to {@code "close"} if {@code keepAlive} is {@code false}.</li>
     *     <li>remove otherwise.</li>
     *     </ul></li>
     * <li>If the connection is closed by default:
     *     <ul>
     *     <li>set to {@code "keep-alive"} if {@code keepAlive} is {@code true}.</li>
     *     <li>remove otherwise.</li>
     *     </ul></li>
     * </ul>
     */
    @Deprecated
    public static void setKeepAlive(HttpMessage message, boolean keepAlive) {
        HttpUtil.setKeepAlive(message, keepAlive);
    }

    /**
     * @deprecated Use {@link #get(CharSequence)} instead.
     */
    @Deprecated
    public static String getHeader(HttpMessage message, String name) {
        return message.headers().get(name);
    }

    /**
     * @deprecated Use {@link #get(CharSequence)} instead.
     *
     * Returns the header value with the specified header name.  If there are
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or {@code null} if there is no such header
     */
    @Deprecated
    public static String getHeader(HttpMessage message, CharSequence name) {
        return message.headers().get(name);
    }

    /**
     * @deprecated Use {@link #get(CharSequence, String)} instead.
     *
     * @see #getHeader(HttpMessage, CharSequence, String)
     */
    @Deprecated
    public static String getHeader(HttpMessage message, String name, String defaultValue) {
        return message.headers().get(name, defaultValue);
    }

    /**
     * @deprecated Use {@link #get(CharSequence, String)} instead.
     *
     * Returns the header value with the specified header name.  If there are
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or the {@code defaultValue} if there is no such
     *         header
     */
    @Deprecated
    public static String getHeader(HttpMessage message, CharSequence name, String defaultValue) {
        return message.headers().get(name, defaultValue);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Object)} instead.
     *
     * @see #setHeader(HttpMessage, CharSequence, Object)
     */
    @Deprecated
    public static void setHeader(HttpMessage message, String name, Object value) {
        message.headers().set(name, value);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Object)} instead.
     *
     * Sets a new header with the specified name and value.  If there is an
     * existing header with the same name, the existing header is removed.
     * If the specified value is not a {@link String}, it is converted into a
     * {@link String} by {@link Object#toString()}, except for {@link Date}
     * and {@link Calendar} which are formatted to the date format defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>.
     */
    @Deprecated
    public static void setHeader(HttpMessage message, CharSequence name, Object value) {
        message.headers().set(name, value);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Iterable)} instead.
     *
     * @see #setHeader(HttpMessage, CharSequence, Iterable)
     */
    @Deprecated
    public static void setHeader(HttpMessage message, String name, Iterable<?> values) {
        message.headers().set(name, values);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Iterable)} instead.
     *
     * Sets a new header with the specified name and values.  If there is an
     * existing header with the same name, the existing header is removed.
     * This getMethod can be represented approximately as the following code:
     * <pre>
     * removeHeader(message, name);
     * for (Object v: values) {
     *     if (v == null) {
     *         break;
     *     }
     *     addHeader(message, name, v);
     * }
     * </pre>
     */
    @Deprecated
    public static void setHeader(HttpMessage message, CharSequence name, Iterable<?> values) {
        message.headers().set(name, values);
    }

    /**
     * @deprecated Use {@link #add(CharSequence, Object)} instead.
     *
     * @see #addHeader(HttpMessage, CharSequence, Object)
     */
    @Deprecated
    public static void addHeader(HttpMessage message, String name, Object value) {
        message.headers().add(name, value);
    }

    /**
     * @deprecated Use {@link #add(CharSequence, Object)} instead.
     *
     * Adds a new header with the specified name and value.
     * If the specified value is not a {@link String}, it is converted into a
     * {@link String} by {@link Object#toString()}, except for {@link Date}
     * and {@link Calendar} which are formatted to the date format defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>.
     */
    @Deprecated
    public static void addHeader(HttpMessage message, CharSequence name, Object value) {
        message.headers().add(name, value);
    }

    /**
     * @deprecated Use {@link #remove(CharSequence)} instead.
     *
     * @see #removeHeader(HttpMessage, CharSequence)
     */
    @Deprecated
    public static void removeHeader(HttpMessage message, String name) {
        message.headers().remove(name);
    }

    /**
     * @deprecated Use {@link #remove(CharSequence)} instead.
     *
     * Removes the header with the specified name.
     */
    @Deprecated
    public static void removeHeader(HttpMessage message, CharSequence name) {
        message.headers().remove(name);
    }

    /**
     * @deprecated Use {@link #clear()} instead.
     *
     * Removes all headers from the specified message.
     */
    @Deprecated
    public static void clearHeaders(HttpMessage message) {
        message.headers().clear();
    }

    /**
     * @deprecated Use {@link #getInt(CharSequence)} instead.
     *
     * @see #getIntHeader(HttpMessage, CharSequence)
     */
    @Deprecated
    public static int getIntHeader(HttpMessage message, String name) {
        return getIntHeader(message, (CharSequence) name);
    }

    /**
     * @deprecated Use {@link #getInt(CharSequence)} instead.
     *
     * Returns the integer header value with the specified header name.  If
     * there are more than one header value for the specified header name, the
     * first value is returned.
     *
     * @return the header value
     * @throws NumberFormatException
     *         if there is no such header or the header value is not a number
     */
    @Deprecated
    public static int getIntHeader(HttpMessage message, CharSequence name) {
        String value = message.headers().get(name);
        if (value == null) {
            throw new NumberFormatException("header not found: " + name);
        }
        return Integer.parseInt(value);
    }

    /**
     * @deprecated Use {@link #getInt(CharSequence, int)} instead.
     *
     * @see #getIntHeader(HttpMessage, CharSequence, int)
     */
    @Deprecated
    public static int getIntHeader(HttpMessage message, String name, int defaultValue) {
        return message.headers().getInt(name, defaultValue);
    }

    /**
     * @deprecated Use {@link #getInt(CharSequence, int)} instead.
     *
     * Returns the integer header value with the specified header name.  If
     * there are more than one header value for the specified header name, the
     * first value is returned.
     *
     * @return the header value or the {@code defaultValue} if there is no such
     *         header or the header value is not a number
     */
    @Deprecated
    public static int getIntHeader(HttpMessage message, CharSequence name, int defaultValue) {
        return message.headers().getInt(name, defaultValue);
    }

    /**
     * @deprecated Use {@link #setInt(CharSequence, int)} instead.
     *
     * @see #setIntHeader(HttpMessage, CharSequence, int)
     */
    @Deprecated
    public static void setIntHeader(HttpMessage message, String name, int value) {
        message.headers().setInt(name, value);
    }

    /**
     * @deprecated Use {@link #setInt(CharSequence, int)} instead.
     *
     * Sets a new integer header with the specified name and value.  If there
     * is an existing header with the same name, the existing header is removed.
     */
    @Deprecated
    public static void setIntHeader(HttpMessage message, CharSequence name, int value) {
        message.headers().setInt(name, value);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Iterable)} instead.
     *
     * @see #setIntHeader(HttpMessage, CharSequence, Iterable)
     */
    @Deprecated
    public static void setIntHeader(HttpMessage message, String name, Iterable<Integer> values) {
        message.headers().set(name, values);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Iterable)} instead.
     *
     * Sets a new integer header with the specified name and values.  If there
     * is an existing header with the same name, the existing header is removed.
     */
    @Deprecated
    public static void setIntHeader(HttpMessage message, CharSequence name, Iterable<Integer> values) {
        message.headers().set(name, values);
    }

    /**
     * @deprecated Use {@link #add(CharSequence, Iterable)} instead.
     *
     * @see #addIntHeader(HttpMessage, CharSequence, int)
     */
    @Deprecated
    public static void addIntHeader(HttpMessage message, String name, int value) {
        message.headers().add(name, value);
    }

    /**
     * @deprecated Use {@link #addInt(CharSequence, int)} instead.
     *
     * Adds a new integer header with the specified name and value.
     */
    @Deprecated
    public static void addIntHeader(HttpMessage message, CharSequence name, int value) {
        message.headers().addInt(name, value);
    }

    /**
     * @deprecated Use {@link #getTimeMillis(CharSequence)} instead.
     *
     * @see #getDateHeader(HttpMessage, CharSequence)
     */
    @Deprecated
    public static Date getDateHeader(HttpMessage message, String name) throws ParseException {
        return getDateHeader(message, (CharSequence) name);
    }

    /**
     * @deprecated Use {@link #getTimeMillis(CharSequence)} instead.
     *
     * Returns the date header value with the specified header name.  If
     * there are more than one header value for the specified header name, the
     * first value is returned.
     *
     * @return the header value
     * @throws ParseException
     *         if there is no such header or the header value is not a formatted date
     */
    @Deprecated
    public static Date getDateHeader(HttpMessage message, CharSequence name) throws ParseException {
        String value = message.headers().get(name);
        if (value == null) {
            throw new ParseException("header not found: " + name, 0);
        }
        Date date = DateFormatter.parseHttpDate(value);
        if (date == null) {
            throw new ParseException("header can't be parsed into a Date: " + value, 0);
        }
        return date;
    }

    /**
     * @deprecated Use {@link #getTimeMillis(CharSequence, long)} instead.
     *
     * @see #getDateHeader(HttpMessage, CharSequence, Date)
     */
    @Deprecated
    public static Date getDateHeader(HttpMessage message, String name, Date defaultValue) {
        return getDateHeader(message, (CharSequence) name, defaultValue);
    }

    /**
     * @deprecated Use {@link #getTimeMillis(CharSequence, long)} instead.
     *
     * Returns the date header value with the specified header name.  If
     * there are more than one header value for the specified header name, the
     * first value is returned.
     *
     * @return the header value or the {@code defaultValue} if there is no such
     *         header or the header value is not a formatted date
     */
    @Deprecated
    public static Date getDateHeader(HttpMessage message, CharSequence name, Date defaultValue) {
        final String value = getHeader(message, name);
        Date date = DateFormatter.parseHttpDate(value);
        return date != null ? date : defaultValue;
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Object)} instead.
     *
     * @see #setDateHeader(HttpMessage, CharSequence, Date)
     */
    @Deprecated
    public static void setDateHeader(HttpMessage message, String name, Date value) {
        setDateHeader(message, (CharSequence) name, value);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Object)} instead.
     *
     * Sets a new date header with the specified name and value.  If there
     * is an existing header with the same name, the existing header is removed.
     * The specified value is formatted as defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>
     */
    @Deprecated
    public static void setDateHeader(HttpMessage message, CharSequence name, Date value) {
        if (value != null) {
            message.headers().set(name, DateFormatter.format(value));
        } else {
            message.headers().set(name, null);
        }
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Iterable)} instead.
     *
     * @see #setDateHeader(HttpMessage, CharSequence, Iterable)
     */
    @Deprecated
    public static void setDateHeader(HttpMessage message, String name, Iterable<Date> values) {
        message.headers().set(name, values);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Iterable)} instead.
     *
     * Sets a new date header with the specified name and values.  If there
     * is an existing header with the same name, the existing header is removed.
     * The specified values are formatted as defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>
     */
    @Deprecated
    public static void setDateHeader(HttpMessage message, CharSequence name, Iterable<Date> values) {
        message.headers().set(name, values);
    }

    /**
     * @deprecated Use {@link #add(CharSequence, Object)} instead.
     *
     * @see #addDateHeader(HttpMessage, CharSequence, Date)
     */
    @Deprecated
    public static void addDateHeader(HttpMessage message, String name, Date value) {
        message.headers().add(name, value);
    }

    /**
     * @deprecated Use {@link #add(CharSequence, Object)} instead.
     *
     * Adds a new date header with the specified name and value.  The specified
     * value is formatted as defined in
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">RFC2616</a>
     */
    @Deprecated
    public static void addDateHeader(HttpMessage message, CharSequence name, Date value) {
        message.headers().add(name, value);
    }

    /**
     * @deprecated Use {@link HttpUtil#getContentLength(HttpMessage)} instead.
     *
     * Returns the length of the content.  Please note that this value is
     * not retrieved from {@link HttpContent#content()} but from the
     * {@code "Content-Length"} header, and thus they are independent from each
     * other.
     *
     * @return the content length
     *
     * @throws NumberFormatException
     *         if the message does not have the {@code "Content-Length"} header
     *         or its value is not a number
     */
    @Deprecated
    public static long getContentLength(HttpMessage message) {
        return HttpUtil.getContentLength(message);
    }

    /**
     * @deprecated Use {@link HttpUtil#getContentLength(HttpMessage, long)} instead.
     *
     * Returns the length of the content.  Please note that this value is
     * not retrieved from {@link HttpContent#content()} but from the
     * {@code "Content-Length"} header, and thus they are independent from each
     * other.
     *
     * @return the content length or {@code defaultValue} if this message does
     *         not have the {@code "Content-Length"} header or its value is not
     *         a number
     */
    @Deprecated
    public static long getContentLength(HttpMessage message, long defaultValue) {
        return HttpUtil.getContentLength(message, defaultValue);
    }

    /**
     * @deprecated Use {@link HttpUtil#setContentLength(HttpMessage, long)} instead.
     */
    @Deprecated
    public static void setContentLength(HttpMessage message, long length) {
        HttpUtil.setContentLength(message, length);
    }

    /**
     * @deprecated Use {@link #get(CharSequence)} instead.
     *
     * Returns the value of the {@code "Host"} header.
     */
    @Deprecated
    public static String getHost(HttpMessage message) {
        return message.headers().get(HttpHeaderNames.HOST);
    }

    /**
     * @deprecated Use {@link #get(CharSequence, String)} instead.
     *
     * Returns the value of the {@code "Host"} header.  If there is no such
     * header, the {@code defaultValue} is returned.
     */
    @Deprecated
    public static String getHost(HttpMessage message, String defaultValue) {
        return message.headers().get(HttpHeaderNames.HOST, defaultValue);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Object)} instead.
     *
     * @see #setHost(HttpMessage, CharSequence)
     */
    @Deprecated
    public static void setHost(HttpMessage message, String value) {
        message.headers().set(HttpHeaderNames.HOST, value);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Object)} instead.
     *
     * Sets the {@code "Host"} header.
     */
    @Deprecated
    public static void setHost(HttpMessage message, CharSequence value) {
        message.headers().set(HttpHeaderNames.HOST, value);
    }

    /**
     * @deprecated Use {@link #getTimeMillis(CharSequence)} instead.
     *
     * Returns the value of the {@code "Date"} header.
     *
     * @throws ParseException
     *         if there is no such header or the header value is not a formatted date
     */
    @Deprecated
    public static Date getDate(HttpMessage message) throws ParseException {
        return getDateHeader(message, HttpHeaderNames.DATE);
    }

    /**
     * @deprecated Use {@link #getTimeMillis(CharSequence, long)} instead.
     *
     * Returns the value of the {@code "Date"} header. If there is no such
     * header or the header is not a formatted date, the {@code defaultValue}
     * is returned.
     */
    @Deprecated
    public static Date getDate(HttpMessage message, Date defaultValue) {
        return getDateHeader(message, HttpHeaderNames.DATE, defaultValue);
    }

    /**
     * @deprecated Use {@link #set(CharSequence, Object)} instead.
     *
     * Sets the {@code "Date"} header.
     */
    @Deprecated
    public static void setDate(HttpMessage message, Date value) {
        message.headers().set(HttpHeaderNames.DATE, value);
    }

    /**
     * @deprecated Use {@link HttpUtil#is100ContinueExpected(HttpMessage)} instead.
     *
     * Returns {@code true} if and only if the specified message contains the
     * {@code "Expect: 100-continue"} header.
     */
    @Deprecated
    public static boolean is100ContinueExpected(HttpMessage message) {
        return HttpUtil.is100ContinueExpected(message);
    }

    /**
     * @deprecated Use {@link HttpUtil#set100ContinueExpected(HttpMessage, boolean)} instead.
     *
     * Sets the {@code "Expect: 100-continue"} header to the specified message.
     * If there is any existing {@code "Expect"} header, they are replaced with
     * the new one.
     */
    @Deprecated
    public static void set100ContinueExpected(HttpMessage message) {
        HttpUtil.set100ContinueExpected(message, true);
    }

    /**
     * @deprecated Use {@link HttpUtil#set100ContinueExpected(HttpMessage, boolean)} instead.
     *
     * Sets or removes the {@code "Expect: 100-continue"} header to / from the
     * specified message.  If {@code set} is {@code true},
     * the {@code "Expect: 100-continue"} header is set and all other previous
     * {@code "Expect"} headers are removed.  Otherwise, all {@code "Expect"}
     * headers are removed completely.
     */
    @Deprecated
    public static void set100ContinueExpected(HttpMessage message, boolean set) {
        HttpUtil.set100ContinueExpected(message, set);
    }

    /**
     * @deprecated Use {@link HttpUtil#isTransferEncodingChunked(HttpMessage)} instead.
     *
     * Checks to see if the transfer encoding in a specified {@link HttpMessage} is chunked
     *
     * @param message The message to check
     * @return True if transfer encoding is chunked, otherwise false
     */
    @Deprecated
    public static boolean isTransferEncodingChunked(HttpMessage message) {
        return HttpUtil.isTransferEncodingChunked(message);
    }

    /**
     * @deprecated Use {@link HttpUtil#setTransferEncodingChunked(HttpMessage, boolean)} instead.
     */
    @Deprecated
    public static void removeTransferEncodingChunked(HttpMessage m) {
        HttpUtil.setTransferEncodingChunked(m, false);
    }

    /**
     * @deprecated Use {@link HttpUtil#setTransferEncodingChunked(HttpMessage, boolean)} instead.
     */
    @Deprecated
    public static void setTransferEncodingChunked(HttpMessage m) {
        HttpUtil.setTransferEncodingChunked(m, true);
    }

    /**
     * @deprecated Use {@link HttpUtil#isContentLengthSet(HttpMessage)} instead.
     */
    @Deprecated
    public static boolean isContentLengthSet(HttpMessage m) {
        return HttpUtil.isContentLengthSet(m);
    }

    /**
     * @deprecated Use {@link AsciiString#contentEqualsIgnoreCase(CharSequence, CharSequence)} instead.
     */
    @Deprecated
    public static boolean equalsIgnoreCase(CharSequence name1, CharSequence name2) {
        return contentEqualsIgnoreCase(name1, name2);
    }

    @Deprecated
    public static void encodeAscii(CharSequence seq, ByteBuf buf) {
        if (seq instanceof AsciiString) {
            ByteBufUtil.copy((AsciiString) seq, 0, buf, seq.length());
        } else {
            buf.writeCharSequence(seq, CharsetUtil.US_ASCII);
        }
    }

    /**
     * @deprecated Use {@link AsciiString} instead.
     * <p>
     * Create a new {@link CharSequence} which is optimized for reuse as {@link HttpHeaders} name or value.
     * So if you have a Header name or value that you want to reuse you should make use of this.
     */
    @Deprecated
    public static CharSequence newEntity(String name) {
        return new AsciiString(name);
    }

    protected HttpHeaders() { }

    /**
     * @see #get(CharSequence)
     */
    public abstract String get(String name);

    /**
     * Returns the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name The name of the header to search
     * @return The first header value or {@code null} if there is no such header
     * @see #getAsString(CharSequence)
     */
    public String get(CharSequence name) {
        return get(name.toString());
    }

    /**
     * Returns the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name The name of the header to search
     * @return The first header value or {@code defaultValue} if there is no such header
     */
    public String get(CharSequence name, String defaultValue) {
        String value = get(name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Returns the integer value of a header with the specified name. If there are more than one values for the
     * specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value if the header is found and its value is an integer. {@code null} if there's no
     *         such header or its value is not an integer.
     */
    public abstract Integer getInt(CharSequence name);

    /**
     * Returns the integer value of a header with the specified name. If there are more than one values for the
     * specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value if the header is found and its value is an integer. {@code defaultValue} if
     *         there's no such header or its value is not an integer.
     */
    public abstract int getInt(CharSequence name, int defaultValue);

    /**
     * Returns the short value of a header with the specified name. If there are more than one values for the
     * specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value if the header is found and its value is a short. {@code null} if there's no
     *         such header or its value is not a short.
     */
    public abstract Short getShort(CharSequence name);

    /**
     * Returns the short value of a header with the specified name. If there are more than one values for the
     * specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value if the header is found and its value is a short. {@code defaultValue} if
     *         there's no such header or its value is not a short.
     */
    public abstract short getShort(CharSequence name, short defaultValue);

    /**
     * Returns the date value of a header with the specified name. If there are more than one values for the
     * specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @return the first header value if the header is found and its value is a date. {@code null} if there's no
     *         such header or its value is not a date.
     */
    public abstract Long getTimeMillis(CharSequence name);

    /**
     * Returns the date value of a header with the specified name. If there are more than one values for the
     * specified name, the first value is returned.
     *
     * @param name the name of the header to search
     * @param defaultValue the default value
     * @return the first header value if the header is found and its value is a date. {@code defaultValue} if
     *         there's no such header or its value is not a date.
     */
    public abstract long getTimeMillis(CharSequence name, long defaultValue);

    /**
     * @see #getAll(CharSequence)
     */
    public abstract List<String> getAll(String name);

    /**
     * Returns the values of headers with the specified name
     *
     * @param name The name of the headers to search
     * @return A {@link List} of header values which will be empty if no values
     *         are found
     * @see #getAllAsString(CharSequence)
     */
    public List<String> getAll(CharSequence name) {
        return getAll(name.toString());
    }

    /**
     * Returns a new {@link List} that contains all headers in this object.  Note that modifying the
     * returned {@link List} will not affect the state of this object.  If you intend to enumerate over the header
     * entries only, use {@link #iterator()} instead, which has much less overhead.
     * @see #iteratorCharSequence()
     */
    public abstract List<Map.Entry<String, String>> entries();

    /**
     * @see #contains(CharSequence)
     */
    public abstract boolean contains(String name);

    /**
     * @deprecated It is preferred to use {@link #iteratorCharSequence()} unless you need {@link String}.
     * If {@link String} is required then use {@link #iteratorAsString()}.
     */
    @Deprecated
    @Override
    public abstract Iterator<Entry<String, String>> iterator();

    /**
     * @return Iterator over the name/value header pairs.
     */
    public abstract Iterator<Entry<CharSequence, CharSequence>> iteratorCharSequence();

    /**
     * Equivalent to {@link #getAll(String)} but it is possible that no intermediate list is generated.
     * @param name the name of the header to retrieve
     * @return an {@link Iterator} of header values corresponding to {@code name}.
     */
    public Iterator<String> valueStringIterator(CharSequence name) {
        return getAll(name).iterator();
    }

    /**
     * Equivalent to {@link #getAll(String)} but it is possible that no intermediate list is generated.
     * @param name the name of the header to retrieve
     * @return an {@link Iterator} of header values corresponding to {@code name}.
     */
    public Iterator<? extends CharSequence> valueCharSequenceIterator(CharSequence name) {
        return valueStringIterator(name);
    }

    /**
     * Checks to see if there is a header with the specified name
     *
     * @param name The name of the header to search for
     * @return True if at least one header is found
     */
    public boolean contains(CharSequence name) {
        return contains(name.toString());
    }

    /**
     * Checks if no header exists.
     */
    public abstract boolean isEmpty();

    /**
     * Returns the number of headers in this object.
     */
    public abstract int size();

    /**
     * Returns a new {@link Set} that contains the names of all headers in this object.  Note that modifying the
     * returned {@link Set} will not affect the state of this object.  If you intend to enumerate over the header
     * entries only, use {@link #iterator()} instead, which has much less overhead.
     */
    public abstract Set<String> names();

    /**
     * @see #add(CharSequence, Object)
     */
    public abstract HttpHeaders add(String name, Object value);

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
     *
     * @return {@code this}
     */
    public HttpHeaders add(CharSequence name, Object value) {
        return add(name.toString(), value);
    }

    /**
     * @see #add(CharSequence, Iterable)
     */
    public abstract HttpHeaders add(String name, Iterable<?> values);

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
     * @param name The name of the headers being set
     * @param values The values of the headers being set
     * @return {@code this}
     */
    public HttpHeaders add(CharSequence name, Iterable<?> values) {
        return add(name.toString(), values);
    }

    /**
     * Adds all header entries of the specified {@code headers}.
     *
     * @return {@code this}
     */
    public HttpHeaders add(HttpHeaders headers) {
        requireNonNull(headers, "headers");
        for (Map.Entry<String, String> e: headers) {
            add(e.getKey(), e.getValue());
        }
        return this;
    }

    /**
     * Add the {@code name} to {@code value}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    public abstract HttpHeaders addInt(CharSequence name, int value);

    /**
     * Add the {@code name} to {@code value}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    public abstract HttpHeaders addShort(CharSequence name, short value);

    /**
     * @see #set(CharSequence, Object)
     */
    public abstract HttpHeaders set(String name, Object value);

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
     * @return {@code this}
     */
    public HttpHeaders set(CharSequence name, Object value) {
        return set(name.toString(), value);
    }

    /**
     * @see #set(CharSequence, Iterable)
     */
    public abstract HttpHeaders set(String name, Iterable<?> values);

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
     * @param name The name of the headers being set
     * @param values The values of the headers being set
     * @return {@code this}
     */
    public HttpHeaders set(CharSequence name, Iterable<?> values) {
        return set(name.toString(), values);
    }

    /**
     * Cleans the current header entries and copies all header entries of the specified {@code headers}.
     *
     * @return {@code this}
     */
    public HttpHeaders set(HttpHeaders headers) {
        requireNonNull(headers, "headers");

        clear();

        if (headers.isEmpty()) {
            return this;
        }

        for (Entry<String, String> entry : headers) {
            add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Retains all current headers but calls {@link #set(String, Object)} for each entry in {@code headers}
     *
     * @param headers The headers used to {@link #set(String, Object)} values in this instance
     * @return {@code this}
     */
    public HttpHeaders setAll(HttpHeaders headers) {
        requireNonNull(headers, "headers");

        if (headers.isEmpty()) {
            return this;
        }

        for (Entry<String, String> entry : headers) {
            set(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    public abstract HttpHeaders setInt(CharSequence name, int value);

    /**
     * Set the {@code name} to {@code value}. This will remove all previous values associated with {@code name}.
     * @param name The name to modify
     * @param value The value
     * @return {@code this}
     */
    public abstract HttpHeaders setShort(CharSequence name, short value);

    /**
     * @see #remove(CharSequence)
     */
    public abstract HttpHeaders remove(String name);

    /**
     * Removes the header with the specified name.
     *
     * @param name The name of the header to remove
     * @return {@code this}
     */
    public HttpHeaders remove(CharSequence name) {
        return remove(name.toString());
    }

    /**
     * Removes all headers from this {@link HttpMessage}.
     *
     * @return {@code this}
     */
    public abstract HttpHeaders clear();

    /**
     * @see #contains(CharSequence, CharSequence, boolean)
     */
    public boolean contains(String name, String value, boolean ignoreCase) {
        Iterator<String> valueIterator = valueStringIterator(name);
        if (ignoreCase) {
            while (valueIterator.hasNext()) {
                if (valueIterator.next().equalsIgnoreCase(value)) {
                    return true;
                }
            }
        } else {
            while (valueIterator.hasNext()) {
                if (valueIterator.next().equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists, {@code false} otherwise.
     * This also handles multiple values that are separated with a {@code ,}.
     * <p>
     * If {@code ignoreCase} is {@code true} then a case insensitive compare is done on the value.
     * @param name the name of the header to find
     * @param value the value of the header to find
     * @param ignoreCase {@code true} then a case insensitive compare is run to compare values.
     * otherwise a case sensitive compare is run to compare values.
     */
    public boolean containsValue(CharSequence name, CharSequence value, boolean ignoreCase) {
        Iterator<? extends CharSequence> itr = valueCharSequenceIterator(name);
        while (itr.hasNext()) {
            if (containsCommaSeparatedTrimmed(itr.next(), value, ignoreCase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCommaSeparatedTrimmed(CharSequence rawNext, CharSequence expected,
                                                         boolean ignoreCase) {
        int begin = 0;
        int end;
        if (ignoreCase) {
            if ((end = AsciiString.indexOf(rawNext, ',', begin)) == -1) {
                if (contentEqualsIgnoreCase(trim(rawNext), expected)) {
                    return true;
                }
            } else {
                do {
                    if (contentEqualsIgnoreCase(trim(rawNext.subSequence(begin, end)), expected)) {
                        return true;
                    }
                    begin = end + 1;
                } while ((end = AsciiString.indexOf(rawNext, ',', begin)) != -1);

                if (begin < rawNext.length()) {
                    if (contentEqualsIgnoreCase(trim(rawNext.subSequence(begin, rawNext.length())), expected)) {
                        return true;
                    }
                }
            }
        } else {
            if ((end = AsciiString.indexOf(rawNext, ',', begin)) == -1) {
                if (contentEquals(trim(rawNext), expected)) {
                    return true;
                }
            } else {
                do {
                    if (contentEquals(trim(rawNext.subSequence(begin, end)), expected)) {
                        return true;
                    }
                    begin = end + 1;
                } while ((end = AsciiString.indexOf(rawNext, ',', begin)) != -1);

                if (begin < rawNext.length()) {
                    if (contentEquals(trim(rawNext.subSequence(begin, rawNext.length())), expected)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * {@link Headers#get(Object)} and convert the result to a {@link String}.
     * @param name the name of the header to retrieve
     * @return the first header value if the header is found. {@code null} if there's no such header.
     */
    public final String getAsString(CharSequence name) {
        return get(name);
    }

    /**
     * {@link Headers#getAll(Object)} and convert each element of {@link List} to a {@link String}.
     * @param name the name of the header to retrieve
     * @return a {@link List} of header values or an empty {@link List} if no values are found.
     */
    public final List<String> getAllAsString(CharSequence name) {
        return getAll(name);
    }

    /**
     * {@link Iterator} that converts each {@link Entry}'s key and value to a {@link String}.
     */
    public final Iterator<Entry<String, String>> iteratorAsString() {
        return iterator();
    }

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists, {@code false} otherwise.
     * <p>
     * If {@code ignoreCase} is {@code true} then a case insensitive compare is done on the value.
     * @param name the name of the header to find
     * @param value the value of the header to find
     * @param ignoreCase {@code true} then a case insensitive compare is run to compare values.
     * otherwise a case sensitive compare is run to compare values.
     */
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return contains(name.toString(), value.toString(), ignoreCase);
    }

    @Override
    public String toString() {
        return HeadersUtils.toString(getClass(), iteratorCharSequence(), size());
    }

    /**
     * Returns a deep copy of the passed in {@link HttpHeaders}.
     */
    public HttpHeaders copy() {
        return new DefaultHttpHeaders().set(this);
    }
}
