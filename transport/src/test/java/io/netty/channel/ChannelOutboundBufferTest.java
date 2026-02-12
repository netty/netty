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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty.buffer.Unpooled.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChannelOutboundBufferTest {

    @Test
    public void testEmptyNioBuffers() {
        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(ImmediateEventExecutor.INSTANCE);
        assertEquals(0, buffer.nioBufferCount());
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertNotNull(buffers);
        for (ByteBuffer b: buffers) {
            assertNull(b);
        }
        assertEquals(0, buffer.nioBufferCount());
        release(buffer);
    }

    @Test
    public void testNioBuffersSingleBacked() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel.executor());
        assertEquals(0, buffer.nioBufferCount());

        ByteBuf buf = copiedBuffer("buf1", CharsetUtil.US_ASCII);
        ByteBuffer nioBuf = buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes());
        buffer.addMessage(buf, buf.readableBytes(), CompletionHandler.ignore());
        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertNotNull(buffers);
        assertEquals(1, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        for (int i = 0;  i < buffer.nioBufferCount(); i++) {
            if (i == 0) {
                assertEquals(buffers[i], nioBuf);
            } else {
                assertNull(buffers[i]);
            }
        }
        release(buffer);
    }

    @Test
    public void testNioBuffersExpand() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel.executor());

        ByteBuf buf = directBuffer().writeBytes("buf1".getBytes(CharsetUtil.US_ASCII));
        for (int i = 0; i < 64; i++) {
            buffer.addMessage(buf.copy(), buf.readableBytes(), CompletionHandler.ignore());
        }
        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertEquals(64, buffer.nioBufferCount());
        for (int i = 0;  i < buffer.nioBufferCount(); i++) {
            assertEquals(buffers[i], buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes()));
        }
        release(buffer);
        buf.release();
    }

    @Test
    public void testNioBuffersExpand2() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel.executor());

        CompositeByteBuf comp = compositeBuffer(256);
        ByteBuf buf = directBuffer().writeBytes("buf1".getBytes(CharsetUtil.US_ASCII));
        for (int i = 0; i < 65; i++) {
            comp.addComponent(true, buf.copy());
        }
        buffer.addMessage(comp, comp.readableBytes(), CompletionHandler.ignore());

        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        ByteBuffer[] buffers = buffer.nioBuffers();
        assertEquals(65, buffer.nioBufferCount());
        for (int i = 0;  i < buffer.nioBufferCount(); i++) {
            if (i < 65) {
                assertEquals(buffers[i], buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes()));
            } else {
                assertNull(buffers[i]);
            }
        }
        release(buffer);
        buf.release();
    }

    @Test
    public void testNioBuffersMaxCount() {
        TestChannel channel = new TestChannel();

        ChannelOutboundBuffer buffer = new ChannelOutboundBuffer(channel.executor());

        CompositeByteBuf comp = compositeBuffer(256);
        ByteBuf buf = directBuffer().writeBytes("buf1".getBytes(CharsetUtil.US_ASCII));
        for (int i = 0; i < 65; i++) {
            comp.addComponent(true, buf.copy());
        }
        assertEquals(65, comp.nioBufferCount());
        buffer.addMessage(comp, comp.readableBytes(), CompletionHandler.ignore());
        assertEquals(0, buffer.nioBufferCount(), "Should still be 0 as not flushed yet");
        buffer.addFlush();
        final int maxCount = 10;    // less than comp.nioBufferCount()
        ByteBuffer[] buffers = buffer.nioBuffers(maxCount, Integer.MAX_VALUE);
        assertTrue(buffer.nioBufferCount() <= maxCount, "Should not be greater than maxCount");
        for (int i = 0;  i < buffer.nioBufferCount(); i++) {
            assertEquals(buffers[i], buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes()));
        }
        release(buffer);
        buf.release();
    }

    private static void release(ChannelOutboundBuffer buffer) {
        for (;;) {
            if (!buffer.remove()) {
                break;
            }
        }
    }

    private static final class TestChannel extends AbstractChannel {
        private final ChannelConfig config = new DefaultChannelConfig(this);

        private static EventLoop newMockedEventLoop() {
            EventLoop loop = Mockito.mock(EventLoop.class);
            Mockito.when(loop.inEventLoop()).thenReturn(true);
            return loop;
        }

        TestChannel() {
            super(newMockedEventLoop(), null, null);
        }

        @Override
        protected SocketAddress localAddress0() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected SocketAddress remoteAddress0() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doShutdown(ChannelShutdownType type, Promise<Void> promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doDeregister(Promise<Void> promise) {
            promise.setSuccess(null);
        }

        @Override
        protected void doRegister(Promise<Void> promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doBind(SocketAddress localAddress, Promise<Void> promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doDisconnect(Promise<Void> promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doClose(Promise<Void> promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doBeginRead() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doWrite(ChannelOutboundBuffer in) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChannelConfig config() {
            return config;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public <T> Promise<T> newPromise() {
            return new DefaultPromise<T>(executor());
        }
    }
}
