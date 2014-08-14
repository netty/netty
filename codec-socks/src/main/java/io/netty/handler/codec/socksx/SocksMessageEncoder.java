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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Encodes an {@link SocksMessage} into a {@link ByteBuf}.
 * {@link MessageToByteEncoder} implementation.
 * Use this with {@link SocksV4CmdRequest},
 * {@link SocksV4CmdRequest},
 * {@link SocksV5InitRequest},
 * {@link SocksV5InitResponse},
 * {@link SocksV5AuthRequest},
 * {@link SocksV5AuthResponse},
 * {@link SocksV5CmdRequest} and
 * {@link SocksV5CmdResponse}
 */
@ChannelHandler.Sharable
public class SocksMessageEncoder extends MessageToByteEncoder<SocksMessage> {
    private static final String name = "SOCKS_MESSAGE_ENCODER";

    @Override
    @SuppressWarnings("deprecation")
    protected void encode(ChannelHandlerContext ctx, SocksMessage msg, ByteBuf out) throws Exception {
        msg.encodeAsByteBuf(out);
    }

    private static class SocksMessageEncoderHolder {
        public static final SocksMessageEncoder HOLDER_INSTANCE = new SocksMessageEncoder();
    }

    public static SocksMessageEncoder getInstance() {
        return SocksMessageEncoderHolder.HOLDER_INSTANCE;
    }
}
