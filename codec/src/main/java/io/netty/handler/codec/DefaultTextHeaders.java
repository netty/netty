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

import io.netty.util.internal.PlatformDependent;

import java.text.ParseException;
import java.util.Comparator;

import static io.netty.handler.codec.AsciiString.*;

public class DefaultTextHeaders extends DefaultConvertibleHeaders<CharSequence, String> implements TextHeaders {
    private static final HashCodeGenerator<CharSequence> CHARSEQUECE_CASE_INSENSITIVE_HASH_CODE_GENERATOR =
            new HashCodeGenerator<CharSequence>() {
        @Override
        public int generateHashCode(CharSequence name) {
            return AsciiString.caseInsensitiveHashCode(name);
        }
    };

    private static final HashCodeGenerator<CharSequence> CHARSEQUECE_CASE_SENSITIVE_HASH_CODE_GENERATOR =
            new HashCodeGenerator<CharSequence>() {
        @Override
        public int generateHashCode(CharSequence name) {
            return name.hashCode();
        }
    };

    public static class DefaultTextValueTypeConverter implements ValueConverter<CharSequence> {
        @Override
        public CharSequence convertObject(Object value) {
            if (value instanceof CharSequence) {
                return (CharSequence) value;
            }
            return value.toString();
        }

        @Override
        public CharSequence convertInt(int value) {
            return String.valueOf(value);
        }

        @Override
        public CharSequence convertLong(long value) {
            return String.valueOf(value);
        }

        @Override
        public CharSequence convertDouble(double value) {
            return String.valueOf(value);
        }

        @Override
        public CharSequence convertChar(char value) {
            return String.valueOf(value);
        }

        @Override
        public CharSequence convertBoolean(boolean value) {
            return String.valueOf(value);
        }

        @Override
        public CharSequence convertFloat(float value) {
            return String.valueOf(value);
        }

        @Override
        public boolean convertToBoolean(CharSequence value) {
            return Boolean.parseBoolean(value.toString());
        }

        @Override
        public CharSequence convertByte(byte value) {
            return String.valueOf(value);
        }

        @Override
        public byte convertToByte(CharSequence value) {
            return Byte.valueOf(value.toString());
        }

        @Override
        public char convertToChar(CharSequence value) {
            return value.charAt(0);
        }

        @Override
        public CharSequence convertShort(short value) {
            return String.valueOf(value);
        }

        @Override
        public short convertToShort(CharSequence value) {
            return Short.valueOf(value.toString());
        }

        @Override
        public int convertToInt(CharSequence value) {
            return Integer.parseInt(value.toString());
        }

        @Override
        public long convertToLong(CharSequence value) {
            return Long.parseLong(value.toString());
        }

        @Override
        public AsciiString convertTimeMillis(long value) {
            return new AsciiString(String.valueOf(value));
        }

        @Override
        public long convertToTimeMillis(CharSequence value) {
            try {
                return HeaderDateFormat.get().parse(value.toString());
            } catch (ParseException e) {
                PlatformDependent.throwException(e);
            }
            return 0;
        }

        @Override
        public float convertToFloat(CharSequence value) {
            return Float.valueOf(value.toString());
        }

        @Override
        public double convertToDouble(CharSequence value) {
            return Double.valueOf(value.toString());
        }
    }

    private static final Headers.ValueConverter<CharSequence> CHARSEQUENCE_FROM_OBJECT_CONVERTER =
            new DefaultTextValueTypeConverter();
    private static final ConvertibleHeaders.TypeConverter<CharSequence, String> CHARSEQUENCE_TO_STRING_CONVERTER =
            new ConvertibleHeaders.TypeConverter<CharSequence, String>() {
        @Override
        public String toConvertedType(CharSequence value) {
            return value.toString();
        }

        @Override
        public CharSequence toUnconvertedType(String value) {
            return value;
        }
    };

    private static final NameConverter<CharSequence> CHARSEQUENCE_IDENTITY_CONVERTER =
            new IdentityNameConverter<CharSequence>();

    public DefaultTextHeaders() {
        this(true);
    }

    public DefaultTextHeaders(boolean ignoreCase) {
        this(ignoreCase, CHARSEQUENCE_FROM_OBJECT_CONVERTER, CHARSEQUENCE_IDENTITY_CONVERTER);
    }

