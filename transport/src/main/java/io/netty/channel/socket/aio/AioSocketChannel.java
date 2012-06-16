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
package io.netty.channel.socket.aio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ChannelBufType;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelStateHandler;
import io.netty.channel.ChannelStateHandlerAdapter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicBoolean;

public class AioSocketChannel extends AbstractAioChannel {

    private static final CompletionHandler<Void, AioSocketChannel> CONNECT_HANDLER  = new ConnectHandler();
    private static final CompletionHandler<Integer, AioSocketChannel> READ_HANDLER = new ReadHandler();
    private static final CompletionHandler<Integer, AioSocketChannel> WRITE_HANDLER = new WriteHandler();
    private static final ChannelStateHandler READ_START_HANDLER = new ChannelStateHandlerAdapter() {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            try {
                super.channelActive(ctx);
                
                // once the channel is active, the first read is scheduled
                AioSocketChannel.read((AioSocketChannel)ctx.channel());
                
            } finally {
                ctx.pipeline().remove(this);
            }

            
        }
        
    };
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private volatile AioSocketChannelConfig config;

    public AioSocketChannel() {
        this(null, null, null);
    }

    public AioSocketChannel(AioServerSocketChannel parent, Integer id, AsynchronousSocketChannel channel) {
        super(parent, id);
        this.ch = channel;
        if (ch != null) {
            config = new AioSocketChannelConfig(javaChannel());
            pipeline().addLast(READ_START_HANDLER);
        }
    }

    @Override
    public boolean isActive() {
        AsynchronousSocketChannel ch = javaChannel();
        return ch.isOpen() && remoteAddress() != null;
    }

    @Override
    protected AsynchronousSocketChannel javaChannel() {
        return (AsynchronousSocketChannel) super.javaChannel();
    }

    @Override
    public ChannelBufType bufferType() {
        return ChannelBufType.BYTE;
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, final ChannelFuture future) {
        assert ch != null;
        if (localAddress != null) {
            try {
                javaChannel().bind(localAddress);
            } catch (IOException e) {
                future.setFailure(e);
                return;
            }
        }

        javaChannel().connect(remoteAddress, this, CONNECT_HANDLER);
    }

    @Override
    protected InetSocketAddress localAddress0() {
        try {
            return (InetSocketAddress) javaChannel().getLocalAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected InetSocketAddress remoteAddress0() {
        try {
            return (InetSocketAddress) javaChannel().getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected Runnable doRegister() throws Exception {
        if (ch == null) {
            ch = AsynchronousSocketChannel.open(AsynchronousChannelGroup.withThreadPool(eventLoop()));
            config = new AioSocketChannelConfig(javaChannel());
            pipeline().addLast(READ_START_HANDLER);
        }


        return null;
    }

    /**
     * Trigger a read from the {@link AioSocketChannel}
     * 
     */
    private static void read(AioSocketChannel channel) {
        ByteBuf byteBuf = channel.pipeline().inboundByteBuffer();
        expandReadBuffer(byteBuf);
        
        // Get a ByteBuffer view on the ByteBuf and clear it before try to read
        ByteBuffer buffer = byteBuf.nioBuffer();
        buffer.clear();
        channel.javaChannel().read(buffer, channel, READ_HANDLER);
    }

    
    private static boolean expandReadBuffer(ByteBuf byteBuf) {
        if (!byteBuf.writable()) {
            // FIXME: Magic number
            byteBuf.ensureWritableBytes(4096);
            return true;
        }
        return false;
    }
    
    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().bind(localAddress);
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    @Override
    protected boolean doFlushByteBuffer(ByteBuf buf) throws Exception {
        // Only one pending write can be scheduled at one time. Otherwise
        // a PendingWriteException will be thrown. So use CAS to not run
        // into this
        if (flushing.compareAndSet(false, true)) {
            ByteBuffer buffer = (ByteBuffer)buf.nioBuffer();
            javaChannel().write(buffer, this, WRITE_HANDLER);
        }
        return false;
    }


    private static final class WriteHandler implements CompletionHandler<Integer, AioSocketChannel> {

        @Override
        public void completed(Integer result, AioSocketChannel channel) {
            ByteBuf buf = channel.pipeline().outboundByteBuffer();

            if (result > 0) {
                if (result < buf.readableBytes()) {
                    // Update the readerIndex with the amount of read bytes
                    buf.readerIndex(buf.readerIndex() + result);
                } else {
                    // not enough space in the buffer anymore so discard everything that
                    // was read already
                    buf.discardReadBytes();
                    
                }
                channel.notifyFlushFutures();
            }
            
            // Allow to have the next write pending
            channel.flushing.set(false);
        }

        @Override
        public void failed(Throwable cause, AioSocketChannel channel) {
            ByteBuf buf = channel.pipeline().outboundByteBuffer();
            if (!buf.readable()) {
                buf.discardReadBytes();
            }

            channel.notifyFlushFutures(cause);
            channel.pipeline().fireExceptionCaught(cause);
            if (cause instanceof IOException) {
                channel.close(channel.unsafe().voidFuture());
            }
            // Allow to have the next write pending
            channel.flushing.set(false);
        }
    }

    private static final class ReadHandler implements CompletionHandler<Integer, AioSocketChannel> {

        @Override
        public void completed(Integer result, AioSocketChannel channel) {
            assert channel.eventLoop().inEventLoop();

            final ChannelPipeline pipeline = channel.pipeline();
            boolean closed = false;
            boolean read = false;
            try {
                
                int localReadAmount = result.intValue();
                if (localReadAmount > 0) {
                    //Set the writerIndex of the buffer correctly to the 
                    // current writerIndex + read amount of bytes.
                    //
                    // This is needed as the ByteBuffer and the ByteBuf does not share
                    // each others index
                    final ByteBuf byteBuf = pipeline.inboundByteBuffer();
                    byteBuf.writerIndex(byteBuf.writerIndex() + result);
                    
                    read = true;

                } else if (localReadAmount < 0) {
                    closed = true;
                }
                
            } catch (Throwable t) {
                if (read) {
                    read = false;
                    pipeline.fireInboundBufferUpdated();
                }
                pipeline.fireExceptionCaught(t);
                if (t instanceof IOException) {
                    channel.close(channel.unsafe().voidFuture());
                }
            } finally {
                if (read) {
                    pipeline.fireInboundBufferUpdated();
                }
                if (closed && channel.isOpen()) {
                    channel.close(channel.unsafe().voidFuture());
                } else {
                    // start the next read
                    AioSocketChannel.read(channel);
                }
            }
        }

        @Override
        public void failed(Throwable t, AioSocketChannel channel) {
            channel.pipeline().fireExceptionCaught(t);
            if (t instanceof IOException) {
                channel.close(channel.unsafe().voidFuture());
            } else {
                // start the next read
                AioSocketChannel.read(channel);
            }
        }
    }

    private static final class ConnectHandler implements CompletionHandler<Void, AioSocketChannel> {

        @Override
        public void completed(Void result, AioSocketChannel channel) {
            ((AsyncUnsafe) channel.unsafe()).connectSuccess();
            
            // start reading from channel
            AioSocketChannel.read(channel);
        }

        @Override
        public void failed(Throwable exc, AioSocketChannel channel) {
            ((AsyncUnsafe) channel.unsafe()).connectFailed(exc);
        }
    }

    @Override
    public AioSocketChannelConfig config() {
        if (config == null) {
            throw new IllegalStateException("Channel not open yet");
        }
        return config;
    }


}
