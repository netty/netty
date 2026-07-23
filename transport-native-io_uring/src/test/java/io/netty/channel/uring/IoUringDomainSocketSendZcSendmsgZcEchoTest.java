package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.testsuite.transport.TestsuitePermutation;
import org.junit.jupiter.api.BeforeAll;

import java.net.SocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringDomainSocketSendZcSendmsgZcEchoTest extends IoUringSocketEchoTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
        assumeTrue(IoUring.isSendZcSupported());
    }

    @Override
    protected SocketAddress newSocketAddress() {
        return IoUringSocketTestPermutation.newDomainSocketAddress();
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return IoUringSocketTestPermutation.INSTANCE.domainSocket();
    }

    @Override
    protected void configure(ServerBootstrap sb, Bootstrap cb, ByteBufAllocator allocator) {
        super.configure(sb, cb, allocator);
        sb.childOption(IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, 0);
        cb.option(IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, 0);
    }
}
