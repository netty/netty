/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.local;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.junit.Test;

import static org.junit.Assert.*;

public class LocalAddressTest {
    private static final String LOCAL_ADDR_ID = "test.id";

    @Test
    public void localConnectOK()
         throws Exception {

        ClientBootstrap cb = new ClientBootstrap(new DefaultLocalClientChannelFactory());
        ServerBootstrap sb = new ServerBootstrap(new DefaultLocalServerChannelFactory());

        cb.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline()
                throws Exception {

                ChannelPipeline pipeline = Channels.pipeline();

                pipeline.addLast("test.handler", new TestHandler());
                return pipeline;
            }
        });

        sb.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline()
                throws Exception {

                ChannelPipeline pipeline = Channels.pipeline();

                pipeline.addLast("test.handler", new TestHandler());
                return pipeline;
            }
        });

        LocalAddress addr = new LocalAddress(LOCAL_ADDR_ID);

        // Start server
        sb.bind(addr);

        // Connect to the server
        ChannelFuture connectFuture = cb.connect(addr);
        connectFuture.awaitUninterruptibly();

        // Send a message event up the pipeline.
        Channels.fireMessageReceived(connectFuture.getChannel(), "Hello, World");

        // Close the channel
        connectFuture.getChannel().close();

        // Wait until the connection is closed, or the connection attempt fails
        connectFuture.getChannel().getCloseFuture().awaitUninterruptibly();

        sb.releaseExternalResources();
        cb.releaseExternalResources();

        assertNull(String.format("Expected null, got channel '%s' for local address '%s'", LocalChannelRegistry.getChannel(addr), addr), LocalChannelRegistry.getChannel(addr));
    }

    @Test
    public void localConnectAgain()
        throws Exception {

        ClientBootstrap cb = new ClientBootstrap(new DefaultLocalClientChannelFactory());
        ServerBootstrap sb = new ServerBootstrap(new DefaultLocalServerChannelFactory());

        cb.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline()
                throws Exception {

                ChannelPipeline pipeline = Channels.pipeline();

                pipeline.addLast("test.handler", new TestHandler());
                return pipeline;
            }
        });

        sb.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline()
                throws Exception {

                ChannelPipeline pipeline = Channels.pipeline();

                pipeline.addLast("test.handler", new TestHandler());
                return pipeline;
            }
        });

        LocalAddress addr = new LocalAddress(LOCAL_ADDR_ID);

        // Start server
        sb.bind(addr);

        // Connect to the server
        ChannelFuture connectFuture = cb.connect(addr);
        connectFuture.awaitUninterruptibly();

        // Send a message event up the pipeline.
        Channels.fireMessageReceived(connectFuture.getChannel(), "Hello, World");

        // Close the channel
        connectFuture.getChannel().close();

        // Wait until the connection is closed, or the connection attempt fails
        connectFuture.getChannel().getCloseFuture().awaitUninterruptibly();

        sb.releaseExternalResources();
        cb.releaseExternalResources();

        assertNull(String.format("Expected null, got channel '%s' for local address '%s'", LocalChannelRegistry.getChannel(addr), addr), LocalChannelRegistry.getChannel(addr));
     }

    public static class TestHandler
        extends SimpleChannelUpstreamHandler {

        @Override
        public void handleUpstream(ChannelHandlerContext ctx,
                                                     ChannelEvent e)
            throws Exception {

            System.err.println(String.format("Received upstream event '%s'", e));
        }
    }
}
