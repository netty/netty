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
package io.netty.handler.fileregion;


import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerUtil;
import io.netty.channel.ChannelOutboundByteHandlerAdapter;
import io.netty.channel.ChannelOutboundMessageHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventExecutor;
import io.netty.channel.socket.nio.AbstractNioChannel;
import io.netty.channel.socket.nio.AbstractNioChannel.NioUnsafe;
import io.netty.channel.socket.nio.NioEventLoop;
import io.netty.channel.socket.nio.NioTask;

/**
 * {@link ChannelOutboundMessageHandlerAdapter} which allows for zero-copy-transfer via {@link FileRegion}.
 *
 * Be aware that this only works with the NIO Transport at the moment. Adding this handler to a
 * {@link ChannelPipeline} which is not bound to an {@link AbstractNioChannel} will result in an exception.
 *
 *
 */
public final class FileRegionHandler extends ChannelOutboundMessageHandlerAdapter<Object> {

    private TransferTask transferTask;

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (!(channel instanceof AbstractNioChannel)) {
            throw new IllegalStateException("Only supported on NIO Channels");
        }
        if (!(((AbstractNioChannel) channel).unsafe().ch() instanceof WritableByteChannel)) {
            throw new IllegalStateException("Only supported on NIO Channels with WritableByteChannel attached");
        }
        super.beforeAdd(ctx);

    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        super.afterAdd(ctx);
        ctx.pipeline().addAfter(ctx.name(), ctx.name() + "-bytebuf", new ByteBufForwardHandler());
    }

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        super.afterRemove(ctx);
        ByteBufForwardHandler handler = ctx.pipeline().get(ByteBufForwardHandler.class);
        if (handler == null) {
            throw new IllegalStateException(ByteBufForwardHandler.class.getName() + " missing");
        }
        handler.remove(ctx.pipeline());
    }

    @Override
    public void flush(final ChannelHandlerContext ctx, final ChannelFuture future) throws Exception {
        if (transferTask == null) {
            MessageBuf<?> messageBuf = ctx.outboundMessageBuffer();
            for (;;) {
                Object msg = messageBuf.poll();
                if (msg == null || flush(ctx, msg, true)) {
                    break;
                }
            }
        }

        ctx.flush(future);
    }

    private boolean flush(final ChannelHandlerContext ctx, Object msg, boolean flushUnsafe) throws Exception {
        assert transferTask == null;

        if (msg instanceof FileRegion) {
            final FileRegion region = (FileRegion) msg;
            final NioUnsafe unsafe = ((AbstractNioChannel) ctx.channel()).unsafe();
            final WritableByteChannel bch = (WritableByteChannel) unsafe.ch();


            transferTask = new TransferTask(region, bch, ctx);
            if (flushUnsafe) {
                unsafe.flush(ctx.channel().newFuture().addListener(new ChannelFutureListener() {

                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        EventExecutor ex = ctx.executor();
                        ex.execute(transferTask);
                    }
                }));
            } else {
                transferTask.run();
            }
            return true;
        } else {
            ChannelHandlerUtil.unfoldAndAdd(ctx, msg, false);
            return false;
        }
    }

    private final class TransferTask implements Runnable {
        private final FileRegion region;
        private final ChannelHandlerContext ctx;
        private long written;
        private final WritableByteChannel bch;

        public TransferTask(FileRegion region, WritableByteChannel bch, ChannelHandlerContext ctx) {
            this.region = region;
            this.bch = bch;
            this.ctx = ctx;
        }

        @Override
        public void run() {
            boolean done = false;
            try {
                // loop and try to transfer data
                for (;;) {
                    long w = region.transferTo(bch, written);
                    if (w > 0) {
                        written +=  w;
                        if (written >= region.getCount()) {
                            // all requested data transfered
                            done = true;
                            break;
                        }
                    } else {
                        // nothing was written, probably the channel is saturated, will try again on the next
                        // event-loop run
                        break;
                    }
                }

            } catch (Throwable cause) {
                ctx.fireUserEventTriggered(cause);
            } finally {
                if (done) {
                    // reset the transfer-task to null
                    transferTask = null;

                    // Close the file-region
                    region.close();

                    processBuffers(ctx);
                } else {
                    NioEventLoop loop = (NioEventLoop) ctx.executor();
                    loop.executeWhenWritable((AbstractNioChannel) ctx.channel(), new NioTask<SelectableChannel>() {
                        @Override
                        public void channelReady(SelectableChannel ch, SelectionKey key) throws Exception {
                            TransferTask.this.run();
                        }

                        @Override
                        public void channelUnregistered(SelectableChannel ch) throws Exception {
                            // ignore
                        }
                    });
                }
            }
        }
    }

    private void processBuffers(final ChannelHandlerContext ctx) {
        MessageBuf<?> messageBuf = ctx.outboundMessageBuffer();
        for (;;) {
            Object msg = messageBuf.poll();
            try {
                if (msg == null) {
                    break;
                }
                if (flush(ctx, msg, false)) {
                    break;
                } else {
                    // we wrote something else then a FileRegion time to flush to not mess up order of
                    // messages
                    ChannelFuture future = ctx.flush(ctx.newFuture()).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                processBuffers(ctx);
                            } else {
                                ctx.fireExceptionCaught(future.cause());
                            }
                        }
                    });
                    break;
                }
            } catch (Throwable t) {
                ctx.fireExceptionCaught(t);
                break;
            }
        }
    }

    private final class ByteBufForwardHandler extends ChannelOutboundByteHandlerAdapter {
        private boolean toRemoved;

        @Override
        public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
            if (!toRemoved) {
                throw new IllegalStateException("Should never be directly removed");
            }
            super.beforeRemove(ctx);
        }

        void remove(ChannelPipeline pipeline) {
            toRemoved = true;
            pipeline.remove(this);
        }

        @Override
        public void flush(ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
            ByteBuf buf = ctx.outboundByteBuffer();
            if (transferTask == null) {
                // transfer in progress so we can transfer the bytes directly
                // TODO: Once its possible to exchange the buffer on the fly we could just directly hand-over
                //       the buffer of the next handler
                ctx.nextOutboundByteBuffer().writeBytes(buf);
            } else {
                // TODO: Do something more efficient
                ctx.nextOutboundMessageBuffer().add(buf.readBytes(buf.readableBytes()));
                buf.discardReadBytes();

            }
            // flush now
            ctx.flush(future);
        }
    }
}
