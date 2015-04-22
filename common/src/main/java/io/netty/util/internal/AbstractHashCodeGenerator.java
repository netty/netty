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
 * Provides methods which don't take start/end positions from {@link HashCodeGenerator} and delegates to methods
 * that do.
 */
public abstract class AbstractHashCodeGenerator implements HashCodeGenerator {
    protected static final int HASH_PRIME = 31;

    @Override
    public final int hashCode(byte[] bytes) {
        return hashCode(bytes, 0, bytes.length);
    }

    @Override
    public final boolean equals(byte[] bytes1, byte[] bytes2) {
        if (bytes1.length != bytes2.length) {
            return false;
        }
        return equals(bytes1, 0, bytes2, 0, bytes2.length);
    }

    @Override
    public final int hashCode(CharSequence data) {
        return hashCode(data, 0, data.length());
    }

    @Override
    public boolean equals(CharSequence bytes1, CharSequence bytes2) {
        if (bytes1.length() != bytes2.length()) {
            return false;
        }
        return equals(bytes1, 0, bytes2, 0, bytes2.length());
    }

    @Override
    public boolean equals(byte[] bytes1, CharSequence bytes2) {
        if (bytes1.length != bytes2.length()) {
            return false;
        }
        return equals(bytes1, 0, bytes2, 0, bytes1.length);
    }

    @Override
    public int emptyHashValue() {
        return 1;
    }
}
