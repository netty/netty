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
 * An unknown socks response.
 *
 * @see SocksV5InitResponseDecoder
 * @see SocksV5AuthResponseDecoder
 * @see SocksV5CmdResponseDecoder
 */
public final class UnknownSocksV5Response extends SocksV5Response {

    public UnknownSocksV5Response() {
        super(SocksV5ResponseType.UNKNOWN);
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        // NOOP
    }

    private static class UnknownSocksV5ResponseHolder {
        public static final UnknownSocksV5Response HOLDER_INSTANCE = new UnknownSocksV5Response();
    }

    public static UnknownSocksV5Response getInstance() {
        return UnknownSocksV5ResponseHolder.HOLDER_INSTANCE;
    }
}
