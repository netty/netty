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
package org.jboss.netty.handler.execution;

import java.util.concurrent.Executor;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.internal.ExecutorUtil;

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
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.has java.util.concurrent.ThreadPoolExecutor
 */
@ChannelPipelineCoverage("all")
public class ExecutionHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler, ExternalResourceReleasable {

    private final Executor executor;

    /**
     * Creates a new instance with the specified {@link Executor}.
     * Specify an {@link OrderedMemoryAwareThreadPoolExecutor} if unsure.
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

    public void handleDownstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent cse = (ChannelStateEvent) e;
            if (cse.getState() == ChannelState.INTEREST_OPS &&
                (((Integer) cse.getValue()).intValue() & Channel.OP_READ) != 0) {

                // setReadable(true) requested
                boolean readSuspended = ctx.getAttachment() != null;
                if (readSuspended) {
                    // Drop the request silently if MemoryAwareThreadPool has
                    // set the flag.
                    e.getFuture().setSuccess();
                    return;
                }
            }
        }

        ctx.sendDownstream(e);
    }
}
