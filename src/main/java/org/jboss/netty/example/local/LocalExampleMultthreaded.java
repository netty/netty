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
package org.jboss.netty.example.local;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.local.DefaultLocalClientChannelFactory;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalAddress;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.jboss.netty.logging.InternalLogLevel;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Frederic Bregier (fredbregier@free.fr)
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class LocalExampleMultthreaded {

    public static void main(String[] args) throws Exception {
        LocalAddress socketAddress = new LocalAddress("1");

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
            System.err.println("Start "+j);
            ChannelFuture channelFuture = cb.connect(socketAddress);
            channelFuture.awaitUninterruptibly();
            if (! channelFuture.isSuccess()) {
                System.err.println("CANNOT CONNECT");
                channelFuture.getCause().printStackTrace();
                break;
            }
            ChannelFuture lastWriteFuture = null;
            for (String line: commands) {
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
            System.err.println("End "+j);
        }

        // Release all resources
        cb.releaseExternalResources();
        sb.releaseExternalResources();
        eventExecutor.shutdownNow();
    }
}
