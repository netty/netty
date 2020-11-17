/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;

import java.net.InetSocketAddress;

/**
 * Handle token related operations.
 */
public interface QuicTokenHandler {

    /**
     * Generate a new token for the given destination connection id and address. This token is written to {@code out}.
     * If no token should be generated and so no token validation should take place at all this method should return
     * {@link false}.
     */
    boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address);

    /**
     * Return the given toke and return the offset, {@code -1} is returned if the token is not valid.
     */
    int validateToken(ByteBuf token, InetSocketAddress address);

    /**
     * Return the maximal token length.
     */
    int maxTokenLength();
}
