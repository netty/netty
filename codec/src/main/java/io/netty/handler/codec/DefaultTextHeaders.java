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

import static io.netty.util.AsciiString.CHARSEQUENCE_CASE_INSENSITIVE_ORDER;
import static io.netty.util.AsciiString.CHARSEQUENCE_CASE_SENSITIVE_ORDER;
import static io.netty.util.internal.StringUtil.COMMA;
import io.netty.util.AsciiString;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.text.ParseException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class DefaultTextHeaders extends DefaultConvertibleHeaders<CharSequence, String> implements TextHeaders {

    public static final NameValidator<CharSequence> NO_NAME_VALIDATOR = NoNameValidator.instance();

    private final ValuesComposer valuesComposer;

    public DefaultTextHeaders() {
        this(false);
    }

    public DefaultTextHeaders(boolean singleHeaderFields) {
        this(new TreeMap<CharSequence, Object>(CHARSEQUENCE_CASE_INSENSITIVE_ORDER), NO_NAME_VALIDATOR,
             CharSequenceConverter.INSTANCE, singleHeaderFields);
    }

    public DefaultTextHeaders(Map<CharSequence, Object> map,
                              NameValidator<CharSequence> nameValidator,
                              ValueConverter<CharSequence> valueConverter,
                              boolean singleHeaderFields) {
        super(map, nameValidator, valueConverter, CharSequenceToStringConverter.INSTANCE);
        valuesComposer = singleHeaderFields ? new SingleHeaderValuesComposer() : new MultipleFieldsValueComposer();
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return contains(name, value,
                ignoreCase ? CHARSEQUENCE_CASE_INSENSITIVE_ORDER : CHARSEQUENCE_CASE_SENSITIVE_ORDER);
    }

    @Override
    public TextHeaders add(CharSequence name, CharSequence value) {
        return valuesComposer.add(name, value);
    }

    @Override
    public TextHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
        return valuesComposer.add(name, values);
    }

    @Override
    public TextHeaders add(CharSequence name, CharSequence... values) {
        return valuesComposer.add(name, values);
    }

    @Override
    public TextHeaders addObject(CharSequence name, Object value) {
        return valuesComposer.addObject(name, value);
    }

    @Override
    public TextHeaders addObject(CharSequence name, Iterable<?> values) {
        return valuesComposer.addObject(name, values);
    }

    @Override
    public TextHeaders addObject(CharSequence name, Object... values) {
        return valuesComposer.addObject(name, values);
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
        return valuesComposer.set(name, values);
    }

    @Override
    public TextHeaders set(CharSequence name, CharSequence... values) {
        return valuesComposer.set(name, values);
    }

    @Override
    public TextHeaders setObject(CharSequence name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public TextHeaders setObject(CharSequence name, Iterable<?> values) {
        return valuesComposer.setObject(name, values);
    }

    @Override
    public TextHeaders setObject(CharSequence name, Object... values) {
        return valuesComposer.setObject(name, values);
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

    /*
     * This interface enables different implementations for adding/setting header values.
     * Concrete implementations can control how values are added, for example to add all
     * values for a header as a comma separated string instead of adding them as multiple
     * headers with a single value.
     */
    private interface ValuesComposer {
        TextHeaders add(CharSequence name, CharSequence value);
        TextHeaders add(CharSequence name, CharSequence... values);
        TextHeaders add(CharSequence name, Iterable<? extends CharSequence> values);

        TextHeaders addObject(CharSequence name, Object value);
        TextHeaders addObject(CharSequence name, Iterable<?> values);
        TextHeaders addObject(CharSequence name, Object... values);

        TextHeaders set(CharSequence name, CharSequence... values);
        TextHeaders set(CharSequence name, Iterable<? extends CharSequence> values);

        TextHeaders setObject(CharSequence name, Object... values);
        TextHeaders setObject(CharSequence name, Iterable<?> values);
    }

    /*
     * Will add multiple values for the same header as multiple separate headers.
     */
    private final class MultipleFieldsValueComposer implements ValuesComposer {

        @Override
        public TextHeaders add(CharSequence name, CharSequence value) {
            DefaultTextHeaders.super.add(name, value);
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders add(CharSequence name, CharSequence... values) {
            DefaultTextHeaders.super.add(name, values);
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
            DefaultTextHeaders.super.add(name, values);
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders addObject(CharSequence name, Object value) {
            DefaultTextHeaders.super.addObject(name, value);
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders addObject(CharSequence name, Iterable<?> values) {
            DefaultTextHeaders.super.addObject(name, values);
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders addObject(CharSequence name, Object... values) {
            DefaultTextHeaders.super.addObject(name, values);
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders set(CharSequence name, CharSequence... values) {
            DefaultTextHeaders.super.set(name, values);
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
            DefaultTextHeaders.super.set(name, values);
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders setObject(CharSequence name, Object... values) {
            DefaultTextHeaders.super.setObject(name, values);
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders setObject(CharSequence name, Iterable<?> values) {
            DefaultTextHeaders.super.setObject(name, values);
            return DefaultTextHeaders.this;
        }
    }

    /**
     * Will add multiple values for the same header as single header with a comma separated list of values.
     *
     * Please refer to section <a href="https://tools.ietf.org/html/rfc7230#section-3.2.2">3.2.2 Field Order</a>
     * of RFC-7230 for details.
     */
    private final class SingleHeaderValuesComposer implements ValuesComposer {

        private final ValueConverter<CharSequence> valueConverter = valueConverter();
        private CsvValueEscaper<Object> objectEscaper;
        private CsvValueEscaper<CharSequence> charSequenceEscaper;

        private CsvValueEscaper<Object> objectEscaper() {
            if (objectEscaper == null) {
                objectEscaper = new CsvValueEscaper<Object>() {
                    @Override
                    public CharSequence escape(Object value) {
                        return StringUtil.escapeCsv(valueConverter.convertObject(value));
                    }
                };
            }
            return objectEscaper;
        }

        private CsvValueEscaper<CharSequence> charSequenceEscaper() {
            if (charSequenceEscaper == null) {
                charSequenceEscaper = new CsvValueEscaper<CharSequence>() {
                    @Override
                    public CharSequence escape(CharSequence value) {
                        return StringUtil.escapeCsv(value);
                    }
                };
            }
            return charSequenceEscaper;
        }

        @Override
        public TextHeaders add(CharSequence name, CharSequence value) {
            return addEscapedValue(name, StringUtil.escapeCsv(value));
        }

        @Override
        public TextHeaders add(CharSequence name, CharSequence... values) {
            return addEscapedValue(name, commaSeparate(charSequenceEscaper(), values));
        }

        @Override
        public TextHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
            return addEscapedValue(name, commaSeparate(charSequenceEscaper(), values));
        }

        @Override
        public TextHeaders addObject(CharSequence name, Object value) {
            return addEscapedValue(name, objectEscaper().escape(value));
        }

        @Override
        public TextHeaders addObject(CharSequence name, Iterable<?> values) {
            return addEscapedValue(name, commaSeparate(objectEscaper(), values));
        }

        @Override
        public TextHeaders addObject(CharSequence name, Object... values) {
            return addEscapedValue(name, commaSeparate(objectEscaper(), values));
        }

        @Override
        public TextHeaders set(CharSequence name, CharSequence... values) {
            DefaultTextHeaders.super.set(name, commaSeparate(charSequenceEscaper(), values));
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
            DefaultTextHeaders.super.set(name, commaSeparate(charSequenceEscaper(), values));
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders setObject(CharSequence name, Object... values) {
            DefaultTextHeaders.super.set(name, commaSeparate(objectEscaper(), values));
            return DefaultTextHeaders.this;
        }

        @Override
        public TextHeaders setObject(CharSequence name, Iterable<?> values) {
            DefaultTextHeaders.super.set(name, commaSeparate(objectEscaper(), values));
            return DefaultTextHeaders.this;
        }

        private TextHeaders addEscapedValue(CharSequence name, CharSequence escapedValue) {
            CharSequence currentValue = DefaultTextHeaders.super.get(name);
            if (currentValue == null) {
                DefaultTextHeaders.super.add(name, escapedValue);
            } else {
                DefaultTextHeaders.super.set(name, commaSeparateEscapedValues(currentValue, escapedValue));
            }
            return DefaultTextHeaders.this;
        }

        private <T> CharSequence commaSeparate(CsvValueEscaper<T> escaper, T... values) {
            final int lengthEstimate = 10;
            StringBuilder sb = new StringBuilder(values.length * lengthEstimate);
            if (values.length > 0) {
                int end = values.length - 1;
                for (int i = 0; i < end; i++) {
                    sb.append(escaper.escape(values[i])).append(COMMA);
                }
                sb.append(escaper.escape(values[end]));
            }
            return sb;
        }

        private <T> CharSequence commaSeparate(CsvValueEscaper<T> escaper, Iterable<? extends T> values) {
            StringBuilder sb = new StringBuilder();
            Iterator<? extends T> iterator = values.iterator();
            if (iterator.hasNext()) {
                T next = iterator.next();
                while (iterator.hasNext()) {
                    sb.append(escaper.escape(next)).append(COMMA);
                    next = iterator.next();
                }
                sb.append(escaper.escape(next));
            }
            return sb;
        }

        private CharSequence commaSeparateEscapedValues(CharSequence currentValue, CharSequence value) {
            return new StringBuilder(currentValue.length() + 1 + value.length())
                    .append(currentValue)
                    .append(COMMA)
                    .append(value);
        }
    }

    /**
     * Escapes comma separated values (CSV).
     *
     * @param <T> The type that a concrete implementation handles
     */
    private interface CsvValueEscaper<T> {
        /**
         * Appends the value to the specified {@link StringBuilder}, escaping if necessary.
         *
         * @param value the value to be appended, escaped if necessary
         */
        CharSequence escape(T value);
    }

    private static final class CharSequenceToStringConverter implements TypeConverter<CharSequence, String> {

        private static final CharSequenceToStringConverter INSTANCE = new CharSequenceToStringConverter();

        @Override
        public String toConvertedType(CharSequence value) {
            return value.toString();
        }

        @Override
        public CharSequence toUnconvertedType(String value) {
            return value;
        }
    }

    protected static class CharSequenceConverter implements ValueConverter<CharSequence> {

        public static final CharSequenceConverter INSTANCE = new CharSequenceConverter();

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
            if (value.length() == 0) {
                throw new IllegalArgumentException("'value' is empty.");
            }
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
        public CharSequence convertTimeMillis(long value) {
            return String.valueOf(value);
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
}
