/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.codec.serialization;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * An encoder which serializes an {@link AddressedEnvelope} object into an {@link DatagramPacket}, while keeping the
 * information about sender and receiver address. To be used with {@link DatagramObjectDecoder}.
 * <p>
 * Please note that the serialized form this encoder produces is not compatible with the standard {@link
 * ObjectInputStream}.  Please use {@link ObjectDecoder} or {@link ObjectDecoderInputStream} to ensure the
 * interoperability with this encoder.
 */
@Sharable
public class DatagramObjectEncoder<M extends Serializable>
        extends MessageToMessageEncoder<AddressedEnvelope<M, InetSocketAddress>> {
    private final ObjectEncoder objectEncoder;

    /**
     * Creates a new datagram encoder with the {@link ObjectEncoder}
     */
    public DatagramObjectEncoder() {
        this(new ObjectEncoder());
    }

    /**
     * Creates a new datagram encoder with a custom implementation of {@link ObjectEncoder}
     *
     * @param objectEncoder the {@link ObjectEncoder} to be used
     */
    public DatagramObjectEncoder(ObjectEncoder objectEncoder) {
        this.objectEncoder = objectEncoder;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, AddressedEnvelope<M, InetSocketAddress> msg, List<Object> out)
            throws Exception {
        ByteBuf buf = ctx.alloc().buffer();
        objectEncoder.encode(ctx, msg.content(), buf);
        out.add(new DatagramPacket(buf, msg.recipient(), msg.sender()));
    }
}
