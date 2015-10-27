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

import io.netty.handler.codec.CharSequenceValueConverter;
import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.HeadersUtils;
import io.netty.handler.codec.ValueConverter;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.PlatformDependent;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import static io.netty.util.AsciiString.CASE_INSENSITIVE_HASHER;
import static io.netty.util.AsciiString.CASE_SENSITIVE_HASHER;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

public class DefaultHttpHeaders extends DefaultHeaders<CharSequence, CharSequence, HttpHeaders> implements HttpHeaders {
    private static final int HIGHEST_INVALID_VALUE_CHAR_MASK = ~15;
    private static final ByteProcessor HEADER_NAME_VALIDATOR = new ByteProcessor() {
        @Override
        public boolean process(byte value) throws Exception {
            validateHeaderNameElement(value);
            return true;
        }
    };
    static final NameValidator<CharSequence> HttpNameValidator = new NameValidator<CharSequence>() {
        @Override
        public void validateName(CharSequence name) {
            if (name instanceof AsciiString) {
                try {
                    ((AsciiString) name).forEachByte(HEADER_NAME_VALIDATOR);
                } catch (Exception e) {
                    PlatformDependent.throwException(e);
                }
            } else {
                checkNotNull(name, "name");
                // Go through each character in the name
                for (int index = 0; index < name.length(); ++index) {
                    validateHeaderNameElement(name.charAt(index));
                }
            }
        }
    };

    public DefaultHttpHeaders() {
        this(true);
    }

    @SuppressWarnings("unchecked")
    public DefaultHttpHeaders(boolean validate) {
        super(CASE_INSENSITIVE_HASHER, valueConverter(validate),
                validate ? HttpNameValidator : NameValidator.NOT_NULL);
    }

    protected DefaultHttpHeaders(boolean validateValue, NameValidator<CharSequence> nameValidator) {
        super(CASE_INSENSITIVE_HASHER, valueConverter(validateValue), nameValidator);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpHeaders)) {
            return false;
        }
        return equals((HttpHeaders) o, CASE_SENSITIVE_HASHER);
    }

    @Override
    public int hashCode() {
        return hashCode(CASE_SENSITIVE_HASHER);
    }

    @Override
    public String getAsString(CharSequence name) {
        return HeadersUtils.getAsString(this, name);
    }

    @Override
    public List<String> getAllAsString(CharSequence name) {
        return HeadersUtils.getAllAsString(this, name);
    }

    @Override
    public Iterator<Entry<String, String>> iteratorAsString() {
        return HeadersUtils.iteratorAsString(this);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        return contains(name, value, false);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return contains(name, value,
                ignoreCase ? CASE_INSENSITIVE_HASHER : CASE_SENSITIVE_HASHER);
    }

    private static void validateHeaderNameElement(byte value) {
        switch (value) {
        case 0x00:
        case '\t':
        case '\n':
        case 0x0b:
        case '\f':
        case '\r':
        case ' ':
        case ',':
        case ':':
        case ';':
        case '=':
            throw new IllegalArgumentException(
               "a header name cannot contain the following prohibited characters: =,;: \\t\\r\\n\\v\\f: " +
                       value);
        default:
            // Check to see if the character is not an ASCII character, or invalid
            if (value < 0) {
                throw new IllegalArgumentException("a header name cannot contain non-ASCII character: " +
                        value);
            }
        }
    }

    private static void validateHeaderNameElement(char value) {
        switch (value) {
        case 0x00:
        case '\t':
        case '\n':
        case 0x0b:
        case '\f':
        case '\r':
        case ' ':
        case ',':
        case ':':
        case ';':
        case '=':
            throw new IllegalArgumentException(
               "a header name cannot contain the following prohibited characters: =,;: \\t\\r\\n\\v\\f: " +
                       value);
        default:
            // Check to see if the character is not an ASCII character, or invalid
            if (value > 127) {
                throw new IllegalArgumentException("a header name cannot contain non-ASCII character: " +
                        value);
            }
        }
    }

    private static ValueConverter<CharSequence> valueConverter(boolean validate) {
        return validate ? HeaderValueConverterAndValidator.INSTANCE : HeaderValueConverter.INSTANCE;
    }

    private static class HeaderValueConverter extends CharSequenceValueConverter {
        static final HeaderValueConverter INSTANCE = new HeaderValueConverter();

        @Override
        public CharSequence convertObject(Object value) {
            if (value instanceof CharSequence) {
                return (CharSequence) value;
            }
            if (value instanceof Date) {
                return HttpHeaderDateFormat.get().format((Date) value);
            }
            if (value instanceof Calendar) {
                return HttpHeaderDateFormat.get().format(((Calendar) value).getTime());
            }
            return value.toString();
        }
    }

    private static final class HeaderValueConverterAndValidator extends HeaderValueConverter {
        static final HeaderValueConverterAndValidator INSTANCE = new HeaderValueConverterAndValidator();

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
