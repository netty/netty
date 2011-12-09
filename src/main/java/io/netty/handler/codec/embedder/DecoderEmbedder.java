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
package io.netty.handler.codec.embedder;

import static io.netty.channel.Channels.*;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferFactory;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelUpstreamHandler;
import io.netty.handler.codec.base64.Base64Decoder;
import io.netty.handler.codec.string.StringDecoder;

/**
 * A helper that wraps a decoder so that it can be used without doing actual
 * I/O in unit tests or higher level codecs.  For example, you can decode a
 * Base64-encoded {@link ChannelBuffer} with {@link Base64Decoder} and
 * {@link StringDecoder} without setting up the {@link ChannelPipeline} and
 * other mock objects by yourself:
 * <pre>
 * {@link ChannelBuffer} base64Data = {@link ChannelBuffers}.copiedBuffer("Zm9vYmFy", CharsetUtil.US_ASCII);
 *
 * {@link DecoderEmbedder}&lt;String&gt; embedder = new {@link DecoderEmbedder}&lt;String&gt;(
 *         new {@link Base64Decoder}(), new {@link StringDecoder}());
 *
 * embedder.offer(base64Data);
 *
 * String decoded = embedder.poll();
 * assert decoded.equals("foobar");
 * </pre>
 *
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @apiviz.landmark
 * @see EncoderEmbedder
 */
public class DecoderEmbedder<E> extends AbstractCodecEmbedder<E> {

    /**
     * Creates a new embedder whose pipeline is composed of the specified
     * handlers.
     */
    public DecoderEmbedder(ChannelUpstreamHandler... handlers) {
        super(handlers);
    }

    /**
     * Creates a new embedder whose pipeline is composed of the specified
     * handlers.
     *
     * @param bufferFactory the {@link ChannelBufferFactory} to be used when
     *                      creating a new buffer.
     */
    public DecoderEmbedder(ChannelBufferFactory bufferFactory, ChannelUpstreamHandler... handlers) {
        super(bufferFactory, handlers);
    }

    @Override
    public boolean offer(Object input) {
        fireMessageReceived(getChannel(), input);
        return !super.isEmpty();
    }
}
