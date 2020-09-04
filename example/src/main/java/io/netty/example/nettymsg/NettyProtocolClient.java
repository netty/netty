/*
 *
 *  * Copyright 2020 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 *  * "License"); you may not use this file except in compliance with the License. You may obtain a
 *  * copy of the License at:
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License
 *  * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  * or implied. See the License for the specific language governing permissions and limitations under
 *  * the License.
 *
 */

package io.netty.example.nettymsg;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NettyProtocolClient {
    private static ExecutorService executor = Executors.newFixedThreadPool(1);

    public static void connect(final int port, final String host) {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new NettyMessageDecoder(1024 * 1024, 4, 4))
                                    .addLast(new NettyMessageEncoder())
                                    .addLast(new ReadTimeoutHandler(50))
                                    .addLast(new LoginAuthReqHandler())
                                    .addLast(new HeartbeatReqHandler());
                        }
                    });

            //            final String localhost = "127.0.0.1";
            //            final int localPort = 1922;
            System.out.println("client connect @" + host + ":" + port);
            ChannelFuture future =
                    bootstrap.connect(new InetSocketAddress(host, port)).sync().channel().closeFuture().sync();
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    {
                        try {
                            int timeout = 15;
                            System.out.println("retry connect after :" + timeout);
                            TimeUnit.SECONDS.sleep(timeout);
                            try {
                                System.out.println("retry connect ...");
                                connect(port, host);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            if (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        int port = 12342;
        String host = "127.0.0.1";
        connect(port, host);
    }
}
