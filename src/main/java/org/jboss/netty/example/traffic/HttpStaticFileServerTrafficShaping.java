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
package org.jboss.netty.example.traffic;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.ssl.SslContext;
import org.jboss.netty.handler.ssl.util.SelfSignedCertificate;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
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
 * useCheckWritability = true
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
 */
public final class HttpStaticFileServerTrafficShaping {
    public static enum ModeTransfer {
        useFutureListener, useSetFutureListener,
        useCheckWritability, useChunkedFile, useDefault
    }
    static ModeTransfer modeTransfer = ModeTransfer.useChunkedFile;
    static boolean simulLongTask = true;
    static boolean useGlobalTSH;

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));
    static GlobalTrafficShapingHandlerWithLog gtsh;
    static Timer timer = new HashedWheelTimer();
    static ExecutionHandler eh = new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(10, 0, 0));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());
        } else {
            sslCtx = null;
        }
        if (useGlobalTSH) {
            gtsh = new GlobalTrafficShapingHandlerWithLog(timer, 1024 * 1024, 1024 * 1024, 1000);
        }
        // Configure the server.
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new HttpStaticFileServerTrafficShapingPipelineFactory(sslCtx));

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(PORT));
    }
}
