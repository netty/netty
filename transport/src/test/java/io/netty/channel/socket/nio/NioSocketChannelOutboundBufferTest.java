/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.socket.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.util.CharsetUtil;
import org.easymock.EasyMock;
import org.junit.Test;

import java.nio.ByteBuffer;

import static io.netty.buffer.Unpooled.*;
import static org.junit.Assert.*;

public class NioSocketChannelOutboundBufferTest {

    @Test
    public void testEmptyNioBuffers() {
        NioSocketChannelConfig config = EasyMock.createMock(NioSocketChannelConfig.class);
        EasyMock.expect(config.getWriteBufferHighWaterMark()).andReturn(Integer.MAX_VALUE).anyTimes();
        EasyMock.expect(config.getWriteBufferLowWaterMark()).andReturn(Integer.MAX_VALUE).anyTimes();
        EasyMock.expect(config.getMessageSizeEstimator()).andReturn(DefaultMessageSizeEstimator.DEFAULT).anyTimes();
        EasyMock.expect(config.isWriteBufferAutoMerge()).andReturn(true).anyTimes();

        NioSocketChannel channel = EasyMock.createMock(NioSocketChannel.class);
        EasyMock.expect(channel.config()).andReturn(config).anyTimes();

        ChannelPromise promise = EasyMock.createMock(ChannelPromise.class);
        EasyMock.expect(promise.trySuccess()).andReturn(true).anyTimes();
        EasyMock.expect(promise.channel()).andReturn(channel).anyTimes();

        EasyMock.replay(config, promise, channel);

        NioSocketChannelOutboundBuffer buffer = NioSocketChannelOutboundBuffer.newBuffer(channel);
        assertEquals(0, buffer.nioBufferCount());
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertEquals(32, buffers.length);
        for (ByteBuffer b: buffers) {
            assertNull(b);
        }
        assertEquals(0, buffer.nioBufferCount());
        release(buffer);
        EasyMock.verify(config, promise, channel);
    }

    @Test
    public void testNioBuffersSingleBacked() {
        NioSocketChannelConfig config = EasyMock.createMock(NioSocketChannelConfig.class);
        EasyMock.expect(config.getWriteBufferHighWaterMark()).andReturn(Integer.MAX_VALUE).anyTimes();
        EasyMock.expect(config.getWriteBufferLowWaterMark()).andReturn(Integer.MAX_VALUE).anyTimes();
        EasyMock.expect(config.getMessageSizeEstimator()).andReturn(DefaultMessageSizeEstimator.DEFAULT).anyTimes();
        EasyMock.expect(config.isWriteBufferAutoMerge()).andReturn(true).anyTimes();

        NioSocketChannel channel = EasyMock.createMock(NioSocketChannel.class);
        EasyMock.expect(channel.config()).andReturn(config).anyTimes();

        ChannelPromise promise = EasyMock.createMock(ChannelPromise.class);
        EasyMock.expect(promise.trySuccess()).andReturn(true).anyTimes();
        EasyMock.expect(promise.channel()).andReturn(channel).anyTimes();

        EasyMock.replay(config, promise, channel);

        NioSocketChannelOutboundBuffer buffer = NioSocketChannelOutboundBuffer.newBuffer(channel);
        assertEquals(0, buffer.nioBufferCount());
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertEquals(32, buffers.length);
        for (ByteBuffer b: buffers) {
            assertNull(b);
        }
        assertEquals(0, buffer.nioBufferCount());

        ByteBuf buf = copiedBuffer("buf1", CharsetUtil.US_ASCII);
        ByteBuffer nioBuf = buf.internalNioBuffer(0, buf.readableBytes());
        buffer.addMessage(buf, buf.readableBytes(), promise);
        buffers = buffer.nioBuffers();
        assertEquals("Should still be 0 as not flushed yet", 0, buffer.nioBufferCount());
        for (ByteBuffer b: buffers) {
            assertNull(b);
        }
        buffer.addFlush();
        buffers = buffer.nioBuffers();
        assertEquals(32, buffers.length);
        assertEquals("Should still be 0 as not flushed yet", 1, buffer.nioBufferCount());
        for (int i = 0;  i < buffers.length; i++) {
            if (i == 0) {
                assertEquals(buffers[0], nioBuf);
            } else {
                assertNull(buffers[i]);
            }
        }
        release(buffer);
        EasyMock.verify(config, promise, channel);
    }

