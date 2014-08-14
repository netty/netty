/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.ByteBuf;

/**
 * An socks auth response.
 *
 * @see Socks5AuthRequest
 * @see Socks5AuthResponseDecoder
 */
public final class Socks5AuthResponse extends Socks5Response {
    private static final Socks5SubnegotiationVersion SUBNEGOTIATION_VERSION =
            Socks5SubnegotiationVersion.AUTH_PASSWORD;
    private final Socks5AuthStatus authStatus;

    public Socks5AuthResponse(Socks5AuthStatus authStatus) {
        super(Socks5ResponseType.AUTH);
        if (authStatus == null) {
            throw new NullPointerException("authStatus");
        }
        this.authStatus = authStatus;
    }

    /**
     * Returns the {@link Socks5AuthStatus} of this {@link Socks5AuthResponse}
     *
     * @return The {@link Socks5AuthStatus} of this {@link Socks5AuthResponse}
     */
    public Socks5AuthStatus authStatus() {
        return authStatus;
    }

    @Override
    void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(SUBNEGOTIATION_VERSION.byteValue());
        byteBuf.writeByte(authStatus.byteValue());
    }
}
