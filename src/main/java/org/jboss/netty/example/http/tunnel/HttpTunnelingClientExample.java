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
package org.jboss.netty.example.http.tunnel;

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
import org.jboss.netty.channel.socket.http.HttpTunnelingServlet;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;

/**
 * Make sure that the {@link LocalTransportRegister} bean is deployed along
 * with the {@link HttpTunnelingServlet} with the following <tt>web.xml</tt>.
 *
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 * &lt;web-app xmlns="http://java.sun.com/xml/ns/j2ee"
 *             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 *             xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd"
 *             version="2.4"&gt;
 *    &lt;!--the name of the channel, this should be a registered local channel. see LocalTransportRegister--&gt;
 *    &lt;context-param&gt;
 *       &lt;param-name&gt;serverChannelName&lt;/param-name&gt;
 *       &lt;param-value&gt;myLocalServer&lt;/param-value&gt;
 *    &lt;/context-param&gt;
 *
 *     &lt;!--Whether or not we are streaming or just polling using normal HTTP requests--&gt;
 *     &lt;context-param&gt;
 *       &lt;param-name&gt;streaming&lt;/param-name&gt;
 *       &lt;param-value&gt;true&lt;/param-value&gt;
 *    &lt;/context-param&gt;
 *
 *     &lt;!--How long to wait for a client reconnecting in milliseconds--&gt;
 *    &lt;context-param&gt;
 *       &lt;param-name&gt;reconnectTimeout&lt;/param-name&gt;
 *       &lt;param-value&gt;3000&lt;/param-value&gt;
 *    &lt;/context-param&gt;
 *
 *    &lt;listener&gt;
 *       &lt;listener-class&gt;org.jboss.netty.channel.socket.http.HttpTunnelingSessionListener&lt;/listener-class&gt;
 *    &lt;/listener&gt;
 *
 *    &lt;listener&gt;
 *       &lt;listener-class&gt;org.jboss.netty.channel.socket.http.HttpTunnelingContextListener&lt;/listener-class&gt;
 *    &lt;/listener&gt;
 *
 *    &lt;servlet&gt;
 *       &lt;servlet-name&gt;NettyTunnelingServlet&lt;/servlet-name&gt;
 *       &lt;servlet-class&gt;org.jboss.netty.channel.socket.http.HttpTunnelingServlet&lt;/servlet-class&gt;
 *    &lt;/servlet&gt;
 *
 *    &lt;servlet-mapping&gt;
 *       &lt;servlet-name&gt;NettyTunnelingServlet&lt;/servlet-name&gt;
 *       &lt;url-pattern&gt;/netty-tunnel&lt;/url-pattern&gt;
 *    &lt;/servlet-mapping&gt;
 * &lt;/web-app&gt;
 * </pre>
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
