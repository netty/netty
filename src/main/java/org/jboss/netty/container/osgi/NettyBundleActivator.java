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
package org.jboss.netty.container.osgi;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.OsgiLoggerFactory;
import org.jboss.netty.util.ExecutorShutdownUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class NettyBundleActivator implements BundleActivator {

    private final List<ServiceRegistration> registrations =
        new ArrayList<ServiceRegistration>();

    private Executor executor;

    public void start(BundleContext ctx) throws Exception {
        initLoggerFactory(ctx);
        executor = Executors.newCachedThreadPool();

        // The default transport is NIO.
        register(ctx,
                 new NioClientSocketChannelFactory(executor, executor),
                 ClientSocketChannelFactory.class);
        register(ctx,
                 new NioServerSocketChannelFactory(executor, executor),
                 ServerSocketChannelFactory.class);

        register(ctx, new OioClientSocketChannelFactory(executor));
        register(ctx, new OioServerSocketChannelFactory(executor, executor));
    }

    public void stop(BundleContext ctx) throws Exception {
        unregisterAll();
        ExecutorShutdownUtil.shutdown(executor);
        executor = null;
    }

    private void initLoggerFactory(BundleContext ctx) {
        ServiceReference logServiceRef = ctx.getServiceReference(LogService.class.getName());
        if (logServiceRef == null) {
            // LogService is not available.
            return;
        }

        LogService logService;
        try {
            logService = (LogService) ctx.getService(logServiceRef);
        } catch (Exception e) {
            // Same name, different service
            return;
        }

        Properties props = new Properties();
        props.setProperty("category", "netty");

        ServiceRegistration reg = ctx.registerService(
                InternalLogger.class.getName(), new Object(), props);
        registrations.add(reg);

        InternalLoggerFactory.setDefaultFactory(
                new OsgiLoggerFactory(logService, reg.getReference()));
    }

    private void register(BundleContext ctx, ChannelFactory factory, Class<?>... factoryTypes) {
        Properties props = new Properties();
        props.setProperty("category", "netty");

        registrations.add(ctx.registerService(factory.getClass().getName(), factory, props));

        for (Class<?> t: factoryTypes) {
            registrations.add(ctx.registerService(t.getName(), factory, props));
        }
    }

    private void unregisterAll() {
        List<ServiceRegistration> registrationsCopy =
            new ArrayList<ServiceRegistration>(registrations);
        registrations.clear();
        for (ServiceRegistration r: registrationsCopy) {
            r.unregister();
        }
    }
}
