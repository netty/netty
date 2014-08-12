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

import io.netty.buffer.ByteBuf;

/**
 * An unknown socks request.
 *
 * @see SocksV5InitRequestDecoder
 * @see SocksV5AuthRequestDecoder
 * @see SocksV5CmdRequestDecoder
 */
public final class UnknownSocksV5Request extends SocksV5Request {

    public UnknownSocksV5Request() {
        super(SocksV5RequestType.UNKNOWN);
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        // NOOP
    }

    private static class UnknownSocksV5RequestHolder {
        public static final UnknownSocksV5Request HOLDER_INSTANCE = new UnknownSocksV5Request();
    }

    public static UnknownSocksV5Request getInstance() {
        return UnknownSocksV5RequestHolder.HOLDER_INSTANCE;
    }
}
