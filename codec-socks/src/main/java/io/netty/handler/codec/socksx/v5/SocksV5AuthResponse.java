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
 * @see SocksV5AuthRequest
 * @see SocksV5AuthResponseDecoder
 */
public final class SocksV5AuthResponse extends SocksV5Response {
    private static final SocksV5SubnegotiationVersion SUBNEGOTIATION_VERSION =
            SocksV5SubnegotiationVersion.AUTH_PASSWORD;
    private final SocksV5AuthStatus authStatus;

    public SocksV5AuthResponse(SocksV5AuthStatus authStatus) {
        super(SocksV5ResponseType.AUTH);
        if (authStatus == null) {
            throw new NullPointerException("authStatus");
        }
        this.authStatus = authStatus;
    }

    /**
     * Returns the {@link SocksV5AuthStatus} of this {@link SocksV5AuthResponse}
     *
     * @return The {@link SocksV5AuthStatus} of this {@link SocksV5AuthResponse}
     */
    public SocksV5AuthStatus authStatus() {
        return authStatus;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(SUBNEGOTIATION_VERSION.byteValue());
        byteBuf.writeByte(authStatus.byteValue());
    }
}
