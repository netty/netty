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

/**
 * WebTransport session interface
 */
public interface WebTransportSession {
    
    /**
     * Create a bidirectional stream
     * @return The future of the created stream
     */
    ChannelFuture createBidirectionalStream();
    
    /**
     * Create a unidirectional stream
     * @return The future of the created stream
     */
    ChannelFuture createUnidirectionalStream();
    
    /**
     * Send a datagram
     * @param data The datagram data
     * @return The future of the send operation
     */
    ChannelFuture sendDatagram(ByteBuf data);
    
    /**
     * Handle incoming datagram
     * @param data The received datagram data
     */
    void handleDatagram(ByteBuf data);
    
    /**
     * Close the session
     * @return The future of the close operation
     */
    ChannelFuture close();
    
    /**
     * Check if the session is active
     * @return true if the session is active, false otherwise
     */
    boolean isActive();
}