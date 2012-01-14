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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.Channels;
import io.netty.channel.sctp.SctpServerSocketChannelFactory;
import io.netty.handler.execution.ExecutionHandler;
import io.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Echoes back any received data from a client.
 */
public class SctpServer {
    
    private final int port;
    
    public SctpServer(int port) {
        this.port = port;
    }

    public void run() {
        // Configure the server.
        ServerBootstrap bootstrap = new ServerBootstrap(
                new SctpServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        final ExecutionHandler executionHandler = new ExecutionHandler(
                new OrderedMemoryAwareThreadPoolExecutor(16, 1048576, 1048576));

        // Set up the pipeline factory.
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(executionHandler, new SctpServerHandler());
            }
        });
        bootstrap.setOption("sendBufferSize", 1048576);
        bootstrap.setOption("receiveBufferSize", 1048576);

        bootstrap.setOption("child.sctpNoDelay", true);

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));
    }
    
    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 2955;
        }
        new SctpServer(port).run();
    }
}
