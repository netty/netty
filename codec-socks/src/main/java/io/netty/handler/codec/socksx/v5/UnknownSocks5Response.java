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
 * @see Socks5InitResponseDecoder
 * @see Socks5AuthResponseDecoder
 * @see Socks5CmdResponseDecoder
 */
public final class UnknownSocks5Response extends Socks5Response {

    public static final UnknownSocks5Response INSTANCE = new UnknownSocks5Response();

    private UnknownSocks5Response() {
        super(Socks5ResponseType.UNKNOWN);
    }

    @Override
    void encodeAsByteBuf(ByteBuf byteBuf) {
        // NOOP
    }
}
