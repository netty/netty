/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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
package org.jboss.netty.example.http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.socket.http.HttpTunnelAddress;
import org.jboss.netty.channel.socket.http.HttpTunnelingClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;

/**
 * make sure that the LocalTransportRegister bean is deployed along with the NettyServlet with the following web.xml
 *
 <?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="http://java.sun.com/xml/ns/j2ee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd"
         version="2.4">
   <!--the name of the channel, this should be a registered local channel. see LocalTransportRegister-->
   <context-param>
      <param-name>serverChannelName</param-name>
      <param-value>org.jboss.netty.exampleChannel</param-value>
   </context-param>

    <!--Whether or not we are streaming or just polling using normal http requests-->
    <context-param>
      <param-name>streaming</param-name>
      <param-value>true</param-value>
   </context-param>

    <!--how long to wait for a client reconnecting in milliseconds-->
   <context-param>
      <param-name>reconnectTimeout</param-name>
      <param-value>3000</param-value>
   </context-param>

   <listener>
      <listener-class>org.jboss.netty.servlet.NettySessionListener</listener-class>
   </listener>

   <listener>
      <listener-class>org.jboss.netty.servlet.NettyServletContextListener</listener-class>
   </listener>

   <servlet>
      <servlet-name>NettyServlet</servlet-name>
      <servlet-class>org.jboss.netty.servlet.NettyServlet</servlet-class>
   </servlet>

   <servlet-mapping>
      <servlet-name>NettyServlet</servlet-name>
      <url-pattern>/nettyServlet</url-pattern>
   </servlet-mapping>
</web-app>

 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class HttpTunnelingClientExample {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println(
                    "Usage: " + HttpClient.class.getSimpleName() +
                    " <URL>");
            return;
        }

        URI uri = new URI(args[0]);
        String scheme = uri.getScheme() == null? "http" : uri.getScheme();

        if (!scheme.equals("http")) {
            // We can actually support HTTPS fairly easily by inserting
            // an SslHandler to the pipeline - left as an exercise.
            System.err.println("Only HTTP is supported.");
            return;
        }
        HttpTunnelingClientSocketChannelFactory factory = new HttpTunnelingClientSocketChannelFactory(new OioClientSocketChannelFactory(Executors.newCachedThreadPool()), Executors.newCachedThreadPool());
        ClientBootstrap bootstrap = new ClientBootstrap(factory);
        bootstrap.getPipeline().addLast("decoder", new StringDecoder());
        bootstrap.getPipeline().addLast("encoder", new StringEncoder());
        bootstrap.getPipeline().addLast("handler", new PrintHandler());
        ChannelFuture channelFuture = bootstrap.connect(new HttpTunnelAddress(uri));
        channelFuture.awaitUninterruptibly();
        System.out.println("Enter text (quit to end)");
        // Read commands from the stdin.
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

        factory.releaseExternalResources();
    }

    @ChannelPipelineCoverage("all")
    static class PrintHandler extends OneToOneDecoder {
        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
            String message = (String) msg;
            System.out.println("received message back '" + message + "'");
            return message;
        }
    }
}
