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
import io.netty.channel.LoggingHandler.Event;
import io.netty.channel.local.LocalAddress;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

public class ReentrantChannelTest extends BaseChannelTest {

    @Test
    public void testWritabilityChanged() throws Exception {

        LocalAddress addr = new LocalAddress("testWritabilityChanged");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync().channel();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.WRITABILITY);

        Channel clientChannel = cb.connect(addr).sync().channel();
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

        ChannelFuture future = clientChannel.write(createTestBuf(2000));

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
    public void testFlushInWritabilityChanged() throws Exception {

        LocalAddress addr = new LocalAddress("testFlushInWritabilityChanged");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync().channel();

        Bootstrap cb = getLocalClientBootstrap();

        setInterest(Event.WRITE, Event.FLUSH, Event.WRITABILITY);

        Channel clientChannel = cb.connect(addr).sync().channel();
        clientChannel.config().setWriteBufferLowWaterMark(512);
        clientChannel.config().setWriteBufferHighWaterMark(1024);

        clientChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
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

    @Test
    public void testWriteFlushPingPong() throws Exception {

        LocalAddress addr = new LocalAddress("testWriteFlushPingPong");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync().channel();

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
        sb.bind(addr).sync().channel();

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

        assertLog("WRITE\nFLUSH\nCLOSE\n");
    }

    @Test
    public void testFlushFailure() throws Exception {

        LocalAddress addr = new LocalAddress("testFlushFailure");

        ServerBootstrap sb = getLocalServerBootstrap();
        sb.bind(addr).sync().channel();

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
            assertThat(cce, Matchers.instanceOf(ClosedChannelException.class));
        }

        clientChannel.closeFuture().sync();

        assertLog("WRITE\nCLOSE\n");
    }
}
