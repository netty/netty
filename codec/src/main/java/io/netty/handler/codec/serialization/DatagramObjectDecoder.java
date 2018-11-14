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

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * A decoder that decodes the content of the received {@link DatagramPacket} using the specified {@link ObjectDecoder}
 * decoder. It wraps the result in a new {@link AddressedEnvelope}<{@link DatagramPacket}, {@link InetSocketAddress}>.
 * <p>
 * This allows usage of the {@link ObjectDecoder} while still being able to access sender and receiver information of
 * the packet. Used in conjuntion with {@link DatagramObjectEncoder}.
 *
 * <pre><code>
 * {@link ChannelPipeline} pipeline = ...;
 * pipeline.addLast("udpDecoder", new {@link DatagramObjectDecoder}, (...));
 * </code></pre>
 */
public class DatagramObjectDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final ObjectDecoder objectDecoder;

    /**
     * Creates a new datagram decoder using the specified objectDecoder
     *
     * @param objectDecoder the {@link ObjectDecoder} to be used
     */
    public DatagramObjectDecoder(ObjectDecoder objectDecoder) {
        this.objectDecoder = objectDecoder;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        Object decoded = objectDecoder.decode(ctx, msg.content());
        out.add(new DefaultAddressedEnvelope<Object, InetSocketAddress>(decoded, msg.recipient(), msg.sender()));
    }
}
