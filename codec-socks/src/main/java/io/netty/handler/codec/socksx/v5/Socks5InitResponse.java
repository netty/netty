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
 * An socks init response.
 *
 * @see Socks5InitRequest
 * @see Socks5InitResponseDecoder
 */
public final class Socks5InitResponse extends Socks5Response {
    private final Socks5AuthScheme authScheme;

    public Socks5InitResponse(Socks5AuthScheme authScheme) {
        super(Socks5ResponseType.INIT);
        if (authScheme == null) {
            throw new NullPointerException("authScheme");
        }
        this.authScheme = authScheme;
    }

    /**
     * Returns the {@link Socks5AuthScheme} of this {@link Socks5InitResponse}
     *
     * @return The {@link Socks5AuthScheme} of this {@link Socks5InitResponse}
     */
    public Socks5AuthScheme authScheme() {
        return authScheme;
    }

    @Override
    void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(protocolVersion().byteValue());
        byteBuf.writeByte(authScheme.byteValue());
    }
}
