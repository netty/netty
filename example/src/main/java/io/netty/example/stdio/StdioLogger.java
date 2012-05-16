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
package io.netty.example.stdio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelHandler;
import io.netty.channel.iostream.IoStreamAddress;
import io.netty.channel.iostream.IoStreamChannelFactory;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

/**
 * An example demonstrating the use of the {@link io.netty.channel.iostream.IoStreamChannel}.
 */
public class StdioLogger {

    private volatile boolean running = true;

    public void run() {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final ClientBootstrap bootstrap = new ClientBootstrap(new IoStreamChannelFactory(executorService));

        // Configure the event pipeline factory.
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
                pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
                pipeline.addLast("decoder", new StringDecoder());
                pipeline.addLast("encoder", new StringEncoder());
                pipeline.addLast("loggingHandler", new SimpleChannelHandler() {
                    @Override
                    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e)
                            throws Exception {

                        final String message = (String) e.getMessage();
                        synchronized (System.out) {
                            e.channel().write("Message received: " + message);
                        }
                        if ("exit".equals(message)) {
                            running = false;
                        }
                        super.messageReceived(ctx, e);
                    }
                }
                );
                return pipeline;
            }
        });

        // Make a new connection.
        ChannelFuture connectFuture = bootstrap.connect(new IoStreamAddress(System.in, System.out));

        // Wait until the connection is made successfully.
        Channel channel = connectFuture.awaitUninterruptibly().channel();

        while (running) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Close the connection.
        channel.close().awaitUninterruptibly();

        // Shut down all thread pools to exit.
        bootstrap.releaseExternalResources();
    }

    public static void main(String[] args) {
        new StdioLogger().run();
    }
}
