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
package org.jboss.netty.container.spring;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.logging.CommonsLoggerFactory;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.ExecutorUtil;
import org.jboss.netty.util.internal.UnterminatableExecutor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * A factory bean that provides the common resources required by
 * {@link ChannelFactory} implementations.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class NettyResourceFactory implements InitializingBean, DisposableBean {
    private Executor executor;
    private Executor unterminatableExecutor;

    public synchronized void afterPropertiesSet() {
        if (executor != null) {
            return;
        }

        executor = Executors.newCachedThreadPool();
        unterminatableExecutor = new UnterminatableExecutor(executor);
        InternalLoggerFactory.setDefaultFactory(new CommonsLoggerFactory());
    }

    public synchronized void destroy() {
        if (executor != null) {
            ExecutorUtil.terminate(executor);
        }

        executor = null;
        unterminatableExecutor = null;
    }

    public synchronized Executor getChannelFactoryExecutor() {
        return unterminatableExecutor;
    }
}
