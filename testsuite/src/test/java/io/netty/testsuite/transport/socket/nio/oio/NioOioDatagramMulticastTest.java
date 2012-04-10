package io.netty.testsuite.transport.socket.nio.oio;

import java.util.concurrent.Executor;

import io.netty.channel.socket.DatagramChannelFactory;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannelFactory;
import io.netty.channel.socket.oio.OioDatagramChannelFactory;
import io.netty.testsuite.transport.socket.AbstractDatagramMulticastTest;

public class NioOioDatagramMulticastTest extends AbstractDatagramMulticastTest {

    @Override
    protected DatagramChannelFactory newServerSocketChannelFactory(Executor executor) {
        return new OioDatagramChannelFactory(executor);
    }

    @Override
    protected DatagramChannelFactory newClientSocketChannelFactory(Executor executor) {
        return new NioDatagramChannelFactory(executor, NioDatagramChannel.ProtocolFamily.INET);

    }

}
