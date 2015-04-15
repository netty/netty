/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec;

import io.netty.util.ByteString;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;

import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class DefaultBinaryHeaders extends DefaultHeaders<ByteString> implements BinaryHeaders {

    private static final NameValidator<ByteString> NO_NAME_VALIDATOR = DefaultHeaders.NoNameValidator.instance();

    public DefaultBinaryHeaders() {
        this(new TreeMap<ByteString, Object>(ByteString.DEFAULT_COMPARATOR));
    }

    public DefaultBinaryHeaders(Map<ByteString, Object> map) {
        this(map, NO_NAME_VALIDATOR);
    }

    public DefaultBinaryHeaders(Map<ByteString, Object> map, NameValidator<ByteString> nameValidator) {
        super(map, nameValidator, ByteStringConverter.INSTANCE);
    }

    @Override
    public BinaryHeaders add(ByteString name, ByteString value) {
        super.add(name, value);
        return this;
    }

    @Override
    public BinaryHeaders add(ByteString name, Iterable<? extends ByteString> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public BinaryHeaders add(ByteString name, ByteString... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public BinaryHeaders addObject(ByteString name, Object value) {
        super.addObject(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addObject(ByteString name, Iterable<?> values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders addObject(ByteString name, Object... values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders addBoolean(ByteString name, boolean value) {
        super.addBoolean(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addChar(ByteString name, char value) {
        super.addChar(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addByte(ByteString name, byte value) {
        super.addByte(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addShort(ByteString name, short value) {
        super.addShort(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addInt(ByteString name, int value) {
        super.addInt(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addLong(ByteString name, long value) {
        super.addLong(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addFloat(ByteString name, float value) {
        super.addFloat(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addDouble(ByteString name, double value) {
        super.addDouble(name, value);
        return this;
    }

    @Override
    public BinaryHeaders addTimeMillis(ByteString name, long value) {
        super.addTimeMillis(name, value);
        return this;
    }

    @Override
    public BinaryHeaders add(BinaryHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public BinaryHeaders set(ByteString name, ByteString value) {
        super.set(name, value);
        return this;
    }

    @Override
    public BinaryHeaders set(ByteString name, Iterable<? extends ByteString> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public BinaryHeaders set(ByteString name, ByteString... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public BinaryHeaders setObject(ByteString name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setObject(ByteString name, Iterable<?> values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders setObject(ByteString name, Object... values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public BinaryHeaders setBoolean(ByteString name, boolean value) {
        super.setBoolean(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setChar(ByteString name, char value) {
        super.setChar(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setByte(ByteString name, byte value) {
        super.setByte(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setShort(ByteString name, short value) {
        super.setShort(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setInt(ByteString name, int value) {
        super.setInt(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setLong(ByteString name, long value) {
        super.setLong(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setFloat(ByteString name, float value) {
        super.setFloat(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setDouble(ByteString name, double value) {
        super.setDouble(name, value);
        return this;
    }

    @Override
    public BinaryHeaders setTimeMillis(ByteString name, long value) {
        super.setTimeMillis(name, value);
        return this;
    }

    @Override
    public BinaryHeaders set(BinaryHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public BinaryHeaders setAll(BinaryHeaders headers) {
        super.setAll(headers);
        return this;
    }

    @Override
    public BinaryHeaders clear() {
        super.clear();
        return this;
    }

    public static final class ByteStringConverter implements ValueConverter<ByteString> {

        public static final ByteStringConverter INSTANCE = new ByteStringConverter();
        private static final Charset DEFAULT_CHARSET = CharsetUtil.UTF_8;

        private ByteStringConverter() {
        }

        @Override
        public ByteString convertObject(Object value) {
            if (value instanceof ByteString) {
                return (ByteString) value;
            }
            if (value instanceof CharSequence) {
                return new ByteString((CharSequence) value, DEFAULT_CHARSET);
            }
            return new ByteString(value.toString(), DEFAULT_CHARSET);
        }

        @Override
        public ByteString convertInt(int value) {
            return new ByteString(String.valueOf(value), DEFAULT_CHARSET);
        }

        @Override
        public ByteString convertLong(long value) {
            return new ByteString(String.valueOf(value), DEFAULT_CHARSET);
        }

        @Override
        public ByteString convertDouble(double value) {
            return new ByteString(String.valueOf(value), DEFAULT_CHARSET);
        }

        @Override
        public ByteString convertChar(char value) {
            return new ByteString(String.valueOf(value), DEFAULT_CHARSET);
        }

        @Override
        public ByteString convertBoolean(boolean value) {
            return new ByteString(String.valueOf(value), DEFAULT_CHARSET);
        }

        @Override
        public ByteString convertFloat(float value) {
            return new ByteString(String.valueOf(value), DEFAULT_CHARSET);
        }

        @Override
        public int convertToInt(ByteString value) {
            return value.parseAsciiInt();
        }

        @Override
        public long convertToLong(ByteString value) {
            return value.parseAsciiLong();
        }

        @Override
        public ByteString convertTimeMillis(long value) {
            return new ByteString(String.valueOf(value), DEFAULT_CHARSET);
        }

        @Override
        public long convertToTimeMillis(ByteString value) {
            try {
                return HeaderDateFormat.get().parse(value.toString());
            } catch (ParseException e) {
                PlatformDependent.throwException(e);
            }
            return 0;
        }

        @Override
        public double convertToDouble(ByteString value) {
            return value.parseAsciiDouble();
        }

        @Override
        public char convertToChar(ByteString value) {
            return value.parseChar();
        }

        @Override
        public boolean convertToBoolean(ByteString value) {
            return value.byteAt(0) != 0;
        }

        @Override
        public float convertToFloat(ByteString value) {
            return value.parseAsciiFloat();
        }

        @Override
        public ByteString convertShort(short value) {
            return new ByteString(String.valueOf(value), DEFAULT_CHARSET);
        }

        @Override
        public short convertToShort(ByteString value) {
            return value.parseAsciiShort();
        }

        @Override
        public ByteString convertByte(byte value) {
            return new ByteString(String.valueOf(value), DEFAULT_CHARSET);
        }

        @Override
        public byte convertToByte(ByteString value) {
            return value.byteAt(0);
        }
    }
}
