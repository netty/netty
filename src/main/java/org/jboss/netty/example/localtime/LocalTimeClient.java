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
package org.jboss.netty.example.localtime;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.ssl.SslContext;
import org.jboss.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Sends a list of continent/city pairs to a {@link LocalTimeServer} to
 * get the local times of the specified cities.
 */
public final class LocalTimeClient {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", "8463"));
    static final List<String> CITIES = Arrays.asList(System.getProperty(
            "cities", "Asia/Seoul,Europe/Berlin,America/Los_Angeles").split(","));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            sslCtx = SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE);
        } else {
            sslCtx = null;
        }

        ClientBootstrap bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
        try {
            // Configure the event pipeline factory.
            bootstrap.setPipelineFactory(new LocalTimeClientPipelineFactory(sslCtx));

            // Make a new connection.
            ChannelFuture connectFuture = bootstrap.connect(new InetSocketAddress(HOST, PORT));

            // Wait until the connection is made successfully.
            Channel channel = connectFuture.sync().getChannel();

            // Get the handler instance to initiate the request.
            LocalTimeClientHandler handler = channel.getPipeline().get(LocalTimeClientHandler.class);

            // Request and get the response.
            List<String> response = handler.getLocalTimes(CITIES);

            // Close the connection.
            channel.close().sync();

            // Print the response at last but not least.
            for (int i = 0; i < CITIES.size(); i ++) {
                System.out.format("%28s: %s%n", CITIES.get(i), response.get(i));
            }
        } finally {
            // Shut down all thread pools to exit.
            bootstrap.releaseExternalResources();
        }
    }
}
