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

import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.DefaultTextHeaders;
import io.netty.handler.codec.TextHeaderProcessor;
import io.netty.handler.codec.TextHeaders;

import java.util.Calendar;
import java.util.Date;

public class DefaultHttpHeaders extends DefaultTextHeaders implements HttpHeaders {

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

    protected final boolean validate;

    public DefaultHttpHeaders() {
        this(true);
    }

    public DefaultHttpHeaders(boolean validate) {
        this.validate = validate;
    }

    @Override
    protected CharSequence convertName(CharSequence name) {
        name = super.convertName(name);
        if (validate) {
            if (name instanceof AsciiString) {
                validateName((AsciiString) name);
            } else {
                validateName(name);
            }
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

        if (validate) {
            if (value instanceof AsciiString) {
                validateValue((AsciiString) seq);
            } else {
                validateValue(seq);
            }
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

    @Override
    public HttpHeaders add(CharSequence name, Object value) {
        super.add(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<?> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Object... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(TextHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Object value) {
        super.set(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Object... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<?> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(TextHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public HttpHeaders clear() {
        super.clear();
        return this;
    }

    @Override
    public HttpHeaders forEachEntry(TextHeaderProcessor processor) {
        super.forEachEntry(processor);
        return this;
    }
}
