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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;

import java.net.InetSocketAddress;

/**
 * Handle token related operations.
 */
public interface QuicTokenHandler {

    /**
     * Generate a new token for the given destination connection id and address. This token is written to {@code out}.
     * If no token should be generated and so no token validation should take place at all this method should return
     * {@code false}.
     *
     * @param out       {@link ByteBuf} into which the token will be written.
     * @param dcid      the destination connection id. The {@link ByteBuf#readableBytes()} will be at most
     *                  {@link Quic#MAX_CONN_ID_LEN}.
     * @param address   the {@link InetSocketAddress} of the sender.
     * @return          {@code true} if a token was written and so validation should happen, {@code false} otherwise.
     */
    boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address);

    /**
     * Validate the token and return the offset, {@code -1} is returned if the token is not valid.
     *
     * @param token     the {@link ByteBuf} that contains the token. The ownership is not transferred.
     * @param address   the {@link InetSocketAddress} of the sender.
     * @return          the start index after the token or {@code -1} if the token was not valid.
     */
    int validateToken(ByteBuf token, InetSocketAddress address);

    /**
     * Return the maximal token length.
     *
     * @return the maximal supported token length.
     */
    int maxTokenLength();
}
