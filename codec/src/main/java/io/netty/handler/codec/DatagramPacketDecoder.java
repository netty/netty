/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.util.List;

/**
 * A decoder that decodes the content of the received {@link DatagramPacket} using
 * the specified {@link ByteBuf} decoder. E.g.,
 *
 * <pre><code>
 * {@link ChannelPipeline} pipeline = ...;
 * pipeline.addLast("udpDecoder", new {@link DatagramPacketDecoder}(new {@link ProtobufDecoder}(...));
 * </code></pre>
 */
public class DatagramPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final MessageToMessageDecoder<ByteBuf> decoder;

    /**
     * Create a {@link DatagramPacket} decoder using the specified {@link ByteBuf} decoder.
     *
     * @param decoder the specified {@link ByteBuf} decoder
     */
    public DatagramPacketDecoder(MessageToMessageDecoder<ByteBuf> decoder) {
        this.decoder = checkNotNull(decoder, "decoder");
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        if (msg instanceof DatagramPacket) {
            return decoder.acceptInboundMessage(((DatagramPacket) msg).content());
        }
        return false;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        decoder.decode(ctx, msg.content(), out);
    }
}
