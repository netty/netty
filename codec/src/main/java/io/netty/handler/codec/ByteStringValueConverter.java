/*
 * Copyright 2015 The Netty Project
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

import java.nio.charset.Charset;
import java.text.ParseException;

import io.netty.handler.codec.DefaultHeaders.HeaderDateFormat;
import io.netty.util.ByteString;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.PlatformDependent;

/**
 * Converts to/from native types, general {@link Object}, and {@link ByteString}s.
 */
public final class ByteStringValueConverter implements ValueConverter<ByteString> {
    public static final ByteStringValueConverter INSTANCE = new ByteStringValueConverter();
    private static final Charset DEFAULT_CHARSET = CharsetUtil.UTF_8;

    private ByteStringValueConverter() {
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
