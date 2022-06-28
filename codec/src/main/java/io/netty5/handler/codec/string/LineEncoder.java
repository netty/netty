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

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.LineBasedFrameDecoder;
import io.netty5.handler.codec.MessageToMessageEncoder;
import io.netty5.util.CharsetUtil;
import io.netty5.util.internal.SilentDispose;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Apply a line separator to the requested {@link String} and encode it into a {@link Buffer}.
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
 * and then you can use a {@link String} instead of a {@link Buffer}
 * as a message:
 * <pre>
 * void channelRead({@link ChannelHandlerContext} ctx, {@link String} msg) {
 *     ch.write("Did you say '" + msg + "'?");
 * }
 * </pre>
 */
public class LineEncoder extends MessageToMessageEncoder<CharSequence> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(LineEncoder.class);

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
    public boolean isSharable() {
        return true;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, CharSequence msg, List<Object> out) throws Exception {
        CharsetEncoder encoder = CharsetUtil.encoder(charset);
        int size = (int) (msg.length() * encoder.maxBytesPerChar() + lineSeparator.length);
        Buffer buf = ctx.bufferAllocator().allocate(size);
        assert buf.countWritableComponents() == 1;
        boolean release = true;
        CharBuffer chars = CharBuffer.wrap(msg);
        try (var iterator = buf.forEachWritable()) {
            var component = requireNonNull(iterator.first(), "writable component");
            ByteBuffer byteBuffer = component.writableBuffer();
            int start = byteBuffer.position();
            CoderResult result = encoder.encode(chars, byteBuffer, true);
            if (!result.isUnderflow()) {
                result.throwException();
            }
            encoder.flush(byteBuffer);
            if (!result.isUnderflow()) {
                result.throwException();
            }
            component.skipWritableBytes(byteBuffer.position() - start);
            release = false;
        } finally {
            if (release) {
                SilentDispose.dispose(buf, logger);
            }
        }
        buf.writeBytes(lineSeparator);
        out.add(buf);
    }
}
