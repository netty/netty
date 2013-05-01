/*
 * Copyright 2012 The Netty Project
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
package io.netty.util;

import io.netty.util.internal.PlatformDependent;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.*;

public class UniqueNameTest {

    /**
     * A {@link ConcurrentMap} of registered names.
     * This is set up before each test
     */
    private ConcurrentMap<String, Boolean> names;

    /**
     * Registers a {@link UniqueName}
     *
     * @param name the name being registered
     * @return the unique name
     */
    public UniqueName registerName(String name) {
        return new UniqueName(names, name);
    }

    @Before
    public void initializeTest() {
        names = PlatformDependent.newConcurrentHashMap();
    }

    @Test(expected = NullPointerException.class)
    public void testCannnotProvideNullMap() {
        new UniqueName(null, "Nothing");
    }

    @Test(expected = NullPointerException.class)
    public void testCannotProvideNullName() {
        new UniqueName(names, null);
    }

    @Test
    public void testArgsCanBePassed() {
        new UniqueName(names, "Argh, matey!", 2, 5, new Object());
    }

    @Test
    public void testRegisteringName() {
        registerName("Abcedrian");

        assertTrue(names.get("Abcedrian"));
        assertNull(names.get("Hellyes"));
    }

    @Test
    public void testNameUniqueness() {
        registerName("Leroy");
        boolean failed = false;
        try {
            registerName("Leroy");
        } catch (IllegalArgumentException ex) {
            failed = true;
        }
        assertTrue(failed);
    }

    @Test
    public void testIDUniqueness() {
        UniqueName one = registerName("one");
        UniqueName two = registerName("two");
        assertNotSame(one.id(), two.id());

        ArrayList<UniqueName> nameList = new ArrayList<UniqueName>();

        for (int index = 0; index < 2500; index++) {
            UniqueName currentName = registerName("test" + index);
            nameList.add(currentName);
            for (UniqueName otherName : nameList) {
                if (!currentName.name().equals(otherName.name())) {
                    assertNotSame(currentName, otherName);
                    assertNotSame(currentName.hashCode(), otherName.hashCode());
                    assertFalse(currentName.equals(otherName));
                    assertNotSame(currentName.toString(), otherName.toString());
                }
            }
        }
    }

    @Test
    public void testCompareNames() {
        UniqueName one = registerName("One");
        UniqueName two = registerName("Two");

        ConcurrentMap<String, Boolean> mapTwo = PlatformDependent.newConcurrentHashMap();

        UniqueName three = new UniqueName(mapTwo, "One");

        assertSame(one.compareTo(one), 0);
        assertSame(one.compareTo(two), -5);
        assertSame(one.compareTo(three), -1);
        assertSame(three.compareTo(one), 1);
    }

}
