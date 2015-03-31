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

import io.netty.handler.codec.DefaultTextHeaders;
import io.netty.handler.codec.TextHeaders;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.PlatformDependent;

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

    private static final class HttpHeadersValidationConverter extends DefaultTextValueTypeConverter {
        private final boolean validate;

        HttpHeadersValidationConverter(boolean validate) {
            this.validate = validate;
        }

        @Override
        public CharSequence convertObject(Object value) {
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

        private static final class ValidateValueProcessor implements ByteProcessor {
            private final CharSequence seq;
            private int state;

            public ValidateValueProcessor(CharSequence seq) {
                this.seq = seq;
            }

            @Override
            public boolean process(byte value) throws Exception {
                state = validateValueChar(state, (char) value, seq);
                return true;
            }

            public int state() {
                return state;
            }
        }

        private static void validateValue(AsciiString seq) {
            ValidateValueProcessor processor = new ValidateValueProcessor(seq);
            try {
                seq.forEachByte(processor);
            } catch (Throwable t) {
                PlatformDependent.throwException(t);
            }

            if (processor.state() != 0) {
                throw new IllegalArgumentException("a header value must not end with '\\r' or '\\n':" + seq);
            }
        }

        private static void validateValue(CharSequence seq) {
            int state = 0;
            // Start looping through each of the character
            for (int index = 0; index < seq.length(); index++) {
                state = validateValueChar(state, seq.charAt(index), seq);
            }

            if (state != 0) {
                throw new IllegalArgumentException("a header value must not end with '\\r' or '\\n':" + seq);
            }
        }

        private static int validateValueChar(int state, char c, CharSequence seq) {
            /*
             * State:
             * 0: Previous character was neither CR nor LF
             * 1: The previous character was CR
             * 2: The previous character was LF
             */
            if ((c & HIGHEST_INVALID_VALUE_CHAR_MASK) == 0) {
                // Check the absolutely prohibited characters.
                switch (c) {
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
                switch (c) {
                case '\r':
                    state = 1;
                    break;
                case '\n':
                    state = 2;
                    break;
                }
                break;
            case 1:
                switch (c) {
                case '\n':
                    state = 2;
                    break;
                default:
                    throw new IllegalArgumentException("only '\\n' is allowed after '\\r': " + seq);
                }
                break;
            case 2:
                switch (c) {
                case '\t':
                case ' ':
                    state = 0;
                    break;
                default:
                    throw new IllegalArgumentException("only ' ' and '\\t' are allowed after '\\n': " + seq);
                }
            }
            return state;
        }
    }

    static class HttpHeadersNameConverter implements NameConverter<CharSequence> {
        protected final boolean validate;

        private static final class ValidateNameProcessor implements ByteProcessor {
            private final CharSequence seq;

            public ValidateNameProcessor(CharSequence seq) {
                this.seq = seq;
            }

            @Override
            public boolean process(byte value) throws Exception {
                // Check to see if the character is not an ASCII character.
                if (value < 0) {
                    throw new IllegalArgumentException("a header name cannot contain non-ASCII character: " + seq);
                }
                validateNameChar(value, seq);
                return true;
            }
        }

        HttpHeadersNameConverter(boolean validate) {
            this.validate = validate;
        }

        @Override
        public CharSequence convertName(CharSequence name) {
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
            try {
                name.forEachByte(new ValidateNameProcessor(name));
            } catch (Throwable t) {
                PlatformDependent.throwException(t);
            }
        }

        private static void validateName(CharSequence name) {
            // Go through each characters in the name.
            for (int index = 0; index < name.length(); index++) {
                char c = name.charAt(index);

                // Check to see if the character is not an ASCII character.
                if (c > 127) {
                    throw new IllegalArgumentException("a header name cannot contain non-ASCII characters: " + name);
                }

                // Check for prohibited characters.
                validateNameChar(c, name);
            }
        }

        private static void validateNameChar(int character, CharSequence seq) {
            if ((character & HIGHEST_INVALID_NAME_CHAR_MASK) == 0 && LOOKUP_TABLE[character] != 0) {
                throw new IllegalArgumentException(
                        "a header name cannot contain the following prohibited characters: =,;: \\t\\r\\n\\v\\f: " +
                                seq);
            }
        }
    }

    private static final HttpHeadersValidationConverter
        VALIDATE_OBJECT_CONVERTER = new HttpHeadersValidationConverter(true);
    private static final HttpHeadersValidationConverter
        NO_VALIDATE_OBJECT_CONVERTER = new HttpHeadersValidationConverter(false);
    private static final HttpHeadersNameConverter VALIDATE_NAME_CONVERTER = new HttpHeadersNameConverter(true);
    private static final HttpHeadersNameConverter NO_VALIDATE_NAME_CONVERTER = new HttpHeadersNameConverter(false);

    public DefaultHttpHeaders() {
        this(true);
    }

    public DefaultHttpHeaders(boolean validate) {
        this(true, validate? VALIDATE_NAME_CONVERTER : NO_VALIDATE_NAME_CONVERTER, false);
    }

    protected DefaultHttpHeaders(boolean validate, boolean singleHeaderFields) {
        this(true, validate? VALIDATE_NAME_CONVERTER : NO_VALIDATE_NAME_CONVERTER, singleHeaderFields);
    }

    protected DefaultHttpHeaders(boolean validate, NameConverter<CharSequence> nameConverter,
                                 boolean singleHeaderFields) {
        super(true, validate ? VALIDATE_OBJECT_CONVERTER : NO_VALIDATE_OBJECT_CONVERTER, nameConverter,
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
}
