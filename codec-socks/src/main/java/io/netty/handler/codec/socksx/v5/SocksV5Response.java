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

import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksMessageType;
import io.netty.handler.codec.socksx.SocksProtocolVersion;
import io.netty.handler.codec.socksx.SocksResponse;

/**
 * An abstract class that defines a SocksResponse, providing common properties for
 * {@link SocksV5InitResponse},
 * {@link SocksV5AuthResponse},
 * {@link SocksV5CmdResponse}
 * and {@link UnknownSocksV5Response}.
 *
 * @see SocksV5InitResponse
 * @see SocksV5AuthResponse
 * @see SocksV5CmdResponse
 * @see UnknownSocksV5Response
 */
public abstract class SocksV5Response extends SocksResponse {
    private final SocksV5ResponseType responseType;

    protected SocksV5Response(SocksV5ResponseType responseType) {
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
    public SocksV5ResponseType responseType() {
        return responseType;
    }
}
