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
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.DefaultTextHeaders;
import io.netty.handler.codec.TextHeaders;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultHttpHeaders extends HttpHeaders {

    private final TextHeaders headers;

    public DefaultHttpHeaders() {
        this(true);
    }

    public DefaultHttpHeaders(boolean validate) {
        headers = validate? new ValidatingTextHeaders() : new NonValidatingTextHeaders();
    }

    DefaultHttpHeaders(TextHeaders headers) {
        this.headers = headers;
    }

    @Override
    public HttpHeaders add(HttpHeaders headers) {
        if (headers instanceof DefaultHttpHeaders) {
            this.headers.add(((DefaultHttpHeaders) headers).headers);
            return this;
        } else {
            return super.add(headers);
        }
    }

    @Override
    public HttpHeaders set(HttpHeaders headers) {
        if (headers instanceof DefaultHttpHeaders) {
            this.headers.set(((DefaultHttpHeaders) headers).headers);
            return this;
        } else {
            return super.set(headers);
        }
    }

    @Override
    public HttpHeaders add(String name, Object value) {
        headers.add(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Object value) {
        headers.add(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        headers.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<?> values) {
        headers.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders remove(String name) {
        headers.remove(name);
        return this;
    }

    @Override
    public HttpHeaders remove(CharSequence name) {
        headers.remove(name);
        return this;
    }

    @Override
    public HttpHeaders set(String name, Object value) {
        headers.set(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Object value) {
        headers.set(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(String name, Iterable<?> values) {
        headers.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<?> values) {
        headers.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders clear() {
        headers.clear();
        return this;
    }

    @Override
    public String get(String name) {
        return headers.get(name);
    }

    @Override
    public String get(CharSequence name) {
        return headers.get(name);
    }

    @Override
    public List<String> getAll(String name) {
        return headers.getAll(name);
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return headers.getAll(name);
    }

    @Override
    public List<Map.Entry<String, String>> entries() {
        return headers.entries();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return headers.iterator();
    }

    @Override
    public boolean contains(String name) {
        return headers.contains(name);
    }

    @Override
    public boolean contains(CharSequence name) {
        return headers.contains(name);
    }

    @Override
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    @Override
    public boolean contains(String name, String value, boolean ignoreCase) {
        return headers.contains(name, value, ignoreCase);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return headers.contains(name, value, ignoreCase);
    }

    @Override
    public Set<String> names() {
        return headers.names();
    }

    void encode(ByteBuf buf) {
        headers.forEachEntry(new HttpHeadersEncoder(buf));
    }

    static class NonValidatingTextHeaders extends DefaultTextHeaders {
        @Override
        protected CharSequence convertValue(Object value) {
            if (value == null) {
                throw new NullPointerException("value");
            }

            CharSequence seq;
            if (value instanceof CharSequence) {
                seq = (CharSequence) value;
            } else if (value instanceof Number) {
                seq = value.toString();
            } else if (value instanceof Date) {
                seq = HttpHeaderDateFormat.get().format((Date) value);
            } else if (value instanceof Calendar) {
                seq = HttpHeaderDateFormat.get().format(((Calendar) value).getTime());
            } else {
                seq = value.toString();
            }

            return seq;
        }
    }

    static class ValidatingTextHeaders extends NonValidatingTextHeaders {
        private static final int HIGHEST_INVALID_NAME_CHAR_MASK = ~63;
        private static final int HIGHEST_INVALID_VALUE_CHAR_MASK = ~15;

        /**
         * A look-up table used for checking if a character in a header name is prohibited.
         */
        private static final byte[] LOOKUP_TABLE = new byte[~HIGHEST_INVALID_NAME_CHAR_MASK + 1];

        static {
            LOOKUP_TABLE['\t'] = -1;
            LOOKUP_TABLE['\n'] = -1;
            LOOKUP_TABLE[0x0b] = -1;
            LOOKUP_TABLE['\f'] = -1;
            LOOKUP_TABLE[' '] = -1;
            LOOKUP_TABLE[','] = -1;
            LOOKUP_TABLE[':'] = -1;
            LOOKUP_TABLE[';'] = -1;
            LOOKUP_TABLE['='] = -1;
        }

        @Override
        protected CharSequence convertName(CharSequence name) {
            name = super.convertName(name);
            if (name instanceof AsciiString) {
                validateName((AsciiString) name);
            } else {
                validateName(name);
            }

            return name;
        }

        private static void validateName(AsciiString name) {
            // Go through each characters in the name
            final int start = name.arrayOffset();
            final int end = start + name.length();
            final byte[] array = name.array();
            for (int index = start; index < end; index ++) {
                byte b = array[index];

                // Check to see if the character is not an ASCII character
                if (b < 0) {
                    throw new IllegalArgumentException(
                            "a header name cannot contain non-ASCII characters: " + name);
                }

                // Check for prohibited characters.
                validateNameChar(name, b);
            }
        }

        private static void validateName(CharSequence name) {
            // Go through each characters in the name
            for (int index = 0; index < name.length(); index ++) {
                char character = name.charAt(index);

                // Check to see if the character is not an ASCII character
                if (character > 127) {
                    throw new IllegalArgumentException(
                            "a header name cannot contain non-ASCII characters: " + name);
                }

                // Check for prohibited characters.
                validateNameChar(name, character);
            }
        }

        private static void validateNameChar(CharSequence name, int character) {
            if ((character & HIGHEST_INVALID_NAME_CHAR_MASK) == 0 && LOOKUP_TABLE[character] != 0) {
                throw new IllegalArgumentException(
                        "a header name cannot contain the following prohibited characters: " +
                                "=,;: \\t\\r\\n\\v\\f: " + name);
            }
        }

        @Override
        protected CharSequence convertValue(Object value) {
            CharSequence seq = super.convertValue(value);
            if (value instanceof AsciiString) {
                validateValue((AsciiString) seq);
            } else {
                validateValue(seq);
            }

            return seq;
        }

        private static void validateValue(AsciiString seq) {
            int state = 0;
            // Start looping through each of the character
            final int start = seq.arrayOffset();
            final int end = start + seq.length();
            final byte[] array = seq.array();
            for (int index = start; index < end; index ++) {
                state = validateValueChar(seq, state, (char) (array[index] & 0xFF));
            }

            if (state != 0) {
                throw new IllegalArgumentException(
                        "a header value must not end with '\\r' or '\\n':" + seq);
            }
        }

        private static void validateValue(CharSequence seq) {
            int state = 0;
            // Start looping through each of the character
            for (int index = 0; index < seq.length(); index ++) {
                state = validateValueChar(seq, state, seq.charAt(index));
            }

            if (state != 0) {
                throw new IllegalArgumentException(
                        "a header value must not end with '\\r' or '\\n':" + seq);
            }
        }

        private static int validateValueChar(CharSequence seq, int state, char character) {
            /*
             * State:
             *
             * 0: Previous character was neither CR nor LF
             * 1: The previous character was CR
             * 2: The previous character was LF
             */
            if ((character & HIGHEST_INVALID_VALUE_CHAR_MASK) == 0) {
                // Check the absolutely prohibited characters.
                switch (character) {
                    case 0x0b: // Vertical tab
                        throw new IllegalArgumentException(
                                "a header value contains a prohibited character '\\v': " + seq);
                    case '\f':
                        throw new IllegalArgumentException(
                                "a header value contains a prohibited character '\\f': " + seq);
                }
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
                                    "only '\\n' is allowed after '\\r': " + seq);
                    }
                    break;
                case 2:
                    switch (character) {
                        case '\t': case ' ':
                            state = 0;
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "only ' ' and '\\t' are allowed after '\\n': " + seq);
                    }
            }
            return state;
        }
    }
}
