/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.AbstractClientSocketTest;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EpollDomainDatagramPathTest extends AbstractClientSocketTest {

    @Test
    void testConnectPathDoesNotExist(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) {
                Throwable cause = assertThrows(ExecutionException.class, () ->
                        bootstrap.handler(new ChannelInboundHandler() { })
                                .connect(EpollSocketTestPermutation.newDomainSocketAddress()).get());
                assertInstanceOf(FileNotFoundException.class, cause.getCause());
            }
        });
    }

    @Test
    void testWriteReceiverPathDoesNotExist(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Exception {
                Channel ch = bootstrap.handler(new ChannelInboundHandler() { })
                        .bind(EpollSocketTestPermutation.newDomainSocketAddress()).get();
                CompletionException e = assertThrows(CompletionException.class,
                        () -> ch.writeAndFlush(
                                new DatagramPacket(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII),
                        EpollSocketTestPermutation.newDomainSocketAddress())).sync());
                assertInstanceOf(FileNotFoundException.class, e.getCause());
            }
        });
    }

    @Override
    protected List<TestsuitePermutation.BootstrapFactory<Bootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.domainDatagramSocket();
    }
}
