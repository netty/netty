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
import io.netty.handler.codec.socksx.SocksRequest;

/**
 * An abstract class that defines a SocksRequest, providing common properties for
 * {@link SocksV5InitRequest},
 * {@link SocksV5AuthRequest},
 * {@link SocksV5CmdRequest} and
 * {@link UnknownSocksV5Request}.
 *
 * @see SocksV5InitRequest
 * @see SocksV5AuthRequest
 * @see SocksV5CmdRequest
 * @see UnknownSocksV5Request
 */
public abstract class SocksV5Request extends SocksRequest {
    private final SocksV5RequestType requestType;

    protected SocksV5Request(SocksV5RequestType requestType) {
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
    public SocksV5RequestType requestType() {
        return requestType;
    }
}
