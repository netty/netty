/*
 * Copyright 2024 The Netty Project
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
package io.netty.resolver.dns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsQueryIdSpaceTest {

    @Test
    public void testOverflow() {
        final DnsQueryIdSpace ids = new DnsQueryIdSpace();
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                ids.pushId(1);
            }
        });
    }

    @Test
    public void testConsumeAndProduceAll() {
        final DnsQueryIdSpace ids = new DnsQueryIdSpace();
        assertEquals(ids.maxUsableIds(), ids.usableIds());
        Set<Integer> producedIdRound1 = new LinkedHashSet<Integer>();
        Set<Integer> producedIdRound2 = new LinkedHashSet<Integer>();

        for (int i = ids.maxUsableIds(); i > 0; i--) {
            int id = ids.nextId();
            assertTrue(id >= 0);
            assertTrue(producedIdRound1.add(id));
        }
        assertEquals(0, ids.usableIds());
        assertEquals(-1, ids.nextId());

        for (Integer v :  producedIdRound1) {
            ids.pushId(v);
        }

        for (int i = ids.maxUsableIds(); i > 0; i--) {
            int id = ids.nextId();
            assertTrue(id >= 0);
            assertTrue(producedIdRound2.add(id));
        }

        assertEquals(producedIdRound1.size(), producedIdRound2.size());

        Iterator<Integer> producedIdRoundIt = producedIdRound1.iterator();
        Iterator<Integer> producedIdRound2It = producedIdRound2.iterator();

        boolean notSame = false;
        while (producedIdRoundIt.hasNext()) {
            if (producedIdRoundIt.next().intValue() != producedIdRound2It.next().intValue()) {
                notSame = true;
                break;
            }
        }
        assertTrue(notSame);
    }
}
