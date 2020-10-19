/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.ObjectUtil;

/**
 * An abstract class that defines a SocksMessage, providing common properties for
 * {@link SocksRequest} and {@link SocksResponse}.
 *
 * @see SocksRequest
 * @see SocksResponse
 */

public abstract class SocksMessage {
    private final SocksMessageType type;
    private final SocksProtocolVersion protocolVersion = SocksProtocolVersion.SOCKS5;

    protected SocksMessage(SocksMessageType type) {
        this.type = ObjectUtil.checkNotNull(type, "type");
    }

    /**
     * Returns the {@link SocksMessageType} of this {@link SocksMessage}
     *
     * @return The {@link SocksMessageType} of this {@link SocksMessage}
     */
    public SocksMessageType type() {
        return type;
    }

    /**
     * Returns the {@link SocksProtocolVersion} of this {@link SocksMessage}
     *
     * @return The {@link SocksProtocolVersion} of this {@link SocksMessage}
     */
    public SocksProtocolVersion protocolVersion() {
        return protocolVersion;
    }

    /**
     * @deprecated Do not use; this method was intended for an internal use only.
     */
    @Deprecated
    public abstract void encodeAsByteBuf(ByteBuf byteBuf);
}
