/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(Parameterized.class)
public class AutoFlushTest {

    @Parameters
    public static Collection<TestEnvironment> parameters() {
        List<TestEnvironment> toReturn = new ArrayList<TestEnvironment>();
        TestEnvironment nioEnvironment = new TestEnvironment(new NioEventLoopGroup(), new NioEventLoopGroup(),
                                                             NioServerSocketChannel.class, NioSocketChannel.class);
        toReturn.add(nioEnvironment);
        return toReturn;
    }

    @Parameter
    public TestEnvironment environment;

    @Test(timeout = 60000)
    public void testAutoFlushSingleWrite() throws Exception {
        environment.connect();
        ByteBuf data = newDataBuffer();
        environment.pipeline.write(data).sync();
    }

    @Test(timeout = 60000)
    public void testAutoFlushMultipleWrites() throws Exception {
        environment.connect();
        final ChannelPromise aggreggatedPromise = environment.clientChannel.newPromise();
        ByteBuf data = newDataBuffer();
        PromiseCombiner promiseCombiner = new PromiseCombiner();
        for (int i = 0; i < 10; i++) {
            ChannelPromise promise = environment.clientChannel.newPromise();
            promiseCombiner.add(promise);
            environment.clientChannel.write(data.retain(), promise);
        }
        promiseCombiner.finish(aggreggatedPromise);
        aggreggatedPromise.sync();
    }

    @Test(timeout = 60000)
    public void testAutoFlushWithinEventloop() throws Exception {
        environment.connect();
        final AtomicReference<ChannelFuture> writeResult = new AtomicReference<ChannelFuture>();
        environment.clientChannel.eventLoop().submit(new Runnable() {
            @Override
            public void run() {
                writeResult.set(environment.pipeline.write(newDataBuffer()));
            }
        }).sync();

        writeResult.get().sync();
    }

    private static ByteBuf newDataBuffer() {
        byte[] dataArr = new byte[32];
        ThreadLocalRandom.current().nextBytes(dataArr);
        return Unpooled.wrappedBuffer(dataArr);
    }

    public static final class TestEnvironment {

        private final EventLoopGroup serverEventloop;
        private final EventLoopGroup clientEventloop;
        private final ServerBootstrap serverBootstrap;
        private final Bootstrap bootstrap;
        private ChannelPipeline pipeline;
        private Channel clientChannel;
        private Channel serverChannel;

        private TestEnvironment(EventLoopGroup serverEventloop, EventLoopGroup clientEventloop,
                                Class<? extends ServerChannel> serverChannelClass,
                                Class<? extends Channel> clientChannelClass) {
            this.serverEventloop = serverEventloop;
            this.clientEventloop = clientEventloop;
            serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(serverEventloop)
                           .channel(serverChannelClass)
                           .childHandler(new ChannelInitializer<Channel>() {
                  @Override
                  protected void initChannel(Channel ch) throws Exception {
                      // Nothing to do.
                  }
              });
            bootstrap = new Bootstrap();
            bootstrap.option(ChannelOption.AUTO_FLUSH, true)
                     .group(clientEventloop)
                     .channel(clientChannelClass)
                     .handler(new ChannelInitializer<Channel>() {
                         @Override
                         protected void initChannel(Channel ch) throws Exception {
                             // Nothing to do.
                         }
                     });
        }

        public void connect() {
            ChannelFuture bind = serverBootstrap.bind(0);
            SocketAddress serverAddr;
            try {
                bind.sync();
                serverChannel = bind.channel();
                serverAddr = serverChannel.localAddress();
                ChannelFuture clientChannelFuture = bootstrap.connect(serverAddr);
                clientChannelFuture.sync();
                clientChannel = clientChannelFuture.channel();
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
            pipeline = clientChannel.pipeline();
            if (!clientChannel.config().getOption(ChannelOption.AUTO_FLUSH)) {
                throw new IllegalStateException("Auto-flush not set.");
            }
        }

        private void shutdown() throws InterruptedException {
            clientChannel.close().sync();
            serverEventloop.shutdownGracefully();
            clientEventloop.shutdownGracefully();
        }
    }
}
