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
import io.netty.handler.codec.quic.QuicStreamChannel;

/**
 * No-operation implementation of WebTransportObserver
 */
public class NoopWebTransportObserver implements WebTransportObserver {
    
    public static final NoopWebTransportObserver INSTANCE = new NoopWebTransportObserver();
    
    private NoopWebTransportObserver() {
        // Singleton
    }
    
    @Override
    public void onSessionEstablished(WebTransportSession session) {
        // No-op
    }
    
    @Override
    public void onSessionClosed(WebTransportSession session, Throwable cause) {
        // No-op
    }
    
    @Override
    public void onBidirectionalStreamCreated(QuicStreamChannel stream) {
        // No-op
    }
    
    @Override
    public void onUnidirectionalStreamCreated(QuicStreamChannel stream) {
        // No-op
    }
    
    @Override
    public void onStreamClosed(QuicStreamChannel stream, Throwable cause) {
        // No-op
    }
    
    @Override
    public void onDatagramSent(WebTransportSession session, ByteBuf data) {
        // No-op
    }
    
    @Override
    public void onDatagramReceived(WebTransportSession session, ByteBuf data) {
        // No-op
    }
    
    @Override
    public void onError(WebTransportSession session, Throwable cause) {
        // No-op
    }
}