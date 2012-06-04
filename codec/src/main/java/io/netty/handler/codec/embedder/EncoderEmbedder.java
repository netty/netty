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
package io.netty.handler.codec.embedder;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.base64.Base64Encoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

/**
 * A helper that wraps an encoder so that it can be used without doing actual
 * I/O in unit tests or higher level codecs.  For example, you can encode a
 * {@link String} into a Base64-encoded {@link ChannelBuffer} with
 * {@link Base64Encoder} and {@link StringEncoder} without setting up the
 * {@link ChannelPipeline} and other mock objects by yourself:
 * <pre>
 * String data = "foobar";
 *
 * {@link EncoderEmbedder}&lt;{@link ChannelBuffer}&gt; embedder = new {@link EncoderEmbedder}&lt;{@link ChannelBuffer}&gt;(
 *         new {@link Base64Encoder}(), new {@link StringEncoder}());
 *
 * embedder.offer(data);
 *
 * {@link ChannelBuffer} encoded = embedder.poll();
 * assert encoded.toString({@link CharsetUtil}.US_ASCII).equals("Zm9vYmFy");
 * </pre>
 * @apiviz.landmark
 * @see DecoderEmbedder
 */
public class EncoderEmbedder<E> extends AbstractCodecEmbedder<E> {

    /**
     * Creates a new embedder whose pipeline is composed of the specified
     * handlers.
     */
    public EncoderEmbedder(ChannelHandler... handlers) {
        super(handlers);
    }

    @Override
    public boolean offer(Object input) {
        channel().write(input);
        return !isEmpty();
    }

    @Override
    protected CodecException newCodecException(Throwable t) {
        return new EncoderException(t);
    }
}
