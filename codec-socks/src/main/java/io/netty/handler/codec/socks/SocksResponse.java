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
package io.netty.handler.codec.socks;

/**
 * An abstract class that defines a SocksResponse, providing common properties for
 * {@link SocksInitResponse}, {@link SocksAuthResponse}, {@link SocksCmdResponse} and {@link UnknownSocksResponse}.
 *
 * @see SocksInitResponse
 * @see SocksAuthResponse
 * @see SocksCmdResponse
 * @see UnknownSocksResponse
 */
public abstract class SocksResponse extends SocksMessage {
    private final SocksResponseType socksResponseType;

    protected SocksResponse(SocksResponseType socksResponseType) {
        super(MessageType.RESPONSE);
        if (socksResponseType == null) {
            throw new NullPointerException("socksResponseType");
        }
        this.socksResponseType = socksResponseType;
    }

    /**
     * Returns socks response type
     *
     * @return socks response type
     */
    public SocksResponseType getSocksResponseType() {
        return socksResponseType;
    }

    /**
     * Type of socks response
     */
    public enum SocksResponseType {
        INIT,
        AUTH,
        CMD,
        UNKNOWN
    }
}
