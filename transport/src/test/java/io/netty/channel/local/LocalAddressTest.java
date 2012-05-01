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
package io.netty.channel.local;

import org.junit.Assert;
import org.junit.Test;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.Channels;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.channel.local.DefaultLocalClientChannelFactory;
import io.netty.channel.local.DefaultLocalServerChannelFactory;
import io.netty.channel.local.LocalAddress;

public class LocalAddressTest {
    private static String LOCAL_ADDR_ID = "test.id";

    @Test
    public void localConnectOK()
         throws Exception {

        ClientBootstrap cb = new ClientBootstrap(new DefaultLocalClientChannelFactory());
        ServerBootstrap sb = new ServerBootstrap(new DefaultLocalServerChannelFactory());

        cb.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline()
                throws Exception {

                ChannelPipeline pipeline = Channels.pipeline();

                pipeline.addLast("test.handler", new TestHandler());
                return pipeline;
            }
        });

        sb.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
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
        Channels.fireMessageReceived(connectFuture.channel(), "Hello, World");

        // Close the channel
        connectFuture.channel().close();

        // Wait until the connection is closed, or the connection attempt fails
        connectFuture.channel().getCloseFuture().awaitUninterruptibly();
        
        sb.releaseExternalResources();
        cb.releaseExternalResources();

        Assert.assertTrue(String.format("Expected null, got channel '%s' for local address '%s'", LocalChannelRegistry.getChannel(addr), addr), LocalChannelRegistry.getChannel(addr) == null);
    }

    @Test
    public void localConnectAgain()
        throws Exception {

        ClientBootstrap cb = new ClientBootstrap(new DefaultLocalClientChannelFactory());
        ServerBootstrap sb = new ServerBootstrap(new DefaultLocalServerChannelFactory());

        cb.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline()
                throws Exception {

                ChannelPipeline pipeline = Channels.pipeline();

                pipeline.addLast("test.handler", new TestHandler());
                return pipeline;
            }
        });

        sb.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
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
        Channels.fireMessageReceived(connectFuture.channel(), "Hello, World");

        // Close the channel
        connectFuture.channel().close();

        // Wait until the connection is closed, or the connection attempt fails
        connectFuture.channel().getCloseFuture().awaitUninterruptibly();

        
        sb.releaseExternalResources();
        cb.releaseExternalResources();

        Assert.assertTrue(String.format("Expected null, got channel '%s' for local address '%s'", LocalChannelRegistry.getChannel(addr), addr), LocalChannelRegistry.getChannel(addr) == null);
     }

    public static class TestHandler
        extends SimpleChannelUpstreamHandler {

        public TestHandler() {
        }

        @Override
        public void handleUpstream(ChannelHandlerContext ctx,
                                                     ChannelEvent e)
            throws Exception {

            System.err.println(String.format("Received upstream event '%s'", e));
        }
    }
}