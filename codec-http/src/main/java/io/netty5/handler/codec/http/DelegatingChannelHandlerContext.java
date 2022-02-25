/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http;

import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.Attribute;
import io.netty5.util.AttributeKey;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

import java.net.SocketAddress;
import java.util.Objects;

abstract class DelegatingChannelHandlerContext implements ChannelHandlerContext {

    private final ChannelHandlerContext ctx;

    DelegatingChannelHandlerContext(ChannelHandlerContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
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

    @Override
    public BufferAllocator bufferAllocator() {
        return ctx.bufferAllocator();
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
    public Future<Void> bind(SocketAddress localAddress) {
        return ctx.bind(localAddress);
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress) {
        return ctx.connect(remoteAddress);
    }

    @Override
    public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return ctx.connect(remoteAddress, localAddress);
    }

    @Override
    public Future<Void> disconnect() {
        return ctx.disconnect();
    }

    @Override
    public Future<Void> close() {
        return ctx.close();
    }

    @Override
    public Future<Void> deregister() {
        return ctx.deregister();
    }

    @Override
    public Future<Void> register() {
        return ctx.register();
    }

    @Override
    public Future<Void> write(Object msg) {
        return ctx.write(msg);
    }

    @Override
    public Future<Void> writeAndFlush(Object msg) {
        return ctx.writeAndFlush(msg);
    }

    @Override
    public Promise<Void> newPromise() {
        return ctx.newPromise();
    }

    @Override
    public Future<Void> newSucceededFuture() {
        return ctx.newSucceededFuture();
    }

    @Override
    public Future<Void> newFailedFuture(Throwable cause) {
        return ctx.newFailedFuture(cause);
    }
}
