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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.socket.SocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

public class WriteBeforeRegisteredTest extends AbstractClientSocketTest {

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testWriteBeforeConnectByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testWriteBeforeConnectByteBuf);
    }

    public void testWriteBeforeConnectByteBuf(Bootstrap cb) throws Throwable {
        TestHandler h = new TestHandler();
        SocketChannel ch = null;
        try {
            cb.handler(h);
            ch = (SocketChannel) cb.createUnregistered();
            ch.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 1 }));
            ch.register().sync();
            ch.connect(newSocketAddress());
        } finally {
            if (ch != null) {
                ch.close();
            }
        }
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testWriteBeforeConnect(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testWriteBeforeConnect);
    }

    public void testWriteBeforeConnect(Bootstrap cb) throws Throwable {
        TestHandler h = new TestHandler();
        SocketChannel ch = null;
        try {
            cb.handler(h);
            ch = (SocketChannel) cb.createUnregistered();
            ch.writeAndFlush(DefaultBufferAllocators.preferredAllocator().copyOf(new byte[] { 1 }));
            ch.register().sync();
            ch.connect(newSocketAddress());
        } finally {
            if (ch != null) {
                ch.close();
            }
        }
    }

    private static class TestHandler implements ChannelHandler {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
        }
    }
}
