/*
 * Copyright 2022 The Netty Project
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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.testsuite.transport.TestsuitePermutation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.FileNotFoundException;
import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIf("isSupported")
public class DomainDatagramPathTest extends AbstractClientSocketTest {

    static boolean isSupported() {
        return NioDomainSocketTestUtil.isDatagramSupported();
    }

    @Test
    void testConnectPathDoesNotExist(TestInfo testInfo) throws Throwable {
        run(testInfo, bootstrap -> {
            try {
                bootstrap.handler(new ChannelHandlerAdapter() { })
                        .connect(SocketTestPermutation.newDomainSocketAddress()).asStage().get();
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
                        .bind(SocketTestPermutation.newDomainSocketAddress()).asStage().get();
                ch.writeAndFlush(new DatagramPacket(
                        ch.bufferAllocator().copyOf("test", US_ASCII),
                        SocketTestPermutation.newDomainSocketAddress())).asStage().sync();
                fail("Expected FileNotFoundException");
            } catch (Exception e) {
                assertTrue(e.getCause() instanceof FileNotFoundException);
            }
        });
    }

    @Override
    protected List<TestsuitePermutation.BootstrapFactory<Bootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.domainDatagramSocket();
    }
}
