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

import static org.junit.Assert.*;

public abstract class AbstractPooledByteBufTest extends AbstractByteBufTest {

    protected abstract ByteBuf alloc(int length);

    @Override
    protected ByteBuf newBuffer(int length) {
        ByteBuf buffer = alloc(length);

        // Testing if the writerIndex and readerIndex are correct when allocate and also after we reset the mark.
        assertEquals(0, buffer.writerIndex());
        assertEquals(0, buffer.readerIndex());
        buffer.resetReaderIndex();
        buffer.resetWriterIndex();
        assertEquals(0, buffer.writerIndex());
        assertEquals(0, buffer.readerIndex());
        return buffer;
    }
}
