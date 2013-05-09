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

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import static io.netty.channel.ChannelHandler.Sharable;

/**
 * Decodes a received {@link DatagramPacket} into a
 * <a href="http://code.google.com/p/protobuf/">Google Protocol Buffers</a>
 * {@link com.google.protobuf.Message} and {@link com.google.protobuf.MessageLite}.
 *
 * There is no need for a framing decoder because UDP datagram packets arrive in their entirety.
 */
@Sharable
public class UdpProtobufDecoder extends MessageToMessageDecoder<DatagramPacket> {
    private static final boolean HAS_PARSER;

    static {
        boolean hasParser = false;
        try {
            // MessageLite.getParsetForType() is not available until protobuf 2.5.0.
            MessageLite.class.getDeclaredMethod("getParserForType");
            hasParser = true;
        } catch (Throwable t) {
            // Ignore
        }

        HAS_PARSER = hasParser;
    }

    private final MessageLite prototype;
    private final ExtensionRegistry extensionRegistry;

    public UdpProtobufDecoder(MessageLite prototype) {
        this(prototype, null);
    }

    public UdpProtobufDecoder(MessageLite prototype, ExtensionRegistry extensionRegistry) {
        if (prototype == null) {
            throw new NullPointerException("prototype");
        }
        this.prototype = prototype.getDefaultInstanceForType();
        this.extensionRegistry = extensionRegistry;

    }
    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket dgram, MessageBuf<Object> out) throws Exception {
        ByteBuf msg = dgram.content();

        final byte[] array;
        final int offset;
        final int length = msg.readableBytes();
        if (msg.hasArray()) {
            array = msg.array();
            offset = msg.arrayOffset() + msg.readerIndex();
        } else {
            array = new byte[length];
            msg.getBytes(msg.readerIndex(), array, 0, length);
            offset = 0;
        }

        if (extensionRegistry == null) {
            if (HAS_PARSER) {
                out.add(prototype.getParserForType().parseFrom(array, offset, length));
            } else {
                out.add(prototype.newBuilderForType().mergeFrom(array, offset, length).build());
            }
        } else {
            if (HAS_PARSER) {
                out.add(prototype.getParserForType().parseFrom(array, offset, length, extensionRegistry));
            } else {
                out.add(prototype.newBuilderForType().mergeFrom(array, offset, length, extensionRegistry).build());
            }
        }
    }
}
