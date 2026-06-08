/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.DatagramConnectedWriteExceptionTest;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class IoUringDatagramConnectedWriteExceptionTest extends DatagramConnectedWriteExceptionTest {

    private static final byte[] BAD_PREFIX = "BAD-PREFIX-".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EXPECTED = "EXPECTED-direct-reader-index".getBytes(StandardCharsets.UTF_8);

    @Override
    protected List<TestsuitePermutation.BootstrapFactory<Bootstrap>> newFactories() {
        return IoUringSocketTestPermutation.INSTANCE.datagramSocket();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testWriteOffsetBytebuf(TestInfo testInfo) throws Throwable {
        run(testInfo, (Runner<Bootstrap>) this::testWriteOffsetBytebuf);
    }

    private void testWriteOffsetBytebuf(Bootstrap clientBootstrap) throws Throwable {
        CompletableFuture<byte[]> received = new CompletableFuture<>();
        Bootstrap serverBootstrap = clientBootstrap.clone()
                .option(ChannelOption.SO_BROADCAST, false)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                        ByteBuf content = packet.content();
                        byte[] bytes = new byte[content.readableBytes()];
                        content.getBytes(content.readerIndex(), bytes);
                        received.complete(bytes);
                    }
                });

        Channel serverChannel = serverBootstrap.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).sync().channel();
        InetSocketAddress serverAddress = (InetSocketAddress) serverChannel.localAddress();

        clientBootstrap.option(ChannelOption.AUTO_READ, false)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                        // no-op
                    }
                });

        Channel clientChannel = clientBootstrap.connect(serverAddress).sync().channel();
        try {
           ByteBuf content = directReaderIndex(serverChannel.alloc());
           clientChannel.writeAndFlush(new DatagramPacket(content, serverAddress)).sync();

           byte[] actual = received.join();
           assertArrayEquals(EXPECTED, actual);
        } finally {
           if (clientChannel != null) {
               clientChannel.close().sync();
           }
           if (serverChannel != null) {
               serverChannel.close().sync();
           }
       }
     }

    private static ByteBuf directReaderIndex(ByteBufAllocator alloc) {
        ByteBuf buf = alloc.directBuffer(BAD_PREFIX.length + EXPECTED.length);
        buf.writeBytes(BAD_PREFIX);
        buf.writeBytes(EXPECTED);
        buf.readerIndex(BAD_PREFIX.length);
        return buf;
    }

}
