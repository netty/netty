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
 * Provides the ability to generate a hash code for array based objects.
 */
public interface HashCodeGenerator {
    /**
     * Same as calling {@link #hashCode(byte[], int, int)} as {@code hashCode(bytes, 0, bytes.length)}.
     */
    int hashCode(byte[] bytes);

    /**
     * Generate a hash code from the [{@code startPos}, {@code endPos}) subsection of {@code bytes}.
     */
    int hashCode(byte[] bytes, int startPos, int endPos);

    /**
     * Same as calling {@link #equals(byte[], int, byte[], int, int)} as
     * {@code equals(bytes1, 0, bytes2, 0, bytes2.length)}.
     */
    boolean equals(byte[] bytes1, byte[] bytes2);

    /**
     * Determine if {@code bytes1} from range {@code [startPos1, startPos1 + len)} with {@code bytes2} from range
     * {@code [startPos2, startPos2 + len)} are equal.
     */
    boolean equals(byte[] bytes1, int startPos1, byte[] bytes2, int startPos2, int len);

    /**
     * Same as calling {@link #hashCode(CharSequence, int, int)} as {@code hashCode(data, 0, data.length())}.
     */
    int hashCode(CharSequence data);

    /**
     * Generate a hash code from the [{@code startPos}, {@code endPos}) subsection of {@code bytes}.
     */
    int hashCode(CharSequence data, int startPos, int endPos);

    /**
     * Same as calling {@link #equals(CharSequence, int, int, CharSequence, int, int)} as
     * {@code equals(bytes1, 0, bytes1.length(), bytes2, 0, bytes2.length())}.
     */
    boolean equals(CharSequence bytes1, CharSequence bytes2);

    /**
     * Determine if {@code bytes1} from range {@code [startPos1, startPos1 + len)} with {@code bytes2} from range
     * {@code [startPos2, startPos2 + len)} are equal.
     */
    boolean equals(CharSequence bytes1, int startPos1, CharSequence bytes2, int startPos2, int len);

    /**
     * Same as calling {@link #equals(byte[], int, CharSequence, int, int)} as
     * {@code equals(bytes1, 0, bytes2, 0, bytes2.length())}.
     */
    boolean equals(byte[] bytes1, CharSequence bytes2);

    /**
     * Determine if {@code bytes1} from range {@code [startPos1, startPos1 + len)} with {@code bytes2} from range
     * {@code [startPos2, startPos2 + len)} are equal.
     */
    boolean equals(byte[] bytes1, int startPos1, CharSequence bytes2, int startPos2, int len);

    /**
     * Get the value that is returned when there is nothing to hash.
     */
    int emptyHashValue();
}
