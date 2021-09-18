/*
 * Copyright 2012 The Netty Project
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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests big-endian heap channel buffers
 */
public class BigEndianHeapByteBufTest extends AbstractByteBufTest {

    @Override
    protected ByteBuf newBuffer(int length, int maxCapacity) {
        ByteBuf buffer = Unpooled.buffer(length, maxCapacity);
        assertEquals(0, buffer.writerIndex());
        return buffer;
    }

    @Test
    public void shouldNotAllowNullInConstructor1() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                new UnpooledHeapByteBuf(null, new byte[1], 0);
            }
        });
    }

    @Test
    public void shouldNotAllowNullInConstructor2() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, null, 0);
            }
        });
    }
}