    @Test
    public void testNioBuffersExpand() {
        NioSocketChannelConfig config = EasyMock.createMock(NioSocketChannelConfig.class);
        EasyMock.expect(config.getWriteBufferHighWaterMark()).andReturn(Integer.MAX_VALUE).anyTimes();
        EasyMock.expect(config.getWriteBufferLowWaterMark()).andReturn(Integer.MAX_VALUE).anyTimes();
        EasyMock.expect(config.getMessageSizeEstimator()).andReturn(DefaultMessageSizeEstimator.DEFAULT).anyTimes();
        EasyMock.expect(config.isWriteBufferAutoMerge()).andReturn(true).anyTimes();

        NioSocketChannel channel = EasyMock.createMock(NioSocketChannel.class);
        EasyMock.expect(channel.config()).andReturn(config).anyTimes();

        ChannelPromise promise = EasyMock.createMock(ChannelPromise.class);
        EasyMock.expect(promise.trySuccess()).andReturn(true).anyTimes();
        EasyMock.expect(promise.channel()).andReturn(channel).anyTimes();

        EasyMock.replay(config, promise, channel);

        NioSocketChannelOutboundBuffer buffer = NioSocketChannelOutboundBuffer.newBuffer(channel);

        ByteBuf buf = directBuffer().writeBytes("buf1".getBytes(CharsetUtil.US_ASCII));
        for (int i = 0; i < 64; i++) {
            buffer.addMessage(buf.copy(), buf.readableBytes(), promise);
        }
        ByteBuffer[] nioBuffers = buffer.nioBuffers();
        assertEquals("Should still be 0 as not flushed yet", 0, buffer.nioBufferCount());
        for (ByteBuffer b: nioBuffers) {
            assertNull(b);
        }
        buffer.addFlush();
        nioBuffers = buffer.nioBuffers();
        assertEquals(64, nioBuffers.length);
        assertEquals(64, buffer.nioBufferCount());
        for (ByteBuffer nioBuf: nioBuffers) {
            assertEquals(nioBuf, buf.internalNioBuffer(0, buf.readableBytes()));
        }
        release(buffer);
        buf.release();
        EasyMock.verify(config, promise, channel);
    }

    @Test
    public void testNioBuffersExpand2() {
        NioSocketChannelConfig config = EasyMock.createMock(NioSocketChannelConfig.class);
        EasyMock.expect(config.getWriteBufferHighWaterMark()).andReturn(Integer.MAX_VALUE).anyTimes();
        EasyMock.expect(config.getWriteBufferLowWaterMark()).andReturn(Integer.MAX_VALUE).anyTimes();
        EasyMock.expect(config.getMessageSizeEstimator()).andReturn(DefaultMessageSizeEstimator.DEFAULT).anyTimes();
        EasyMock.expect(config.isWriteBufferAutoMerge()).andReturn(true).anyTimes();

        NioSocketChannel channel = EasyMock.createMock(NioSocketChannel.class);
        EasyMock.expect(channel.config()).andReturn(config).anyTimes();

        ChannelPromise promise = EasyMock.createMock(ChannelPromise.class);
        EasyMock.expect(promise.trySuccess()).andReturn(true).anyTimes();
        EasyMock.expect(promise.channel()).andReturn(channel).anyTimes();

        EasyMock.replay(config, promise, channel);

        NioSocketChannelOutboundBuffer buffer = NioSocketChannelOutboundBuffer.newBuffer(channel);

        CompositeByteBuf comp = compositeBuffer(256);
        ByteBuf buf = directBuffer().writeBytes("buf1".getBytes(CharsetUtil.US_ASCII));
        for (int i = 0; i < 65; i++) {
            comp.addComponent(buf.copy()).writerIndex(comp.writerIndex() + buf.readableBytes());
        }
        buffer.addMessage(comp, comp.readableBytes(), promise);

        ByteBuffer[] buffers = buffer.nioBuffers();
        assertEquals("Should still be 0 as not flushed yet", 0, buffer.nioBufferCount());
        for (ByteBuffer b: buffers) {
            assertNull(b);
        }
        buffer.addFlush();
        buffers = buffer.nioBuffers();
        assertEquals(128, buffers.length);
        assertEquals(65, buffer.nioBufferCount());
        for (int i = 0;  i < buffers.length; i++) {
            if (i < 65) {
                assertEquals(buffers[i], buf.internalNioBuffer(0, buf.readableBytes()));
            } else {
                assertNull(buffers[i]);
            }
        }
        release(buffer);
        buf.release();
        EasyMock.verify(config, promise, channel);
    }

    private static void release(ChannelOutboundBuffer buffer) {
        for (;;) {
            if (!buffer.remove()) {
                break;
            }
        }
    }
}
