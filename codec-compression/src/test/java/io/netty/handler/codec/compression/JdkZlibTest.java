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
package io.netty.handler.codec.compression;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Queue;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class JdkZlibTest extends ZlibTest {

    @Override
    protected ZlibEncoder createEncoder(ZlibWrapper wrapper) {
        return new JdkZlibEncoder(wrapper);
    }

    @Override
    protected ZlibDecoder createDecoder(ZlibWrapper wrapper, int maxAllocation) {
        return new JdkZlibDecoder(wrapper, maxAllocation);
    }

    @Test
    @Override
    public void testZLIB_OR_NONE3() throws Exception {
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                JdkZlibTest.super.testZLIB_OR_NONE3();
            }
        });
    }

    @Test
    // verifies backward compatibility
    public void testConcatenatedStreamsReadFirstOnly() throws IOException {
        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(createDecoder(ZlibWrapper.GZIP));

        try {
            byte[] bytes = IOUtils.toByteArray(getClass().getResourceAsStream("/multiple.gz"));

            assertTrue(chDecoderGZip.writeInbound(Unpooled.copiedBuffer(bytes)));
            Queue<Object> messages = chDecoderGZip.inboundMessages();
            assertEquals(1, messages.size());

            ByteBuf msg = (ByteBuf) messages.poll();
            assertEquals("a", msg.toString(CharsetUtil.UTF_8));
            ReferenceCountUtil.release(msg);
        } finally {
            assertFalse(chDecoderGZip.finish());
            chDecoderGZip.close();
        }
    }

    @Test
    public void testConcatenatedStreamsReadFully() throws IOException {
        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(new JdkZlibDecoder(true, 0));

        try {
            byte[] bytes = IOUtils.toByteArray(getClass().getResourceAsStream("/multiple.gz"));

            assertTrue(chDecoderGZip.writeInbound(Unpooled.copiedBuffer(bytes)));
            Queue<Object> messages = chDecoderGZip.inboundMessages();
            assertEquals(2, messages.size());

            for (String s : Arrays.asList("a", "b")) {
                ByteBuf msg = (ByteBuf) messages.poll();
                assertEquals(s, msg.toString(CharsetUtil.UTF_8));
                ReferenceCountUtil.release(msg);
            }
        } finally {
            assertFalse(chDecoderGZip.finish());
            chDecoderGZip.close();
        }
    }

    @Test
    public void testConcatenatedStreamsReadFullyWhenFragmented() throws IOException {
        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(new JdkZlibDecoder(true, 0));

        try {
            byte[] bytes = IOUtils.toByteArray(getClass().getResourceAsStream("/multiple.gz"));

            // Let's feed the input byte by byte to simulate fragmentation.
            ByteBuf buf = Unpooled.copiedBuffer(bytes);
            boolean written = false;
            while (buf.isReadable()) {
                written |= chDecoderGZip.writeInbound(buf.readRetainedSlice(1));
            }
            buf.release();

            assertTrue(written);
            Queue<Object> messages = chDecoderGZip.inboundMessages();
            assertEquals(2, messages.size());

            for (String s : Arrays.asList("a", "b")) {
                ByteBuf msg = (ByteBuf) messages.poll();
                assertEquals(s, msg.toString(CharsetUtil.UTF_8));
                ReferenceCountUtil.release(msg);
            }
        } finally {
            assertFalse(chDecoderGZip.finish());
            chDecoderGZip.close();
        }
    }

    @Test
    public void testDecodeWithHeaderFollowingFooter() throws Exception {
        byte[] bytes = new byte[1024];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        GZIPOutputStream out = new GZIPOutputStream(bytesOut);
        out.write(bytes);
        out.close();

        byte[] compressed = bytesOut.toByteArray();
        ByteBuf buffer = Unpooled.buffer().writeBytes(compressed).writeBytes(compressed);
        EmbeddedChannel channel = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP, true, 0));
        // Write it into the Channel in a way that we were able to decompress the first data completely but not the
        // whole footer.
        assertTrue(channel.writeInbound(buffer.readRetainedSlice(compressed.length - 1)));
        assertTrue(channel.writeInbound(buffer));
        assertTrue(channel.finish());

        ByteBuf uncompressedBuffer = Unpooled.wrappedBuffer(bytes);
        ByteBuf read = channel.readInbound();
        assertEquals(uncompressedBuffer, read);
        read.release();

        read = channel.readInbound();
        assertEquals(uncompressedBuffer, read);
        read.release();

        assertNull(channel.readInbound());
        uncompressedBuffer.release();
    }

    @Test
    public void testLargeEncode() throws Exception {
        // construct a 128M buffer out of many times the same 1M buffer :)
        byte[] smallArray = new byte[1024 * 1024];
        byte[][] arrayOfArrays = new byte[128][];
        Arrays.fill(arrayOfArrays, smallArray);
        ByteBuf bigBuffer = Unpooled.wrappedBuffer(arrayOfArrays);

        EmbeddedChannel channel = new EmbeddedChannel(new JdkZlibEncoder(ZlibWrapper.NONE));
        channel.config().setAllocator(new LimitedByteBufAllocator(channel.alloc()));
        assertTrue(channel.writeOutbound(bigBuffer));
        assertTrue(channel.finish());
        channel.checkException();
        assertTrue(channel.releaseOutbound());
    }

    /**
     * Allocator that will limit buffer capacity to 1M.
     */
    private static final class LimitedByteBufAllocator extends AbstractByteBufAllocator {
        private static final int MAX = 1024 * 1024;

        private final ByteBufAllocator wrapped;

        LimitedByteBufAllocator(ByteBufAllocator wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public boolean isDirectBufferPooled() {
            return wrapped.isDirectBufferPooled();
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            return wrapped.heapBuffer(initialCapacity, Math.min(maxCapacity, MAX));
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            return wrapped.directBuffer(initialCapacity, Math.min(maxCapacity, MAX));
        }
    }
}
