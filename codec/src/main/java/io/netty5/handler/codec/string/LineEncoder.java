/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.string;

import static java.util.Objects.requireNonNull;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufUtil;
import io.netty5.channel.ChannelHandler.Sharable;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.LineBasedFrameDecoder;
import io.netty5.handler.codec.MessageToMessageEncoder;
import io.netty5.util.CharsetUtil;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Apply a line separator to the requested {@link String} and encode it into a {@link ByteBuf}.
 * A typical setup for a text-based line protocol in a TCP/IP socket would be:
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 *
 * // Decoders
 * pipeline.addLast("frameDecoder", new {@link LineBasedFrameDecoder}(80));
 * pipeline.addLast("stringDecoder", new {@link StringDecoder}(CharsetUtil.UTF_8));
 *
 * // Encoder
 * pipeline.addLast("lineEncoder", new {@link LineEncoder}(LineSeparator.UNIX, CharsetUtil.UTF_8));
 * </pre>
 * and then you can use a {@link String} instead of a {@link ByteBuf}
 * as a message:
 * <pre>
 * void channelRead({@link ChannelHandlerContext} ctx, {@link String} msg) {
 *     ch.write("Did you say '" + msg + "'?");
 * }
 * </pre>
 */
@Sharable
public class LineEncoder extends MessageToMessageEncoder<CharSequence> {

    private final Charset charset;
    private final byte[] lineSeparator;

    /**
     * Creates a new instance with the current system line separator and UTF-8 charset encoding.
     */
    public LineEncoder() {
        this(LineSeparator.DEFAULT, CharsetUtil.UTF_8);
    }

    /**
     * Creates a new instance with the specified line separator and UTF-8 charset encoding.
     */
    public LineEncoder(LineSeparator lineSeparator) {
        this(lineSeparator, CharsetUtil.UTF_8);
    }

    /**
     * Creates a new instance with the specified character set.
     */
    public LineEncoder(Charset charset) {
        this(LineSeparator.DEFAULT, charset);
    }

    /**
     * Creates a new instance with the specified line separator and character set.
     */
    public LineEncoder(LineSeparator lineSeparator, Charset charset) {
        this.charset = requireNonNull(charset, "charset");
        this.lineSeparator = requireNonNull(lineSeparator, "lineSeparator").value().getBytes(charset);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, CharSequence msg, List<Object> out) throws Exception {
        ByteBuf buffer = ByteBufUtil.encodeString(ctx.alloc(), CharBuffer.wrap(msg), charset, lineSeparator.length);
        buffer.writeBytes(lineSeparator);
        out.add(buffer);
    }
}
