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
package io.netty.handler.codec.socks.v5;

import io.netty.handler.codec.socks.common.SocksMessage;
import io.netty.handler.codec.socks.common.SocksMessageType;
import io.netty.handler.codec.socks.common.SocksProtocolVersion;

/**
 * An abstract class that defines a SocksResponse, providing common properties for
 * {@link io.netty.handler.codec.socks.v5.SocksV5InitResponse},
 * {@link io.netty.handler.codec.socks.v5.SocksV5AuthResponse},
 * {@link io.netty.handler.codec.socks.v5.SocksV5CmdResponse}
 * and {@link io.netty.handler.codec.socks.v5.UnknownSocksV5Response}.
 *
 * @see io.netty.handler.codec.socks.v5.SocksV5InitResponse
 * @see io.netty.handler.codec.socks.v5.SocksV5AuthResponse
 * @see io.netty.handler.codec.socks.v5.SocksV5CmdResponse
 * @see io.netty.handler.codec.socks.v5.UnknownSocksV5Response
 */
public abstract class SocksV5Response extends SocksMessage {
    private final SocksV5ResponseType responseType;

    protected SocksV5Response(SocksV5ResponseType responseType) {
        super(SocksProtocolVersion.SOCKS5, SocksMessageType.RESPONSE);
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
    public SocksV5ResponseType responseType() {
        return responseType;
    }
}
