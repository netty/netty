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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringEncoder;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 */
public class HttpClient {
    public static void main(String[] args) throws Exception {

        // Parse options.
        String host = "localhost";
        int port = 8080;

        // Configure the client.
        ChannelFactory factory =
            new NioClientSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

        ClientBootstrap bootstrap = new ClientBootstrap(factory);
        HttpPipelineFactory handler = new HttpPipelineFactory(new HttpResponseHandler());
        bootstrap.setPipelineFactory(handler);
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("keepAlive", true);
        // Start the connection attempt.
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));

        // Wait until the connection attempt succeeds or fails.
        Channel channel = future.awaitUninterruptibly().getChannel();
        if (!future.isSuccess()) {
            future.getCause().printStackTrace();
            System.exit(0);
        }
        String message = "It's Hello From me";
        ChannelBuffer buf = ChannelBuffers.wrappedBuffer(message.getBytes());
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, new URI("/netty/"));
        request.addHeader(HttpHeaders.HOST, host);
        request.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buf.writerIndex()));
        request.setContent(buf);
        ChannelFuture lastWriteFuture = channel.write(request);
        buf = ChannelBuffers.wrappedBuffer(message.getBytes());
        QueryStringEncoder queryStringEncoder = new QueryStringEncoder("/netty/");
        queryStringEncoder.addParam("testparam", "hey ho");
        queryStringEncoder.addParam("testparam2", "hey ho again");
        request = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, queryStringEncoder.toUri());
        request.addHeader(HttpHeaders.HOST, host);
        request.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buf.writerIndex()));
        request.setContent(buf);
        lastWriteFuture = channel.write(request);
        lastWriteFuture.awaitUninterruptibly();
    }
}
