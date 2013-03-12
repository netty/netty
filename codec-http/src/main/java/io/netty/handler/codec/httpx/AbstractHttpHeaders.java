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
package io.netty.handler.codec.httpx;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * Represent the start line and headers of an HTTP message.
 */
public abstract class AbstractHttpHeaders implements HttpHeaders {

    private final HttpMessageType type;

    protected AbstractHttpHeaders(HttpMessageType type) {
        if (type == null) {
            throw new NullPointerException("type");
        }
        this.type = type;
    }

    // Properties related with a start line.

    @Override
    public HttpMessageType getType() {
        return type;
    }

    @Override
    public boolean isRequest() {
        return type == HttpMessageType.REQUEST;
    }

    @Override
    public boolean isResponse() {
        return type == HttpMessageType.RESPONSE;
    }

    @Override
    public String getHost() {
        return get(Names.HOST);
    }

    @Override
    public String getHost(String defaultValue) {
        return get(Names.HOST, defaultValue);
    }

    @Override
    public HttpHeaders setContentLength(long length) {
        return set(Names.CONTENT_LENGTH, length);
    }

    @Override
    public HttpHeaders setHost(String value) {
        return set(Names.HOST, value);
    }

    // Properties related with message flow.

    @Override
    public boolean isKeepAlive() {
        String connection = get(Names.CONNECTION);
        if (Values.CLOSE.equalsIgnoreCase(connection)) {
            return false;
        }

        if (getVersion().isKeepAliveDefault()) {
            return !Values.CLOSE.equalsIgnoreCase(connection);
        } else {
            return Values.KEEP_ALIVE.equalsIgnoreCase(connection);
        }
    }

    @Override
    public HttpHeaders setKeepAlive(boolean keepAlive) {
        if (getVersion().isKeepAliveDefault()) {
            if (keepAlive) {
                remove(Names.CONNECTION);
            } else {
                set(Names.CONNECTION, Values.CLOSE);
            }
        } else {
            if (keepAlive) {
                set(Names.CONNECTION, Values.KEEP_ALIVE);
            } else {
                remove(Names.CONNECTION);
            }
        }
        return this;
    }

