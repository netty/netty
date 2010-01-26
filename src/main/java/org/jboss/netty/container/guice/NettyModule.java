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
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
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
