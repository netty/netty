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
package net.gleamynode.netty.array;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 */
public class ByteArrayUtil {

    public static int hashCode(ByteArray a) {
        final int intCount = a.length() / 4;
        final int byteCount = a.length() % 4;

        int hashCode = 1;
        int arrayIndex = a.firstIndex();
        for (int i = intCount; i > 0; i --) {
            hashCode = 31 * hashCode + a.getBE32(arrayIndex);
            arrayIndex += 4;
        }
        for (int i = byteCount; i > 0; i --) {
            hashCode = 31 * hashCode + a.get8(arrayIndex ++);
        }

        if (hashCode == 0) {
            hashCode = 1;
        }
        return hashCode;
    }

    public static boolean equals(ByteArray a, ByteArray b) {
        if (a.length() != b.length()) {
            return false;
        }

        final int longCount = a.length() / 8;
        final int byteCount = a.length() % 8;

        if (a.firstIndex() == b.firstIndex()) {
            int aIndex = a.firstIndex();
            for (int i = longCount; i > 0; i --) {
                if (a.getBE64(aIndex) != b.getBE64(aIndex)) {
                    return false;
                }
                aIndex += 8;
            }
            for (int i = byteCount; i > 0; i --) {
                if (a.get8(aIndex) != b.get8(aIndex)) {
                    return false;
                }
                aIndex ++;
            }
        } else {
            int aIndex = a.firstIndex();
            int bIndex = b.firstIndex();
            for (int i = longCount; i > 0; i --) {
                if (a.getBE64(aIndex) != b.getBE64(bIndex)) {
                    return false;
                }
                aIndex += 8;
                bIndex += 8;
            }
            for (int i = byteCount; i > 0; i --) {
                if (a.get8(aIndex ++) != b.get8(bIndex ++)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static int compare(ByteArray a, ByteArray b) {
        final int minLength = Math.min(a.length(), b.length());
        final int longCount = minLength / 8;
        final int byteCount = minLength % 8;

        if (a.firstIndex() == b.firstIndex()) {
            int aIndex = a.firstIndex();
            for (int i = longCount; i > 0; i --) {
                long va = a.getBE64(aIndex);
                long vb = b.getBE64(aIndex);
                if (va > vb) {
                    return 1;
                } else if (va < vb) {
                    return -1;
                }
                aIndex += 8;
            }
            for (int i = byteCount; i > 0; i --) {
                byte va = a.get8(aIndex);
                byte vb = b.get8(aIndex);
                if (va > vb) {
                    return 1;
                } else if (va < vb) {
                    return -1;
                }
                aIndex ++;
            }
        } else {
            int aIndex = a.firstIndex();
            int bIndex = b.firstIndex();
            for (int i = longCount; i > 0; i --) {
                long va = a.getBE64(aIndex);
                long vb = b.getBE64(bIndex);
                if (va > vb) {
                    return 1;
                } else if (va < vb) {
                    return -1;
                }
                aIndex += 8;
                bIndex += 8;
            }
            for (int i = byteCount; i > 0; i --) {
                byte va = a.get8(aIndex ++);
                byte vb = b.get8(bIndex ++);
                if (va > vb) {
                    return 1;
                } else if (va < vb) {
                    return -1;
                }
            }
        }

        return a.length() - b.length();
    }

    public static long indexOf(ByteArray array, int fromIndex, byte value) {
        if (fromIndex < array.firstIndex()) {
            fromIndex = array.firstIndex();
        }

        int toIndex = array.endIndex();
        if (fromIndex >= toIndex || array.empty()) {
            return ByteArray.NOT_FOUND;
        }

        for (int i = fromIndex; i < toIndex; i ++) {
            if (array.get8(i) == value) {
                return i;
            }
        }

        return ByteArray.NOT_FOUND;
    }

    public static long lastIndexOf(ByteArray array, int fromIndex, byte value) {
        if (fromIndex > array.endIndex()) {
            fromIndex = array.endIndex();
        }
        int toIndex = array.firstIndex();
        if (fromIndex < toIndex || array.empty()) {
            return ByteArray.NOT_FOUND;
        }

        for (int i = fromIndex - 1; i >= toIndex; i --) {
            if (array.get8(i) == value) {
                return i;
            }
        }

        return ByteArray.NOT_FOUND;
    }

    public static long indexOf(ByteArray array, int fromIndex, ByteArrayIndexFinder indexFinder) {
        if (fromIndex < array.firstIndex()) {
            fromIndex = array.firstIndex();
        }

        int toIndex = array.endIndex();
        if (fromIndex >= toIndex || array.empty()) {
            return ByteArray.NOT_FOUND;
        }

        for (int i = fromIndex; i < toIndex; i ++) {
            if (indexFinder.find(array, i)) {
                return i;
            }
        }

        return ByteArray.NOT_FOUND;
    }

    public static long lastIndexOf(ByteArray array, int fromIndex, ByteArrayIndexFinder indexFinder) {
        if (fromIndex > array.endIndex()) {
            fromIndex = array.endIndex();
        }
        int toIndex = array.firstIndex();
        if (fromIndex < toIndex || array.empty()) {
            return ByteArray.NOT_FOUND;
        }

        for (int i = fromIndex - 1; i >= toIndex; i --) {
            if (indexFinder.find(array, i)) {
                return i;
            }
        }

        return ByteArray.NOT_FOUND;
    }

    private ByteArrayUtil() {
        // Unused
    }
}
