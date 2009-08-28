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
