/*
 * Copyright 2015 The Netty Project
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
package io.netty.example.udt.file;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.CharsetUtil;

import java.util.List;

/**
 * Encodes a {@link UdtFileMessage} into {@link ByteBuf}.
 */
public class UdtFileResponseEncoder extends MessageToMessageEncoder<Object> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        if (msg instanceof ByteBuf) {
            if (((ByteBuf) msg).readableBytes() > 0) {
                out.add(((ByteBuf) msg).retain());
            } else {
                out.add(Unpooled.EMPTY_BUFFER);
            }
            return;
        }

        if (msg instanceof UdtFileMessage) {
            out.add(encodeHeader(ctx, (UdtFileMessage) msg));
            return;
        }
    }

    private ByteBuf encodeHeader(ChannelHandlerContext ctx, UdtFileMessage msg) {
        ByteBuf buf = ctx.alloc().buffer(10);
        buf.writeByte(msg.magic());
        buf.writeByte(msg.opcode());
        buf.writeInt(msg.messageLength());
        buf.writeInt(msg.fileLength());
        buf.writeBytes((msg.message()).getBytes(CharsetUtil.UTF_8));
        return buf;
    }
}
