/*
 * Copyright 2014 The Netty Project
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
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.junit.Test;

import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class NioChannelTest {
    private static class TestChannelHandler extends SimpleChannelHandler {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            buf.append(ctx.getChannel().isWritable());
            buf.append(' ');
            super.channelInterestChanged(ctx, e);
        }
    }

    private static class TestChannelSink extends AbstractNioChannelSink {

        public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
            if (e instanceof MessageEvent) {
                MessageEvent event = (MessageEvent) e;
                NioSocketChannel channel = (NioSocketChannel) event.getChannel();
                channel.writeBufferQueue.offer(event);
            }
            e.getFuture().setSuccess();
            // Do nothing
        }
    }

    private static class TestNioChannel extends NioSocketChannel {

        TestNioChannel(ChannelPipeline pipeline, ChannelSink sink,
                SocketChannel socket, NioWorker worker) {
            super(null, null, pipeline, sink, socket, worker);
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isBound() {
            return true;
        }
        
        private ChannelFuture flushIoTasks()
        {
            final ChannelFuture f = new DefaultChannelFuture(this, false);
            worker.executeInIoThread(new Runnable() {
                public void run() {
                    f.setSuccess();
                }
            }, true);
            return f;
        }

        MessageEvent poll() {
            final DefaultChannelFuture f = new DefaultChannelFuture(this, false);
            final AtomicReference<MessageEvent> me = new AtomicReference<MessageEvent>();
            worker.executeInIoThread(new Runnable() {
                @Override
                public void run() {
                    me.set(writeBufferQueue.poll());
                    f.setSuccess();
                }
            });
            f.syncUninterruptibly();
            return me.get();
        }
    }

    private static ChannelBuffer writeZero(int size) {
        ChannelBuffer cb =  ChannelBuffers.buffer(size);
        cb.writeZero(size);
        return cb;
    }

    @Test
    public void testWritability() throws Exception {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
        TestChannelHandler handler = new TestChannelHandler();
        StringBuilder buf = handler.buf;
        pipeline.addLast("TRACE", handler);
        SocketChannel socketChannel = SocketChannel.open();
        ExecutorService executor = Executors.newCachedThreadPool();
        NioWorker worker = new NioWorker(executor);
        TestNioChannel channel = new TestNioChannel(pipeline, new TestChannelSink(),
                socketChannel, worker);
        channel.getConfig().setWriteBufferLowWaterMark(128);
        channel.getConfig().setWriteBufferHighWaterMark(256);

        // Startup check
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);

        // Ensure exceeding the low watermark does not make channel unwritable.
        channel.write(writeZero(128)).await();
        assertEquals("", buf.toString());
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        // Ensure exceeding the high watermark makes channel unwritable.
        channel.write(writeZero(64)).await();
        channel.write(writeZero(64)).await();
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false ", buf.toString());
        // Ensure going down to the low watermark makes channel writable again by flushing the first write.
        assertNotNull(channel.poll());
        assertEquals(128, channel.writeBufferSize.get());
        // once more since in Netty 3.9, the check is < and not <=
        assertNotNull(channel.poll());
        assertEquals(64, channel.writeBufferSize.get());
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        channel.flushIoTasks().await();
        assertEquals("false true ", buf.toString());

        while (! channel.writeBufferQueue.isEmpty()) {
            channel.poll();
        }
        worker.shutdown();
        executor.shutdown();
    }

    @Test
    public void testUserDefinedWritability() throws Exception {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
        TestChannelHandler handler = new TestChannelHandler();
        StringBuilder buf = handler.buf;
        pipeline.addLast("TRACE", handler);
        SocketChannel socketChannel = SocketChannel.open();
        ExecutorService executor = Executors.newCachedThreadPool();
        NioWorker worker = new NioWorker(executor);
        TestNioChannel channel = new TestNioChannel(pipeline, new TestChannelSink(),
                socketChannel, worker);
        channel.getConfig().setWriteBufferLowWaterMark(128);
        channel.getConfig().setWriteBufferHighWaterMark(256);

        // Startup check
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        // Ensure that the default value of a user-defined writability flag is true.
        for (int i = 1; i <= 30; i ++) {
            assertTrue(channel.getUserDefinedWritability(i));
        }

        // Ensure that setting a user-defined writability flag to false affects channel.isWritable();
        channel.setUserDefinedWritability(1, false);
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false ", buf.toString());
        // Ensure that setting a user-defined writability flag to true affects channel.isWritable();
        channel.setUserDefinedWritability(1, true);
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        channel.flushIoTasks().await();
        assertEquals("false true ", buf.toString());

        while (! channel.writeBufferQueue.isEmpty()) {
            channel.poll();
        }
        worker.shutdown();
        executor.shutdown();
    }

    @Test
    public void testUserDefinedWritability2() throws Exception {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
        TestChannelHandler handler = new TestChannelHandler();
        StringBuilder buf = handler.buf;
        pipeline.addLast("TRACE", handler);
        SocketChannel socketChannel = SocketChannel.open();
        ExecutorService executor = Executors.newCachedThreadPool();
        NioWorker worker = new NioWorker(executor);
        TestNioChannel channel = new TestNioChannel(pipeline, new TestChannelSink(),
                socketChannel, worker);
        channel.getConfig().setWriteBufferLowWaterMark(128);
        channel.getConfig().setWriteBufferHighWaterMark(256);

        // Startup check
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        // Ensure that the default value of a user-defined writability flag is true.
        for (int i = 1; i <= 30; i ++) {
            assertTrue(channel.getUserDefinedWritability(i));
        }

        // Ensure that setting a user-defined writability flag to false affects channel.isWritable();
        channel.setUserDefinedWritability(1, false);
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false ", buf.toString());
        // Ensure that setting another user-defined writability flag to false does not trigger
        // channelWritabilityChanged.
        channel.setUserDefinedWritability(2, false);
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false ", buf.toString());
        // Ensure that setting only one user-defined writability flag to true does not affect channel.isWritable()
        channel.setUserDefinedWritability(1, true);
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false ", buf.toString());
        // Ensure that setting all user-defined writability flags to true affects channel.isWritable()
        channel.setUserDefinedWritability(2, true);
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        channel.flushIoTasks().await();
        assertEquals("false true ", buf.toString());

        while (! channel.writeBufferQueue.isEmpty()) {
            channel.poll();
        }
        worker.shutdown();
        executor.shutdown();
    }

    @Test
    public void testMixedWritability() throws Exception {
        DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
        TestChannelHandler handler = new TestChannelHandler();
        StringBuilder buf = handler.buf;
        pipeline.addLast("TRACE", handler);
        SocketChannel socketChannel = SocketChannel.open();
        ExecutorService executor = Executors.newCachedThreadPool();
        NioWorker worker = new NioWorker(executor);
        TestNioChannel channel = new TestNioChannel(pipeline, new TestChannelSink(),
                socketChannel, worker);
        channel.getConfig().setWriteBufferLowWaterMark(128);
        channel.getConfig().setWriteBufferHighWaterMark(256);

        // Startup check
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        // Ensure that the default value of a user-defined writability flag is true.
        for (int i = 1; i <= 30; i ++) {
            assertTrue(channel.getUserDefinedWritability(i));
        }

        // First case: write, userDefinedWritability off, poll, userDefinedWritability on
        // Trigger channelWritabilityChanged() by writing a lot.
        channel.write(writeZero(256));
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false ", buf.toString());
        // Ensure that setting a user-defined writability flag to false does not trigger channelWritabilityChanged()
        channel.setUserDefinedWritability(1, false);
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false ", buf.toString());
        // Ensure reducing the totalPendingWriteBytes down to zero does not trigger channelWritabilityChanged()
        // because of the user-defined writability flag.
        assertNotNull(channel.poll());
        assertEquals(0, channel.writeBufferSize.get());
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false ", buf.toString());
        // Ensure that setting the user-defined writability flag to true triggers channelWritabilityChanged()
        channel.setUserDefinedWritability(1, true);
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        channel.flushIoTasks().await();
        assertEquals("false true ", buf.toString());

        // second case: userDefinedWritability off, write, userDefinedWritability on, poll
        // Ensure that setting a user-defined writability flag to false does trigger channelWritabilityChanged()
        channel.setUserDefinedWritability(1, false);
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false true false ", buf.toString());
        // Since already triggered, writing a lot should not trigger it
        channel.write(writeZero(256));
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false true false ", buf.toString());
        // Ensure that setting the user-defined writability flag to true does not yet
        // trigger channelWritabilityChanged()
        channel.setUserDefinedWritability(1, true);
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false true false ", buf.toString());
        // Ensure reducing the totalPendingWriteBytes down to zero does trigger channelWritabilityChanged()
        assertNotNull(channel.poll());
        assertEquals(0, channel.writeBufferSize.get());
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        channel.flushIoTasks().await();
        assertEquals("false true false true ", buf.toString());

        // third case: write, userDefinedWritability off, userDefinedWritability on, poll
        // Trigger channelWritabilityChanged() by writing a lot.
        channel.write(writeZero(512));
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false true false true false ", buf.toString());
        // Ensure that setting a user-defined writability flag to false does not trigger channelWritabilityChanged()
        channel.setUserDefinedWritability(1, false);
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false true false true false ", buf.toString());
        // Ensure that setting the user-defined writability flag to true does not triggers channelWritabilityChanged()
        channel.setUserDefinedWritability(1, true);
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false true false true false ", buf.toString());
        // Ensure reducing the totalPendingWriteBytes down to zero does not trigger channelWritabilityChannged()
        // because of the user-defined writability flag.
        assertNotNull(channel.poll());
        assertEquals(0, channel.writeBufferSize.get());
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        channel.flushIoTasks().await();
        assertEquals("false true false true false true ", buf.toString());


        // fourth case: userDefinedWritability off, write, poll, userDefinedWritability on
        // Ensure that setting a user-defined writability flag to false triggers channelWritabilityChanged()
        channel.setUserDefinedWritability(1, false);
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false true false true false true false ", buf.toString());
        // Since already triggered, writing a lot should not trigger it
        channel.write(writeZero(512));
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false true false true false true false ", buf.toString());
        // Ensure reducing the totalPendingWriteBytes down to zero does not trigger channelWritabilityChannged()
        // because of the user-defined writability flag.
        assertNotNull(channel.poll());
        assertEquals(0, channel.writeBufferSize.get());
        assertTrue((channel.getInterestOps() & Channel.OP_WRITE) != 0);
        channel.flushIoTasks().await();
        assertEquals("false true false true false true false ", buf.toString());
        // Ensure that setting the user-defined writability flag to true does triggers channelWritabilityChanged()
        channel.setUserDefinedWritability(1, true);
        assertEquals(0, channel.getInterestOps() & Channel.OP_WRITE);
        channel.flushIoTasks().await();
        assertEquals("false true false true false true false true ", buf.toString());

        while (! channel.writeBufferQueue.isEmpty()) {
            channel.poll();
        }
        worker.shutdown();
        executor.shutdown();
    }
}
