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
package io.netty.bootstrap;

import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInboundMessageHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.aio.AioEventLoopGroup;
import io.netty.channel.socket.aio.AioServerSocketChannel;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.AttributeKey;
import io.netty.util.NetworkConstants;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * {@link Bootstrap} sub-class which allows easy bootstrap of {@link ServerChannel}
 *
 */
public class ServerBootstrap extends AbstractBootstrap<ServerBootstrap> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ServerBootstrap.class);
    private static final InetSocketAddress DEFAULT_LOCAL_ADDR = new InetSocketAddress(NetworkConstants.LOCALHOST, 0);

    private final ChannelHandler acceptor = new ChannelInitializer<Channel>() {
        @Override
        public void initChannel(Channel ch) throws Exception {
            ch.pipeline().addLast(new Acceptor());
        }
    };

    private final Map<ChannelOption<?>, Object> childOptions = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<AttributeKey<?>, Object> childAttrs = new LinkedHashMap<AttributeKey<?>, Object>();
    private EventLoopGroup childGroup;
    private ChannelHandler childHandler;

    /**
     * Specify the {@link EventLoopGroup} which is used for the parent (acceptor) and the child (client).
     */
    @Override
    public ServerBootstrap group(EventLoopGroup group) {
        return group(group, group);
    }

    /**
     * Set the {@link EventLoopGroup} for the parent (acceptor) and the child (client). These
     * {@link EventLoopGroup}'s are used to handle all the events and IO for {@link SocketChannel} and
     * {@link Channel}'s.
     */
    public ServerBootstrap group(EventLoopGroup parentGroup, EventLoopGroup childGroup) {
        super.group(parentGroup);
        if (childGroup == null) {
            throw new NullPointerException("childGroup");
        }
        if (this.childGroup != null) {
            throw new IllegalStateException("childGroup set already");
        }
        this.childGroup = childGroup;
        return this;
    }

    /**
     * The {@link Class} which is used to create the {@link ServerChannel} from (for the acceptor).
     */
    @Override
    public ServerBootstrap channel(Class<? extends Channel> channelClass) {
        if (channelClass == null) {
            throw new NullPointerException("channelClass");
        }
        if (!ServerChannel.class.isAssignableFrom(channelClass)) {
            throw new IllegalArgumentException();
        }
        if (channelClass == AioServerSocketChannel.class) {
            channelFactory(new AioServerSocketChannelFactory());
        }
        return super.channel(channelClass);
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Channel} instances once they get created
     * (after the acceptor accepted the {@link Channel}). Use a value of <code>null</code> to remove a previous set
     * {@link ChannelOption}.
     */
    public <T> ServerBootstrap childOption(ChannelOption<T> childOption, T value) {
        if (childOption == null) {
            throw new NullPointerException("childOption");
        }
        if (value == null) {
            childOptions.remove(childOption);
        } else {
            childOptions.put(childOption, value);
        }
        return this;
    }

    public <T> ServerBootstrap childAttr(AttributeKey<T> childKey, T value) {
        if (childKey == null) {
            throw new NullPointerException("childKey");
        }
        if (value == null) {
            childAttrs.remove(childKey);
        } else {
            childAttrs.put(childKey, value);
        }
        return this;
    }

    /**
     * Set the {@link ChannelHandler} which is used to server the request for the {@link Channel}'s.
     */
    public ServerBootstrap childHandler(ChannelHandler childHandler) {
        if (childHandler == null) {
            throw new NullPointerException("childHandler");
        }
        this.childHandler = childHandler;
        return this;
    }

    @Override
    public ChannelFuture bind(ChannelFuture future) {
        validate(future);
        Channel channel = future.channel();
        if (channel.isActive()) {
            future.setFailure(new IllegalStateException("channel already bound: " + channel));
            return future;
        }
        if (channel.isRegistered()) {
            future.setFailure(new IllegalStateException("channel already registered: " + channel));
            return future;
        }
        if (!channel.isOpen()) {
            future.setFailure(new ClosedChannelException());
            return future;
        }

        try {
            channel.config().setOptions(options());
        } catch (Exception e) {
            future.setFailure(e);
            return future;
        }

        for (Entry<AttributeKey<?>, Object> e: attrs().entrySet()) {
            channel.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
        }

        ChannelPipeline p = future.channel().pipeline();
        if (handler() != null) {
            p.addLast(handler());
        }
        p.addLast(acceptor);

        ChannelFuture f = group().register(channel).awaitUninterruptibly();
        if (!f.isSuccess()) {
            future.setFailure(f.cause());
            return future;
        }

        if (!ensureOpen(future)) {
            return future;
        }

        channel.bind(localAddress(), future).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        return future;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (childGroup != null) {
            childGroup.shutdown();
        }
    }

    @Override
    protected void validate() {
        super.validate();
        if (childHandler == null) {
            throw new IllegalStateException("childHandler not set");
        }
        if (childGroup == null) {
            logger.warn("childGroup is not set. Using parentGroup instead.");
            childGroup = group();
        }
        if (localAddress() == null) {
            logger.warn("localAddress is not set. Using " + DEFAULT_LOCAL_ADDR + " instead.");
            localAddress(DEFAULT_LOCAL_ADDR);
        }
    }


    private class Acceptor
            extends ChannelInboundHandlerAdapter implements ChannelInboundMessageHandler<Channel> {

        @Override
        public MessageBuf<Channel> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.messageBuffer();
        }

        @SuppressWarnings("unchecked")
        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx) {
            MessageBuf<Channel> in = ctx.inboundMessageBuffer();
            for (;;) {
                Channel child = in.poll();
                if (child == null) {
                    break;
                }

                child.pipeline().addLast(childHandler);

                for (Entry<ChannelOption<?>, Object> e: childOptions.entrySet()) {
                    try {
                        if (!child.config().setOption((ChannelOption<Object>) e.getKey(), e.getValue())) {
                            logger.warn("Unknown channel option: " + e);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to set a channel option: " + child, t);
                    }
                }

                for (Entry<AttributeKey<?>, Object> e: childAttrs.entrySet()) {
                    child.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
                }

                try {
                    childGroup.register(child);
                } catch (Throwable t) {
                    child.unsafe().closeForcibly();
                    logger.warn("Failed to register an accepted channel: " + child, t);
                }
            }
        }
    }

    private final class AioServerSocketChannelFactory implements ChannelFactory {
        @Override
        public Channel newChannel() {
            return new AioServerSocketChannel((AioEventLoopGroup) group(),  (AioEventLoopGroup) childGroup);
        }
    }
}

