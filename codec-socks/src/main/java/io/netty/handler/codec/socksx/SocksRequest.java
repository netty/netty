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
package io.netty.handler.codec.socksx;



/**
 * An abstract class that defines a SocksRequest, providing common properties for
 * {@link SocksV5InitRequest},
 * {@link SocksV5AuthRequest},
 * {@link SocksV5CmdRequest}
 * and {@link UnknownSocksV5Request}.
 *
 * @see io.netty.handler.codec.socks.SocksInitRequest
 * @see io.netty.handler.codec.socks.SocksAuthRequest
 * @see io.netty.handler.codec.socks.SocksCmdRequest
 * @see io.netty.handler.codec.socks.UnknownSocksRequest
 */
public abstract class SocksRequest extends SocksMessage {

    protected SocksRequest(SocksProtocolVersion protocolVersion) {
        super(protocolVersion, SocksMessageType.REQUEST);
    }
}
