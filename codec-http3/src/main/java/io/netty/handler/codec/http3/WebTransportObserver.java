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
 * WebTransport observer interface
 */
public interface WebTransportObserver {
    
    /**
     * Called when a WebTransport session is established
     * @param session The established session
     */
    void onSessionEstablished(WebTransportSession session);
    
    /**
     * Called when a WebTransport session is closed
     * @param session The closed session
     * @param cause The cause of the session closure (may be null)
     */
    void onSessionClosed(WebTransportSession session, Throwable cause);
    
    /**
     * Called when a bidirectional stream is created
     * @param stream The created stream
     */
    void onBidirectionalStreamCreated(QuicStreamChannel stream);
    
    /**
     * Called when a unidirectional stream is created
     * @param stream The created stream
     */
    void onUnidirectionalStreamCreated(QuicStreamChannel stream);
    
    /**
     * Called when a stream is closed
     * @param stream The closed stream
     * @param cause The cause of the stream closure (may be null)
     */
    void onStreamClosed(QuicStreamChannel stream, Throwable cause);
    
    /**
     * Called when a datagram is sent
     * @param session The session that sent the datagram
     * @param data The sent datagram data
     */
    void onDatagramSent(WebTransportSession session, ByteBuf data);
    
    /**
     * Called when a datagram is received
     * @param session The session that received the datagram
     * @param data The received datagram data
     */
    void onDatagramReceived(WebTransportSession session, ByteBuf data);
    
    /**
     * Called when an error occurs
     * @param session The session that encountered the error
     * @param cause The error cause
     */
    void onError(WebTransportSession session, Throwable cause);
}