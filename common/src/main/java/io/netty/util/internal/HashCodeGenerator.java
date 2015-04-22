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
     * Same as calling {@link #hashCodeAsBytes(char[], int, int)} as {@code hashCode(bytes, 0, bytes.length)}.
     */
    int hashCodeAsBytes(char[] bytes);

    /**
     * Generate a hash code from the [{@code startPos}, {@code endPos}) subsection of {@code bytes}.
     * <p>
     * This method will treat the {@code byte[]} as though it was a {@code byte[]}. This means that the
     * Most Significant Byte of each {@code char} will be ignored. This is useful when a {@code char[]} is used to
     * represent a {@code byte[]} and the results need to be equivalent when hashing the two different types.
     * <p>
     * This method is preferred over {@link #hashCodeAsBytes(CharSequence, int, int, int)} because arrays have more
     * unsafe support for optimizations.
     */
    int hashCodeAsBytes(char[] bytes, int startPos, int endPos);

    /**
     * Same as calling {@link #hashCodeAsBytes(CharSequence, int, int)} as {@code hashCode(data, 0, data.length())}.
     */
    int hashCodeAsBytes(CharSequence data);

    /**
     * Generate a hash code from the [{@code startPos}, {@code endPos}) subsection of {@code bytes}.
     * <p>
     * This method will treat the {@code byte[]} as though it was a {@code byte[]}. This means that the
     * Most Significant Byte of each {@code char} will be ignored. This is useful when a {@code char[]} is used to
     * represent a {@code byte[]} and the results need to be equivalent when hashing the two different types.
     */
    int hashCodeAsBytes(CharSequence data, int startPos, int endPos);

    /**
     * Get the value that is returned when there is nothing to hash.
     */
    int emptyHashValue();
}
