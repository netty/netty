/*
 * Copyright 2013 The Netty Project
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

package io.netty.util.internal;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.*;

public class TypeParameterMatcherTest {

    @Test
    public void testConcreteClass() throws Exception {
        TypeParameterMatcher m = TypeParameterMatcher.find(new TypeQ(), TypeX.class, "A");
        assertFalse(m.match(new Object()));
        assertFalse(m.match(new A()));
        assertFalse(m.match(new AA()));
        assertTrue(m.match(new AAA()));
        assertFalse(m.match(new B()));
        assertFalse(m.match(new BB()));
        assertFalse(m.match(new BBB()));
        assertFalse(m.match(new C()));
        assertFalse(m.match(new CC()));
    }

    @Test(expected = IllegalStateException.class)
    public void testUnsolvedParameter() throws Exception {
        TypeParameterMatcher.find(new TypeQ(), TypeX.class, "B");
    }

    @Test
    public void testAnonymousClass() throws Exception {
        TypeParameterMatcher m = TypeParameterMatcher.find(new TypeQ<BBB>() { }, TypeX.class, "B");
        assertFalse(m.match(new Object()));
        assertFalse(m.match(new A()));
        assertFalse(m.match(new AA()));
        assertFalse(m.match(new AAA()));
        assertFalse(m.match(new B()));
        assertFalse(m.match(new BB()));
        assertTrue(m.match(new BBB()));
        assertFalse(m.match(new C()));
        assertFalse(m.match(new CC()));
    }

    @Test
    public void testAbstractClass() throws Exception {
        TypeParameterMatcher m = TypeParameterMatcher.find(new TypeQ(), TypeX.class, "C");
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

    public abstract static class TypeZ<G extends AA, H extends BB> extends TypeY<CC, G, H> { }

    public static class TypeQ<I extends BBB> extends TypeZ<AAA, I> { }

    public static class A { }
    public static class AA extends A { }
    public static class AAA extends AA { }

    public static class B { }
    public static class BB extends B { }
    public static class BBB extends BB { }

    public static class C { }
    public static class CC extends C { }

    @Test
    public void testInaccessibleClass() throws Exception {
        TypeParameterMatcher m = TypeParameterMatcher.find(new U<T>() { }, U.class, "E");
        assertFalse(m.match(new Object()));
        assertTrue(m.match(new T()));
    }

    private static class T { }
    private static class U<E> { E a; }

    @Test
    public void testArrayAsTypeParam() throws Exception {
        TypeParameterMatcher m = TypeParameterMatcher.find(new U<byte[]>() { }, U.class, "E");
        assertFalse(m.match(new Object()));
        assertTrue(m.match(new byte[1]));
    }

    @Test
    public void testRawType() throws Exception {
        TypeParameterMatcher m = TypeParameterMatcher.find(new U() { }, U.class, "E");
        assertTrue(m.match(new Object()));
    }

    private static class V<E> {
        U<E> u = new U<E>() { };
    }

    @Test
    public void testInnerClass() throws Exception {
        TypeParameterMatcher m = TypeParameterMatcher.find(new V<String>().u, U.class, "E");
        assertTrue(m.match(new Object()));
    }

    private abstract static class W<E> {
        E e;
    }

    private static class X<T, E> extends W<E> {
        T t;
    }

    @Test(expected = IllegalStateException.class)
    public void testErasure() throws Exception {
        TypeParameterMatcher m = TypeParameterMatcher.find(new X<String, Date>(), W.class, "E");
        assertTrue(m.match(new Date()));
        assertFalse(m.match(new Object()));
    }
}
