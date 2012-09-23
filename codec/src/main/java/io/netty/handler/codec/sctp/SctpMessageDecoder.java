/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.sctp;

import com.sun.nio.sctp.MessageInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SctpMessage;
import io.netty.handler.codec.MessageToMessageDecoder;

public class SctpMessageDecoder extends MessageToMessageDecoder<SctpMessage, ByteBuf> {
    private ByteBuf cumulation = Unpooled.EMPTY_BUFFER;

    @Override
    public ByteBuf decode(ChannelHandlerContext ctx, SctpMessage msg) throws Exception {
        ByteBuf byteBuf = cumulation = Unpooled.wrappedBuffer(cumulation, msg.getPayloadBuffer());
        if (msg.isComplete()) {
            cumulation = Unpooled.EMPTY_BUFFER;
            return byteBuf;
        } else {
            return null;
        }
    }
}
