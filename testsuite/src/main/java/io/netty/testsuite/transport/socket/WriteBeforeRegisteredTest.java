/*
 * Copyright 2013 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static io.netty.testsuite.transport.TestsuitePermutation.randomBufferType;

public class WriteBeforeRegisteredTest extends AbstractClientSocketTest {

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testWriteBeforeConnect(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testWriteBeforeConnect(bootstrap);
            }
        });
    }

    public void testWriteBeforeConnect(Bootstrap cb) throws Throwable {
        TestHandler h = new TestHandler();
        SocketChannel ch = null;
        try {
            ch = (SocketChannel) cb.handler(h).connect(newSocketAddress()).channel();
            ch.writeAndFlush(randomBufferType(ch.alloc(), new byte[] { 1 }, 0, 1));
        } finally {
            if (ch != null) {
                ch.close();
            }
        }
    }

    private static class TestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
        }
    }
}
