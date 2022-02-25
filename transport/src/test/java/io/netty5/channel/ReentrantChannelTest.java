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
package io.netty5.channel;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.LoggingHandler.Event;
import io.netty5.channel.local.LocalAddress;
import io.netty5.util.concurrent.Future;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ReentrantChannelTest extends BaseChannelTest {

    @Test
    public void testWritabilityChangedByteBuf() throws Exception {

        LocalAddress addr = new LocalAddress("testWritabilityChangedByteBuf");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.WRITABILITY);

        Channel clientChannel = cb.connect(addr).get();
        clientChannel.config().setWriteBufferLowWaterMark(512);
        clientChannel.config().setWriteBufferHighWaterMark(1024);

        // What is supposed to happen from this point:
        //
        // 1. Because this write attempt has been made from a non-I/O thread,
        //    ChannelOutboundBuffer.pendingWriteBytes will be increased before
        //    write() event is really evaluated.
        //    -> channelWritabilityChanged() will be triggered,
        //       because the Channel became unwritable.
        //
        // 2. The write() event is handled by the pipeline in an I/O thread.
        //    -> write() will be triggered.
        //
        // 3. Once the write() event is handled, ChannelOutboundBuffer.pendingWriteBytes
        //    will be decreased.
        //    -> channelWritabilityChanged() will be triggered,
        //       because the Channel became writable again.
        //
        // 4. The message is added to the ChannelOutboundBuffer and thus
        //    pendingWriteBytes will be increased again.
        //    -> channelWritabilityChanged() will be triggered.
        //
        // 5. The flush() event causes the write request in theChannelOutboundBuffer
        //    to be removed.
        //    -> flush() and channelWritabilityChanged() will be triggered.
        //
        // Note that the channelWritabilityChanged() in the step 4 can occur between
        // the flush() and the channelWritabilityChanged() in the step 5, because
        // the flush() is invoked from a non-I/O thread while the other are from
        // an I/O thread.

        Future<Void> future = clientChannel.write(createTestBuf(2000));

        clientChannel.flush();
        future.sync();

        clientChannel.close().sync();

        assertLog(
                // Case 1:
                "WRITABILITY: writable=false\n" +
                "WRITE\n" +
                "WRITABILITY: writable=false\n" +
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITABILITY: writable=true\n",
                // Case 2:
                "WRITABILITY: writable=false\n" +
                "WRITE\n" +
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITABILITY: writable=true\n" +
                "WRITABILITY: writable=true\n");
    }

    @Test
    public void testWritabilityChanged() throws Exception {

        LocalAddress addr = new LocalAddress("testWritabilityChanged");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.WRITABILITY);

        Channel clientChannel = cb.connect(addr).get();
        clientChannel.config().setWriteBufferLowWaterMark(512);
        clientChannel.config().setWriteBufferHighWaterMark(1024);

        // What is supposed to happen from this point:
        //
        // 1. Because this write attempt has been made from a non-I/O thread,
        //    ChannelOutboundBuffer.pendingWriteBytes will be increased before
        //    write() event is really evaluated.
        //    -> channelWritabilityChanged() will be triggered,
        //       because the Channel became unwritable.
        //
        // 2. The write() event is handled by the pipeline in an I/O thread.
        //    -> write() will be triggered.
        //
        // 3. Once the write() event is handled, ChannelOutboundBuffer.pendingWriteBytes
        //    will be decreased.
        //    -> channelWritabilityChanged() will be triggered,
        //       because the Channel became writable again.
        //
        // 4. The message is added to the ChannelOutboundBuffer and thus
        //    pendingWriteBytes will be increased again.
        //    -> channelWritabilityChanged() will be triggered.
        //
        // 5. The flush() event causes the write request in theChannelOutboundBuffer
        //    to be removed.
        //    -> flush() and channelWritabilityChanged() will be triggered.
        //
        // Note that the channelWritabilityChanged() in the step 4 can occur between
        // the flush() and the channelWritabilityChanged() in the step 5, because
        // the flush() is invoked from a non-I/O thread while the other are from
        // an I/O thread.

        Future<Void> future = clientChannel.write(createTestBuffer(2000));

        clientChannel.flush();
        future.sync();

        clientChannel.close().sync();

        assertLog(
                // Case 1:
                "WRITABILITY: writable=false\n" +
                "WRITE\n" +
                "WRITABILITY: writable=false\n" +
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITABILITY: writable=true\n",
                // Case 2:
                "WRITABILITY: writable=false\n" +
                "WRITE\n" +
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITABILITY: writable=true\n" +
                "WRITABILITY: writable=true\n");
    }

    /**
     * Similar to {@link #testWritabilityChanged()} with slight variation.
     */
    @Test
    public void testFlushInWritabilityChangedByteBuf() throws Exception {

        LocalAddress addr = new LocalAddress("testFlushInWritabilityChangedByteBuf");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.WRITABILITY);

        Channel clientChannel = cb.connect(addr).get();
        clientChannel.config().setWriteBufferLowWaterMark(512);
        clientChannel.config().setWriteBufferHighWaterMark(1024);

        clientChannel.pipeline().addLast(new ChannelHandler() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                if (!ctx.channel().isWritable()) {
                    ctx.channel().flush();
                }
                ctx.fireChannelWritabilityChanged();
            }
        });

        assertTrue(clientChannel.isWritable());

        clientChannel.write(createTestBuf(2000)).sync();
        clientChannel.close().sync();

        assertLog(
                // Case 1:
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "WRITABILITY: writable=false\n" +
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITABILITY: writable=true\n",
                // Case 2:
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITABILITY: writable=true\n" +
                "WRITABILITY: writable=true\n");
    }

    /**
     * Similar to {@link #testWritabilityChanged()} with slight variation.
     */
    @Test
    public void testFlushInWritabilityChanged() throws Exception {

        LocalAddress addr = new LocalAddress("testFlushInWritabilityChanged");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.WRITABILITY);

        Channel clientChannel = cb.connect(addr).get();
        clientChannel.config().setWriteBufferLowWaterMark(512);
        clientChannel.config().setWriteBufferHighWaterMark(1024);

        clientChannel.pipeline().addLast(new ChannelHandler() {
            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                if (!ctx.channel().isWritable()) {
                    ctx.channel().flush();
                }
                ctx.fireChannelWritabilityChanged();
            }
        });

        assertTrue(clientChannel.isWritable());

        clientChannel.write(createTestBuffer(2000)).sync();
        clientChannel.close().sync();

        assertLog(
                // Case 1:
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "WRITABILITY: writable=false\n" +
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITABILITY: writable=true\n",
                // Case 2:
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITABILITY: writable=true\n" +
                "WRITABILITY: writable=true\n");
    }

    @Test
    public void testWriteFlushPingPongByteBuf() throws Exception {

        LocalAddress addr = new LocalAddress("testWriteFlushPingPongByteBuf");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).get();

        clientChannel.pipeline().addLast(new ChannelHandler() {

            int writeCount;
            int flushCount;

            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                if (writeCount < 5) {
                    writeCount++;
                    ctx.channel().flush();
                }
                return ctx.write(msg);
            }

            @Override
            public void flush(ChannelHandlerContext ctx) {
                if (flushCount < 5) {
                    flushCount++;
                    ctx.channel().write(createTestBuf(2000));
                }
                ctx.flush();
            }
        });

        clientChannel.writeAndFlush(createTestBuf(2000));
        clientChannel.close().sync();

        assertLog(
                "WRITE\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "FLUSH\n" +
                "CLOSE\n");
    }

    @Test
    public void testWriteFlushPingPong() throws Exception {

        LocalAddress addr = new LocalAddress("testWriteFlushPingPong");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).get();

        clientChannel.pipeline().addLast(new ChannelHandler() {

            int writeCount;
            int flushCount;

            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                if (writeCount < 5) {
                    writeCount++;
                    ctx.channel().flush();
                }
                return ctx.write(msg);
            }

            @Override
            public void flush(ChannelHandlerContext ctx) {
                if (flushCount < 5) {
                    flushCount++;
                    ctx.channel().write(createTestBuffer(2000));
                }
                ctx.flush();
            }
        });

        clientChannel.writeAndFlush(createTestBuffer(2000));
        clientChannel.close().sync();

        assertLog(
                "WRITE\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "FLUSH\n" +
                "WRITE\n" +
                "FLUSH\n" +
                "CLOSE\n");
    }

    @Test
    public void testCloseInFlushByteBuf() throws Exception {

        LocalAddress addr = new LocalAddress("testCloseInFlush");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).get();

        clientChannel.pipeline().addLast(new ChannelHandler() {

            @Override
            public Future<Void> write(final ChannelHandlerContext ctx, Object msg) {
                Future<Void> f = ctx.write(msg).addListener(ctx, ChannelFutureListeners.CLOSE);
                ctx.channel().flush();
                return f;
            }
        });

        clientChannel.write(createTestBuf(2000)).sync();
        clientChannel.closeFuture().sync();

        assertLog("WRITE\nFLUSH\nCLOSE\n");
    }

    @Test
    public void testCloseInFlush() throws Exception {

        LocalAddress addr = new LocalAddress("testCloseInFlushByteBuf");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).get();

        clientChannel.pipeline().addLast(new ChannelHandler() {

            @Override
            public Future<Void> write(final ChannelHandlerContext ctx, Object msg) {
                Future<Void> f = ctx.write(msg).addListener(ctx, ChannelFutureListeners.CLOSE);
                ctx.channel().flush();
                return f;
            }
        });

        clientChannel.write(createTestBuffer(2000)).sync();
        clientChannel.closeFuture().sync();

        assertLog("WRITE\nFLUSH\nCLOSE\n");
    }

    @Test
    public void testFlushFailureByteBuf() throws Exception {

        LocalAddress addr = new LocalAddress("testFlushFailureByteBuf");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).get();

        clientChannel.pipeline().addLast(new ChannelHandler() {

            @Override
            public void flush(ChannelHandlerContext ctx) {
                throw new IllegalStateException("intentional failure");
            }

        }, new ChannelHandler() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                ctx.close();
            }
        });

        try {
            clientChannel.writeAndFlush(createTestBuf(2000)).sync();
            fail();
        } catch (Throwable cce) {
            // FIXME:  shouldn't this contain the "intentional failure" exception?
            assertThat(cce.getCause(), Matchers.instanceOf(ClosedChannelException.class));
        }

        clientChannel.closeFuture().sync();

        assertLog("WRITE\nCLOSE\n");
    }

    @Test
    public void testFlushFailure() throws Exception {

        LocalAddress addr = new LocalAddress("testFlushFailure");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).get();

        clientChannel.pipeline().addLast(new ChannelHandler() {

            @Override
            public void flush(ChannelHandlerContext ctx) {
                throw new IllegalStateException("intentional failure");
            }

        }, new ChannelHandler() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                ctx.close();
            }
        });

        try {
            clientChannel.writeAndFlush(createTestBuffer(2000)).sync();
            fail();
        } catch (Throwable cce) {
            // FIXME:  shouldn't this contain the "intentional failure" exception?
            assertThat(cce.getCause(), Matchers.instanceOf(ClosedChannelException.class));
        }

        clientChannel.closeFuture().sync();

        assertLog("WRITE\nCLOSE\n");
    }
}
