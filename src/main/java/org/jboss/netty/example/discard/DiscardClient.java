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
package org.jboss.netty.example.discard;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.ssl.SslContext;
import org.jboss.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.jboss.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.jboss.netty.handler.traffic.GlobalTrafficShapingHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Keeps sending random data to the specified address.
 */
public final class DiscardClient {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", "8009"));
    static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));
    static final int MAXGLOBALTHROUGHPUT = Integer.parseInt(System.getProperty("maxGlobalThroughput", "0"));
    static final int MAXCHANNELTHROUGHPUT = Integer.parseInt(System.getProperty("maxChannelThroughput", "0"));
    static final int connectionCount = Integer.parseInt(System.getProperty("connectionCount", "1"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            sslCtx = SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE);
        } else {
            sslCtx = null;
        }

        // Configure the bootstrap.
        ClientBootstrap bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
        final Timer timer = new HashedWheelTimer();
        final GlobalTrafficShapingHandler gtsh;
        final GlobalChannelTrafficShapingHandlerWithLog gctsh;
        if (MAXGLOBALTHROUGHPUT > 0 && MAXCHANNELTHROUGHPUT > 0) {
            gctsh = new GlobalChannelTrafficShapingHandlerWithLog(timer, MAXGLOBALTHROUGHPUT, 0,
                    MAXCHANNELTHROUGHPUT, 0, 1000);
            gtsh = null;
        } else if (MAXGLOBALTHROUGHPUT > 0) {
            gtsh = new GlobalTrafficShapingHandler(timer, MAXGLOBALTHROUGHPUT, 0, 1000);
            gctsh = null;
        } else {
            gtsh = null;
            gctsh = null;
        }
        try {
            bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                public ChannelPipeline getPipeline() {
                    ChannelPipeline p = Channels.pipeline();
                    if (sslCtx != null) {
                        p.addLast("ssl", sslCtx.newHandler(HOST, PORT));
                    }
                    if (MAXGLOBALTHROUGHPUT > 0 && MAXCHANNELTHROUGHPUT > 0) {
                        p.addLast("Mixte", gctsh);
                    } else if (MAXGLOBALTHROUGHPUT > 0) {
                        p.addLast("Global", gtsh);
                    } else if (MAXCHANNELTHROUGHPUT > 0) {
                        p.addLast("Channel", new ChannelTrafficShapingHandler(timer, MAXCHANNELTHROUGHPUT, 0, 1000));
                    }
                    p.addLast("discard", new DiscardClientHandler());
                    return p;
                }
            });

            List<ChannelFuture> futures = new LinkedList<ChannelFuture>();
            for (int i = 0; i < connectionCount; i++) {
                // Start the connection attempt.
                futures.add(bootstrap.connect(new InetSocketAddress(HOST, PORT)));
            }
            // Wait until the connection is closed or the connection attempt fails.
            for (ChannelFuture channelFuture : futures) {
                channelFuture.getChannel().getCloseFuture().sync();
                if (MAXCHANNELTHROUGHPUT > 0 && MAXGLOBALTHROUGHPUT <= 0) {
                    ChannelTrafficShapingHandler ctsh =
                            channelFuture.getChannel().getPipeline().get(ChannelTrafficShapingHandler.class);
                    if (ctsh != null) {
                        ctsh.releaseExternalResources();
                    }
                }
            }
        } finally {
            // Shut down thread pools to exit.
            bootstrap.releaseExternalResources();
            if (gtsh != null) {
                gtsh.releaseExternalResources();
            }
            if (gctsh != null) {
                gctsh.releaseExternalResources();
            }
            timer.stop();
        }
    }
}
