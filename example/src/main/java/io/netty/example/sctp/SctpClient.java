/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.sctp;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.Channels;
import io.netty.channel.sctp.SctpClientSocketChannelFactory;
import io.netty.handler.execution.ExecutionHandler;
import io.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Simple SCTP Echo Client
 */
public class SctpClient {

    private final String host;
    private final int port;
    
    public SctpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void run() {
        // Configure the client.
        ClientBootstrap bootstrap = new ClientBootstrap(
                new SctpClientSocketChannelFactory(
                        Executors.newCachedThreadPool()));

        final ExecutionHandler executionHandler = new ExecutionHandler(
                new OrderedMemoryAwareThreadPoolExecutor(16, 1048576, 1048576));

        // Set up the pipeline factory.
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(executionHandler, new SctpClientHandler());
            }
        });

        bootstrap.setOption("sendBufferSize", 1048576);
        bootstrap.setOption("receiveBufferSize", 1048576);

        bootstrap.setOption("sctpNoDelay", true);

        // Start the connection attempt.
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));

        // Wait until the connection is closed or the connection attempt fails.
        future.getChannel().getCloseFuture().awaitUninterruptibly();

        //  Please check SctpClientHandler to see, how echo message is sent & received

        // Shut down thread pools to exit.
        bootstrap.releaseExternalResources();
    }

    public static void main(String[] args) throws Exception {
        // Print usage if no argument is specified.
        if (args.length != 2) {
            System.err.println(
                    "Usage: " + SctpClient.class.getSimpleName() +
                    " <host> <port>");
            return;
        }

        // Parse options.
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);

        new SctpClient(host, port).run();
    }
}
