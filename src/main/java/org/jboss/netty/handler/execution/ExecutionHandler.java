/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.execution;

import java.util.concurrent.Executor;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.internal.ExecutorUtil;

/**
 * Forwards an upstream {@link ChannelEvent} to an {@link Executor}.
 * <p>
 * {@link ExecutionHandler} is often used when your {@link ChannelHandler}
 * performs a blocking operation that takes long time or accesses a resource
 * which is not CPU-bound business logic such as DB access.  Running such
 * operations in a pipeline without an {@link ExecutionHandler} will result in
 * unwanted hiccup during I/O because an I/O thread cannot perform I/O until
 * your handler returns the control to the I/O thread.
 * <p>
 * In most cases, an {@link ExecutionHandler} is coupled with an
 * {@link OrderedMemoryAwareThreadPoolExecutor} because it guarantees the
 * correct event execution order and prevents an {@link OutOfMemoryError}
 * under load:
 * <pre>
 * public class DatabaseGatewayPipelineFactory implements {@link ChannelPipelineFactory} {
 *
 *     <b>private final {@link ExecutionHandler} executionHandler;</b>
 *
 *     public DatabaseGatewayPipelineFactory({@link ExecutionHandler} executionHandler) {
 *         this.executionHandler = executionHandler;
 *     }
 *
 *     public {@link ChannelPipeline} getPipeline() {
 *         return {@link Channels}.pipeline(
 *                 new DatabaseGatewayProtocolEncoder(),
 *                 new DatabaseGatewayProtocolDecoder(),
 *                 <b>executionHandler, // Must be shared</b>
 *                 new DatabaseQueryingHandler());
 *     }
 * }
 * ...
 *
 * public static void main(String[] args) {
 *     {@link ServerBootstrap} bootstrap = ...;
 *     ...
 *     <b>{@link ExecutionHandler} executionHandler = new {@link ExecutionHandler}(
 *             new {@link OrderedMemoryAwareThreadPoolExecutor}(16, 1048576, 1048576))
 *     bootstrap.setPipelineFactory(
 *             new DatabaseGatewayPipelineFactory(executionHandler));</b>
 *     ...
 *     bootstrap.bind(...);
 *     ...
 *
 *     while (!isServerReadyToShutDown()) {
 *         // ... wait ...
 *     }
 *
 *     bootstrap.releaseExternalResources();
 *     <b>executionHandler.releaseExternalResources();</b>
 * }
 * </pre>
 *
 * Please refer to {@link OrderedMemoryAwareThreadPoolExecutor} for the
 * detailed information about how the event order is guaranteed.
 *
 * <h3>SEDA (Staged Event-Driven Architecture)</h3>
 * You can implement an alternative thread model such as
 * <a href="http://en.wikipedia.org/wiki/Staged_event-driven_architecture">SEDA</a>
 * by adding more than one {@link ExecutionHandler} to the pipeline.
 *
 * <h3>Using other {@link Executor} implementation</h3>
 *
 * Although it's recommended to use {@link OrderedMemoryAwareThreadPoolExecutor},
 * you can use other {@link Executor} implementations.  However, you must note
 * that other {@link Executor} implementation might break your application
 * because they often do not maintain event execution order nor interact with
 * I/O threads to control the incoming traffic and avoid {@link OutOfMemoryError}.
 *
 * @apiviz.landmark
 * @apiviz.has java.util.concurrent.ThreadPoolExecutor
 */
@Sharable
public class ExecutionHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler, ExternalResourceReleasable {

    private final Executor executor;
    private final boolean handleDownstream;
    private final boolean handleUpstream;
    
    /**
     * Creates a new instance with the specified {@link Executor}.
     * Specify an {@link OrderedMemoryAwareThreadPoolExecutor} if unsure.
     */
    public ExecutionHandler(Executor executor) {
        this(executor, false, true);
    }

    
    /**
     * Use {@link #ExecutionHandler(Executor, boolean, boolean)}
     * 
     * {@link Deprecated}
     */
    @Deprecated
    public ExecutionHandler(Executor executor, boolean handleDownstream) {
        this(executor, handleDownstream, true);
    }
    
    /**
     * Creates a new instance with the specified {@link Executor}.
     * Specify an {@link OrderedMemoryAwareThreadPoolExecutor} if unsure.
     */
    public ExecutionHandler(Executor executor, boolean handleDownstream, boolean handleUpstream) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
        if (!handleDownstream && !handleUpstream) {
            throw new IllegalArgumentException("You must handle at least handle one event type");
        }
        this.executor = executor;
        this.handleDownstream = handleDownstream;
        this.handleUpstream = handleUpstream;
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
        Executor executor = getExecutor();
        ExecutorUtil.terminate(executor);
        if (executor instanceof ExternalResourceReleasable) {
            ((ExternalResourceReleasable) executor).releaseExternalResources();
        }
    }

    public void handleUpstream(
            ChannelHandlerContext context, ChannelEvent e) throws Exception {
        if (handleUpstream) {
            executor.execute(new ChannelUpstreamEventRunnable(context, e));
        } else {
            context.sendUpstream(e);
        }
    }

    public void handleDownstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        // check if the read was suspend
        if (!handleReadSuspend(ctx, e)) {
            if (handleDownstream) {
                executor.execute(new ChannelDownstreamEventRunnable(ctx, e));
            } else {
                ctx.sendDownstream(e);
            }
        }
    }

    /**
     * Handle suspended reads
     */
    protected boolean handleReadSuspend(ChannelHandlerContext ctx, ChannelEvent e) {
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
                    return true;
                }
            }
        }

        return false;
    }
}
