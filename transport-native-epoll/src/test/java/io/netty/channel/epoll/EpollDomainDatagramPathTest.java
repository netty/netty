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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.unix.DomainDatagramPacket;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.AbstractClientSocketTest;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.FileNotFoundException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EpollDomainDatagramPathTest extends AbstractClientSocketTest {

    @Test
    void testConnectPathDoesNotExist(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) {
                try {
                    bootstrap.handler(new ChannelInboundHandlerAdapter())
                             .connect(EpollSocketTestPermutation.newSocketAddress()).sync().channel();
                    fail("Expected FileNotFoundException");
                } catch (Exception e) {
                    assertTrue(e instanceof FileNotFoundException);
                }
            }
        });
    }

    @Test
    void testWriteReceiverPathDoesNotExist(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) {
                try {
                    Channel ch = bootstrap.handler(new ChannelInboundHandlerAdapter())
                                          .bind(EpollSocketTestPermutation.newSocketAddress()).sync().channel();
                    ch.writeAndFlush(new DomainDatagramPacket(
                            Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII),
                            EpollSocketTestPermutation.newSocketAddress())).sync();
                    fail("Expected FileNotFoundException");
                } catch (Exception e) {
                    assertTrue(e instanceof FileNotFoundException);
                }
            }
        });
    }

    @Override
    protected List<TestsuitePermutation.BootstrapFactory<Bootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.domainDatagramSocket();
    }
}
