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
package io.netty.handler.codec.socksx.v4;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.socks.SocksMessage;
import io.netty.handler.codec.socksx.SocksProtocolVersion;
import io.netty.handler.codec.socksx.SocksResponse;

/**
 * An abstract class that defines a SocksResponse, providing common properties for
 * {@link Socks4CmdResponse}.
 *
 * @see Socks4CmdResponse
 * @see UnknownSocks4Response
 */
public abstract class Socks4Response extends SocksResponse {

    protected Socks4Response() {
        super(SocksProtocolVersion.SOCKS4a);
    }

    /**
     * We could have defined this method in {@link SocksMessage} as a protected method, but we did not,
     * because we do not want to expose this method to users.
     */
    abstract void encodeAsByteBuf(ByteBuf byteBuf);
}
