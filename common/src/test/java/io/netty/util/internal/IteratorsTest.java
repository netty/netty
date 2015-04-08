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

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Test for {@link Iterators}.
 */
public class IteratorsTest {

    @Test
    public void iterateOverArrayShouldSucceed() {
        Integer[] in = {1, 2, 3};
        Integer[] out = values(Iterators.newIterator(in));
        assertArrayEquals(in, out);
    }

    @Test
    public void iterateOverPartialArrayShouldSucceed() {
        Integer[] in = {1, 2, 3, 4};

        Integer[] out = values(Iterators.newIterator(in, 1, 2));
        assertArrayEquals(new Integer[]{2, 3}, out);

        out = values(Iterators.newIterator(in, 0, 1));
        assertArrayEquals(new Integer[]{1}, out);

        out = values(Iterators.newIterator(in, 3, 1));
        assertArrayEquals(new Integer[]{4}, out);
    }

    private static Integer[] values(Iterator<Integer> iter) {
        List<Integer> values = new ArrayList<Integer>();
        while (iter.hasNext()) {
            values.add(iter.next());
        }
        return values.toArray(new Integer[values.size()]);
    }
}
