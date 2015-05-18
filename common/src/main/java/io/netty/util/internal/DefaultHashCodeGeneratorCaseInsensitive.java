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

import static io.netty.util.internal.StringUtil.asciiToLowerCase;

/**
 * Default implementation of {@link HashCodeGenerator} which will ignore case during hash code generation and
 * equality comparison operations.
 * <p>
 * Note that the hashCode methods must be compatible with {@link DefaultHashCodeGenerator}.
 */
class DefaultHashCodeGeneratorCaseInsensitive extends AbstractHashCodeGenerator {
    @Override
    public int hashCode(byte[] bytes, int startPos, int endPos) {
        int h = emptyHashValue();
        for (int i = startPos; i < endPos; ++i) {
            h = HASH_PRIME * h + (char) (asciiToLowerCase(bytes[i]) & 0xFF);
        }
        return h;
    }

    @Override
    public boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int len) {
        final int end = startPos1 + len;
        for (int i = startPos1, j = startPos2; i < end; ++i, ++j) {
            byte b1 = bytes1[i];
            byte b2 = bytes2[j];
            if (b1 != b2 && asciiToLowerCase(b1) != asciiToLowerCase(b2)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode(CharSequence data, int startPos, int endPos) {
        int h = emptyHashValue();
        for (int i = startPos; i < endPos; ++i) {
            h = HASH_PRIME * h + asciiToLowerCase(data.charAt(i));
        }
        return h;
    }

    @Override
    public boolean equals(CharSequence bytes1, int startPos1, CharSequence bytes2, int startPos2, int len) {
        final int end = startPos1 + len;
        for (int i = startPos1, j = startPos2; i < end; ++i, ++j) {
            char c1 = bytes1.charAt(i);
            char c2 = bytes2.charAt(j);
            if (c1 != c2 && asciiToLowerCase(c1) != asciiToLowerCase(c2)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(byte[] bytes1, int startPos1, CharSequence bytes2, int startPos2, int len) {
        final int end = startPos1 + len;
        for (int i = startPos1, j = startPos2; i < end; ++i, ++j) {
            char c1 = (char) (bytes1[i] & 0xFF);
            char c2 = bytes2.charAt(j);
            if (c1 != c2 && asciiToLowerCase(c1) != asciiToLowerCase(c2)) {
                return false;
            }
        }
        return true;
    }
}
