/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ObjectUtil;

import java.net.SocketAddress;

abstract class DelegatingChannelHandlerContext implements ChannelHandlerContext {

    private final ChannelHandlerContext ctx;

    DelegatingChannelHandlerContext(ChannelHandlerContext ctx) {
        this.ctx = ObjectUtil.checkNotNull(ctx, "ctx");
    }

    @Override
    public Channel channel() {
        return ctx.channel();
    }

    @Override
    public EventExecutor executor() {
        return ctx.executor();
    }

    @Override
    public String name() {
        return ctx.name();
    }

    @Override
    public ChannelHandler handler() {
        return ctx.handler();
    }

    @Override
    public boolean isRemoved() {
        return ctx.isRemoved();
    }

    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        ctx.fireChannelRegistered();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        ctx.fireChannelUnregistered();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        ctx.fireChannelActive();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        ctx.fireChannelInactive();
        return this;
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
        ctx.fireExceptionCaught(cause);
        return this;
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(Object evt) {
        ctx.fireUserEventTriggered(evt);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelRead(Object msg) {

        ctx.fireChannelRead(msg);
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        ctx.fireChannelReadComplete();
        return this;
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        ctx.fireChannelWritabilityChanged();
        return this;
    }

    @Override
    public ChannelHandlerContext read() {
        ctx.read();
        return this;
    }

    @Override
    public ChannelHandlerContext flush() {
        ctx.flush();
        return this;
    }

    @Override
    public ChannelPipeline pipeline() {
        return ctx.pipeline();
    }

    @Override
    public ByteBufAllocator alloc() {
        return ctx.alloc();
    }

    @Deprecated
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return ctx.attr(key);
    }

    @Deprecated
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return ctx.hasAttr(key);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return ctx.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return ctx.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return ctx.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return ctx.disconnect();
    }

    @Override
    public ChannelFuture close() {
        return ctx.close();
    }

    @Override
    public ChannelFuture deregister() {
        return ctx.deregister();
    }

    @Override
    public ChannelFuture register() {
        return ctx.register();
    }

    @Override
    public ChannelFuture register(ChannelPromise promise) {
        return ctx.register(promise);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return ctx.bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return ctx.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return ctx.disconnect(promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return ctx.close(promise);
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return ctx.deregister(promise);
    }

    @Override
    public ChannelFuture write(Object msg) {
        return ctx.write(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return ctx.write(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return ctx.writeAndFlush(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return ctx.writeAndFlush(msg);
    }

    @Override
    public ChannelPromise newPromise() {
        return ctx.newPromise();
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return ctx.newProgressivePromise();
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return ctx.newSucceededFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return ctx.newFailedFuture(cause);
    }

    @Override
    public ChannelPromise voidPromise() {
        return ctx.voidPromise();
    }
}
