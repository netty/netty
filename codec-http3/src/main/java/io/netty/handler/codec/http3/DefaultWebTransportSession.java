/*
 * Copyright 2023 The Netty Project
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
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;

/**
 * Default implementation of WebTransportSession
 */
public class DefaultWebTransportSession implements WebTransportSession {
    
    private final QuicChannel quicChannel;
    private final WebTransportObserver observer;
    private volatile boolean active = true;
    
    public DefaultWebTransportSession(QuicChannel quicChannel, WebTransportObserver observer) {
        this.quicChannel = quicChannel;
        this.observer = observer;
    }
    
    @Override
    public ChannelFuture createBidirectionalStream() {
        return quicChannel.createStream()
                .addListener(future -> {
                    if (future.isSuccess()) {
                        QuicStreamChannel stream = (QuicStreamChannel) future.get();
                        observer.onBidirectionalStreamCreated(stream);
                    } else {
                        observer.onError(this, future.cause());
                    }
                });
    }
    
    @Override
    public ChannelFuture createUnidirectionalStream() {
        return quicChannel.createUnidirectionalStream()
                .addListener(future -> {
                    if (future.isSuccess()) {
                        QuicStreamChannel stream = (QuicStreamChannel) future.get();
                        observer.onUnidirectionalStreamCreated(stream);
                    } else {
                        observer.onError(this, future.cause());
                    }
                });
    }
    
    @Override
    public ChannelFuture sendDatagram(ByteBuf data) {
        return quicChannel.writeAndFlush(data)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        observer.onDatagramSent(this, data);
                    } else {
                        observer.onError(this, future.cause());
                    }
                });
    }
    
    @Override
    public void handleDatagram(ByteBuf data) {
        observer.onDatagramReceived(this, data);
    }
    
    @Override
    public ChannelFuture close() {
        active = false;
        return quicChannel.close()
                .addListener(future -> {
                    if (future.isSuccess()) {
                        observer.onSessionClosed(this, null);
                    } else {
                        observer.onSessionClosed(this, future.cause());
                    }
                });
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
}