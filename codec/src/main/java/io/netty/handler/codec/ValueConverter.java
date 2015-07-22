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
 */package io.netty.handler.codec;

/**
 * Converts to/from a generic object to the type.
 */
public interface ValueConverter<T> {
    T convertObject(Object value);

    T convertBoolean(boolean value);

    boolean convertToBoolean(T value);

    T convertByte(byte value);

    byte convertToByte(T value);

    T convertChar(char value);

    char convertToChar(T value);

    T convertShort(short value);

    short convertToShort(T value);

    T convertInt(int value);

    int convertToInt(T value);

    T convertLong(long value);

    long convertToLong(T value);

    T convertTimeMillis(long value);

    long convertToTimeMillis(T value);

    T convertFloat(float value);

    float convertToFloat(T value);

    T convertDouble(double value);

    double convertToDouble(T value);
}
