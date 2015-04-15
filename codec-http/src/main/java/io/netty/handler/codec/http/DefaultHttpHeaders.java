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

import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.DefaultTextHeaders;
import io.netty.handler.codec.TextHeaders;
import io.netty.util.AsciiString;

import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

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

    public DefaultHttpHeaders() {
        this(true);
    }

    public DefaultHttpHeaders(boolean validate) {
        this(validate, false);
    }

    protected DefaultHttpHeaders(boolean validate, boolean singleHeaderFields) {
        this(true, validate ? HeaderNameValidator.INSTANCE : NO_NAME_VALIDATOR, singleHeaderFields);
    }

    protected DefaultHttpHeaders(boolean validate,
                                 DefaultHeaders.NameValidator<CharSequence> nameValidator,
                                 boolean singleHeaderFields) {
        super(new TreeMap<CharSequence, Object>(AsciiString.CHARSEQUENCE_CASE_INSENSITIVE_ORDER),
              nameValidator,
              validate ? HeaderValueConverterAndValidator.INSTANCE : HeaderValueConverter.INSTANCE,
              singleHeaderFields);
    }

    @Override
    public HttpHeaders add(CharSequence name, CharSequence value) {
        super.add(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, CharSequence... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders addObject(CharSequence name, Object value) {
        super.addObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders addObject(CharSequence name, Iterable<?> values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders addObject(CharSequence name, Object... values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders addBoolean(CharSequence name, boolean value) {
        super.addBoolean(name, value);
        return this;
    }

    @Override
    public HttpHeaders addChar(CharSequence name, char value) {
        super.addChar(name, value);
        return this;
    }

    @Override
    public HttpHeaders addByte(CharSequence name, byte value) {
        super.addByte(name, value);
        return this;
    }

    @Override
    public HttpHeaders addShort(CharSequence name, short value) {
        super.addShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders addInt(CharSequence name, int value) {
        super.addInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders addLong(CharSequence name, long value) {
        super.addLong(name, value);
        return this;
    }

    @Override
    public HttpHeaders addFloat(CharSequence name, float value) {
        super.addFloat(name, value);
        return this;
    }

    @Override
    public HttpHeaders addDouble(CharSequence name, double value) {
        super.addDouble(name, value);
        return this;
    }

    @Override
    public HttpHeaders addTimeMillis(CharSequence name, long value) {
        super.addTimeMillis(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(TextHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, CharSequence value) {
        super.set(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, CharSequence... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders setObject(CharSequence name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders setObject(CharSequence name, Iterable<?> values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders setObject(CharSequence name, Object... values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders setBoolean(CharSequence name, boolean value) {
        super.setBoolean(name, value);
        return this;
    }

    @Override
    public HttpHeaders setChar(CharSequence name, char value) {
        super.setChar(name, value);
        return this;
    }

    @Override
    public HttpHeaders setByte(CharSequence name, byte value) {
        super.setByte(name, value);
        return this;
    }

    @Override
    public HttpHeaders setShort(CharSequence name, short value) {
        super.setShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders setInt(CharSequence name, int value) {
        super.setInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders setLong(CharSequence name, long value) {
        super.setLong(name, value);
        return this;
    }

    @Override
    public HttpHeaders setFloat(CharSequence name, float value) {
        super.setFloat(name, value);
        return this;
    }

    @Override
    public HttpHeaders setDouble(CharSequence name, double value) {
        super.setDouble(name, value);
        return this;
    }

    @Override
    public HttpHeaders setTimeMillis(CharSequence name, long value) {
        super.setTimeMillis(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(TextHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public HttpHeaders setAll(TextHeaders headers) {
        super.setAll(headers);
        return this;
    }

    @Override
    public HttpHeaders clear() {
        super.clear();
        return this;
    }

    @Override
    public int hashCode() {
        return size();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof HttpHeaders)) {
            return false;
        }
        HttpHeaders headers = (HttpHeaders) other;
        return DefaultHeaders.comparatorEquals(this, headers, AsciiString.CHARSEQUENCE_CASE_SENSITIVE_ORDER);
    }

    static final class HeaderNameValidator implements DefaultHeaders.NameValidator<CharSequence> {

        public static final HeaderNameValidator INSTANCE = new HeaderNameValidator();

        private HeaderNameValidator() {
        }

        @Override
        public void validate(CharSequence name) {
            // Go through each character in the name
            for (int index = 0; index < name.length(); index++) {
                char character = name.charAt(index);

                // Check to see if the character is not an ASCII character
                if (character > 127) {
                    throw new IllegalArgumentException("a header name cannot contain non-ASCII characters: " + name);
                }

                // Check for prohibited characters.
                if ((character & HIGHEST_INVALID_NAME_CHAR_MASK) == 0 && LOOKUP_TABLE[character] != 0) {
                    throw new IllegalArgumentException(
                            "a header name cannot contain the following prohibited characters: =,;: \\t\\r\\n\\v\\f: " +
                            name);
                }
            }
        }
    }

    private static class HeaderValueConverter extends CharSequenceConverter {

        public static final HeaderValueConverter INSTANCE = new HeaderValueConverter();

        @Override
        public CharSequence convertObject(Object value) {
            checkNotNull(value, "value");
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

    private static final class HeaderValueConverterAndValidator extends HeaderValueConverter {

        public static final HeaderValueConverterAndValidator INSTANCE = new HeaderValueConverterAndValidator();

        @Override
        public CharSequence convertObject(Object value) {
            CharSequence seq = super.convertObject(value);
            int state = 0;
            // Start looping through each of the character
            for (int index = 0; index < seq.length(); index++) {
                state = validateValueChar(seq, state, seq.charAt(index));
            }

            if (state != 0) {
                throw new IllegalArgumentException("a header value must not end with '\\r' or '\\n':" + seq);
            }
            return seq;
        }

        private static int validateValueChar(CharSequence seq, int state, char character) {
            /*
             * State:
             * 0: Previous character was neither CR nor LF
             * 1: The previous character was CR
             * 2: The previous character was LF
             */
            if ((character & HIGHEST_INVALID_VALUE_CHAR_MASK) == 0) {
                // Check the absolutely prohibited characters.
                switch (character) {
                case 0x0: // NULL
                    throw new IllegalArgumentException("a header value contains a prohibited character '\0': " + seq);
                case 0x0b: // Vertical tab
                    throw new IllegalArgumentException("a header value contains a prohibited character '\\v': " + seq);
                case '\f':
                    throw new IllegalArgumentException("a header value contains a prohibited character '\\f': " + seq);
                }
            }

            // Check the CRLF (HT | SP) pattern
            switch (state) {
                case 0:
                    switch (character) {
                        case '\r':
                            return 1;
                        case '\n':
                            return 2;
                    }
                    break;
                case 1:
                    switch (character) {
                        case '\n':
                            return 2;
                        default:
                            throw new IllegalArgumentException("only '\\n' is allowed after '\\r': " + seq);
                    }
                case 2:
                    switch (character) {
                        case '\t':
                        case ' ':
                            return 0;
                        default:
                            throw new IllegalArgumentException("only ' ' and '\\t' are allowed after '\\n': " + seq);
                    }
            }
            return state;
        }
    }
}