    @Override
    public boolean is100ContinueExpected() {
        // Expect: 100-continue is for requests only.
        if (getType() != HttpMessageType.REQUEST) {
            return false;
        }

        // It works only on HTTP/1.1 or later.
        if (getVersion().compareTo(HttpVersion.HTTP_1_1) < 0) {
            return false;
        }

        // In most cases, there will be one or zero 'Expect' header.
        String value = get(Names.EXPECT);
        if (value == null) {
            return false;
        }
        if (Values.CONTINUE.equalsIgnoreCase(value)) {
            return true;
        }

        // Multiple 'Expect' headers.  Search through them.
        for (String v: getAll(Names.EXPECT)) {
            if (Values.CONTINUE.equalsIgnoreCase(v)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public HttpHeaders set100ContinueExpected(boolean expected) {
        if (expected) {
            return set(Names.EXPECT, Values.CONTINUE);
        } else {
            return remove(Names.EXPECT, Values.CONTINUE);
        }
    }

    // Validation methods

    @Override
    public HttpHeaders validateName(String name) {
        //Check to see if the name is null
        if (name == null) {
            throw new NullPointerException("name");
        }
        //Go through each of the characters in the name
        for (int index = 0; index < name.length(); index ++) {
            //Actually get the character
            char character = name.charAt(index);

            //Check to see if the character is not an ASCII character
            if (character > 127) {
                throw new IllegalArgumentException(
                        "name cannot contain non-ASCII characters: " + name);
            }

            //Check for prohibited characters.
            switch (character) {
                case '\t': case '\n': case 0x0b: case '\f': case '\r':
                case ' ':  case ',':  case ':':  case ';':  case '=':
                    throw new IllegalArgumentException(
                            "name cannot contain the following prohibited characters: " +
                                    "=,;: \\t\\r\\n\\v\\f: " + name);
            }
        }

        return this;
    }

    @Override
    public HttpHeaders validateValue(String value) {
        //Check to see if the value is null
        if (value == null) {
            throw new NullPointerException("value");
        }

        /*
         * Set up the state of the validation
         *
         * States are as follows:
         *
         * 0: Previous character was neither CR nor LF
         * 1: The previous character was CR
         * 2: The previous character was LF
         */
        int state = 0;

        //Start looping through each of the character

        for (int index = 0; index < value.length(); index ++) {
            char character = value.charAt(index);

            //Check the absolutely prohibited characters.
            switch (character) {
                case 0x0b: // Vertical tab
                    throw new IllegalArgumentException(
                            "value contains a prohibited character '\\v': " + value);
                case '\f':
                    throw new IllegalArgumentException(
                            "value contains a prohibited character '\\f': " + value);
            }

            // Check the CRLF (HT | SP) pattern
            switch (state) {
                case 0:
                    switch (character) {
                        case '\r':
                            state = 1;
                            break;
                        case '\n':
                            state = 2;
                            break;
                    }
                    break;
                case 1:
                    switch (character) {
                        case '\n':
                            state = 2;
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Only '\\n' is allowed after '\\r': " + value);
                    }
                    break;
                case 2:
                    switch (character) {
                        case '\t': case ' ':
                            state = 0;
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Only ' ' and '\\t' are allowed after '\\n': " + value);
                    }
            }
        }

        if (state != 0) {
            throw new IllegalArgumentException(
                    "value must not end with '\\r' or '\\n':" + value);
        }

        return this;
    }

    @Override
    public boolean isTransferEncodingChunked() {
        List<String> transferEncodingHeaders = getAll(Names.TRANSFER_ENCODING);
        if (transferEncodingHeaders.isEmpty()) {
            return false;
        }

        for (String value: transferEncodingHeaders) {
            if (value.equalsIgnoreCase(Values.CHUNKED)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public HttpHeaders setTransferEncodingChunked(boolean chunked) {
        if (chunked) {
            add(Names.TRANSFER_ENCODING, Values.CHUNKED);
            remove(Names.CONTENT_LENGTH);
        } else {
            remove(Names.TRANSFER_ENCODING, Values.CHUNKED);
        }

        return this;
    }

    // Generic property accessors

    @Override
    public String get(String name, Object defaultValue) {
        String value = get(name);
        if (value == null) {
            return toString(defaultValue);
        }
        return value;
    }

    @Override
    public int getInt(String name) {
        String value = get(name);
        if (value == null) {
            throw new NumberFormatException("header not found: " + name);
        }
        return Integer.parseInt(value);
    }

    @Override
    public int getInt(String name, int defaultValue) {
        String value = get(name);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Date getDate(String name) throws ParseException {
        String value = get(name);
        if (value == null) {
            throw new ParseException("header not found: " + name, 0);
        }
        return new HttpHeaderDateFormat().parse(value);
    }

    @Override
    public Date getDate(String name, Date defaultValue) {
        final String value = get(name);
        if (value == null) {
            return defaultValue;
        }

        try {
            return new HttpHeaderDateFormat().parse(value);
        } catch (ParseException e) {
            return defaultValue;
        }
    }

    @Override
    public long getContentLength() {
        String value = get(Names.CONTENT_LENGTH);
        if (value != null) {
            return Long.parseLong(value);
        }

        // We know the content length if it's a Web Socket message even if
        // Content-Length header is missing.
        long webSocketContentLength = getWebSocketContentLength();
        if (webSocketContentLength >= 0) {
            return webSocketContentLength;
        }

        // Otherwise we don't.
        throw new NumberFormatException("header not found: " + Names.CONTENT_LENGTH);
    }

    @Override
    public long getContentLength(long defaultValue) {
        String contentLength = get(Names.CONTENT_LENGTH);
        if (contentLength != null) {
            try {
                return Long.parseLong(contentLength);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        // We know the content length if it's a Web Socket message even if
        // Content-Length header is missing.
        long webSocketContentLength = getWebSocketContentLength();
        if (webSocketContentLength >= 0) {
            return webSocketContentLength;
        }

        // Otherwise we don't.
        return defaultValue;
    }

    /**
     * Returns the content length of the specified web socket message.  If the
     * specified message is not a web socket message, {@code -1} is returned.
     */
    private int getWebSocketContentLength() {
        // WebSockset messages have constant content-lengths.
        switch (getType()) {
            case REQUEST:
                if (HttpMethod.GET.equals(getMethod()) &&
                    contains(Names.SEC_WEBSOCKET_KEY1) && contains(Names.SEC_WEBSOCKET_KEY2)) {
                    return 8;
                }
                break;
            case RESPONSE:
                if (getStatus().code() == 101 &&
                    contains(Names.SEC_WEBSOCKET_ORIGIN) && contains(Names.SEC_WEBSOCKET_LOCATION)) {
                    return 16;
                }
                break;
        }

        // Not a web socket message
        return -1;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return entries().iterator();
    }

    @Override
    public boolean contains(String name) {
        return get(name) != null;
    }

    @Override
    public final HttpHeaders add(String name, Object value) {
        validateName(name);
        String strValue = toString(value);
        validateValue(strValue);
        add0(name, strValue);
        return this;
    }

    protected abstract void add0(String name, String value);

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        if (values == null) {
            throw new NullPointerException("values");
        }

        validateName(name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            String strValue = toString(v);
            validateValue(strValue);
            add0(name, strValue);
        }

        return this;
    }

    @Override
    public HttpHeaders add(HttpHeaders headers) {
        if (headers == null) {
            throw new NullPointerException("headers");
        }
        for (Map.Entry<String, String> e: headers) {
            add0(e.getKey(), e.getValue());
        }
        return this;
    }

    @Override
    public final HttpHeaders set(String name, Object value) {
        validateName(name);
        String strValue = toString(value);
        validateValue(strValue);
        set0(name, strValue);
        return this;
    }

    protected abstract void set0(String name, String value);

    @Override
    public HttpHeaders set(String name, Iterable<?> values) {
        if (values == null) {
            throw new NullPointerException("values");
        }

        validateName(name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            String strValue = toString(v);
            validateValue(strValue);
            set0(name, strValue);
        }

        return this;
    }

    @Override
    public HttpHeaders set(HttpHeaders headers) {
        if (headers == null) {
            throw new NullPointerException("headers");
        }
        clear();
        for (Map.Entry<String, String> e: headers) {
            add0(e.getKey(), e.getValue());
        }
        return this;
    }

    @Override
    public final HttpHeaders remove(String name, Object value) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (value == null) {
            throw new NullPointerException("value");
        }

        String strValue = toString(value);
        remove0(name, strValue);
        return this;
    }

    protected abstract void remove0(String name, String value);

    @Override
    public String toString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Date) {
            return new HttpHeaderDateFormat().format((Date) value);
        }
        if (value instanceof Calendar) {
            return new HttpHeaderDateFormat().format(((Calendar) value).getTime());
        }
        return value.toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(512);
        buf.append(getClass().getSimpleName());
        buf.append("(type: ");
        buf.append(getType());
        switch (getType()) {
            case REQUEST:
                buf.append(", method: ");
                buf.append(getMethod());
                buf.append(", path: ");
                buf.append(getUri());
                buf.append(", version: ");
                buf.append(getVersion());
                break;
            case RESPONSE:
                buf.append(", version: ");
                buf.append(getVersion());
                buf.append(", status: ");
                buf.append(getStatus());
                break;
        }

        buf.append(", entries: ");
        buf.append(entries());
        buf.append(')');

        return buf.toString();
    }
}
