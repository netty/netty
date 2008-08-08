/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.example.telnet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

public class TelnetClient {

    public static void main(String[] args) throws Exception {
        // Print usage if no argument is specified.
        if (args.length != 2) {
            System.err.println(
                    "Usage: " + TelnetClient.class.getSimpleName() +
                    " <host> <port>");
            return;
        }

        // Parse options.
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        // Start client.
        ChannelFactory factory =
            new NioClientSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

        ClientBootstrap bootstrap = new ClientBootstrap(factory);
        TelnetClientHandler handler = new TelnetClientHandler();

        bootstrap.setPipelineFactory(new TelnetPipelineFactory(handler));
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("keepAlive", true);

        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));

        // Wait until the connection attempt succeeds or fails.
        Channel channel = future.awaitUninterruptibly().getChannel();
        if (!future.isSuccess()) {
            future.getCause().printStackTrace();
            System.exit(0);
        }

        // Read commands from the stdin.
        ChannelFuture lastWriteFuture = null;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        for (;;) {
            String line = in.readLine();
            if (line == null) {
                break;
            }
            lastWriteFuture = channel.write(line + '\n');
        }

        // Wait until all messages are flushed before closing the channel.
        if (lastWriteFuture != null) {
            lastWriteFuture.awaitUninterruptibly();
        }

        channel.close().awaitUninterruptibly();

        // We should shut down all thread pools here to exit normally.
        // However, it's just fine to call System.exit(0) because we are
        // finished with the business.
        System.exit(0);
    }
}
