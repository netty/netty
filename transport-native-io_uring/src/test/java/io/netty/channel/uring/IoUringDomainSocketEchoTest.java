package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.testsuite.transport.TestsuitePermutation;

import java.net.SocketAddress;
import java.util.List;

public class IoUringDomainSocketEchoTest extends IoUringSocketEchoTest {
    @Override
    protected SocketAddress newSocketAddress() {
        return IoUringSocketTestPermutation.newDomainSocketAddress();
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return IoUringSocketTestPermutation.INSTANCE.domainSocket();
    }
}
