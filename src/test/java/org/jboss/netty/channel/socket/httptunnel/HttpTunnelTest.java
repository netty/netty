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

package org.jboss.netty.channel.socket.httptunnel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 */
public class HttpTunnelTest
{

   private HttpTunnelClientChannelFactory clientFactory;

   private HttpTunnelServerChannelFactory serverFactory;

   private ClientBootstrap clientBootstrap;

   private ServerBootstrap serverBootstrap;

   ChannelGroup activeConnections;

   ChannelHandler clientCaptureHandler;

   ServerEndHandler connectionCaptureHandler;

   Channel serverEnd;

   CountDownLatch serverEndLatch;

   ChannelBuffer receivedBytes;

   CountDownLatch messageReceivedLatch;

   ChannelBuffer clientReceivedBytes;

   CountDownLatch clientMessageReceivedLatch;

   private Channel serverChannel;

   @Before
   public void setUp() throws UnknownHostException
   {
      activeConnections = new DefaultChannelGroup();
      clientFactory = new HttpTunnelClientChannelFactory(new NioClientSocketChannelFactory(
            Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
      serverFactory = new HttpTunnelServerChannelFactory(new NioServerSocketChannelFactory(
            Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

      clientBootstrap = new ClientBootstrap(clientFactory);

      clientCaptureHandler = new ClientEndHandler();
      clientBootstrap.setPipelineFactory(new ChannelPipelineFactory()
      {

         public ChannelPipeline getPipeline() throws Exception
         {
            ChannelPipeline pipeline = Channels.pipeline();
            pipeline.addLast("clientCapture", clientCaptureHandler);
            return pipeline;
         }
      });

      clientReceivedBytes = ChannelBuffers.dynamicBuffer();
      clientMessageReceivedLatch = new CountDownLatch(1);

      serverBootstrap = new ServerBootstrap(serverFactory);

      connectionCaptureHandler = new ServerEndHandler();
      serverBootstrap.setPipelineFactory(new ChannelPipelineFactory()
      {

         public ChannelPipeline getPipeline() throws Exception
         {
            ChannelPipeline pipeline = Channels.pipeline();
            pipeline.addLast("capture", connectionCaptureHandler);
            return pipeline;
         }
      });

      serverEndLatch = new CountDownLatch(1);
      receivedBytes = ChannelBuffers.dynamicBuffer();
      messageReceivedLatch = new CountDownLatch(1);

      serverChannel = serverBootstrap.bind(new InetSocketAddress(InetAddress.getLocalHost(), 12345));
      activeConnections.add(serverChannel);
   }

   @After
   public void tearDown() throws Exception
   {
      activeConnections.disconnect().await(1000L);
      clientBootstrap.releaseExternalResources();
      serverBootstrap.releaseExternalResources();
   }

   @Test(timeout = 2000)
   public void testConnectClientToServer() throws Exception
   {
      ChannelFuture connectFuture = clientBootstrap.connect(new InetSocketAddress(InetAddress.getLocalHost(), 12345));
      assertTrue(connectFuture.await(1000L));
      assertTrue(connectFuture.isSuccess());
      assertNotNull(connectFuture.getChannel());

      Channel clientChannel = connectFuture.getChannel();
      activeConnections.add(clientChannel);
      assertEquals(serverChannel.getLocalAddress(), clientChannel.getRemoteAddress());

      assertTrue(serverEndLatch.await(1000, TimeUnit.MILLISECONDS));
      assertNotNull(serverEnd);
      assertEquals(clientChannel.getLocalAddress(), serverEnd.getRemoteAddress());
   }

   @Test
   public void testSendDataFromClientToServer() throws Exception
   {
      ChannelFuture connectFuture = clientBootstrap.connect(new InetSocketAddress(InetAddress.getLocalHost(), 12345));
      assertTrue(connectFuture.await(1000L));

      Channel clientEnd = connectFuture.getChannel();
      activeConnections.add(clientEnd);

      assertTrue(serverEndLatch.await(1000, TimeUnit.MILLISECONDS));

      ChannelFuture writeFuture = Channels.write(clientEnd, NettyTestUtils.createData(100L));
      assertTrue(writeFuture.await(1000L));
      assertTrue(writeFuture.isSuccess());

      assertTrue(messageReceivedLatch.await(1000L, TimeUnit.MILLISECONDS));
      assertEquals(100L, receivedBytes.readLong());
   }

   @Test
   public void testSendDataFromServerToClient() throws Exception
   {
      ChannelFuture connectFuture = clientBootstrap.connect(new InetSocketAddress(InetAddress.getLocalHost(), 12345));
      assertTrue(connectFuture.await(1000L));

      Channel clientEnd = connectFuture.getChannel();
      activeConnections.add(clientEnd);

      assertTrue(serverEndLatch.await(1000, TimeUnit.MILLISECONDS));

      ChannelFuture writeFuture = Channels.write(serverEnd, NettyTestUtils.createData(4321L));
      assertTrue(writeFuture.await(1000L));
      assertTrue(writeFuture.isSuccess());

      assertTrue(clientMessageReceivedLatch.await(1000, TimeUnit.MILLISECONDS));
      assertEquals(4321L, clientReceivedBytes.readLong());
   }

   class ServerEndHandler extends SimpleChannelUpstreamHandler
   {

      @Override
      public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception
      {
         serverEnd = e.getChannel();
         activeConnections.add(serverEnd);
         serverEndLatch.countDown();
         super.channelConnected(ctx, e);
      }

      @Override
      public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
      {
         receivedBytes.writeBytes((ChannelBuffer) e.getMessage());
         messageReceivedLatch.countDown();
      }
   }

   class ClientEndHandler extends SimpleChannelUpstreamHandler
   {

      @Override
      public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
      {
         clientReceivedBytes.writeBytes((ChannelBuffer) e.getMessage());
         clientMessageReceivedLatch.countDown();
      }
   }
}
