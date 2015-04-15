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
import io.netty.handler.codec.DefaultHeaders.NameConverter;
import io.netty.handler.codec.DefaultTextHeaders;
import io.netty.handler.codec.DefaultTextHeaders.DefaultTextValueTypeConverter;
import io.netty.handler.codec.Headers.EntryVisitor;
import io.netty.handler.codec.TextHeaders;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.PlatformDependent;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Default implementation of {@link HttpHeaders}.
 */
public class DefaultHttpHeaders extends HttpHeaders {

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

    private final TextHeaders headers;
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

    public DefaultHttpHeaders(boolean validate, boolean singleHeaderFields) {
        this(true, validate? VALIDATE_NAME_CONVERTER : NO_VALIDATE_NAME_CONVERTER, singleHeaderFields);
    }

    protected DefaultHttpHeaders(boolean validate, NameConverter<CharSequence> nameConverter,
                                 boolean singleHeaderFields) {
        headers = new DefaultTextHeaders(true,
                validate ? VALIDATE_OBJECT_CONVERTER : NO_VALIDATE_OBJECT_CONVERTER, nameConverter, singleHeaderFields);
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
        headers.addObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Object value) {
        headers.addObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        headers.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<?> values) {
        headers.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders addInt(CharSequence name, int value) {
        headers.addInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders addShort(CharSequence name, short value) {
        headers.addShort(name, value);
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
        headers.setObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Object value) {
        headers.setObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(String name, Iterable<?> values) {
        headers.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<?> values) {
        headers.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders setInt(CharSequence name, int value) {
        headers.setInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders setShort(CharSequence name, short value) {
        headers.setShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders clear() {
        headers.clear();
        return this;
    }

    @Override
    public String get(String name) {
        return headers.getAndConvert(name);
    }

    @Override
    public String get(CharSequence name) {
        return headers.getAndConvert(name);
    }

    @Override
    public Integer getInt(CharSequence name) {
        return headers.getInt(name);
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        return headers.getInt(name, defaultValue);
    }

    @Override
    public Short getShort(CharSequence name) {
        return headers.getShort(name);
    }

    @Override
    public short getShort(CharSequence name, short defaultValue) {
        return headers.getInt(name, defaultValue);
    }

    @Override
    public Long getTimeMillis(CharSequence name) {
        return headers.getTimeMillis(name);
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        return headers.getTimeMillis(name, defaultValue);
    }

    @Override
    public List<String> getAll(String name) {
        return headers.getAllAndConvert(name);
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return headers.getAllAndConvert(name);
    }

    @Override
    public List<Map.Entry<String, String>> entries() {
        return headers.entriesConverted();
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return headers.iteratorConverted();
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
    public Entry<CharSequence, CharSequence> forEachEntry(EntryVisitor<CharSequence> visitor) throws Exception {
        return headers.forEachEntry(visitor);
    }

    @Override
    public Set<String> names() {
        return headers.namesAndConvert(String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultHttpHeaders)) {
            return false;
        }

        DefaultHttpHeaders other = (DefaultHttpHeaders) o;

        return headers.equals(other.headers);
    }

    @Override
    public int hashCode() {
        return headers.hashCode();
    }

    void encode(ByteBuf buf) throws Exception {
        headers.forEachEntry(new HttpHeadersEncoder(buf));
    }
}
