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

import io.netty.handler.codec.DefaultHeaders.HeaderDateFormat;
import io.netty.util.internal.PlatformDependent;

import java.text.ParseException;

/**
 * Converts to/from native types, general {@link Object}, and {@link CharSequence}s.
 */
public class CharSequenceValueConverter implements ValueConverter<CharSequence> {
    public static final CharSequenceValueConverter INSTANCE = new CharSequenceValueConverter();

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
