/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.util;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ConvertUtil {

    public static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else {
            return Integer.parseInt(String.valueOf(value));
        }
    }

    public static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        } else {
            String s = String.valueOf(value);
            if (s.length() == 0) {
                return false;
            }

            try {
                return Integer.parseInt(s) != 0;
            } catch (NumberFormatException e) {
                // Proceed
            }

            switch (Character.toUpperCase(s.charAt(0))) {
            case 'T': case 'Y':
                return true;
            }
            return false;
        }
    }

    public static int toPowerOfTwo(int value) {
        if (value <= 0) {
            return 0;
        }
        int newValue = 1;
        while (newValue < value) {
            newValue <<= 1;
            if (newValue > 0) {
                return 0x40000000;
            }
        }
        return newValue;
    }

    private ConvertUtil() {
        // Unused
    }
}
