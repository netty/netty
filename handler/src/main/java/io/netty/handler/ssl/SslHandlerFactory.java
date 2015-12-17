/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.ssl;

import io.netty.buffer.ByteBufAllocator;

/**
 * A factory for {@link SslHandler}s.
 *
 * @see SslContext
 * @see SniHandler
 */
public interface SslHandlerFactory {

    /**
     * Returns the {@link SslContext} that is used by this factory.
     *
     * @return the {@link SslContext}
     */
    SslContext sslContext();

    /**
     * Creates a new {@link SslHandler}.
     *
     * @return a new {@link SslHandler}
     */
    SslHandler newHandler(ByteBufAllocator alloc);

    /**
     * Creates a new {@link SslHandler} with advisory peer information.
     *
     * @param peerHost the non-authoritative name of the host
     * @param peerPort the non-authoritative port
     *
     * @return a new {@link SslHandler}
     */
    SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort);
}
