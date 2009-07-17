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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.local.DefaultLocalClientChannelFactory;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalClientChannelFactory;
import org.jboss.netty.channel.local.LocalServerChannelFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioDatagramChannelFactory;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.jboss.netty.util.internal.ExecutorUtil;
import org.jboss.netty.util.internal.UnterminatableExecutor;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;

/**
 * A Guice {@link Module} that defines the bindings for all known
 * {@link ChannelFactory}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class NettyModule extends AbstractModule {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Releases all resources created by this module.
     */
    public void destroy() {
        ExecutorUtil.terminate(executor);
    }

    @Override
    protected void configure() {
        if (executor.isShutdown()) {
            throw new IllegalStateException(
                    "Executor has been shut down already.");
        }

        Executor executor = new UnterminatableExecutor(this.executor);

        bind(Executor.class).
            annotatedWith(ChannelFactoryResource.class).
            toInstance(executor);

        bind(ClientSocketChannelFactory.class).
            toProvider(NioClientSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(ServerSocketChannelFactory.class).
            toProvider(NioServerSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(DatagramChannelFactory.class).
            toProvider(OioDatagramChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(NioClientSocketChannelFactory.class).
            toProvider(NioClientSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(NioServerSocketChannelFactory.class).
            toProvider(NioServerSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(NioDatagramChannelFactory.class).
            toProvider(NioDatagramChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(OioClientSocketChannelFactory.class).
            toProvider(OioClientSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(OioServerSocketChannelFactory.class).
            toProvider(OioServerSocketChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        bind(OioDatagramChannelFactory.class).
            toProvider(OioDatagramChannelFactoryProvider.class).
            in(Scopes.SINGLETON);

        // Local transports
        bind(LocalClientChannelFactory.class).
            to(DefaultLocalClientChannelFactory.class).
            in(Scopes.SINGLETON);

        bind(LocalServerChannelFactory.class).
            to(DefaultLocalServerChannelFactory.class).
            in(Scopes.SINGLETON);

        bind(DefaultLocalClientChannelFactory.class).
            to(DefaultLocalClientChannelFactory.class).
            in(Scopes.SINGLETON);

        bind(DefaultLocalServerChannelFactory.class).
            to(DefaultLocalServerChannelFactory.class).
            in(Scopes.SINGLETON);
    }
}
