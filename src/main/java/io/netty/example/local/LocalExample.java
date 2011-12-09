/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.local;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.Channels;
import io.netty.channel.local.DefaultLocalClientChannelFactory;
import io.netty.channel.local.DefaultLocalServerChannelFactory;
import io.netty.channel.local.LocalAddress;
import io.netty.example.echo.EchoServerHandler;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.logging.InternalLogLevel;

/**
 */
public class LocalExample {
    public static void main(String[] args) throws Exception {
        // Address to bind on / connect to.
        LocalAddress socketAddress = new LocalAddress("1");

        // Configure the server.
        ServerBootstrap sb = new ServerBootstrap(
                new DefaultLocalServerChannelFactory());

        // Set up the default server-side event pipeline.
        EchoServerHandler handler = new EchoServerHandler();
        sb.getPipeline().addLast("handler", handler);

        // Start up the server.
        sb.bind(socketAddress);

        // Configure the client.
        ClientBootstrap cb = new ClientBootstrap(
                new DefaultLocalClientChannelFactory());

        // Set up the client-side pipeline factory.
        cb.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(
                        new StringDecoder(),
                        new StringEncoder(),
                        new LoggingHandler(InternalLogLevel.INFO));
            }
        });

        // Make the connection attempt to the server.
        ChannelFuture channelFuture = cb.connect(socketAddress);
        channelFuture.awaitUninterruptibly();

        // Read commands from the stdin.
        System.out.println("Enter text (quit to end)");
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

        // Release all resources used by the local transport.
        cb.releaseExternalResources();
        sb.releaseExternalResources();
    }
}
