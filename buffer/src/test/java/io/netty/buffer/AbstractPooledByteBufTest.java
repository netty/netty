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

public abstract class AbstractPooledByteBufTest extends AbstractByteBufTest {

    protected abstract ByteBuf alloc(int length);

    @Override
    protected ByteBuf newBuffer(int length) {
        ByteBuf buffer = alloc(length);
        assertEquals(0, buffer.writerIndex());
        assertEquals(0, buffer.readerIndex());
        return buffer;
    }

    @Test
    public void testDiscardMarks() {
        testDiscardMarks(4);
    }

    @Test
    public void testDiscardMarksUnpooled() {
        testDiscardMarks(32 * 1024 * 1024);
    }

    private void testDiscardMarks(int capacity) {
        ByteBuf buf = newBuffer(capacity);
        buf.writeShort(1);

        buf.skipBytes(1);

        buf.markReaderIndex();
        buf.markWriterIndex();
        assertTrue(buf.release());

        ByteBuf buf2 = newBuffer(capacity);

        assertSame(unwrapIfNeeded(buf), unwrapIfNeeded(buf2));

        buf2.writeShort(1);

        buf2.resetReaderIndex();
        buf2.resetWriterIndex();

        assertEquals(0, buf2.readerIndex());
        assertEquals(0, buf2.writerIndex());
        assertTrue(buf2.release());
    }

    private static ByteBuf unwrapIfNeeded(ByteBuf buf) {
        if (buf instanceof AdvancedLeakAwareByteBuf || buf instanceof SimpleLeakAwareByteBuf) {
            return buf.unwrap();
        }
        return buf;
    }
}
