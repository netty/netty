/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.SocketFileRegionTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class KQueueSocketFileRegionTest extends SocketFileRegionTest {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testZipFileRegion(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap sb, Bootstrap cb) throws Throwable {
                final byte[] data = new byte[256 * 1024 + 73];
                new Random(0).nextBytes(data);
                final byte[] expected = Arrays.copyOfRange(data, 37, data.length - 19);
                Path archive = Files.createTempDirectory("netty-region");
                URI uri = URI.create("jar:" + archive.resolve("file.zip").toUri());
                Channel server = null;
                Channel client = null;
                final CompletableFuture<byte[]> received =
                        new CompletableFuture<byte[]>();
                final AtomicReference<Channel> child =
                        new AtomicReference<Channel>();
                try (FileSystem zip = FileSystems.newFileSystem(
                        uri, Collections.singletonMap("create", "true"))) {
                    Path path = zip.getPath("/file");
                    Files.write(path, data);
                    sb.childHandler(new SimpleChannelInboundHandler<ByteBuf>() {
                        private final byte[] bytes = new byte[expected.length];
                        private int offset;

                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            child.set(ctx.channel());
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                            int length = msg.readableBytes();
                            msg.readBytes(bytes, offset, length);
                            offset += length;
                            if (offset == bytes.length) {
                                received.complete(bytes);
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            received.completeExceptionally(cause);
                            ctx.close();
                        }
                    });
                    cb.handler(new ChannelInboundHandlerAdapter());
                    server = sb.bind().sync().channel();
                    client = cb.connect(server.localAddress()).sync().channel();
                    DefaultFileRegion region = new DefaultFileRegion(
                            FileChannel.open(path, StandardOpenOption.READ), 37, expected.length);
                    client.writeAndFlush(region).sync();
                    assertThat(received.get(10, TimeUnit.SECONDS))
                            .containsExactly(expected);
                    assertThat(region.refCnt()).isZero();
                } finally {
                    if (client != null) {
                        client.close().syncUninterruptibly();
                    }
                    if (child.get() != null) {
                        child.get().close().syncUninterruptibly();
                    }
                    if (server != null) {
                        server.close().syncUninterruptibly();
                    }
                    Files.deleteIfExists(archive.resolve("file.zip"));
                    Files.delete(archive);
                }
            }
        });
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return KQueueSocketTestPermutation.INSTANCE.socket();
    }
}
