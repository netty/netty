package io.netty.channel.epoll;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * @author Brandon Jiang
 * Helper class to switch for Nio or Epoll.
 * modified based on https://github.com/apache/zookeeper/blob/11c07921c15e2fb7692375327b53f26a583b77ca/zookeeper-server/src/main/java/org/apache/zookeeper/common/NettyUtils.java
 */
public final class NioEpollSwitcher {

    private NioEpollSwitcher() {
    }

    /**
     * If {@link Epoll#isAvailable()} {@code == true}, returns a new
     * {@link EpollEventLoopGroup}, otherwise returns a new
     * {@link NioEventLoopGroup}. Creates the event loop group using the
     * default number of threads.
     *
     * @return a new {@link EventLoopGroup}.
     */
    public static EventLoopGroup NioOrEpollEventLoopGroup() {
        return NioOrEpollEventLoopGroup(0);
    }

    /**
     * If {@link Epoll#isAvailable()} {@code == true}, returns a new
     * {@link EpollEventLoopGroup}, otherwise returns a new
     * {@link NioEventLoopGroup}. Creates the event loop group using the
     * specified number of threads instead of the default.
     *
     * @param nThreads see {@link NioEventLoopGroup#NioEventLoopGroup(int)}.
     * @return a new {@link EventLoopGroup}.
     */
    public static EventLoopGroup NioOrEpollEventLoopGroup(int nThreads) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(nThreads);
        } else {
            return new NioEventLoopGroup(nThreads);
        }
    }

    /**
     * If {@link Epoll#isAvailable()} {@code == true}, returns
     * {@link EpollSocketChannel}, otherwise returns {@link NioSocketChannel}.
     *
     * @return a socket channel class.
     */
    public static Class<? extends SocketChannel> nioOrEpollSocketChannel() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        } else {
            return NioSocketChannel.class;
        }
    }

    /**
     * If {@link Epoll#isAvailable()} {@code == true}, returns
     * {@link EpollServerSocketChannel}, otherwise returns
     * {@link NioServerSocketChannel}.
     *
     * @return a server socket channel class.
     */
    public static Class<? extends ServerSocketChannel> nioOrEpollServerSocketChannel() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        } else {
            return NioServerSocketChannel.class;
        }
    }


}
