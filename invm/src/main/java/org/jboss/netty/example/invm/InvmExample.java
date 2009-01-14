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
package org.jboss.netty.example.invm;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.invm.InvmServerChannelFactory;
import org.jboss.netty.channel.socket.invm.InvmClientChannelFactory;
import org.jboss.netty.channel.socket.invm.InvmSocketAddress;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.example.echo.EchoHandler;
import org.jboss.netty.example.echo.ThroughputMonitor;

import java.util.concurrent.Executors;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class InvmExample
{
   public static void main(String[] args)
   {
      InvmServerChannelFactory factory = new InvmServerChannelFactory(Executors.newCachedThreadPool());
      ServerBootstrap bootstrap = new ServerBootstrap(factory);
      EchoHandler handler = new EchoHandler();
      InvmSocketAddress socketAddress = new InvmSocketAddress("1");
      bootstrap.getPipeline().addLast("handler", handler);
      bootstrap.bind(socketAddress);
      // Start performance monitor.
      new ThroughputMonitor(handler).start();

      ChannelFactory channelFactory = new InvmClientChannelFactory(factory, Executors.newCachedThreadPool());
      ClientBootstrap clientBootstrap = new ClientBootstrap(channelFactory);
      EchoHandler echoHandler = new EchoHandler(10);

      clientBootstrap.getPipeline().addLast("handler", echoHandler);
      ChannelFuture channelFuture = clientBootstrap.connect(socketAddress);
      channelFuture.awaitUninterruptibly();
      if(!channelFuture.isSuccess())
      {
         System.exit(1);
      }
   }

}
