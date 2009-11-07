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
package org.jboss.netty.handler.codec.embedder;

import static org.jboss.netty.channel.Channels.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.base64.Base64Encoder;
import org.jboss.netty.handler.codec.string.StringEncoder;

/**
 * A helper that wraps an encoder so that it can be used without doing actual
 * I/O in unit tests or higher level codecs.  For example, you can encode a
 * {@link String} into a Base64-encoded {@link ChannelBuffer} with
 * {@link Base64Encoder} and {@link StringEncoder} without setting up the
 * {@link ChannelPipeline} and other mock objects by yourself:
 * <pre>
 * String data = "foobar";
 *
 * EncoderEmbedder&lt;ChannelBuffer&gt; embedder = new EncoderEmbedder&lt;ChannelBuffer&gt;(
 *         new Base64Encoder(), new StringEncoder());
 *
 * embedded.offer(data);
 *
 * ChannelBuffer encoded = embedded.poll();
 * assert encoded.toString("ASCII").equals("Zm9vYmFy");
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @see DecoderEmbedder
 */
public class EncoderEmbedder<E> extends AbstractCodecEmbedder<E> {

    /**
     * Creates a new embedder whose pipeline is composed of the specified
     * handlers.
     */
    public EncoderEmbedder(ChannelDownstreamHandler... handlers) {
        super(handlers);
    }

    /**
     * Creates a new embedder whose pipeline is composed of the specified
     * handlers.
     *
     * @param bufferFactory the {@link ChannelBufferFactory} to be used when
     *                      creating a new buffer.
     */
    public EncoderEmbedder(ChannelBufferFactory bufferFactory, ChannelDownstreamHandler... handlers) {
        super(bufferFactory, handlers);
    }

    public boolean offer(Object input) {
        write(getChannel(), input).setSuccess();
        return !isEmpty();
    }
}
