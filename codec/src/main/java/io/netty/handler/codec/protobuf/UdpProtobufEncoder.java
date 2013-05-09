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
package io.netty.handler.codec.protobuf;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.net.InetSocketAddress;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.channel.ChannelHandler.Sharable;

/**
 * Encodes the requested <a href="http://code.google.com/p/protobuf/">Google
 * Protocol Buffers</a> {@link com.google.protobuf.Message} and {@link MessageLite} into a
 * {@link DatagramPacket}.
 *
 * Since datagram packets require {@link InetSocketAddress} the outgoing protobuf must be wrapped
 * by {@link UdpProtobufMessage}.
 *
 * Since UDP packets are atomic, as long as your serialized protobuf is smaller than that maximum. But what is the
 * maximum?  Opinions vary, since the official docs say one thing, but realistic scenarios limit that number.
 * One loose consensus is that your datagram payload should be less than 512 bytes.  There is a lot of
 * discussion <a href="http://stackoverflow.com/questions/1098897/what-is-the-largest-safe-udp-packet-size-on-the-internet"
 * on stack overflow.</a>
 *
 */
@Sharable
public class UdpProtobufEncoder extends MessageToMessageEncoder<UdpProtobufEncoder.UdpProtobufMessage> {

    public static class UdpProtobufMessage {
        public final InetSocketAddress remoteAddress;
        public final MessageLiteOrBuilder message;

        public UdpProtobufMessage(InetSocketAddress remoteAddress, MessageLiteOrBuilder message) {
            this.remoteAddress = remoteAddress;
            this.message = message;
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, UdpProtobufEncoder.UdpProtobufMessage msg, MessageBuf<Object> out) throws Exception {
        if (msg.message instanceof MessageLite) {
            out.add(new DatagramPacket(wrappedBuffer(((MessageLite) msg.message).toByteArray()),
                    msg.remoteAddress));
        }
        if (msg.message instanceof MessageLite.Builder) {
            out.add(new DatagramPacket(wrappedBuffer(((MessageLite.Builder) msg.message).build().toByteArray()),
                    msg.remoteAddress));
        }
    }
}
