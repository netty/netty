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
package io.netty.example.local;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.Channels;
import io.netty.channel.local.DefaultLocalClientChannelFactory;
import io.netty.channel.local.DefaultLocalServerChannelFactory;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import io.netty.handler.logging.LoggingHandler;
import io.netty.logging.InternalLogLevel;

public class LocalExampleMultithreaded {

    private final String port;

    public LocalExampleMultithreaded(String port) {
        this.port = port;
    }

    public void run() {
        LocalAddress socketAddress = new LocalAddress(port);

        OrderedMemoryAwareThreadPoolExecutor eventExecutor =
            new OrderedMemoryAwareThreadPoolExecutor(
                    5, 1000000, 10000000, 100,
                    TimeUnit.MILLISECONDS);

        ServerBootstrap sb = new ServerBootstrap(
                new DefaultLocalServerChannelFactory());

        sb.setPipelineFactory(new LocalServerPipelineFactory(eventExecutor));
        sb.bind(socketAddress);

        ClientBootstrap cb = new ClientBootstrap(
                new DefaultLocalClientChannelFactory());

        cb.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(
                        new StringDecoder(),
                        new StringEncoder(),
                        new LoggingHandler(InternalLogLevel.INFO));
            }
        });

        // Read commands from array
        String[] commands = { "First", "Second", "Third", "quit" };
        for (int j = 0; j < 5 ; j++) {
            System.err.println("Start " + j);
            ChannelFuture channelFuture = cb.connect(socketAddress);
            channelFuture.awaitUninterruptibly();
            if (! channelFuture.isSuccess()) {
                System.err.println("CANNOT CONNECT");
                channelFuture.cause().printStackTrace();
                break;
            }
            ChannelFuture lastWriteFuture = null;
            for (String line: commands) {
                // Sends the received line to the server.
                lastWriteFuture = channelFuture.channel().write(line);
            }

            // Wait until all messages are flushed before closing the channel.
            if (lastWriteFuture != null) {
                lastWriteFuture.awaitUninterruptibly();
            }
            channelFuture.channel().close();
            // Wait until the connection is closed or the connection attempt fails.
            channelFuture.channel().getCloseFuture().awaitUninterruptibly();
            System.err.println("End " + j);
        }

        // Release all resources
        cb.releaseExternalResources();
        sb.releaseExternalResources();
        eventExecutor.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        new LocalExampleMultithreaded("1").run();
    }
}
