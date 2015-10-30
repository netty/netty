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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PooledDuplicatedByteBufTest extends DuplicateByteBufTest {
    @Override
    protected ByteBuf newBuffer(int length) {
        ByteBuf wrapped = Unpooled.buffer(length);
        ByteBuf buffer = PooledDuplicatedByteBuf.newInstance(wrapped);
        assertEquals(wrapped.writerIndex(), buffer.writerIndex());
        assertEquals(wrapped.readerIndex(), buffer.readerIndex());

        assertEquals(2, buffer.refCnt());
        assertEquals(2, buffer.unwrap().refCnt());
        assertFalse(buffer.release());
        assertEquals(1, buffer.refCnt());
        assertEquals(1, buffer.unwrap().refCnt());
        return buffer;
    }
}
