/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakHint;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;

abstract class AbstractChannelHandlerContext implements ChannelHandlerContext, ResourceLeakHint {

    volatile AbstractChannelHandlerContext next;
    volatile AbstractChannelHandlerContext prev;

    private final boolean inbound;
    private final boolean outbound;
    private final AbstractChannel channel;
    private final DefaultChannelPipeline pipeline;
    private final String name;
    private boolean removed;

    final ChannelHandlerInvoker invoker;
    private ChannelFuture succeededFuture;

    // Lazily instantiated tasks used to trigger events to a handler with different executor.
    // These needs to be volatile as otherwise an other Thread may see an half initialized instance.
    // See the JMM for more details
    volatile Runnable invokeChannelReadCompleteTask;
    volatile Runnable invokeReadTask;
    volatile Runnable invokeChannelWritableStateChangedTask;
    volatile Runnable invokeFlushTask;

    AbstractChannelHandlerContext(
            DefaultChannelPipeline pipeline, ChannelHandlerInvoker invoker,
            String name, boolean inbound, boolean outbound) {

        if (name == null) {
            throw new NullPointerException("name");
        }

        channel = pipeline.channel;
        this.pipeline = pipeline;
        this.name = name;
        this.invoker = invoker;

        this.inbound = inbound;
        this.outbound = outbound;
    }

    /** Invocation initiated by {@link DefaultChannelPipeline#teardownAll()}}. */
    void teardown() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            teardown0();
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    teardown0();
                }
            });
        }
    }

    private void teardown0() {
        AbstractChannelHandlerContext prev = this.prev;
        if (prev != null) {
            synchronized (pipeline) {
                pipeline.remove0(this);
            }
            prev.teardown();
        }
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public ByteBufAllocator alloc() {
        return channel().config().getAllocator();
    }

    @Override
    public EventExecutor executor() {
        return invoker().executor();
    }

    public ChannelHandlerInvoker invoker() {
        if (invoker == null) {
            return channel.unsafe().invoker();
        } else {
            return invoker;
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return channel.attr(key);
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return channel.hasAttr(key);
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker().invokeChannelRegistered(next);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker().invokeChannelUnregistered(next);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker().invokeChannelActive(next);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker().invokeChannelInactive(next);
        return this;
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
        AbstractChannelHandlerContext next = this.next;
        next.invoker().invokeExceptionCaught(next, cause);
        return this;
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(Object event) {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker().invokeUserEventTriggered(next, event);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelRead(Object msg) {
        AbstractChannelHandlerContext next = findContextInbound();
        ReferenceCountUtil.touch(msg, next);
        next.invoker().invokeChannelRead(next, msg);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker().invokeChannelReadComplete(next);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invoker().invokeChannelWritabilityChanged(next);
        return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, newPromise());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public ChannelFuture close() {
        return close(newPromise());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(newPromise());
    }

    @Override
    public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invoker().invokeBind(next, localAddress, promise);
        return promise;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invoker().invokeConnect(next, remoteAddress, localAddress, promise);
        return promise;
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        if (!channel().metadata().hasDisconnect()) {
            return close(promise);
        }

        AbstractChannelHandlerContext next = findContextOutbound();
        next.invoker().invokeDisconnect(next, promise);
        return promise;
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invoker().invokeClose(next, promise);
        return promise;
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invoker().invokeDeregister(next, promise);
        return promise;
    }

    @Override
    public ChannelHandlerContext read() {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invoker().invokeRead(next);
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return write(msg, newPromise());
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        ReferenceCountUtil.touch(msg, next);
        next.invoker().invokeWrite(next, msg, promise);
        return promise;
    }

    @Override
    public ChannelHandlerContext flush() {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invoker().invokeFlush(next);
        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        ReferenceCountUtil.touch(msg, next);
        ChannelHandlerInvoker invoker = next.invoker();
        invoker.invokeWrite(next, msg, promise);
        invoker.invokeFlush(next);
        return promise;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return writeAndFlush(msg, newPromise());
    }

    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel(), executor());
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return new DefaultChannelProgressivePromise(channel(), executor());
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        ChannelFuture succeededFuture = this.succeededFuture;
        if (succeededFuture == null) {
            this.succeededFuture = succeededFuture = new SucceededChannelFuture(channel(), executor());
        }
        return succeededFuture;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(channel(), executor(), cause);
    }

    private AbstractChannelHandlerContext findContextInbound() {
        AbstractChannelHandlerContext ctx = this;
        do {
            ctx = ctx.next;
        } while (!ctx.inbound);
        return ctx;
    }

    private AbstractChannelHandlerContext findContextOutbound() {
        AbstractChannelHandlerContext ctx = this;
        do {
            ctx = ctx.prev;
        } while (!ctx.outbound);
        return ctx;
    }

    @Override
    public ChannelPromise voidPromise() {
        return channel.voidPromise();
    }

    void setRemoved() {
        removed = true;
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }

    @Override
    public String toHintString() {
        return '\'' + name + "' will handle the message from this point.";
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(ChannelHandlerContext.class) + '(' + name + ", " + channel + ')';
    }
}
