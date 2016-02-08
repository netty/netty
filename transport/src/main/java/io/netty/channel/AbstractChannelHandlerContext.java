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
import io.netty.util.ResourceLeakHint;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;

abstract class AbstractChannelHandlerContext implements ChannelHandlerContext, ResourceLeakHint {

    volatile AbstractChannelHandlerContext next;
    volatile AbstractChannelHandlerContext prev;

    private final boolean inbound;
    private final boolean outbound;
    private final DefaultChannelPipeline pipeline;
    private final String name;
    private boolean handlerRemoved;

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

        this.pipeline = pipeline;
        this.name = name;
        this.invoker = invoker;

        this.inbound = inbound;
        this.outbound = outbound;
    }

    @Override
    public Channel channel() {
        return pipeline.channel();
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

    @Override
    public String name() {
        return name;
    }

    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return channel().attr(key);
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return channel().hasAttr(key);
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelRegistered();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelUnregistered();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelActive();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelInactive();
        return this;
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
        AbstractChannelHandlerContext next = this.next;
        next.invokeExceptionCaught(cause);
        return this;
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(Object event) {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeUserEventTriggered(event);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelRead(Object msg) {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelRead(pipeline.touch(msg, next));
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelReadComplete();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        AbstractChannelHandlerContext next = findContextInbound();
        next.invokeChannelWritabilityChanged();
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
        next.invokeBind(localAddress, promise);
        return promise;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeConnect(remoteAddress, localAddress, promise);
        return promise;
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        if (!channel().metadata().hasDisconnect()) {
            return close(promise);
        }

        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeDisconnect(promise);
        return promise;
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeClose(promise);
        return promise;
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeDeregister(promise);
        return promise;
    }

    @Override
    public ChannelHandlerContext read() {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeRead();
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return write(msg, newPromise());
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeWrite(pipeline.touch(msg, next), promise);
        return promise;
    }

    @Override
    public ChannelHandlerContext flush() {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeFlush();
        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        AbstractChannelHandlerContext next = findContextOutbound();
        next.invokeWriteAndFlush(pipeline.touch(msg, next), promise);
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
        return channel().voidPromise();
    }

    void setRemoved() {
        handlerRemoved = true;
    }

    @Override
    public boolean isRemoved() {
        return handlerRemoved;
    }

    final void invokeChannelRegistered() {
        invoker().invokeChannelRegistered(this);
    }

    final void invokeChannelUnregistered() {
        invoker().invokeChannelUnregistered(this);
    }

    final void invokeChannelActive() {
        invoker().invokeChannelActive(this);
    }

    final void invokeChannelInactive() {
        invoker().invokeChannelInactive(this);
    }

    final void invokeExceptionCaught(final Throwable cause) {
        invoker().invokeExceptionCaught(this, cause);
    }

    final void invokeUserEventTriggered(final Object event) {
        invoker().invokeUserEventTriggered(this, event);
    }

    final void invokeChannelRead(final Object msg) {
        invoker().invokeChannelRead(this, msg);
    }

    final void invokeChannelReadComplete() {
        invoker().invokeChannelReadComplete(this);
    }

    final void invokeChannelWritabilityChanged() {
        invoker().invokeChannelWritabilityChanged(this);
    }

    final void invokeBind(final SocketAddress localAddress, final ChannelPromise promise) {
        invoker().invokeBind(this, localAddress, promise);
    }

    final void invokeConnect(final SocketAddress remoteAddress,
                              final SocketAddress localAddress, final ChannelPromise promise) {
        invoker().invokeConnect(this, remoteAddress, localAddress, promise);
    }

    final void invokeDisconnect(final ChannelPromise promise) {
        invoker().invokeDisconnect(this, promise);
    }

    final void invokeClose(final ChannelPromise promise) {
        invoker().invokeClose(this, promise);
    }

    final void invokeDeregister(final ChannelPromise promise) {
        invoker().invokeDeregister(this, promise);
    }

    final void invokeRead() {
        invoker().invokeRead(this);
    }

    final void invokeWrite(final Object msg, final ChannelPromise promise) {
        invoker().invokeWrite(this, msg, promise);
    }

    final void invokeFlush() {
        invoker().invokeFlush(this);
    }

    final void invokeWriteAndFlush(final Object msg, final ChannelPromise promise) {
        final ChannelHandlerInvoker invoker = invoker();
        invoker.invokeWrite(this, msg, promise);
        invoker.invokeFlush(this);
    }

    @Override
    public ChannelHandlerInvoker invoker() {
        return invoker == null ? channel().unsafe().invoker() : invoker;
    }

    @Override
    public String toHintString() {
        return '\'' + name + "' will handle the message from this point.";
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(ChannelHandlerContext.class) + '(' + name + ", " + channel() + ')';
    }
}
