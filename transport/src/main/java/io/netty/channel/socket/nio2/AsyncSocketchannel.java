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
package io.netty.channel.socket.nio2;

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
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSocketchannel extends AbstractAsyncChannel {

    private static final CompletionHandler<Void, AsyncSocketchannel> CONNECT_HANDLER  = new ConnectHandler();
    private static final CompletionHandler<Integer, AsyncSocketchannel> READ_HANDLER = new ReadHandler();
    private static final CompletionHandler<Integer, AsyncSocketchannel> WRITE_HANDLER = new WriteHandler();
    private static final ChannelStateHandler READ_START_HANDLER = new ChannelStateHandlerAdapter() {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            try {
                super.channelActive(ctx);
                AsyncSocketchannel.read((AsyncSocketchannel)ctx.channel());

            } finally {
                ctx.pipeline().remove(this);
            }

            
        }
        
    };
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private volatile AsyncSocketChannelConfig config;

    public AsyncSocketchannel() {
        this(null, null, null);
    }

    public AsyncSocketchannel(AsyncServerSocketChannel parent, Integer id, AsynchronousSocketChannel channel) {
        super(parent, id);
        this.ch = channel;
        if (ch != null) {
            config = new AsyncSocketChannelConfig(javaChannel());
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
            config = new AsyncSocketChannelConfig(javaChannel());
            pipeline().addLast(READ_START_HANDLER);
        }


        return null;
    }

    private static void read(AsyncSocketchannel channel) {
        channel.javaChannel().read(channel.pipeline().inboundByteBuffer().nioBuffer(), channel, READ_HANDLER);
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
        // TODO: Fix me
        return true;
    }

    protected boolean doFlushByteBuffer(ByteBuf buf) throws Exception {
        if (flushing.compareAndSet(false, true)) {
            javaChannel().write(buf.nioBuffer(), this, WRITE_HANDLER);
        }
        return false;
    }


    private static final class WriteHandler implements CompletionHandler<Integer, AsyncSocketchannel> {

        @Override
        public void completed(Integer result, AsyncSocketchannel channel) {
            ByteBuf buf = channel.pipeline().outboundByteBuffer();
            if (!buf.readable()) {
                buf.discardReadBytes();
            }

            if (result > 0) {
                channel.notifyFlushFutures();
            }
            channel.flushing.set(false);
        }

        @Override
        public void failed(Throwable cause, AsyncSocketchannel channel) {
            ByteBuf buf = channel.pipeline().outboundByteBuffer();
            if (!buf.readable()) {
                buf.discardReadBytes();
            }

            channel.notifyFlushFutures(cause);
            channel.pipeline().fireExceptionCaught(cause);
            if (cause instanceof IOException) {
                channel.close(channel.unsafe().voidFuture());
            }
            channel.flushing.set(false);
        }
    }

    private static final class ReadHandler implements CompletionHandler<Integer, AsyncSocketchannel> {

        @Override
        public void completed(Integer result, AsyncSocketchannel channel) {
            assert channel.eventLoop().inEventLoop();

            final ChannelPipeline pipeline = channel.pipeline();
            final ByteBuf byteBuf = pipeline.inboundByteBuffer();
            boolean closed = false;
            boolean read = false;
            try {
                expandReadBuffer(byteBuf);
                for (;;) {
                    int localReadAmount = result.intValue();
                    if (localReadAmount > 0) {
                        read = true;
                    } else if (localReadAmount < 0) {
                        closed = true;
                        break;
                    }
                    if (!expandReadBuffer(byteBuf)) {
                        break;
                    }
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
                    AsyncSocketchannel.read(channel);
                }
            }
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
        public void failed(Throwable t, AsyncSocketchannel channel) {
            channel.pipeline().fireExceptionCaught(t);
            if (t instanceof IOException) {
                channel.close(channel.unsafe().voidFuture());
            } else {
                // start the next read
                AsyncSocketchannel.read(channel);
            }
        }
    }

    private static final class ConnectHandler implements CompletionHandler<Void, AsyncSocketchannel> {

        @Override
        public void completed(Void result, AsyncSocketchannel channel) {
            ((AsyncUnsafe) channel.unsafe()).connectSuccess();
            
            // start reading from channel
            AsyncSocketchannel.read(channel);
        }

        @Override
        public void failed(Throwable exc, AsyncSocketchannel channel) {
            ((AsyncUnsafe) channel.unsafe()).connectFailed(exc);
        }
    }

    @Override
    public AsyncSocketChannelConfig config() {
        if (config == null) {
            throw new IllegalStateException("Channel not registered yet");
        }
        return config;
    }


}
