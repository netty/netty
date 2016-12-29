package io.netty.channel.nio;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.NetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Created by sixie.xyn on 2016/12/29.
 */
public class NioServerSocketChannelMaxConnectionsTest {

    @Test
    public void testMaxConnections() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            final int MAX_CONNECTIONS = 5;
            final int BACKLOG = 16;
            final List<ChannelHandlerContext> connections = new CopyOnWriteArrayList<ChannelHandlerContext>();

            ServerBootstrap sb = new ServerBootstrap();
            sb.group(group).channel(NioServerSocketChannel.class);
            sb.option(ChannelOption.SO_MAXCONNECTIONS, MAX_CONNECTIONS);
            sb.option(ChannelOption.SO_BACKLOG, BACKLOG);
            sb.childOption(ChannelOption.SO_SNDBUF, 1024);
            sb.childHandler(new MyHandler(connections));

            SocketAddress address = sb.bind(0).sync().channel().localAddress();

            //no more than MAX_CONNECTIONS connections could be accepted
            List<Socket> clients = new ArrayList<Socket>();
            for (int i = 0; i < MAX_CONNECTIONS+(BACKLOG/2); i++) {
                Socket s = new Socket(NetUtil.LOCALHOST, ((InetSocketAddress) address).getPort());
                clients.add(s);
            }
            Thread.sleep(500);
            Assert.assertEquals(connections.size(), MAX_CONNECTIONS);


            //fill the backlog queue and trigger ConnectException
            try{
                for(int i=0;i<BACKLOG*2;i++) {
                    Socket s = new Socket(NetUtil.LOCALHOST, ((InetSocketAddress) address).getPort());
                }
            }catch (Throwable t){
                assertThat(t, is(instanceOf(ConnectException.class)));
            }

            //accept connections in backlog queue
            for(int i=0;i<MAX_CONNECTIONS;i++) {
                connections.remove(0).close();
            }
            Thread.sleep(500);
            Assert.assertEquals(connections.size(), MAX_CONNECTIONS);
        } finally {
            group.shutdownGracefully().sync();
        }

    }

    @ChannelHandler.Sharable
    private static class MyHandler extends ChannelInboundHandlerAdapter {

        private List<ChannelHandlerContext> connections;

        public MyHandler(List<ChannelHandlerContext> connections) {
            this.connections = connections;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            this.connections.add(ctx);
        }
    }
}
