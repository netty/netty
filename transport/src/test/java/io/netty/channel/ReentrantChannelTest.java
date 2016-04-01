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
package io.netty.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.LoggingHandler.Event;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class ReentrantChannelTest {

    private EventLoopGroup clientGroup;
    private EventLoopGroup serverGroup;
    private LoggingHandler loggingHandler;

    private ServerBootstrap getLocalServerBootstrap() {
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(serverGroup);
        sb.channel(LocalServerChannel.class);
        sb.childHandler(new ChannelInboundHandlerAdapter());

        return sb;
    }

    private Bootstrap getLocalClientBootstrap() {
        Bootstrap cb = new Bootstrap();
        cb.group(clientGroup);
        cb.channel(LocalChannel.class);
        cb.handler(loggingHandler);

        return cb;
    }

    private static ByteBuf createTestBuf(int len) {
        ByteBuf buf = Unpooled.buffer(len, len);
        buf.setIndex(0, len);
        return buf;
    }

    private void assertLog(String firstExpected, String... otherExpected) {
        String actual = loggingHandler.getLog();
        if (firstExpected.equals(actual)) {
            return;
        }
        for (String e: otherExpected) {
            if (e.equals(actual)) {
                return;
            }
        }

        // Let the comparison fail with the first expectation.
        assertEquals(firstExpected, actual);
    }

    private void setInterest(LoggingHandler.Event... events) {
        loggingHandler.setInterest(events);
    }

    @Before
    public void setup() {
        loggingHandler = new LoggingHandler();
        clientGroup = new DefaultEventLoop();
        serverGroup = new DefaultEventLoop();
    }

    @After
    public void teardown() {
        clientGroup.shutdownGracefully();
        serverGroup.shutdownGracefully();
    }

    @Test
    public void testWritabilityChangedWriteAndFlush() throws Exception {
        testWritabilityChanged0(true);
    }

    @Test
    public void testWritabilityChangedWriteThenFlush() throws Exception {
        testWritabilityChanged0(false);
    }

    private void testWritabilityChanged0(boolean writeAndFlush) throws Exception {
        LocalAddress addr = new LocalAddress("testFlushInWritabilityChanged");
        ServerBootstrap sb = getLocalServerBootstrap();
        Channel serverChannel = sb.bind(addr).sync().channel();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.WRITABILITY);

        Channel clientChannel = cb.connect(addr).sync().channel();
        clientChannel.config().setWriteBufferLowWaterMark(512);
        clientChannel.config().setWriteBufferHighWaterMark(1024);

        assertTrue(clientChannel.isWritable());
        if (writeAndFlush) {
            clientChannel.writeAndFlush(createTestBuf(2000)).sync();
        } else {
            ChannelFuture future = clientChannel.write(createTestBuf(2000));
            clientChannel.flush();
            future.sync();
        }
        clientChannel.close().sync();
        serverChannel.close().sync();

        // Because of timing of the scheduling we either should see:
        // - WRITE, FLUSH
        // - WRITE, WRITABILITY: writable=false, FLUSH
        // - WRITE, WRITABILITY: writable=false, FLUSH, WRITABILITY: writable=true
        //
        // This is the case as between the write and flush the EventLoop may already pick up the pending writes and
        // put these into the ChannelOutboundBuffer. Once the flush then happen from outside the EventLoop we may be
        // able to flush everything and also schedule the writabilityChanged event before the actual close takes
        // place which means we may see another writability changed event to inform the channel is writable again.
        assertLog(
                "WRITE\n" +
                "FLUSH\n",
                "WRITE\n" +
                "WRITABILITY: writable=false\n" +
                "FLUSH\n",
                "WRITE\n" +
                "WRITABILITY: writable=false\n" +
                "FLUSH\n" +
                "WRITABILITY: writable=true\n"
            );
    }

    @Test
    public void testWriteFlushPingPong() throws Exception {

        LocalAddress addr = new LocalAddress("testWriteFlushPingPong");

        ServerBootstrap sb = getLocalServerBootstrap();
        Channel serverChannel = sb.bind(addr).sync().channel();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).sync().channel();

        clientChannel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {

            int writeCount;
            int flushCount;

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (writeCount < 5) {
                    writeCount++;
                    ctx.channel().flush();
                }
                super.write(ctx, msg,  promise);
            }

            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                if (flushCount < 5) {
                    flushCount++;
                    ctx.channel().write(createTestBuf(2000));
                }
                super.flush(ctx);
            }
        });

        clientChannel.writeAndFlush(createTestBuf(2000));
        clientChannel.close().sync();
        serverChannel.close().sync();

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
    public void testCloseInFlush() throws Exception {

        LocalAddress addr = new LocalAddress("testCloseInFlush");

        ServerBootstrap sb = getLocalServerBootstrap();
        Channel serverChannel = sb.bind(addr).sync().channel();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).sync().channel();

        clientChannel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {

            @Override
            public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                promise.addListener(new GenericFutureListener<Future<? super Void>>() {
                    @Override
                    public void operationComplete(Future<? super Void> future) throws Exception {
                        ctx.channel().close();
                    }
                });
                super.write(ctx, msg, promise);
                ctx.channel().flush();
            }
        });

        clientChannel.write(createTestBuf(2000)).sync();
        clientChannel.closeFuture().sync();
        serverChannel.close().sync();

        assertLog("WRITE\nFLUSH\nCLOSE\n");
    }

    @Test
    public void testFlushFailure() throws Exception {

        LocalAddress addr = new LocalAddress("testFlushFailure");

        ServerBootstrap sb = getLocalServerBootstrap();
        Channel serverChannel = sb.bind(addr).sync().channel();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).sync().channel();

        clientChannel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {

            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                throw new Exception("intentional failure");
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                ctx.close();
            }
        });

        try {
            clientChannel.writeAndFlush(createTestBuf(2000)).sync();
            fail();
        } catch (Throwable cce) {
            // FIXME:  shouldn't this contain the "intentional failure" exception?
            assertEquals(ClosedChannelException.class, cce.getClass());
        }

        clientChannel.closeFuture().sync();
        serverChannel.close().sync();

        assertLog("WRITE\nCLOSE\n");
    }

    // Test for https://github.com/netty/netty/issues/5028
    @Test
    public void testWriteRentrance() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                for (int i = 0; i < 3; i++) {
                    ctx.write(i);
                }
                ctx.write(3, promise);
            }

            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                if (ctx.channel().isWritable()) {
                    ctx.channel().writeAndFlush(-1);
                }
            }
        });

        channel.config().setMessageSizeEstimator(new MessageSizeEstimator() {
            @Override
            public Handle newHandle() {
                return new Handle() {
                    @Override
                    public int size(Object msg) {
                        // Each message will just increase the pending bytes by 1.
                        return 1;
                    }
                };
            }
        });
        channel.config().setWriteBufferLowWaterMark(3);
        channel.config().setWriteBufferHighWaterMark(4);
        channel.writeOutbound(-1);
        assertTrue(channel.finish());
        assertSequenceOutbound(channel);
        assertSequenceOutbound(channel);
        assertNull(channel.readOutbound());
    }

    private static void assertSequenceOutbound(EmbeddedChannel channel) {
        assertEquals(0, channel.readOutbound());
        assertEquals(1, channel.readOutbound());
        assertEquals(2, channel.readOutbound());
        assertEquals(3, channel.readOutbound());
    }
}
