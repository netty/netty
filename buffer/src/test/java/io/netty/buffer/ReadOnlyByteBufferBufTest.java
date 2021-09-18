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

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReadOnlyByteBufferBufTest extends ReadOnlyDirectByteBufferBufTest {
    @Override
    protected ByteBuffer allocate(int size) {
        return ByteBuffer.allocate(size);
    }

    @Test
    public void testCopyDirect() {
        testCopy(true);
    }

    @Test
    public void testCopyHeap() {
        testCopy(false);
    }

    private static void testCopy(boolean direct) {
        byte[] bytes = new byte[1024];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);

        ByteBuffer nioBuffer = direct ? ByteBuffer.allocateDirect(bytes.length) : ByteBuffer.allocate(bytes.length);
        nioBuffer.put(bytes).flip();

        ByteBuf buf = new ReadOnlyByteBufferBuf(UnpooledByteBufAllocator.DEFAULT, nioBuffer.asReadOnlyBuffer());
        ByteBuf copy = buf.copy();

        assertEquals(buf, copy);
        assertEquals(buf.alloc(), copy.alloc());
        assertEquals(buf.isDirect(), copy.isDirect());

        copy.release();
        buf.release();
    }
}
