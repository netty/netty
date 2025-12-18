/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.ObjectUtil;

/**
 * WebTransportSession 的默认实现。
 */
public class DefaultWebTransportSession implements WebTransportSession {

    private final String sessionId;
    private final Channel channel;
    private final WebTransportObserver observer;

    public DefaultWebTransportSession(String sessionId, Channel channel, WebTransportObserver observer) {
        this.sessionId = ObjectUtil.checkNotNull(sessionId, "sessionId");
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        this.observer = observer != null ? observer : NoopWebTransportObserver.INSTANCE;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public Future<WebTransportStream> createBidirectionalStream() {
        Promise<WebTransportStream> promise = channel.eventLoop().newPromise();
        // 简化实现，使用递增的流ID
        long streamId = System.nanoTime();
        WebTransportStream stream = new DefaultWebTransportStream(channel, streamId);
        promise.setSuccess(stream);
        observer.onStreamCreated(stream);
        return promise;
    }

    @Override
    public Future<WebTransportStream> createUnidirectionalStream() {
        Promise<WebTransportStream> promise = channel.eventLoop().newPromise();
        // 简化实现，使用递增的流ID
        long streamId = System.nanoTime();
        WebTransportStream stream = new DefaultWebTransportStream(channel, streamId);
        promise.setSuccess(stream);
        observer.onStreamCreated(stream);
        return promise;
    }

    @Override
    public ChannelFuture sendDatagram(ByteBuf datagram) {
        return sendDatagram(datagram, channel.newPromise());
    }

    @Override
    public ChannelFuture sendDatagram(ByteBuf datagram, ChannelPromise promise) {
        WebTransportDatagram webTransportDatagram = new DefaultWebTransportDatagram(channel, datagram);
        ChannelFuture future = channel.writeAndFlush(webTransportDatagram, promise);
        future.addListener(f -> {
            if (f.isSuccess()) {
                observer.onDatagramSent(this, webTransportDatagram);
            }
        });
        return future;
    }

    @Override
    public ChannelFuture close() {
        return close(channel.newPromise());
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        ChannelFuture future = channel.close(promise);
        future.addListener(f -> {
            observer.onSessionClosed(this, future);
        });
        return future;
    }

    @Override
    public Future<Void> closeFuture() {
        return channel.closeFuture();
    }

    @Override
    public WebTransportObserver observer() {
        return observer;
    }
}
