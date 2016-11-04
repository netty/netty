/*
 * Copyright 2016 The Netty Project
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
package io.netty.resolver;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoundRobinInetAddressResolverTest {
    @Test
    public void testToIndex() {
        assertEquals(1, RoundRobinInetAddressResolver.toIndex(1, 5));
        assertEquals(4, RoundRobinInetAddressResolver.toIndex(-1, 5));
        assertEquals(0, RoundRobinInetAddressResolver.toIndex(-5, 5));
        assertEquals(0, RoundRobinInetAddressResolver.toIndex(0, 5));
        assertTrue(RoundRobinInetAddressResolver.toIndex(Integer.MIN_VALUE, 5) > 0);
        assertTrue(RoundRobinInetAddressResolver.toIndex(Integer.MAX_VALUE, 5) > 0);
    }
}
