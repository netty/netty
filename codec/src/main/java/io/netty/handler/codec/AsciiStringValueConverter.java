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

import java.text.ParseException;

import io.netty.handler.codec.DefaultHeaders.HeaderDateFormat;
import io.netty.util.AsciiString;
import io.netty.util.ByteString;
import io.netty.util.internal.PlatformDependent;

/**
 * Converts to/from native types, general {@link Object}, and {@link ByteString}s.
 */
public final class AsciiStringValueConverter implements ValueConverter<AsciiString> {
    public static final AsciiStringValueConverter INSTANCE = new AsciiStringValueConverter();

    private AsciiStringValueConverter() {
    }

    @Override
    public AsciiString convertObject(Object value) {
        if (value instanceof AsciiString) {
            return (AsciiString) value;
        }
        if (value instanceof ByteString) {
            return new AsciiString((ByteString) value, false);
        }
        if (value instanceof CharSequence) {
            return new AsciiString((CharSequence) value);
        }
        return new AsciiString(value.toString());
    }

    @Override
    public AsciiString convertInt(int value) {
        return new AsciiString(String.valueOf(value));
    }

    @Override
    public AsciiString convertLong(long value) {
        return new AsciiString(String.valueOf(value));
    }

    @Override
    public AsciiString convertDouble(double value) {
        return new AsciiString(String.valueOf(value));
    }

    @Override
    public AsciiString convertChar(char value) {
        return new AsciiString(String.valueOf(value));
    }

    @Override
    public AsciiString convertBoolean(boolean value) {
        return new AsciiString(String.valueOf(value));
    }

    @Override
    public AsciiString convertFloat(float value) {
        return new AsciiString(String.valueOf(value));
    }

    @Override
    public int convertToInt(AsciiString value) {
        return value.parseAsciiInt();
    }

    @Override
    public long convertToLong(AsciiString value) {
        return value.parseAsciiLong();
    }

    @Override
    public AsciiString convertTimeMillis(long value) {
        return new AsciiString(String.valueOf(value));
    }

    @Override
    public long convertToTimeMillis(AsciiString value) {
        try {
            return HeaderDateFormat.get().parse(value.toString());
        } catch (ParseException e) {
            PlatformDependent.throwException(e);
        }
        return 0;
    }

    @Override
    public double convertToDouble(AsciiString value) {
        return value.parseAsciiDouble();
    }

    @Override
    public char convertToChar(AsciiString value) {
        return value.parseChar();
    }

    @Override
    public boolean convertToBoolean(AsciiString value) {
        return value.byteAt(0) != 0;
    }

    @Override
    public float convertToFloat(AsciiString value) {
        return value.parseAsciiFloat();
    }

    @Override
    public AsciiString convertShort(short value) {
        return new AsciiString(String.valueOf(value));
    }

    @Override
    public short convertToShort(AsciiString value) {
        return value.parseAsciiShort();
    }

    @Override
    public AsciiString convertByte(byte value) {
        return new AsciiString(String.valueOf(value));
    }

    @Override
    public byte convertToByte(AsciiString value) {
        return value.byteAt(0);
    }
}
