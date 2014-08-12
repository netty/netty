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
package io.netty.handler.codec.socks.v4;

import io.netty.handler.codec.socks.common.SocksMessage;
import io.netty.handler.codec.socks.common.SocksMessageType;
import io.netty.handler.codec.socks.common.SocksProtocolVersion;

/**
 * An abstract class that defines a SocksRequest, providing common properties for
 * {@link io.netty.handler.codec.socks.v4.SocksV4CmdRequest}.
 *
 * @see io.netty.handler.codec.socks.v4.SocksV4CmdRequest
 * @see io.netty.handler.codec.socks.v4.UnknownSocksV4Request
 */
public abstract class SocksV4Request extends SocksMessage {
    protected SocksV4Request() {
        super(SocksProtocolVersion.SOCKS4a, SocksMessageType.REQUEST);
    }
}
