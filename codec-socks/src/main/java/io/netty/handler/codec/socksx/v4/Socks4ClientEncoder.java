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
 * Encodes a {@link Socks4CommandRequest} into a {@link ByteBuf}.
 */
@Sharable
public final class Socks4ClientEncoder extends MessageToByteEncoder<Socks4CommandRequest> {

    /**
     * The singleton instance of {@link Socks4ClientEncoder}
     */
    public static final Socks4ClientEncoder INSTANCE = new Socks4ClientEncoder();

    private static final byte[] IPv4_DOMAIN_MARKER = {0x00, 0x00, 0x00, 0x01};

    private Socks4ClientEncoder() { }

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks4CommandRequest msg, ByteBuf out) throws Exception {
        out.writeByte(msg.version().byteValue());
        out.writeByte(msg.type().byteValue());
        ByteBufUtil.writeShortBE(out, msg.dstPort());
        if (NetUtil.isValidIpV4Address(msg.dstAddr())) {
            out.writeBytes(NetUtil.createByteArrayFromIpAddressString(msg.dstAddr()));
            ByteBufUtil.writeAscii(out, msg.userId());
            out.writeByte(0);
        } else {
            out.writeBytes(IPv4_DOMAIN_MARKER);
            ByteBufUtil.writeAscii(out, msg.userId());
            out.writeByte(0);
            ByteBufUtil.writeAscii(out, msg.dstAddr());
            out.writeByte(0);
        }
    }
}
