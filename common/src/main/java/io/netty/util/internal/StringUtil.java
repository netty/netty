/*
 * Copyright 2012 The Netty Project
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
package io.netty.util.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/**
 * String utility class.
 */
public final class StringUtil {

    public static final String NEWLINE;

    private static final String[] BYTE2HEX_PAD = new String[256];
    private static final String[] BYTE2HEX_NOPAD = new String[256];
    private static final String EMPTY_STRING = "";

    static {
        // Determine the newline character of the current platform.
        String newLine;

        try {
            newLine = new Formatter().format("%n").toString();
        } catch (Exception e) {
            // Should not reach here, but just in case.
            newLine = "\n";
        }

        NEWLINE = newLine;

        // Generate the lookup table that converts a byte into a 2-digit hexadecimal integer.
        int i;
        for (i = 0; i < 10; i ++) {
            StringBuilder buf = new StringBuilder(2);
            buf.append('0');
            buf.append(i);
            BYTE2HEX_PAD[i] = buf.toString();
            BYTE2HEX_NOPAD[i] = String.valueOf(i);
        }
        for (; i < 16; i ++) {
            StringBuilder buf = new StringBuilder(2);
            char c = (char) ('a' + i - 10);
            buf.append('0');
            buf.append(c);
            BYTE2HEX_PAD[i] = buf.toString();
            BYTE2HEX_NOPAD[i] = String.valueOf(c);
        }
        for (; i < BYTE2HEX_PAD.length; i ++) {
            StringBuilder buf = new StringBuilder(2);
            buf.append(Integer.toHexString(i));
            String str = buf.toString();
            BYTE2HEX_PAD[i] = str;
            BYTE2HEX_NOPAD[i] = str;
        }
    }

    /**
     * Splits the specified {@link String} with the specified delimiter.  This operation is a simplified and optimized
     * version of {@link String#split(String)}.
     */
    public static String[] split(String value, char delim) {
        final int end = value.length();
        final List<String> res = new ArrayList<String>();

        int start = 0;
        for (int i = 0; i < end; i ++) {
            if (value.charAt(i) == delim) {
                if (start == i) {
                    res.add(EMPTY_STRING);
                } else {
                    res.add(value.substring(start, i));
                }
                start = i + 1;
            }
        }

        if (start == 0) { // If no delimiter was found in the value
            res.add(value);
        } else {
            if (start != end) {
                // Add the last element if it's not empty.
                res.add(value.substring(start, end));
            } else {
                // Truncate trailing empty elements.
                for (int i = res.size() - 1; i >= 0; i --) {
                    if (res.get(i).isEmpty()) {
                        res.remove(i);
                    } else {
                        break;
                    }
                }
            }
        }

        return res.toArray(new String[res.size()]);
    }

    /**
     * Converts the specified byte value into a 2-digit hexadecimal integer.
     */
    public static String byteToHexStringPadded(int value) {
        return BYTE2HEX_PAD[value & 0xff];
    }

    /**
     * Converts the specified byte value into a 2-digit hexadecimal integer and appends it to the specified buffer.
     */
    public static <T extends Appendable> T byteToHexStringPadded(T buf, int value) {
        try {
            buf.append(byteToHexStringPadded(value));
        } catch (IOException e) {
            PlatformDependent.throwException(e);
        }
        return buf;
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexStringPadded(byte[] src) {
        return toHexStringPadded(src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexStringPadded(byte[] src, int offset, int length) {
        return toHexStringPadded(new StringBuilder(length << 1), src, offset, length).toString();
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexStringPadded(T dst, byte[] src) {
        return toHexStringPadded(dst, src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexStringPadded(T dst, byte[] src, int offset, int length) {
        final int end = offset + length;
        for (int i = offset; i < end; i ++) {
            byteToHexStringPadded(dst, src[i]);
        }
        return dst;
    }

    /**
     * Converts the specified byte value into a hexadecimal integer.
     */
    public static String byteToHexString(int value) {
        return BYTE2HEX_NOPAD[value & 0xff];
    }

    /**
     * Converts the specified byte value into a hexadecimal integer and appends it to the specified buffer.
     */
    public static <T extends Appendable> T byteToHexString(T buf, int value) {
        try {
            buf.append(byteToHexString(value));
        } catch (IOException e) {
            PlatformDependent.throwException(e);
        }
        return buf;
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexString(byte[] src) {
        return toHexString(src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexString(byte[] src, int offset, int length) {
        return toHexString(new StringBuilder(length << 1), src, offset, length).toString();
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexString(T dst, byte[] src) {
        return toHexString(dst, src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexString(T dst, byte[] src, int offset, int length) {
        assert length >= 0;
        if (length == 0) {
            return dst;
        }

        final int end = offset + length;
        final int endMinusOne = end - 1;
        int i;

        // Skip preceding zeroes.
        for (i = offset; i < endMinusOne; i ++) {
            if (src[i] != 0) {
                break;
            }
        }

        byteToHexString(dst, src[i ++]);
        int remaining = end - i;
        toHexStringPadded(dst, src, i, remaining);

        return dst;
    }

    /**
     * The shortcut to {@link #simpleClassName(Class) simpleClassName(o.getClass())}.
     */
    public static String simpleClassName(Object o) {
        if (o == null) {
            return "null_object";
        } else {
            return simpleClassName(o.getClass());
        }
    }

    /**
     * Generates a simplified name from a {@link Class}.  Similar to {@link Class#getSimpleName()}, but it works fine
     * with anonymous classes.
     */
    public static String simpleClassName(Class<?> clazz) {
        if (clazz == null) {
            return "null_class";
        }

        Package pkg = clazz.getPackage();
        if (pkg != null) {
            return clazz.getName().substring(pkg.getName().length() + 1);
        } else {
            return clazz.getName();
        }
    }

    private StringUtil() {
        // Unused.
    }
}
