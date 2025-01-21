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
package io.netty.buffer;


import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AdaptiveLittleEndianHeapByteBufTest extends AbstractAdaptiveByteBufTest {

    @Override
    protected ByteBuf alloc(AdaptiveByteBufAllocator allocator, int length, int maxCapacity) {
        ByteBuf buffer = allocator.heapBuffer(length, maxCapacity)
                .order(ByteOrder.LITTLE_ENDIAN);
        assertSame(ByteOrder.LITTLE_ENDIAN, buffer.order());
        assertFalse(buffer.isDirect());
        return buffer;
    }
}
