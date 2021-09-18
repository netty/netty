/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.dns;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DnsRecordTypeTest {

    private static List<DnsRecordType> allTypes() throws Exception {
        List<DnsRecordType> result = new ArrayList<DnsRecordType>();
        for (Field field : DnsRecordType.class.getFields()) {
            if ((field.getModifiers() & Modifier.STATIC) != 0 && field.getType() == DnsRecordType.class) {
                result.add((DnsRecordType) field.get(null));
            }
        }
        assertFalse(result.isEmpty());
        return result;
    }

    @Test
    public void testSanity() throws Exception {
        assertEquals(allTypes().size(), new HashSet<DnsRecordType>(allTypes()).size(),
                "More than one type has the same int value");
    }

    /**
     * Test of hashCode method, of class DnsRecordType.
     */
    @Test
    public void testHashCode() throws Exception {
        for (DnsRecordType t : allTypes()) {
            assertEquals(t.intValue(), t.hashCode());
        }
    }

    /**
     * Test of equals method, of class DnsRecordType.
     */
    @Test
    public void testEquals() throws Exception {
        for (DnsRecordType t1 : allTypes()) {
            for (DnsRecordType t2 : allTypes()) {
                if (t1 != t2) {
                    assertNotEquals(t1, t2);
                }
            }
        }
    }

    /**
     * Test of find method, of class DnsRecordType.
     */
    @Test
    public void testFind() throws Exception {
        for (DnsRecordType t : allTypes()) {
            DnsRecordType found = DnsRecordType.valueOf(t.intValue());
            assertSame(t, found);
            found = DnsRecordType.valueOf(t.name());
            assertSame(t, found, t.name());
        }
    }
}
