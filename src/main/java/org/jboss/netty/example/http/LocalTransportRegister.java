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
package org.jboss.netty.example.http;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.local.LocalAddress;
import org.jboss.netty.channel.local.LocalServerChannelFactory;
import org.jboss.netty.example.echo.EchoHandler;

/**
 * you can deploy this in jboss 5 by adding the following bean.
 *
 * <bean name="org.jboss.netty.example.servlet.LocalTransportRegister"
        class="org.jboss.netty.example.servlet.LocalTransportRegister" />
 *
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class LocalTransportRegister {

    private final ChannelFactory factory = new LocalServerChannelFactory();
    private volatile Channel serverChannel;

    public void start() {
        ServerBootstrap serverBootstrap = new ServerBootstrap(factory);
        EchoHandler handler = new EchoHandler();
        serverBootstrap.getPipeline().addLast("handler", handler);
        serverChannel = serverBootstrap.bind(new LocalAddress("localAddress"));
    }

    public void stop() {
        serverChannel.close();
    }
}
