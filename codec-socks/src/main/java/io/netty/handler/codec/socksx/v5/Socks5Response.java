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
import io.netty.handler.codec.socks.SocksMessage;
import io.netty.handler.codec.socksx.SocksProtocolVersion;
import io.netty.handler.codec.socksx.SocksResponse;

/**
 * An abstract class that defines a SocksResponse, providing common properties for
 * {@link Socks5InitResponse},
 * {@link Socks5AuthResponse},
 * {@link Socks5CmdResponse}
 * and {@link UnknownSocks5Response}.
 *
 * @see Socks5InitResponse
 * @see Socks5AuthResponse
 * @see Socks5CmdResponse
 * @see UnknownSocks5Response
 */
public abstract class Socks5Response extends SocksResponse {
    private final Socks5ResponseType responseType;

    protected Socks5Response(Socks5ResponseType responseType) {
        super(SocksProtocolVersion.SOCKS5);
        if (responseType == null) {
            throw new NullPointerException("responseType");
        }
        this.responseType = responseType;
    }

    /**
     * Returns socks response type
     *
     * @return socks response type
     */
    public Socks5ResponseType responseType() {
        return responseType;
    }

    /**
     * We could have defined this method in {@link SocksMessage} as a protected method, but we did not,
     * because we do not want to expose this method to users.
     */
    abstract void encodeAsByteBuf(ByteBuf byteBuf);
}
