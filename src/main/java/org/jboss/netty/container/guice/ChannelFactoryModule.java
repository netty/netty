/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
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
package org.jboss.netty.container.guice;

import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class ChannelFactoryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ClientSocketChannelFactory.class).
            toProvider(NioClientSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(ServerSocketChannelFactory.class).
            toProvider(NioServerSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(NioClientSocketChannelFactory.class).
            toProvider(NioClientSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(NioServerSocketChannelFactory.class).
            toProvider(NioServerSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(OioClientSocketChannelFactory.class).
            toProvider(OioClientSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(OioServerSocketChannelFactory.class).
            toProvider(OioServerSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);
    }
}
