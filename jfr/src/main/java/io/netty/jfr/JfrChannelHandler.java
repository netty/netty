/*
 * Copyright 2021 The Netty Project
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
package io.netty.jfr;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Threshold;

import java.net.SocketAddress;
import java.util.Optional;

/**
 * A {@link ChannelHandler} that creates JDK Flight Recorder events for all Netty channel events/operations.
 */
@Sharable
public final class JfrChannelHandler extends ChannelDuplexHandler {

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        final ChannelRegisteredEvent channelRegisteredEvent = new ChannelRegisteredEvent();
        channelRegisteredEvent.begin();
        ctx.fireChannelRegistered();
        channelRegisteredEvent.end();
        if (channelRegisteredEvent.shouldCommit()) {
            channelRegisteredEvent.setChannel(ctx.channel());
            channelRegisteredEvent.commit();
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        final ChannelUnregisteredEvent channelUnregisteredEvent = new ChannelUnregisteredEvent();
        channelUnregisteredEvent.begin();
        ctx.fireChannelUnregistered();
        channelUnregisteredEvent.end();
        if (channelUnregisteredEvent.shouldCommit()) {
            channelUnregisteredEvent.setChannel(ctx.channel());
            channelUnregisteredEvent.commit();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        final ChannelActiveEvent channelActiveEvent = new ChannelActiveEvent();
        channelActiveEvent.begin();
        ctx.fireChannelActive();
        channelActiveEvent.end();
        if (channelActiveEvent.shouldCommit()) {
            channelActiveEvent.setChannel(ctx.channel());
            channelActiveEvent.commit();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        final ChannelInactiveEvent channelInactiveEvent = new ChannelInactiveEvent();
        channelInactiveEvent.begin();
        ctx.fireChannelInactive();
        channelInactiveEvent.end();
        if (channelInactiveEvent.shouldCommit()) {
            channelInactiveEvent.setChannel(ctx.channel());
        }
        channelInactiveEvent.commit();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        final ExceptionCaughtEvent exceptionCaughtEvent = new ExceptionCaughtEvent();
        exceptionCaughtEvent.begin();
        ctx.fireExceptionCaught(cause);
        exceptionCaughtEvent.end();
        if (exceptionCaughtEvent.shouldCommit()) {
            exceptionCaughtEvent.setChannel(ctx.channel());
            exceptionCaughtEvent.setCause(cause);
            exceptionCaughtEvent.commit();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        final UserEventTriggeredEvent userEventTriggeredEvent = new UserEventTriggeredEvent();
        userEventTriggeredEvent.begin();
        userEventTriggeredEvent.setUserEvent(evt);
        ctx.fireUserEventTriggered(evt);
        userEventTriggeredEvent.end();
        if (userEventTriggeredEvent.shouldCommit()) {
            userEventTriggeredEvent.setChannel(ctx.channel());
            userEventTriggeredEvent.commit();
        }
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
        final BindEvent bindEvent = new BindEvent();
        bindEvent.begin();
        ctx.bind(localAddress, promise);
        bindEvent.end();
        if (bindEvent.shouldCommit()) {
            bindEvent.setChannel(ctx.channel());
            bindEvent.setLocalAddress(localAddress);
            bindEvent.commit();
        }
    }

    @Override
    public void connect(
            ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        final ConnectEvent connectEvent = new ConnectEvent();
        connectEvent.begin();
        ctx.connect(remoteAddress, localAddress, promise);
        connectEvent.end();
        if (connectEvent.shouldCommit()) {
            connectEvent.setChannel(ctx.channel());
            connectEvent.setRemoteAddress(remoteAddress);
            connectEvent.setLocalAddress(localAddress);
            connectEvent.commit();
        }
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
        final DisconnectEvent disconnectEvent = new DisconnectEvent();
        disconnectEvent.begin();
        ctx.disconnect(promise);
        disconnectEvent.end();
        if (disconnectEvent.shouldCommit()) {
            disconnectEvent.setChannel(ctx.channel());
            disconnectEvent.commit();
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        final CloseEvent closeEvent = new CloseEvent();
        closeEvent.begin();
        ctx.close(promise);
        closeEvent.end();
        if (closeEvent.shouldCommit()) {
            closeEvent.setChannel(ctx.channel());
            closeEvent.commit();
        }
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
        final DeregisterEvent deregisterEvent = new DeregisterEvent();
        deregisterEvent.begin();
        ctx.deregister(promise);
        deregisterEvent.end();
        if (deregisterEvent.shouldCommit()) {
            deregisterEvent.setChannel(ctx.channel());
            deregisterEvent.commit();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        final ChannelReadCompleteEvent channelReadCompleteEvent = new ChannelReadCompleteEvent();
        channelReadCompleteEvent.begin();
        ctx.fireChannelReadComplete();
        channelReadCompleteEvent.end();
        if (channelReadCompleteEvent.shouldCommit()) {
            channelReadCompleteEvent.setChannel(ctx.channel());
            channelReadCompleteEvent.commit();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        final ChannelReadEvent channelReadEvent = new ChannelReadEvent();
        channelReadEvent.begin();
        channelReadEvent.setMsg(msg);
        ctx.fireChannelRead(msg);
        channelReadEvent.end();
        if (channelReadEvent.shouldCommit()) {
            channelReadEvent.setChannel(ctx.channel());
            channelReadEvent.commit();
        }
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        final ReadEvent readEvent = new ReadEvent();
        readEvent.begin();
        ctx.read();
        readEvent.end();
        if (readEvent.shouldCommit()) {
            readEvent.setChannel(ctx.channel());
            readEvent.commit();
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        final WriteEvent writeEvent = new WriteEvent();
        writeEvent.begin();
        writeEvent.setMsg(msg);
        ctx.write(msg, promise);
        writeEvent.end();
        if (writeEvent.shouldCommit()) {
            writeEvent.setChannel(ctx.channel());
            writeEvent.commit();
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        final ChannelWritabilityChangedEvent channelWritabilityChangedEvent = new ChannelWritabilityChangedEvent();
        channelWritabilityChangedEvent.begin();
        channelWritabilityChangedEvent.setWasWritable(ctx.channel().isWritable());
        ctx.fireChannelWritabilityChanged();
        channelWritabilityChangedEvent.end();
        if (channelWritabilityChangedEvent.shouldCommit()) {
            channelWritabilityChangedEvent.setChannel(ctx.channel());
            channelWritabilityChangedEvent.setIsWritable(ctx.channel().isWritable());
            channelWritabilityChangedEvent.commit();
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        final FlushEvent flushEvent = new FlushEvent();
        flushEvent.begin();
        ctx.flush();
        flushEvent.end();
        if (flushEvent.shouldCommit()) {
            flushEvent.setChannel(ctx.channel());
            flushEvent.commit();
        }
    }

    // Event types

    @Category({ "Netty", "Channel" })
    @Enabled(true)
    @Threshold("1 us")
    private abstract static class ChannelEvent extends Event {
        @Label("Channel")
        String channel;
        @Label("Long Channel ID")
        String longChannelId;
        @Label("Short Channel ID")
        String shortChannelId;

        void setChannel(Channel channel) {
            this.channel = String.valueOf(channel);
            //TODO: More specific channel fields?
            this.longChannelId = channel.id().asLongText();
            this.shortChannelId = channel.id().asShortText();
        }
    }

    @Threshold("20 ms")
    private abstract static class ChannelIOEvent extends ChannelEvent {
    }

    @Label("Bind")
    @Name("io.netty.channel.Bind")
    private static final class BindEvent extends ChannelEvent {
        @Label("Local Address")
        String localAddress;

        void setLocalAddress(SocketAddress localAddress) {
            this.localAddress = String.valueOf(localAddress);
        }
    }

    @Label("Write")
    @Name("io.netty.channel.Write")
    private static final class WriteEvent extends ChannelIOEvent {
        @Label("Message")
        String msg;

        void setMsg(Object msg) {
            this.msg = String.valueOf(msg);
        }
    }

    @Label("Channel Read")
    @Name("io.netty.channel.ChannelRead")
    private static final class ChannelReadEvent extends ChannelIOEvent {
        @Label("Message")
        String msg;

        void setMsg(Object msg) {
            this.msg = String.valueOf(msg);
        }
    }

    @Label("Channel Read Complete")
    @Name("io.netty.channel.ChannelReadComplete")
    private static final class ChannelReadCompleteEvent extends ChannelIOEvent {
    }

    @Label("Close")
    @Name("io.netty.channel.Close")
    private static final class CloseEvent extends ChannelEvent {
    }

    @Label("Disconnect")
    @Name("io.netty.channel.Disconnect")
    private static final class DisconnectEvent extends ChannelEvent {
    }

    @Label("Connect")
    @Name("io.netty.channel.Connect")
    private static final class ConnectEvent extends ChannelEvent {
        @Label("Remote Address")
        String remoteAddress;
        @Label("Local Address")
        String localAddress;

        void setRemoteAddress(SocketAddress remoteAddress) {
            this.remoteAddress = String.valueOf(remoteAddress);
        }

        void setLocalAddress(SocketAddress localAddress) {
            this.localAddress = String.valueOf(localAddress);
        }
    }

    @Label("Channel Registered")
    @Name("io.netty.channel.ChannelRegistered")
    private static final class ChannelRegisteredEvent extends ChannelEvent {
    }

    @Label("Channel Unregistered")
    @Name("io.netty.channel.ChannelUnregistered")
    private static final class ChannelUnregisteredEvent extends ChannelEvent {
    }

    @Label("Channel Writability Changed")
    @Name("io.netty.channel.ChannelWritabilityChanged")
    private static final class ChannelWritabilityChangedEvent extends ChannelEvent {
        @Label("Is Writable")
        boolean isWritable;
        @Label("Was Writable")
        boolean wasWritable;

        void setIsWritable(boolean writable) {
            isWritable = writable;
        }

        void setWasWritable(boolean wasWritable) {
            this.wasWritable = wasWritable;
        }
    }

    @Label("Channel Inactive")
    @Name("io.netty.channel.ChannelInactive")
    private static final class ChannelInactiveEvent extends ChannelEvent {
    }

    @Label("Channel Active")
    @Name("io.netty.channel.ChannelActive")
    private static final class ChannelActiveEvent extends ChannelEvent {
    }

    @Label("Deregister")
    @Name("io.netty.channel.Deregister")
    private static final class DeregisterEvent extends ChannelEvent {
    }

    @Label("User Event Triggered")
    @Name("io.netty.channel.UserEventTriggered")
    private static final class UserEventTriggeredEvent extends ChannelEvent {
        @Label("User Event")
        String userEvent;

        void setUserEvent(Object evt) {
            this.userEvent = String.valueOf(evt);
        }
    }

    @Label("Flush")
    @Name("io.netty.channel.Flush")
    private static final class FlushEvent extends ChannelIOEvent {
    }

    @Label("Exception Caught")
    @Name("io.netty.channel.ExceptionCaught")
    private static final class ExceptionCaughtEvent extends ChannelEvent {
        @Label("Message")
        String message;

        @Label("Class")
        Class<?> thrownClass;

        void setCause(Throwable cause) {
            this.message = Optional.ofNullable(cause).map(Throwable::getMessage).orElse(null);
            this.thrownClass = Optional.ofNullable(cause).map(Throwable::getClass).orElse(null);
        }
    }

    @Label("Read")
    @Name("io.netty.channel.Read")
    private static final class ReadEvent extends ChannelIOEvent {
    }
}
