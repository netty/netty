/*
 * Copyright 2012 The Netty Project
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
package io.netty.example.traffic;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Example for TrafficShaping: in the handler, several methods are implemented to show different
 * variants on handling the write buffer, and then the memory consumption, in order to prevent
 * Out Of Memory exception. You can try them by switching the modeTransfer to the desired value.
 *<br><br>
 * You can also change the behavior of the handler (long time task using simulLongTask) and adding
 * a GlobalTransferShapingHandler (by default, only the ChannelTrafficShapingHandler is added).
 *<br><br>
 * useFutureListener = true
 *     => best choice: 1 block at a time in memory, but lot of object creations (GenericFutureListener)<br>
 * useSetFutureListener = true
 *     => best choice: similar to useFutureListener by set of blocks<br>
 * useCheckWriteSuspended = true
 *     => second best choice: limited blocks in memory, but depending on application speed:
 *     too quick could fill up the buffers, else will be fine<br>
 * useChunkFile = true
 *     => not recommended since will create all blocks in memory without taking into account contention<br>
 * useDefault = true
 *     => not recommended since will create all blocks in memory without taking into account contention<br>
 *
 * simulLongTask = true
 *     => to show that it could impact object memory in useCheckWriteServerTrafficShaping mode<br>
 * useGlobalTSH = true
 *     => to show that individual speeds are divided by the number of concurrent requests<br>
 *
 * @author "Frederic Bregier"
 *
 */
public final class HttpStaticFileServerTrafficShaping {

    public static enum ModeTransfer {
        useFutureListener, useSetFutureListener, useCheckWriteSuspended,
        useCheckWritability, useChunkedFile, useDefault
    }
    static ModeTransfer modeTransfer = ModeTransfer.useCheckWritability;
    static boolean simulLongTask = false;
    static boolean useGlobalTSH = false;
    /*
     * useFutureListener = true
     *     => best choice: 1 block at a time in memory, but lot of object creations (GenericFutureListener)
     * useSetFutureListener = true
     *     => best choice: similar to useFutureListener by set of blocks
     * useCheckWriteSuspended = true
     *     => second best choice: limited blocks in memory, but depending on application speed:
     *     too quick could fill up the buffers, else will be fine
     * useChunkFile = true
     *     => not recommended since will create all blocks in memory without taking into account contention
     * useDefault = true
     *     => not recommended since will create all blocks in memory without taking into account contention
     *
     * simulLongTask = true
     *     => to show that it could impact object memory in useCheckWriteServerTrafficShaping mode
     * useGlobalTSH = true
     *     => to show that individual speeds are divided by the number of concurrent requests
     */

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));
    public static EventLoopGroup businessGroup = new NioEventLoopGroup();
    static GlobalTrafficShapingHandlerWithLog gtsh;

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContext.newServerContext(SslProvider.JDK, ssc.certificate(), ssc.privateKey());
        } else {
            sslCtx = null;
        }
        EventLoopGroup globalGroup = null;
        if (useGlobalTSH) {
            globalGroup = new NioEventLoopGroup();
            gtsh = new GlobalTrafficShapingHandlerWithLog(globalGroup, 1024*1024, 1024*1024, 1000);
        }
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new HttpStaticFileServerTrafficShapingInitializer(sslCtx));

            Channel ch = b.bind(PORT).sync().channel();

            System.err.println("Open your web browser and navigate to " +
                    (SSL? "https" : "http") + "://127.0.0.1:" + PORT + '/');

            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            businessGroup.shutdownGracefully();
            if (useGlobalTSH) {
                globalGroup.shutdownGracefully();
            }
        }
    }


}
