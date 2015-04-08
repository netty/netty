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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Utilities to support iterator creation.
 */
public class Iterators {

    /**
     * Creates a new {@link Iterator} for iteration over the given array.
     */
    public static <T> Iterator<T> newIterator(final T[] values) {
        checkNotNull(values, "values");
        return newIterator(values, 0, values.length);
    }

    /**
     * Creates a new {@link Iterator} for iteration over the given array.
     */
    public static <T> Iterator<T> newIterator(final T[] values, final int offset, int length) {
        checkNotNull(values, "values");
        if (offset < 0 || offset >= values.length) {
            throw new ArrayIndexOutOfBoundsException("offset outside of array bounds: " + offset);
        }
        if (length == 0) {
            return Collections.emptyIterator();
        }

        // Determine the end index (exclusive).
        final int end = offset + length;
        if (end < offset || end > values.length) {
            throw new ArrayIndexOutOfBoundsException("Requested range exceeds array boundary");
        }

        return new Iterator<T>() {
            int index = offset;

            @Override
            public boolean hasNext() {
                return index < end;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return values[index++];
            }
        };
    }
}
