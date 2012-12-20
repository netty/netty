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
package org.jboss.netty.handler.codec.socks;

/**
 * An abstract class that defines a SocksRequest, providing common properties for
 * {@link SocksInitRequest}, {@link SocksAuthRequest}, {@link SocksCmdRequest} and {@link UnknownSocksRequest}.
 *
 * @see {@link SocksInitRequest}
 * @see {@link SocksAuthRequest}
 * @see {@link SocksCmdRequest}
 * @see {@link UnknownSocksRequest}
 */
public abstract class SocksRequest extends SocksMessage {
    private final SocksRequestType socksRequestType;

    protected SocksRequest(SocksRequestType socksRequestType) {
        super(MessageType.REQUEST);
        if (socksRequestType == null) {
            throw new NullPointerException("socksRequestType");
        }
        this.socksRequestType = socksRequestType;
    }

    /**
     * Returns socks request type
     *
     * @return socks request type
     */
    public SocksRequestType getSocksRequestType() {
        return socksRequestType;
    }

    /**
     * Type of socks request
     */
    public enum SocksRequestType {
        INIT,
        AUTH,
        CMD,
        UNKNOWN
    }
}