    protected DefaultTextHeaders(boolean ignoreCase, Headers.ValueConverter<CharSequence> valueConverter,
            NameConverter<CharSequence> nameConverter) {
        super(comparator(ignoreCase), comparator(ignoreCase),
                ignoreCase ? CHARSEQUECE_CASE_INSENSITIVE_HASH_CODE_GENERATOR
                        : CHARSEQUECE_CASE_SENSITIVE_HASH_CODE_GENERATOR, valueConverter,
                CHARSEQUENCE_TO_STRING_CONVERTER, nameConverter);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return contains(name, value, comparator(ignoreCase));
    }

    @Override
    public boolean containsObject(CharSequence name, Object value, boolean ignoreCase) {
        return containsObject(name, value, comparator(ignoreCase));
    }

    @Override
    public TextHeaders add(CharSequence name, CharSequence value) {
        super.add(name, value);
        return this;
    }

    @Override
    public TextHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public TextHeaders add(CharSequence name, CharSequence... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public TextHeaders addObject(CharSequence name, Object value) {
        super.addObject(name, value);
        return this;
    }

    @Override
    public TextHeaders addObject(CharSequence name, Iterable<?> values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public TextHeaders addObject(CharSequence name, Object... values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public TextHeaders addBoolean(CharSequence name, boolean value) {
        super.addBoolean(name, value);
        return this;
    }

    @Override
    public TextHeaders addChar(CharSequence name, char value) {
        super.addChar(name, value);
        return this;
    }

    @Override
    public TextHeaders addByte(CharSequence name, byte value) {
        super.addByte(name, value);
        return this;
    }

    @Override
    public TextHeaders addShort(CharSequence name, short value) {
        super.addShort(name, value);
        return this;
    }

    @Override
    public TextHeaders addInt(CharSequence name, int value) {
        super.addInt(name, value);
        return this;
    }

    @Override
    public TextHeaders addLong(CharSequence name, long value) {
        super.addLong(name, value);
        return this;
    }

    @Override
    public TextHeaders addFloat(CharSequence name, float value) {
        super.addFloat(name, value);
        return this;
    }

    @Override
    public TextHeaders addDouble(CharSequence name, double value) {
        super.addDouble(name, value);
        return this;
    }

    @Override
    public TextHeaders addTimeMillis(CharSequence name, long value) {
        super.addTimeMillis(name, value);
        return this;
    }

    @Override
    public TextHeaders add(TextHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public TextHeaders set(CharSequence name, CharSequence value) {
        super.set(name, value);
        return this;
    }

    @Override
    public TextHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public TextHeaders set(CharSequence name, CharSequence... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public TextHeaders setObject(CharSequence name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public TextHeaders setObject(CharSequence name, Iterable<?> values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public TextHeaders setObject(CharSequence name, Object... values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public TextHeaders setBoolean(CharSequence name, boolean value) {
        super.setBoolean(name, value);
        return this;
    }

    @Override
    public TextHeaders setChar(CharSequence name, char value) {
        super.setChar(name, value);
        return this;
    }

    @Override
    public TextHeaders setByte(CharSequence name, byte value) {
        super.setByte(name, value);
        return this;
    }

    @Override
    public TextHeaders setShort(CharSequence name, short value) {
        super.setShort(name, value);
        return this;
    }

    @Override
    public TextHeaders setInt(CharSequence name, int value) {
        super.setInt(name, value);
        return this;
    }

    @Override
    public TextHeaders setLong(CharSequence name, long value) {
        super.setLong(name, value);
        return this;
    }

    @Override
    public TextHeaders setFloat(CharSequence name, float value) {
        super.setFloat(name, value);
        return this;
    }

    @Override
    public TextHeaders setDouble(CharSequence name, double value) {
        super.setDouble(name, value);
        return this;
    }

    @Override
    public TextHeaders setTimeMillis(CharSequence name, long value) {
        super.setTimeMillis(name, value);
        return this;
    }

    @Override
    public TextHeaders set(TextHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public TextHeaders setAll(TextHeaders headers) {
        super.setAll(headers);
        return this;
    }

    @Override
    public TextHeaders clear() {
        super.clear();
        return this;
    }

    private static Comparator<CharSequence> comparator(boolean ignoreCase) {
        return ignoreCase ? CHARSEQUENCE_CASE_INSENSITIVE_ORDER : CHARSEQUENCE_CASE_SENSITIVE_ORDER;
    }
}
