/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.example.http.tunnel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.http.HttpTunnelingClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.example.securechat.SecureChatSslContextFactory;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.jboss.netty.logging.InternalLogLevel;

/**
 * An HTTP tunneled version of the telnet client example.  Please refer to the
 * API documentation of the <tt>org.jboss.netty.channel.socket.http</tt> package
 * for the detailed instruction on how to deploy the server-side HTTP tunnel in
 * your Servlet container.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev$, $Date$
 */
public class HttpTunnelingClientExample {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println(
                    "Usage: " + HttpTunnelingClientExample.class.getSimpleName() +
                    " <URL>");
            System.err.println(
                    "Example: " + HttpTunnelingClientExample.class.getSimpleName() +
                    " http://localhost:8080/netty-tunnel");
            return;
        }

        URI uri = new URI(args[0]);
        String scheme = uri.getScheme() == null? "http" : uri.getScheme();

        // Configure the client.
        ClientBootstrap b = new ClientBootstrap(
                new HttpTunnelingClientSocketChannelFactory(
                        new OioClientSocketChannelFactory(Executors.newCachedThreadPool())));

        // Set up the default event pipeline.
        b.getPipeline().addLast("decoder", new StringDecoder());
        b.getPipeline().addLast("encoder", new StringEncoder());
        b.getPipeline().addLast("handler", new LoggingHandler(InternalLogLevel.INFO));

        // Set additional options required by the HTTP tunneling transport.
        b.setOption("serverName", uri.getHost());
        b.setOption("serverPath", uri.getRawPath());

        // Configure SSL if necessary
        if (scheme.equals("https")) {
            b.setOption("sslContext", SecureChatSslContextFactory.getClientContext());
        } else if (!scheme.equals("http")) {
            // Only HTTP and HTTPS are supported.
            System.err.println("Only HTTP(S) is supported.");
            return;
        }

        // Make the connection attempt.
        ChannelFuture channelFuture = b.connect(
                new InetSocketAddress(uri.getHost(), uri.getPort()));
        channelFuture.awaitUninterruptibly();

        // Read commands from the stdin.
        System.out.println("Enter text ('quit' to exit)");
        ChannelFuture lastWriteFuture = null;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        for (; ;) {
            String line = in.readLine();
            if (line == null || "quit".equalsIgnoreCase(line)) {
                break;
            }

            // Sends the received line to the server.
            lastWriteFuture = channelFuture.getChannel().write(line);
        }

        // Wait until all messages are flushed before closing the channel.
        if (lastWriteFuture != null) {
            lastWriteFuture.awaitUninterruptibly();
        }

        channelFuture.getChannel().close();
        // Wait until the connection is closed or the connection attempt fails.
        channelFuture.getChannel().getCloseFuture().awaitUninterruptibly();

        // Shut down all threads.
        b.releaseExternalResources();
    }
}
