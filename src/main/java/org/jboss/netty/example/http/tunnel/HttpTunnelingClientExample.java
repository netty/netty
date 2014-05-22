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
package org.jboss.netty.example.http.tunnel;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.http.HttpTunnelingClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.jboss.netty.handler.ssl.JdkSslClientContext;
import org.jboss.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.jboss.netty.logging.InternalLogLevel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;

/**
 * An HTTP tunneled version of the telnet client example.  Please refer to the
 * API documentation of the <tt>org.jboss.netty.channel.socket.http</tt> package
 * for the detailed instruction on how to deploy the server-side HTTP tunnel in
 * your Servlet container.
 */
public final class HttpTunnelingClientExample {

    static final String URL = System.getProperty("url", "http://localhost:8080/netty-tunnel");

    public static void main(String[] args) throws Exception {
        URI uri = new URI(URL);
        String scheme = uri.getScheme() == null? "http" : uri.getScheme();
        String host = uri.getHost() == null? "127.0.0.1" : uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            if ("http".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("https".equalsIgnoreCase(scheme)) {
                port = 443;
            }
        }

        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            System.err.println("Only HTTP(S) is supported.");
            return;
        }

        // Configure the client.
        ClientBootstrap b = new ClientBootstrap(
                new HttpTunnelingClientSocketChannelFactory(
                        new OioClientSocketChannelFactory(Executors.newCachedThreadPool())));

        try {
            b.setPipelineFactory(new ChannelPipelineFactory() {
                public ChannelPipeline getPipeline() {
                    return Channels.pipeline(
                            new StringDecoder(),
                            new StringEncoder(),
                            new LoggingHandler(InternalLogLevel.INFO));
                }
            });

            // Set additional options required by the HTTP tunneling transport.
            b.setOption("serverName", uri.getHost());
            b.setOption("serverPath", uri.getRawPath());

            // Configure SSL if necessary
            if ("https".equals(scheme)) {
                b.setOption("sslContext", new JdkSslClientContext(InsecureTrustManagerFactory.INSTANCE).context());
            }

            // Make the connection attempt.
            ChannelFuture channelFuture = b.connect(new InetSocketAddress(host, port));
            channelFuture.sync();

            // Read commands from the stdin.
            System.err.println("Enter text ('quit' to exit)");

            ChannelFuture lastWriteFuture = null;
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            for (;;) {
                String line = in.readLine();
                if (line == null || "quit".equalsIgnoreCase(line)) {
                    break;
                }

                // Sends the received line to the server.
                lastWriteFuture = channelFuture.getChannel().write(line);
            }

            // Wait until all messages are flushed before closing the channel.
            if (lastWriteFuture != null) {
                lastWriteFuture.sync();
            }

            // Close the connection.
            channelFuture.getChannel().close().sync();
        } finally {
            // Shut down all threads.
            b.releaseExternalResources();
        }
    }
}
