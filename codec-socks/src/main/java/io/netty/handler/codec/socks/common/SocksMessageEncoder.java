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
package io.netty.handler.codec.socks.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Encodes an {@link io.netty.handler.codec.socks.common.SocksMessage} into a {@link ByteBuf}.
 * {@link MessageToByteEncoder} implementation.
 * Use this with {@link io.netty.handler.codec.socks.v4.SocksV4CmdRequest},
 * {@link io.netty.handler.codec.socks.v4.SocksV4CmdRequest},
 * {@link io.netty.handler.codec.socks.v5.SocksV5InitRequest},
 * {@link io.netty.handler.codec.socks.v5.SocksV5InitResponse},
 * {@link io.netty.handler.codec.socks.v5.SocksV5AuthRequest},
 * {@link io.netty.handler.codec.socks.v5.SocksV5AuthResponse},
 * {@link io.netty.handler.codec.socks.v5.SocksV5CmdRequest} and
 * {@link io.netty.handler.codec.socks.v5.SocksV5CmdResponse}
 */
@ChannelHandler.Sharable
public class SocksMessageEncoder extends MessageToByteEncoder<SocksMessage> {
    private static final String name = "SOCKS_MESSAGE_ENCODER";

    /**
     * @deprecated Will be removed at the next minor version bump.
     */
    @Deprecated
    public static String getName() {
        return name;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void encode(ChannelHandlerContext ctx, SocksMessage msg, ByteBuf out) throws Exception {
        msg.encodeAsByteBuf(out);
    }
}
