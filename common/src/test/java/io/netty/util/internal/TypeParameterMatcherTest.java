/*
 * Copyright 2013 The Netty Project
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

import org.junit.Test;

import static org.junit.Assert.*;

public class TypeParameterMatcherTest {

    @Test
    public void testSimple() throws Exception {
        Object o = new TypeQ();

        TypeParameterMatcher m;

        m = TypeParameterMatcher.find(o, TypeX.class, "A");
        assertFalse(m.match(new Object()));
        assertFalse(m.match(new A()));
        assertFalse(m.match(new AA()));
        assertTrue(m.match(new AAA()));
        assertFalse(m.match(new B()));
        assertFalse(m.match(new BB()));
        assertFalse(m.match(new BBB()));
        assertFalse(m.match(new C()));
        assertFalse(m.match(new CC()));

        try {
            TypeParameterMatcher.find(o, TypeX.class, "B");
        } catch (IllegalStateException e) {
            // expected
        }

        m = TypeParameterMatcher.find(new TypeQ<BBB>() { }, TypeX.class, "B");
        assertFalse(m.match(new Object()));
        assertFalse(m.match(new A()));
        assertFalse(m.match(new AA()));
        assertFalse(m.match(new AAA()));
        assertFalse(m.match(new B()));
        assertFalse(m.match(new BB()));
        assertTrue(m.match(new BBB()));
        assertFalse(m.match(new C()));
        assertFalse(m.match(new CC()));

        m = TypeParameterMatcher.find(o, TypeX.class, "C");
        assertFalse(m.match(new Object()));
        assertFalse(m.match(new A()));
        assertFalse(m.match(new AA()));
        assertFalse(m.match(new AAA()));
        assertFalse(m.match(new B()));
        assertFalse(m.match(new BB()));
        assertFalse(m.match(new BBB()));
        assertFalse(m.match(new C()));
        assertTrue(m.match(new CC()));
    }

    public static class TypeX<A, B, C> {
        A a;
        B b;
        C c;
    }

    public static class TypeY<D extends C, E extends A, F extends B> extends TypeX<E, F, D> { }

    public static class TypeZ<G extends AA, H extends BB> extends TypeY<CC, G, H> { }

    public static class TypeQ<I extends BBB> extends TypeZ<AAA, I> { }

    @SuppressWarnings("ClassMayBeInterface")
    public static class A { }
    public static class AA extends A { }
    public static class AAA extends AA { }

    @SuppressWarnings("ClassMayBeInterface")
    public static class B { }
    public static class BB extends B { }
    public static class BBB extends BB { }

    @SuppressWarnings("ClassMayBeInterface")
    public static class C { }
    public static class CC extends C { }
}
