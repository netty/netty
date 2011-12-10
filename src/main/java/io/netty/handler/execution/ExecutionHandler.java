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
package io.netty.handler.execution;

import java.util.concurrent.Executor;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDownstreamHandler;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.ChannelUpstreamHandler;
import io.netty.channel.Channels;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.execution.seda.SedaExecutor;
import io.netty.util.ExternalResourceReleasable;
import io.netty.util.internal.ExecutorUtil;

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
 * Alternative you may want to have a look at {@link SedaExecutor}.
 *
 * <h3>Using other {@link Executor} implementation</h3>
 *
 * Although it's recommended to use {@link OrderedMemoryAwareThreadPoolExecutor},
 * you can use other {@link Executor} implementations.  However, you must note
 * that other {@link Executor} implementation might break your application
 * because they often do not maintain event execution order nor interact with
 * I/O threads to control the incoming traffic and avoid {@link OutOfMemoryError}.
 * @apiviz.landmark
 * @apiviz.has java.util.concurrent.ThreadPoolExecutor
 */
@Sharable
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
    @Override
    public void releaseExternalResources() {
        ExecutorUtil.terminate(getExecutor());
    }

    @Override
    public void handleUpstream(
            ChannelHandlerContext context, ChannelEvent e) throws Exception {
        executor.execute(new ChannelEventRunnable(context, e));
    }

    @Override
    public void handleDownstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        handleReadSuspend(ctx, e);
        ctx.sendDownstream(e);
    }
    
    /**
     * Handle suspended reads
     *  
     * @param ctx
     * @param e
     */
    protected void handleReadSuspend(ChannelHandlerContext ctx, ChannelEvent e) {
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
    }
}
