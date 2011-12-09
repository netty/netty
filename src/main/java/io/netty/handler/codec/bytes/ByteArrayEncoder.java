/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.bytes;

import static io.netty.buffer.ChannelBuffers.wrappedBuffer;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MessageEvent;
import io.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.frame.LengthFieldPrepender;
import io.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * Encodes the requested array of bytes into a {@link ChannelBuffer}.
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
 * and then you can use an array of bytes instead of a {@link ChannelBuffer}
 * as a message:
 * <pre>
 * void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *     byte[] bytes = (byte[]) e.getMessage();
 *     ...
 * }
 * </pre>
 */
public class ByteArrayEncoder extends OneToOneEncoder {

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof byte[])) {
            return msg;
        }
        return wrappedBuffer((byte[]) msg);
    }

}
