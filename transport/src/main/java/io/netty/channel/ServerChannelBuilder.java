package io.netty.channel;

import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.SocketAddresses;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

public class ServerChannelBuilder {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ServerChannelBuilder.class);
    private static final InetSocketAddress DEFAULT_LOCAL_ADDR = new InetSocketAddress(SocketAddresses.LOCALHOST, 0);

    private final Acceptor acceptor = new Acceptor();
    private final Map<ChannelOption<?>, Object> parentOptions = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<ChannelOption<?>, Object> childOptions = new LinkedHashMap<ChannelOption<?>, Object>();
    private EventLoop parentEventLoop;
    private EventLoop childEventLoop;
    private ServerChannel parentChannel;
    private ChannelHandler parentInitializer;
    private ChannelHandler childInitializer;
    private SocketAddress localAddress;

    public ServerChannelBuilder parentEventLoop(EventLoop parentEventLoop) {
        if (parentEventLoop == null) {
            throw new NullPointerException("parentEventLoop");
        }
        this.parentEventLoop = parentEventLoop;
        return this;
    }

    public ServerChannelBuilder childEventLoop(EventLoop childEventLoop) {
        if (childEventLoop == null) {
            throw new NullPointerException("childEventLoop");
        }
        this.childEventLoop = childEventLoop;
        return this;
    }

    public ServerChannelBuilder parentChannel(ServerChannel parentChannel) {
        if (parentChannel == null) {
            throw new NullPointerException("parentChannel");
        }
        this.parentChannel = parentChannel;
        return this;
    }

    public <T> ServerChannelBuilder parentOption(ChannelOption<T> parentOption, T value) {
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

    public <T> ServerChannelBuilder childOption(ChannelOption<T> childOption, T value) {
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

    public ServerChannelBuilder parentInitializer(ChannelHandler parentInitializer) {
        this.parentInitializer = parentInitializer;
        return this;
    }

    public ServerChannelBuilder childInitializer(ChannelHandler childInitializer) {
        if (childInitializer == null) {
            throw new NullPointerException("childInitializer");
        }
        this.childInitializer = childInitializer;
        return this;
    }

    public ServerChannelBuilder localAddress(SocketAddress localAddress) {
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        this.localAddress = localAddress;
        return this;
    }

    public ChannelFuture bind() {
        validate();
        return bind(parentChannel.newFuture());
    }

    public ChannelFuture bind(ChannelFuture future) {
        validate();
        if (parentChannel.isActive()) {
            future.setFailure(new IllegalStateException("parentChannel already bound: " + parentChannel));
            return future;
        }
        if (parentChannel.isRegistered()) {
            future.setFailure(new IllegalStateException("parentChannel already registered: " + parentChannel));
            return future;
        }
        if (!parentChannel.isOpen()) {
            future.setFailure(new ClosedChannelException());
            return future;
        }

        ChannelPipeline p = parentChannel.pipeline();
        if (parentInitializer != null) {
            p.addLast(ChannelBuilder.generateName(parentInitializer), parentInitializer);
        }
        p.addLast(ChannelBuilder.generateName(acceptor), acceptor);

        ChannelFuture f = parentEventLoop.register(parentChannel).awaitUninterruptibly();
        if (!f.isSuccess()) {
            future.setFailure(f.cause());
            return future;
        }

        parentChannel.bind(localAddress, future).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        return future;
    }

    public void validate() {
        if (parentEventLoop == null) {
            throw new IllegalStateException("parentEventLoop not set");
        }
        if (parentChannel == null) {
            throw new IllegalStateException("parentChannel not set");
        }
        if (childInitializer == null) {
            throw new IllegalStateException("childInitializer not set");
        }
        if (childEventLoop == null) {
            logger.warn("childEventLoop is not set. Using parentEventLoop instead.");
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
            return ChannelBufferHolders.messageBuffer(new ArrayDeque<Channel>());
        }

        @Override
        public void inboundBufferUpdated(ChannelInboundHandlerContext<Channel> ctx) {
            Queue<Channel> in = ctx.in().messageBuffer();
            for (;;) {
                Channel child = in.poll();
                if (child == null) {
                    break;
                }

                child.pipeline().addLast(ChannelBuilder.generateName(childInitializer), childInitializer);

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
