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
package org.jboss.netty.example.portunification;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.ssl.SslContext;
import org.jboss.netty.handler.ssl.util.SelfSignedCertificate;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Serves two protocols (HTTP and Factorial) using only one port, enabling
 * either SSL or GZIP dynamically on demand.
 * <p>
 * Because SSL and GZIP are enabled on demand, 5 combinations per protocol
 * are possible: none, SSL only, GZIP only, SSL + GZIP, and GZIP + SSL.
 */
public final class PortUnificationServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8080"));

    public static void main(String[] args) throws Exception {
        // Configure SSL context
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        final SslContext sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());

        // Configure the server.
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {
                return Channels.pipeline(new PortUnificationServerHandler(sslCtx));
            }
        });

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(PORT));
    }
}
