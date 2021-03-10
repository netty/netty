/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec;

import io.netty.util.AsciiString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CharSequenceValueConverterTest {

    private final CharSequenceValueConverter converter = CharSequenceValueConverter.INSTANCE;

    @Test
    public void testBoolean() {
        assertTrue(converter.convertToBoolean(converter.convertBoolean(true)));
        assertFalse(converter.convertToBoolean(converter.convertBoolean(false)));
    }

    @Test
    public void testByteFromAsciiString() {
        assertEquals(127, converter.convertToByte(AsciiString.of("127")));
    }

    @Test(expected = NumberFormatException.class)
    public void testByteFromEmptyAsciiString() {
        converter.convertToByte(AsciiString.EMPTY_STRING);
    }

    @Test
    public void testByte() {
        assertEquals(Byte.MAX_VALUE, converter.convertToByte(converter.convertByte(Byte.MAX_VALUE)));
    }

    @Test
    public void testChar() {
        assertEquals(Character.MAX_VALUE, converter.convertToChar(converter.convertChar(Character.MAX_VALUE)));
    }

    @Test
    public void testDouble() {
        assertEquals(Double.MAX_VALUE, converter.convertToDouble(converter.convertDouble(Double.MAX_VALUE)), 0);
    }

    @Test
    public void testFloat() {
        assertEquals(Float.MAX_VALUE, converter.convertToFloat(converter.convertFloat(Float.MAX_VALUE)), 0);
    }

    @Test
    public void testInt() {
        assertEquals(Integer.MAX_VALUE, converter.convertToInt(converter.convertInt(Integer.MAX_VALUE)));
    }

    @Test
    public void testShort() {
        assertEquals(Short.MAX_VALUE, converter.convertToShort(converter.convertShort(Short.MAX_VALUE)));
    }

    @Test
    public void testLong() {
        assertEquals(Long.MAX_VALUE, converter.convertToLong(converter.convertLong(Long.MAX_VALUE)));
    }

    @Test
    public void testTimeMillis() {
        // Zero out the millis as this is what the convert is doing as well.
        long millis = (System.currentTimeMillis() / 1000) * 1000;
        assertEquals(millis, converter.convertToTimeMillis(converter.convertTimeMillis(millis)));
    }
}
