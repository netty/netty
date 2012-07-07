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

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotSame;
import org.junit.Before;
import org.junit.Test;

public class UniqueNameTest {

    /**
     * A {@link ConcurrentHashMap} of registered names.
     * This is set up before each test
     */
    private ConcurrentHashMap<String, Boolean> names;
    
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
        this.names = new ConcurrentHashMap<String, Boolean>();
    }

    @Test
    public void testRegisteringName() {
        registerName("Abcedrian");

        assertTrue(this.names.get("Abcedrian"));
        assertTrue(this.names.get("Hellyes") == null);
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
                    assertNotSame(currentName.id(), otherName.name());
                }
            }
        }
    }

}
