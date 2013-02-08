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
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToMessageDecoder;

/**
 * Decodes a received {@link ByteBuf} into a
 * <a href="http://code.google.com/p/protobuf/">Google Protocol Buffers</a>
 * {@link Message} and {@link MessageLite}.  Please note that this decoder must
 * be used with a proper {@link ByteToMessageDecoder} such as {@link ProtobufVarint32FrameDecoder}
 * or {@link LengthFieldBasedFrameDecoder} if you are using a stream-based
 * transport such as TCP/IP.  A typical setup for TCP/IP would be:
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 *
 * // Decoders
 * pipeline.addLast("frameDecoder",
 *                  new {@link LengthFieldBasedFrameDecoder}(1048576, 0, 4, 0, 4));
 * pipeline.addLast("protobufDecoder",
 *                  new {@link ProtobufDecoder}(MyMessage.getDefaultInstance()));
 *
 * // Encoder
 * pipeline.addLast("frameEncoder", new {@link LengthFieldPrepender}(4));
 * pipeline.addLast("protobufEncoder", new {@link ProtobufEncoder}());
 * </pre>
 * and then you can use a {@code MyMessage} instead of a {@link ByteBuf}
 * as a message:
 * <pre>
 * void messageReceived({@link ChannelHandlerContext} ctx, MyMessage req) {
 *     MyMessage res = MyMessage.newBuilder().setText(
 *                               "Did you say '" + req.getText() + "'?").build();
 *     ch.write(res);
 * }
 * </pre>
 * @apiviz.landmark
 */
@Sharable
public class ProtobufDecoder extends MessageToMessageDecoder<ByteBuf> {

    private final MessageLite prototype;
    private final ExtensionRegistry extensionRegistry;

    /**
     * Creates a new instance.
     */
    public ProtobufDecoder(MessageLite prototype) {
        this(prototype, null);
    }

    public ProtobufDecoder(MessageLite prototype, ExtensionRegistry extensionRegistry) {
        if (prototype == null) {
            throw new NullPointerException("prototype");
        }
        this.prototype = prototype.getDefaultInstanceForType();
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        if (msg.hasArray()) {
            final int offset = msg.readerIndex();
            if (extensionRegistry == null) {
                return prototype.newBuilderForType().mergeFrom(
                        msg.array(), msg.arrayOffset() + offset, msg.readableBytes()).build();
            } else {
                return prototype.newBuilderForType().mergeFrom(
                        msg.array(), msg.arrayOffset() + offset, msg.readableBytes(), extensionRegistry).build();
            }
        } else {
            if (extensionRegistry == null) {
                return prototype.newBuilderForType().mergeFrom(
                        new ByteBufInputStream(msg)).build();
            } else {
                return prototype.newBuilderForType().mergeFrom(
                        new ByteBufInputStream(msg), extensionRegistry).build();
            }
        }
    }
}
