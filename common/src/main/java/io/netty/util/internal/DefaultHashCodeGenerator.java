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
package io.netty.util.internal;

/**
 * Default implementation of {@link HashCodeGenerator}. This will use a similar mechanism as
 * {@link Arrays#hashCode(byte[])} for the hash code and a direct byte by byte comparison for equals.
 * <p>
 * Note that the hashCode methods must be compatible with {@link DefaultHashCodeGeneratorCaseInsensitive}.
 */
class DefaultHashCodeGenerator extends AbstractHashCodeGenerator {
    @Override
    public int hashCode(byte[] bytes, int startPos, int endPos) {
        int h = emptyHashValue();
        for (int i = startPos; i < endPos; ++i) {
            h = HASH_PRIME * h + (char) (bytes[i] & 0xFF);
        }
        return h;
    }

    @Override
    public boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int len) {
        final int end = startPos1 + len;
        for (int i = startPos1, j = startPos2; i < end; ++i, ++j) {
            if (bytes1[i] != bytes2[j]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode(CharSequence data, int startPos, int endPos) {
        int h = emptyHashValue();
        for (int i = startPos; i < endPos; ++i) {
            h = HASH_PRIME * h + data.charAt(i);
        }
        return h;
    }

    @Override
    public boolean equals(CharSequence bytes1, CharSequence bytes2) {
        // Take advantage of HotSpot intrinsics for String
        if (bytes1.getClass() == String.class && bytes2.getClass() == String.class) {
            return bytes1.equals(bytes2);
        }
        if (bytes1.length() != bytes2.length()) {
            return false;
        }
        return equals(bytes1, 0, bytes2, 0, bytes2.length());
    }

    @Override
    public boolean equals(CharSequence bytes1, int startPos1, CharSequence bytes2, int startPos2, int len) {
        final int end = startPos1 + len;
        for (int i = startPos1, j = startPos2; i < end; ++i, ++j) {
            if (bytes1.charAt(i) != bytes2.charAt(j)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(byte[] bytes1, int startPos1, CharSequence bytes2, int startPos2, int len) {
        final int end = startPos1 + len;
        for (int i = startPos1, j = startPos2; i < end; ++i, ++j) {
            if ((char) (bytes1[i] & 0xFF) != bytes2.charAt(j)) {
                return false;
            }
        }
        return true;
    }
}
