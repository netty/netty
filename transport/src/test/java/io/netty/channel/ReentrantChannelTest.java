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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.LoggingHandler.Event;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ReentrantChannelTest extends BaseChannelTest {

    @Test
    public void testWritabilityChanged() throws Exception {

        LocalAddress addr = new LocalAddress("testWritabilityChanged");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).get();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.WRITABILITY);

        Channel clientChannel = cb.connect(addr).get();
        clientChannel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(512, 1024));

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
                // --- Start case 1 ---

                // start with writability false as the write from outside the EventLoop will increment the pending
                // bytes before it is submitted for execution
                "WRITABILITY: writable=false\n" +

                // Now our executor will pick up the write and so decrement the pending bytes before doing so which
                // will cause a writability event
                "WRITABILITY: writable=true\n" +

                // The actual write is executed and so we observe a write event.
                "WRITE\n" +

                // This causes the writability to change to false again as now everything is buffered in
                // our outbound buffer.
                "WRITABILITY: writable=false\n" +

                // Flush is submitted an executed.
                "FLUSH\n" +

                // Everything is written so writability is true again.
                "WRITABILITY: writable=true\n",

                // --- Start case 2 ---

                // start with writability false as the write from outside the EventLoop will increment the pending
                // bytes before it is submitted for execution
                "WRITABILITY: writable=false\n" +

                // Now our executor will pick up the write and so decrement the pending bytes before doing so which
                // will cause a writability event
                "WRITABILITY: writable=true\n" +

                // The actual write is executed and so we observe a write event.
                "WRITE\n" +

                // This causes the writability to change to false again as now everything is buffered in
                // our outbound buffer.
                "WRITABILITY: writable=false\n" +

                // Flush is submitted an executed.
                "FLUSH\n");
    }

    /**
     * Similar to {@link #testWritabilityChanged()} with slight variation.
     */
    @Test
    public void testFlushInWritabilityChanged() throws Exception {

        LocalAddress addr = new LocalAddress("testFlushInWritabilityChanged");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).get();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.WRITABILITY, Event.CLOSE);

        Channel clientChannel = cb.connect(addr).get();
        clientChannel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(512, 1024));

        clientChannel.pipeline().addLast(new ChannelInboundHandler() {

            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                if (!ctx.channel().isWritable()) {
                    ctx.flush();
                }
                ctx.fireChannelWritabilityChanged();
            }
        });

        assertTrue(clientChannel.isWritable());

        clientChannel.write(createTestBuf(2000)).sync();
        clientChannel.close().sync();

        assertLog(
                // --- Start case 1 ---

                // start with writability false as the write from outside the EventLoop will increment the pending
                // bytes before it is submitted for execution
                "WRITABILITY: writable=false\n" +

                // This will trigger a flush in our handler
                "FLUSH\n" +

                // Now our executor will pick up the write and so decrement the pending bytes before doing so which
                // will cause a writability event
                "WRITABILITY: writable=true\n" +

                // The actual write is executed and so we observe a write event.
                "WRITE\n" +

                // This causes the writability to change to false again as now everything is buffered in
                // our outbound buffer.
                "WRITABILITY: writable=false\n" +

                // This will trigger a flush in our handler
                "FLUSH\n" +

                // Everything is written so writability is true again.
                "WRITABILITY: writable=true\n" +

                // Channel is closed
                 "CLOSE\n",

                // --- Start case 2 ---

                // start with writability false as the write from outside the EventLoop will increment the pending
                // bytes before it is submitted for execution
                "WRITABILITY: writable=false\n" +

                // This will trigger a flush in our handler
                "FLUSH\n" +

                // Now our executor will pick up the write and so decrement the pending bytes before doing so which
                // will cause a writability event
                "WRITABILITY: writable=true\n" +

                // The actual write is executed and so we observe a write event.
                "WRITE\n" +

                // This causes the writability to change to false again as now everything is buffered in
                // our outbound buffer.
                "WRITABILITY: writable=false\n" +

                // This will trigger a flush in our handler
                "FLUSH\n" +

                // Channel is closed
                "CLOSE\n"
        );
    }

    @Test
    public void testWriteFlushPingPong() throws Exception {

        LocalAddress addr = new LocalAddress("testWriteFlushPingPong");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).get();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).get();

        clientChannel.pipeline().addLast(new ChannelOutboundHandler() {

            int writeCount;
            int flushCount;

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
                if (writeCount < 5) {
                    writeCount++;
                    ctx.channel().flush();
                }
                ctx.write(msg, handler);
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
    public void testCloseInFlush() throws Exception {

        LocalAddress addr = new LocalAddress("testCloseInFlush");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).get();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).get();

        clientChannel.pipeline().addLast(new ChannelOutboundHandler() {

            @Override
            public void write(final ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
                ctx.write(msg, handler.andThen(new CompletionHandler<>() {
                    @Override
                    public void onSuccess(Void result) {
                        ctx.channel().close();
                    }

                    @Override
                    public void onFailure(Throwable cause) {
                        ctx.channel().close();
                    }
                }, ctx.executor()));
                ctx.channel().flush();
            }
        });

        clientChannel.write(createTestBuf(2000)).sync();
        clientChannel.closeFuture().sync();

        assertLog("WRITE\nFLUSH\nCLOSE\n");
    }

    @Test
    public void testFlushFailure() throws Exception {

        LocalAddress addr = new LocalAddress("testFlushFailure");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).get();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.CLOSE, Event.EXCEPTION);

        Channel clientChannel = cb.connect(addr).get();

        clientChannel.pipeline().addLast(new ChannelOutboundHandler() {

            @Override
            public void flush(ChannelHandlerContext ctx) {
                throw new IllegalStateException("intentional failure");
            }
        });

        try {
            clientChannel.writeAndFlush(createTestBuf(2000)).sync();
            fail();
        } catch (Exception e) {
            // FIXME:  shouldn't this contain the "intentional failure" exception?
            assertThat(e).isInstanceOf(ClosedChannelException.class);
        }

        clientChannel.closeFuture().sync();

        assertLog("WRITE\nCLOSE\n");
    }

    @Test
    public void nestReentrancy() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
        try {
            LocalAddress addr = new LocalAddress("nestReentrancy");

            BlockingQueue<Object> received = new LinkedBlockingQueue<>();

            Channel server = new ServerBootstrap()
                    .group(group)
                    .channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.config().setAutoRead(false);
                            // first handler splits input by \n and into strings
                            ch.pipeline().addLast(new ChannelInboundHandler() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    String string = ((ByteBuf) msg).toString(StandardCharsets.UTF_8);
                                    ((ByteBuf) msg).release();
                                    for (String s : string.split("\n")) {
                                        ctx.fireChannelRead(s);
                                    }
                                }
                            });
                            // second handler buffers messages, sends them on in channelReadComplete, and acts as flow
                            // control
                            class TestHandler implements ChannelInboundHandler, ChannelOutboundHandler {
                                final Queue<Object> queue = new ArrayDeque<>();
                                boolean demand = true;

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    queue.add(msg);
                                }

                                @Override
                                public void channelReadComplete(ChannelHandlerContext ctx) {
                                    while (demand) {
                                        Object item = queue.poll();
                                        if (item == null) {
                                            break;
                                        }
                                        demand = false;
                                        ctx.fireChannelRead(item);
                                    }
                                    ctx.fireChannelReadComplete();
                                }

                                @Override
                                public void read(ChannelHandlerContext ctx) {
                                    Object item = queue.poll();
                                    if (item != null) {
                                        ctx.fireChannelRead(item);
                                    } else {
                                        demand = true;
                                        ctx.read();
                                    }
                                }

                                @Override
                                public void handlerAdded(ChannelHandlerContext ctx) {
                                    ctx.read();
                                }
                            }
                            ch.pipeline().addLast(new TestHandler());
                            // third handler saves incoming packets so that we can test their order
                            ch.pipeline().addLast(new ChannelInboundHandler() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    received.add(msg);
                                    ctx.fireChannelRead(msg);
                                }
                            });
                            // final handler relieves backpressure, triggering reentrant channelReads
                            ch.pipeline().addLast(new ChannelInboundHandler() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ctx.read();
                                }

                                @Override
                                public void channelReadComplete(ChannelHandlerContext ctx) {
                                    ctx.read();
                                }
                            });
                        }
                    }).bind(addr).get();
            Channel client = new Bootstrap()
                    .group(group)
                    .channel(LocalChannel.class)
                    .handler(new ChannelInboundHandler() { })
                    .connect(addr).get();

            client.writeAndFlush(Unpooled.copiedBuffer("A\nB\nC", StandardCharsets.UTF_8)).sync();

            // order should be unchanged
            assertEquals("A", received.take());
            assertEquals("B", received.take());
            assertEquals("C", received.take());

            client.close().sync();
            server.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
