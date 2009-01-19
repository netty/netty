/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.handler.execution;

import java.util.concurrent.Executor;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.util.ExecutorUtil;
import org.jboss.netty.util.ExternalResourceReleasable;

/**
 * Forwards an upstream {@link ChannelEvent} to an {@link Executor}.
 * <p>
 * You can implement various thread model by adding this handler to a
 * {@link ChannelPipeline}.  The most common use case of this handler is to
 * add a {@link ExecutionHandler} which was specified with
 * {@link OrderedMemoryAwareThreadPoolExecutor}:
 * <pre>
 * ChannelPipeline pipeline = ...;
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 *
 * // HERE
 * <strong>pipeline.addLast("executor", new {@link ExecutionHandler}(new {@link OrderedMemoryAwareThreadPoolExecutor}(16, 1048576, 1048576)));</strong>
 *
 * pipeline.addLast("handler", new MyBusinessLogicHandler());
 * </pre>
 * to utilize more processors to handle {@link ChannelEvent}s.  You can also
 * use other {@link Executor} implementation than the recommended
 * {@link OrderedMemoryAwareThreadPoolExecutor}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.has java.util.concurrent.ThreadPoolExecutor
 */
@ChannelPipelineCoverage("all")
public class ExecutionHandler implements ChannelUpstreamHandler, ExternalResourceReleasable {

    private final Executor executor;

    /**
     * Creates a new instance with the specified {@link Executor}.
     */
    public ExecutionHandler(Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
        this.executor = executor;
    }

    /**
     * Returns the {@link Executor} which was specified with the constructor.
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Shuts down the {@link Executor} which was specified with the constructor
     * and wait for its termination.
     */
    public void releaseExternalResources() {
        ExecutorUtil.terminate(getExecutor());
    }

    public void handleUpstream(
            ChannelHandlerContext context, ChannelEvent e) throws Exception {
        executor.execute(new ChannelEventRunnable(context, e));
    }
}
