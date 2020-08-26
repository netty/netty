package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.SocketFixedLengthEchoTest;

import java.util.List;

public class IOUringSocketFixedLengthEchoTest extends SocketFixedLengthEchoTest {

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return IOUringSocketTestPermutation.INSTANCE.socket();
    }
}
