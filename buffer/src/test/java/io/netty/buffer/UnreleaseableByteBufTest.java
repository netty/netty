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
package io.netty.buffer;

import org.junit.jupiter.api.Test;

import static io.netty.buffer.Unpooled.buffer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnreleaseableByteBufTest {

    @Test
    public void testCantRelease() {
        ByteBuf buf = Unpooled.unreleasableBuffer(Unpooled.copyInt(1));
        assertEquals(1, buf.refCnt());
        assertFalse(buf.release());
        assertEquals(1, buf.refCnt());
        assertFalse(buf.release());
        assertEquals(1, buf.refCnt());

        buf.retain(5);
        assertEquals(1, buf.refCnt());

        buf.retain();
        assertEquals(1, buf.refCnt());

        assertTrue(buf.unwrap().release());
        assertEquals(0, buf.refCnt());
    }

    @Test
    public void testWrappedReadOnly() {
        ByteBuf buf = Unpooled.unreleasableBuffer(buffer(1).asReadOnly());
        assertSame(buf, buf.asReadOnly());

        assertTrue(buf.unwrap().release());
    }
}
