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
package io.netty.handler.codec.bytes;

import io.netty.buffer.BufType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundMessageHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

/**
 * Encodes the requested array of bytes into a {@link ByteBuf}.
 * A typical setup for TCP/IP would be:
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 *
 * // Decoders
 * pipeline.addLast("frameDecoder",
 *                  new {@link LengthFieldBasedFrameDecoder}(1048576, 0, 4, 0, 4));
 * pipeline.addLast("bytesDecoder",
 *                  new {@link ByteArrayDecoder}());
 *
 * // Encoder
 * pipeline.addLast("frameEncoder", new {@link LengthFieldPrepender}(4));
 * pipeline.addLast("bytesEncoder", new {@link ByteArrayEncoder}());
 * </pre>
 * and then you can use an array of bytes instead of a {@link ByteBuf}
 * as a message:
 * <pre>
 * void messageReceived({@link ChannelHandlerContext} ctx, byte[] bytes) {
 *     ...
 * }
 * </pre>
 */
public class ByteArrayEncoder extends ChannelOutboundMessageHandlerAdapter<byte[]> {

    private final BufType nextBufferType;

    public ByteArrayEncoder(BufType nextBufferType) {
        if (nextBufferType == null) {
            throw new NullPointerException("nextBufferType");
        }
        this.nextBufferType = nextBufferType;
    }

    @Override
    protected void flush(ChannelHandlerContext ctx, byte[] msg) throws Exception {
        if (msg.length == 0) {
            return;
        }

        switch (nextBufferType) {
            case BYTE:
                ctx.nextOutboundByteBuffer().writeBytes(msg);
                break;
            case MESSAGE:
                ctx.nextOutboundMessageBuffer().add(Unpooled.wrappedBuffer(msg));
                break;
            default:
                throw new Error();
        }
    }
}
