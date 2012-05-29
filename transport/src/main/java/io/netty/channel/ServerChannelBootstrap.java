package io.netty.channel;

import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.SocketAddresses;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

public class ServerChannelBootstrap {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ServerChannelBootstrap.class);
    private static final InetSocketAddress DEFAULT_LOCAL_ADDR = new InetSocketAddress(SocketAddresses.LOCALHOST, 0);

    private final ChannelHandler acceptor = new ChannelInitializer<Channel>() {
        @Override
        public void initChannel(Channel ch) throws Exception {
            Acceptor acceptor = new Acceptor();
            ch.pipeline().addLast(DefaultChannelPipeline.generateName(acceptor), acceptor);
        }
    };

    private final Map<ChannelOption<?>, Object> parentOptions = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<ChannelOption<?>, Object> childOptions = new LinkedHashMap<ChannelOption<?>, Object>();
    private EventLoop parentEventLoop;
    private EventLoop childEventLoop;
    private ServerChannel channel;
    private ChannelHandler initializer;
    private ChannelHandler childInitializer;
    private SocketAddress localAddress;

    public ServerChannelBootstrap eventLoop(EventLoop parentEventLoop, EventLoop childEventLoop) {
        if (parentEventLoop == null) {
            throw new NullPointerException("parentEventLoop");
        }
        this.parentEventLoop = parentEventLoop;
        this.childEventLoop = childEventLoop;
        return this;
    }

    public ServerChannelBootstrap channel(ServerChannel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;
        return this;
    }

    public <T> ServerChannelBootstrap option(ChannelOption<T> parentOption, T value) {
        if (parentOption == null) {
            throw new NullPointerException("parentOption");
        }
        if (value == null) {
            parentOptions.remove(parentOption);
        } else {
            parentOptions.put(parentOption, value);
        }
        return this;
    }

    public <T> ServerChannelBootstrap childOption(ChannelOption<T> childOption, T value) {
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

    public ServerChannelBootstrap initializer(ChannelHandler initializer) {
        this.initializer = initializer;
        return this;
    }

    public ServerChannelBootstrap childInitializer(ChannelHandler childInitializer) {
        if (childInitializer == null) {
            throw new NullPointerException("childInitializer");
        }
        this.childInitializer = childInitializer;
        return this;
    }

    public ServerChannelBootstrap localAddress(SocketAddress localAddress) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        this.localAddress = localAddress;
        return this;
    }

    public ServerChannelBootstrap localAddress(int port) {
        localAddress = new InetSocketAddress(port);
        return this;
    }

    public ServerChannelBootstrap localAddress(String host, int port) {
        localAddress = new InetSocketAddress(host, port);
        return this;
    }

    public ChannelFuture bind() {
        validate();
        return bind(channel.newFuture());
    }

    public ChannelFuture bind(ChannelFuture future) {
        validate();
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

        ChannelPipeline p = channel.pipeline();
        if (initializer != null) {
            p.addLast(DefaultChannelPipeline.generateName(initializer), initializer);
        }
        p.addLast(DefaultChannelPipeline.generateName(acceptor), acceptor);

        ChannelFuture f = parentEventLoop.register(channel).awaitUninterruptibly();
        if (!f.isSuccess()) {
            future.setFailure(f.cause());
            return future;
        }

        channel.bind(localAddress, future).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        return future;
    }

    public void shutdown() {
        if (parentEventLoop != null) {
            parentEventLoop.shutdown();
        }
        if (childEventLoop != null) {
            childEventLoop.shutdown();
        }
    }

    private void validate() {
        if (parentEventLoop == null) {
            throw new IllegalStateException("eventLoop not set");
        }
        if (channel == null) {
            throw new IllegalStateException("channel not set");
        }
        if (childInitializer == null) {
            throw new IllegalStateException("childInitializer not set");
        }
        if (childEventLoop == null) {
            logger.warn("childEventLoop is not set. Using eventLoop instead.");
            childEventLoop = parentEventLoop;
        }
        if (localAddress == null) {
            logger.warn("localAddress is not set. Using " + DEFAULT_LOCAL_ADDR + " instead.");
            localAddress = DEFAULT_LOCAL_ADDR;
        }
    }

    private class Acceptor extends ChannelInboundHandlerAdapter<Channel> {
        @Override
        public ChannelBufferHolder<Channel> newInboundBuffer(ChannelInboundHandlerContext<Channel> ctx) {
            return ChannelBufferHolders.messageBuffer();
        }

        @Override
        public void inboundBufferUpdated(ChannelInboundHandlerContext<Channel> ctx) {
            Queue<Channel> in = ctx.inbound().messageBuffer();
            for (;;) {
                Channel child = in.poll();
                if (child == null) {
                    break;
                }

                child.pipeline().addLast(DefaultChannelPipeline.generateName(childInitializer), childInitializer);

                for (Entry<ChannelOption<?>, Object> e: childOptions.entrySet()) {
                    try {
                        if (!child.config().setOption((ChannelOption<Object>) e.getKey(), e.getValue())) {
                            logger.warn("Unknown channel option: " + e);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to set a channel option: " + child, t);
                    }
                }

                try {
                    childEventLoop.register(child);
                } catch (Throwable t) {
                    logger.warn("Failed to register an accepted channel: " + child, t);
                }
            }
        }

    }
}
