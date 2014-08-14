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
import io.netty.handler.codec.socksx.SocksRequest;

/**
 * An abstract class that defines a SocksRequest, providing common properties for
 * {@link Socks5InitRequest},
 * {@link Socks5AuthRequest},
 * {@link Socks5CmdRequest} and
 * {@link UnknownSocks5Request}.
 *
 * @see Socks5InitRequest
 * @see Socks5AuthRequest
 * @see Socks5CmdRequest
 * @see UnknownSocks5Request
 */
public abstract class Socks5Request extends SocksRequest {
    private final Socks5RequestType requestType;

    protected Socks5Request(Socks5RequestType requestType) {
        super(SocksProtocolVersion.SOCKS5);
        if (requestType == null) {
            throw new NullPointerException("requestType");
        }
        this.requestType = requestType;
    }

    /**
     * Returns socks request type
     *
     * @return socks request type
     */
    public Socks5RequestType requestType() {
        return requestType;
    }

    /**
     * We could have defined this method in {@link SocksMessage} as a protected method, but we did not,
     * because we do not want to expose this method to users.
     */
    abstract void encodeAsByteBuf(ByteBuf byteBuf);
}
