/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.net.SocketAddress;
import java.util.List;

@ChannelHandler.Sharable
public class DnsMessageFrameDecoder extends MessageToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, Object msg, List out) throws Exception {
        SocketAddress recipient;
        SocketAddress sender;
        ByteBuf payload;
        int payloadSize;
        int readerIndex;
        AddressedEnvelope<ByteBuf, SocketAddress> r;
        if (msg instanceof DatagramPacket) {
            out.add(((DatagramPacket) msg).retain());
        } else if (msg instanceof ByteBuf) {
            ((ByteBuf) msg).resetReaderIndex();
            payloadSize = ((ByteBuf) msg).readUnsignedShort();
            readerIndex = ((ByteBuf) msg).readerIndex();
            if (payloadSize > ((ByteBuf) msg).readableBytes()) {
                return;
            }
            recipient = ctx.channel().localAddress();
            sender = ctx.channel().remoteAddress();
            payload = ((ByteBuf) msg).copy(readerIndex, payloadSize).retain();
            r = new DefaultAddressedEnvelope<ByteBuf, SocketAddress>(payload, recipient, sender);
            out.add(r);
        } else {
            // Error
            throw new Error();
        }
    }
}
