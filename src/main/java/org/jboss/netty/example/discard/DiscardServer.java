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

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.ssl.SslContext;
import org.jboss.netty.handler.ssl.util.SelfSignedCertificate;
import org.jboss.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.jboss.netty.handler.traffic.GlobalTrafficShapingHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Discards any incoming data.
 */
public final class DiscardServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "8009"));
    static final int MAXGLOBALTHROUGHPUT = Integer.parseInt(System.getProperty("maxGlobalThroughput", "0"));
    static final int MAXCHANNELTHROUGHPUT = Integer.parseInt(System.getProperty("maxChannelThroughput", "0"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());
        } else {
            sslCtx = null;
        }

        // Configure the bootstrap.
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
        final Timer timer = new HashedWheelTimer();
        final GlobalTrafficShapingHandler gtsh;
        final GlobalChannelTrafficShapingHandlerWithLog gctsh;
        if (MAXGLOBALTHROUGHPUT > 0 && MAXCHANNELTHROUGHPUT > 0) {
            gctsh = new GlobalChannelTrafficShapingHandlerWithLog(timer, 0, MAXGLOBALTHROUGHPUT,
                    0, MAXCHANNELTHROUGHPUT, 1000);
            gtsh = null;
        } else if (MAXGLOBALTHROUGHPUT > 0) {
            gtsh = new GlobalTrafficShapingHandler(timer, 0, MAXGLOBALTHROUGHPUT, 1000);
            gctsh = null;
        } else {
            gtsh = null;
            gctsh = null;
        }
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                ChannelPipeline p = Channels.pipeline();
                if (sslCtx != null) {
                    p.addLast("ssl", sslCtx.newHandler());
                }
                if (MAXGLOBALTHROUGHPUT > 0 && MAXCHANNELTHROUGHPUT > 0) {
                    p.addLast("Mixte", gctsh);
                } else if (MAXGLOBALTHROUGHPUT > 0) {
                    p.addLast("Global", gtsh);
                } else if (MAXCHANNELTHROUGHPUT > 0) {
                    p.addLast("Channel", new ChannelTrafficShapingHandler(timer, 0, MAXCHANNELTHROUGHPUT, 1000));
                }
                p.addLast("discard", new DiscardServerHandler());
                return p;
            }
        });

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(PORT));
    }
}
