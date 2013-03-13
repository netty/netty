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
package org.jboss.netty.handler.codec.protobuf;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

/**
 * Decodes a received {@link ChannelBuffer} into a
 * <a href="http://code.google.com/p/protobuf/">Google Protocol Buffers</a>
 * {@link Message} and {@link MessageLite}.  Please note that this decoder must
 * be used with a proper {@link FrameDecoder} such as {@link ProtobufVarint32FrameDecoder}
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
 * and then you can use a {@code MyMessage} instead of a {@link ChannelBuffer}
 * as a message:
 * <pre>
 * void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *     MyMessage req = (MyMessage) e.getMessage();
 *     MyMessage res = MyMessage.newBuilder().setText(
 *                               "Did you say '" + req.getText() + "'?").build();
 *     ch.write(res);
 * }
 * </pre>
 *
 * @apiviz.landmark
 */
@Sharable
public class ProtobufDecoder extends OneToOneDecoder {
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
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof ChannelBuffer)) {
            return msg;
        }

        ChannelBuffer buf = (ChannelBuffer) msg;
        final byte[] array;
        final int offset;
        final int length = buf.readableBytes();

        if (buf.hasArray()) {
            array = buf.array();
            offset = buf.arrayOffset() + buf.readerIndex();
        } else {
            array = new byte[length];
            buf.getBytes(buf.readerIndex(), array, 0, length);
            offset = 0;
        }

        if (extensionRegistry == null) {
            if (HAS_PARSER) {
                return prototype.getParserForType().parseFrom(array, offset, length);
            } else {
                return prototype.newBuilderForType().mergeFrom(array, offset, length).build();
            }
        } else {
            if (HAS_PARSER) {
                return prototype.getParserForType().parseFrom(array, offset, length, extensionRegistry);
            } else {
                return prototype.newBuilderForType().mergeFrom(array, offset, length, extensionRegistry).build();
            }
        }
    }
}
