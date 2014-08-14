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

import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksMessageType;
import io.netty.handler.codec.socksx.SocksProtocolVersion;
import io.netty.handler.codec.socksx.SocksRequest;

/**
 * An abstract class that defines a SocksRequest, providing common properties for
 * {@link SocksV4CmdRequest}.
 *
 * @see SocksV4CmdRequest
 * @see UnknownSocksV4Request
 */
public abstract class SocksV4Request extends SocksRequest {
    protected SocksV4Request() {
        super(SocksProtocolVersion.SOCKS4a);
    }
}
