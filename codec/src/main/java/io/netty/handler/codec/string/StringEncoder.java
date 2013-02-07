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
package io.netty.handler.codec.string;

import io.netty.buffer.BufType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundMessageHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.LineBasedFrameDecoder;

import java.nio.charset.Charset;

/**
 * Encodes the requested {@link String} into a {@link ByteBuf}.
 * A typical setup for a text-based line protocol in a TCP/IP socket would be:
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 *
 * // Decoders
 * pipeline.addLast("frameDecoder", new {@link LineBasedFrameDecoder}(80));
 * pipeline.addLast("stringDecoder", new {@link StringDecoder}(CharsetUtil.UTF_8));
 *
 * // Encoder
 * pipeline.addLast("stringEncoder", new {@link StringEncoder}(CharsetUtil.UTF_8));
 * </pre>
 * and then you can use a {@link String} instead of a {@link ByteBuf}
 * as a message:
 * <pre>
 * void messageReceived({@link ChannelHandlerContext} ctx, {@link String} msg) {
 *     ch.write("Did you say '" + msg + "'?\n");
 * }
 * </pre>
 * @apiviz.landmark
 */
@Sharable
public class StringEncoder extends ChannelOutboundMessageHandlerAdapter<CharSequence> {

    private final BufType nextBufferType;
    // TODO Use CharsetEncoder instead.
    private final Charset charset;

    /**
     * Creates a new instance with the current system character set.
     */
    public StringEncoder(BufType nextBufferType) {
        this(nextBufferType, Charset.defaultCharset());
    }

    /**
     * Creates a new instance with the specified character set.
     */
    public StringEncoder(BufType nextBufferType, Charset charset) {
        if (nextBufferType == null) {
            throw new NullPointerException("nextBufferType");
        }
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.nextBufferType = nextBufferType;
        this.charset = charset;
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        MessageBuf<Object> in = ctx.outboundMessageBuffer();
        MessageBuf<Object> msgOut = ctx.nextOutboundMessageBuffer();
        ByteBuf byteOut = ctx.nextOutboundByteBuffer();

        try {
            for (;;) {
                Object m = in.poll();
                if (m == null) {
                    break;
                }

                if (!(m instanceof CharSequence)) {
                    msgOut.add(m);
                    continue;
                }

                CharSequence s = (CharSequence) m;
                ByteBuf encoded = Unpooled.copiedBuffer(s, charset);

                switch (nextBufferType) {
                case BYTE:
                    byteOut.writeBytes(encoded);
                    break;
                case MESSAGE:
                    msgOut.add(encoded);
                    break;
                default:
                    throw new Error();
                }
            }
        } finally {
            ctx.flush(promise);
        }
    }
}
