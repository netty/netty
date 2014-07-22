/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.dns;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import org.junit.Test;

public class DnsClassTest {

    private List<DnsClass> allTypes() throws Exception {
        List<DnsClass> result = new ArrayList<DnsClass>();
        for (Field field : DnsClass.class.getDeclaredFields()) {
            if ((field.getModifiers() & Modifier.STATIC) != 0) {
                result.add((DnsClass) field.get(null));
            }
        }
        assertFalse(result.isEmpty());
        return result;
    }

    @Test
    public void testSanity() throws Exception {
        assertEquals("More than one type has the same int value",
                allTypes().size(), new HashSet(allTypes()).size());
    }

    /**
     * Test of hashCode method, of class DnsClass.
     */
    @Test
    public void testHashCode() throws Exception {
        for (DnsClass t : allTypes()) {
            assertEquals(t.clazz, t.hashCode());
        }
    }

    /**
     * Test of equals method, of class DnsClass.
     */
    @Test
    public void testEquals() throws Exception {
        for (DnsClass t1 : allTypes()) {
            for (DnsClass t2 : allTypes()) {
                if (t1 != t2) {
                    assertNotEquals(t1, t2);
                }
            }
        }
    }

    /**
     * Test of find method, of class DnsClass.
     */
    @Test
    public void testFind() throws Exception {
        for (DnsClass t : allTypes()) {
            DnsClass found = DnsClass.find(t.clazz);
            assertSame(t, found);
            found = DnsClass.forName(t.toString());
            assertSame(t.toString(), t, found);
        }
    }
}
