/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.socksx.v4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.NetUtil;

/**
 * Encodes a {@link Socks4CommandResponse} into a {@link ByteBuf}.
 */
@Sharable
public final class Socks4ServerEncoder extends MessageToByteEncoder<Socks4CommandResponse> {

    public static final Socks4ServerEncoder INSTANCE = new Socks4ServerEncoder();

    private static final byte[] IPv4_HOSTNAME_ZEROED = { 0x00, 0x00, 0x00, 0x00 };

    private Socks4ServerEncoder() { }

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks4CommandResponse msg, ByteBuf out) throws Exception {
        out.writeByte(0);
        out.writeByte(msg.status().byteValue());
        ByteBufUtil.writeShortBE(out, msg.dstPort());
        out.writeBytes(msg.dstAddr() == null? IPv4_HOSTNAME_ZEROED
                                            : NetUtil.createByteArrayFromIpAddressString(msg.dstAddr()));
    }
}
