/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConstantPoolTest {

    static final class TestConstant extends AbstractConstant<TestConstant> {
        TestConstant(int id, String name) {
            super(id, name);
        }
    }

    private static final ConstantPool<TestConstant> pool = new ConstantPool<TestConstant>() {
        @Override
        protected TestConstant newConstant(int id, String name) {
            return new TestConstant(id, name);
        }
    };

    @Test(expected = NullPointerException.class)
    public void testCannotProvideNullName() {
        pool.valueOf(null);
    }

    @Test
    @SuppressWarnings("RedundantStringConstructorCall")
    public void testUniqueness() {
        TestConstant a = pool.valueOf(new String("Leroy"));
        TestConstant b = pool.valueOf(new String("Leroy"));
        assertThat(a, is(sameInstance(b)));
    }

    @Test
    public void testIdUniqueness() {
        TestConstant one = pool.valueOf("one");
        TestConstant two = pool.valueOf("two");
        assertThat(one.id(), is(not(two.id())));
    }

    @Test
    public void testCompare() {
        TestConstant a = pool.valueOf("a_alpha");
        TestConstant b = pool.valueOf("b_beta");
        TestConstant c = pool.valueOf("c_gamma");
        TestConstant d = pool.valueOf("d_delta");
        TestConstant e = pool.valueOf("e_epsilon");

        Set<TestConstant> set = new TreeSet<TestConstant>();
        set.add(b);
        set.add(c);
        set.add(e);
        set.add(d);
        set.add(a);

        TestConstant[] array = set.toArray(new TestConstant[0]);
        assertThat(array.length, is(5));

        // Sort by name
        Arrays.sort(array, new Comparator<TestConstant>() {
            @Override
            public int compare(TestConstant o1, TestConstant o2) {
                return o1.name().compareTo(o2.name());
            }
        });

        assertThat(array[0], is(sameInstance(a)));
        assertThat(array[1], is(sameInstance(b)));
        assertThat(array[2], is(sameInstance(c)));
        assertThat(array[3], is(sameInstance(d)));
        assertThat(array[4], is(sameInstance(e)));
    }

    @Test
    public void testComposedName() {
        TestConstant a = pool.valueOf(Object.class, "A");
        assertThat(a.name(), is("java.lang.Object#A"));
    }
}
