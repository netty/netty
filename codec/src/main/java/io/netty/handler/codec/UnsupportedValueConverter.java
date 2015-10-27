/*
 * Copyright 2015 The Netty Project
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

/**
 * {@link UnsupportedOperationException} will be thrown from all {@link ValueConverter} methods.
 */
public final class UnsupportedValueConverter<V> implements ValueConverter<V> {
    @SuppressWarnings("rawtypes")
    private static final UnsupportedValueConverter INSTANCE = new UnsupportedValueConverter();
    private UnsupportedValueConverter() { }

    @SuppressWarnings("unchecked")
    public static <V> UnsupportedValueConverter<V> instance() {
        return (UnsupportedValueConverter<V>) INSTANCE;
    }

    @Override
    public V convertObject(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V convertBoolean(boolean value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean convertToBoolean(V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V convertByte(byte value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte convertToByte(V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V convertChar(char value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public char convertToChar(V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V convertShort(short value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public short convertToShort(V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V convertInt(int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int convertToInt(V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V convertLong(long value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long convertToLong(V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V convertTimeMillis(long value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long convertToTimeMillis(V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V convertFloat(float value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float convertToFloat(V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V convertDouble(double value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double convertToDouble(V value) {
        throw new UnsupportedOperationException();
    }
}
