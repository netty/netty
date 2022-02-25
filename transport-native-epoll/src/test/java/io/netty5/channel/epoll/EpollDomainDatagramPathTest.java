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
package io.netty5.channel.epoll;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.Unpooled;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.unix.DomainDatagramPacket;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.testsuite.transport.socket.AbstractClientSocketTest;
import io.netty5.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.FileNotFoundException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EpollDomainDatagramPathTest extends AbstractClientSocketTest {

    @Test
    void testConnectPathDoesNotExist(TestInfo testInfo) throws Throwable {
        run(testInfo, bootstrap -> {
            try {
                bootstrap.handler(new ChannelHandlerAdapter() { })
                         .connect(EpollSocketTestPermutation.newDomainSocketAddress()).get();
                fail("Expected FileNotFoundException");
            } catch (Exception e) {
                assertTrue(e.getCause() instanceof FileNotFoundException);
            }
        });
    }

    @Test
    void testWriteReceiverPathDoesNotExist(TestInfo testInfo) throws Throwable {
        run(testInfo, bootstrap -> {
            try {
                Channel ch = bootstrap.handler(new ChannelHandlerAdapter() { })
                                      .bind(EpollSocketTestPermutation.newDomainSocketAddress()).get();
                ch.writeAndFlush(new DomainDatagramPacket(
                        Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII),
                        EpollSocketTestPermutation.newDomainSocketAddress())).sync();
                fail("Expected FileNotFoundException");
            } catch (Exception e) {
                assertTrue(e.getCause() instanceof FileNotFoundException);
            }
        });
    }

    @Override
    protected List<TestsuitePermutation.BootstrapFactory<Bootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.domainDatagramSocket();
    }
}
