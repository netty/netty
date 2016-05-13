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
package io.netty.buffer;

import org.junit.Test;

import static org.junit.Assert.*;

public class DefaultByteBufHolderTest {

    @Test
    public void testToString() {
        ByteBufHolder holder = new DefaultByteBufHolder(Unpooled.buffer());
        assertEquals(1, holder.refCnt());
        assertNotNull(holder.toString());
        assertTrue(holder.release());
        assertNotNull(holder.toString());
    }

    @Test
    public void testEqualsAndHashCode() {
        ByteBufHolder holder = new DefaultByteBufHolder(Unpooled.EMPTY_BUFFER);
        ByteBufHolder copy = holder.copy();
        try {
            assertEquals(holder, copy);
            assertEquals(holder.hashCode(), copy.hashCode());
        } finally {
            holder.release();
            copy.release();
        }
    }
}
