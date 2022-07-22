/*
 * Copyright 2012 The Netty Project
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
package io.netty5.channel;

import io.netty5.util.concurrent.Future;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.EnumSet;

final class LoggingHandler implements ChannelHandler {

    enum Event { WRITE, FLUSH, BIND, CONNECT, DISCONNECT, CLOSE, REGISTER, DEREGISTER, READ, WRITABILITY,
        HANDLER_ADDED, HANDLER_REMOVED, EXCEPTION, READ_COMPLETE, REGISTERED, UNREGISTERED, ACTIVE, INACTIVE,
        USER }

    private StringBuilder log = new StringBuilder();

    private final EnumSet<Event> interest = EnumSet.allOf(Event.class);

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        log(Event.WRITE);
        return ctx.write(msg);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        log(Event.FLUSH);
        ctx.flush();
    }

    @Override
    public Future<Void> bind(ChannelHandlerContext ctx, SocketAddress localAddress) {
        log(Event.BIND, "localAddress=" + localAddress);
        return  ctx.bind(localAddress);
    }

    @Override
    public Future<Void> connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress) {
        log(Event.CONNECT, "remoteAddress=" + remoteAddress + " localAddress=" + localAddress);
        return ctx.connect(remoteAddress, localAddress);
    }

    @Override
    public Future<Void> disconnect(ChannelHandlerContext ctx) {
        log(Event.DISCONNECT);
        return ctx.disconnect();
    }

    @Override
    public Future<Void> close(ChannelHandlerContext ctx) {
        log(Event.CLOSE);
        return ctx.close();
    }

    @Override
    public Future<Void> register(ChannelHandlerContext ctx) {
        log(Event.REGISTER);
        return ctx.register();
    }

    @Override
    public Future<Void> deregister(ChannelHandlerContext ctx) {
        log(Event.DEREGISTER);
        return ctx.deregister();
    }

    @Override
    public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
        log(Event.READ);
        ctx.read(readBufferAllocator);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        log(Event.WRITABILITY, "writable=" + ctx.channel().isWritable());
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        log(Event.HANDLER_ADDED);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log(Event.HANDLER_REMOVED);
    }

    @Override
    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log(Event.EXCEPTION, cause.toString());
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        log(Event.REGISTERED);
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        log(Event.UNREGISTERED);
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log(Event.ACTIVE);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log(Event.INACTIVE);
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log(Event.READ);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        log(Event.READ_COMPLETE);
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
        log(Event.USER, evt.toString());
        ctx.fireChannelInboundEvent(evt);
    }

    String getLog() {
        return log.toString();
    }

    void clear() {
        log = new StringBuilder();
    }

    void setInterest(Event... events) {
        interest.clear();
        Collections.addAll(interest, events);
    }

    private void log(Event e) {
        log(e, null);
    }

    private void log(Event e, String msg) {
        if (interest.contains(e)) {
            log.append(e);
            if (msg != null) {
                log.append(": ").append(msg);
            }
            log.append('\n');
        }
    }
}
