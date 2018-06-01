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

import io.netty.util.AsciiString;
import io.netty.util.internal.PlatformDependent;

import java.text.ParseException;
import java.util.Date;

/**
 * Converts to/from native types, general {@link Object}, and {@link CharSequence}s.
 */
public class CharSequenceValueConverter implements ValueConverter<CharSequence> {
    public static final CharSequenceValueConverter INSTANCE = new CharSequenceValueConverter();
    private static final AsciiString TRUE_ASCII = new AsciiString("true");

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
        return AsciiString.contentEqualsIgnoreCase(value, TRUE_ASCII);
    }

    @Override
    public CharSequence convertByte(byte value) {
        return String.valueOf(value);
    }

    @Override
    public byte convertToByte(CharSequence value) {
        if (value instanceof AsciiString && value.length() == 1) {
            return ((AsciiString) value).byteAt(0);
        }
        return Byte.parseByte(value.toString());
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
        if (value instanceof AsciiString) {
            return ((AsciiString) value).parseShort();
        }
        return Short.parseShort(value.toString());
    }

    @Override
    public int convertToInt(CharSequence value) {
        if (value instanceof AsciiString) {
            return ((AsciiString) value).parseInt();
        }
        return Integer.parseInt(value.toString());
    }

    @Override
    public long convertToLong(CharSequence value) {
        if (value instanceof AsciiString) {
            return ((AsciiString) value).parseLong();
        }
        return Long.parseLong(value.toString());
    }

    @Override
    public CharSequence convertTimeMillis(long value) {
        return DateFormatter.format(new Date(value));
    }

    @Override
    public long convertToTimeMillis(CharSequence value) {
        Date date = DateFormatter.parseHttpDate(value);
        if (date == null) {
            PlatformDependent.throwException(new ParseException("header can't be parsed into a Date: " + value, 0));
            return 0;
        }
        return date.getTime();
    }

    @Override
    public float convertToFloat(CharSequence value) {
        if (value instanceof AsciiString) {
            return ((AsciiString) value).parseFloat();
        }
        return Float.parseFloat(value.toString());
    }

    @Override
    public double convertToDouble(CharSequence value) {
        if (value instanceof AsciiString) {
            return ((AsciiString) value).parseDouble();
        }
        return Double.parseDouble(value.toString());
    }
}
