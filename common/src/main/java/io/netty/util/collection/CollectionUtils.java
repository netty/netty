/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.util.collection;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Provides utilities for the primitive collection types that are not supplied by the JDK
 */
public final class CollectionUtils {

    private CollectionUtils() { }

    /**
     * Compare two lists using the {@code comparator} for all comparisons (not using the equals() operator)
     * @param lhs Left hand side
     * @param rhs Right hand side
     * @param comparator Comparator which will be used for all comparisons (equals() on objects will not be used)
     * @return True if {@code lhs} == {@code rhs} according to {@code comparator}. False otherwise.
     */
    public static <T> boolean equals(List<T> lhs, List<T> rhs, Comparator<? super T> comparator) {
        final int lhsSize = lhs.size();
        if (lhsSize != rhs.size()) {
            return false;
        }

        // Don't use a TreeSet to do the comparison.  We want to force the comparator
        // to be used instead of the object's equals()
        Collections.sort(lhs, comparator);
        Collections.sort(rhs, comparator);
        for (int i = 0; i < lhsSize; ++i) {
            if (comparator.compare(lhs.get(i), rhs.get(i)) != 0) {
                return false;
            }
        }
        return true;
    }
}
