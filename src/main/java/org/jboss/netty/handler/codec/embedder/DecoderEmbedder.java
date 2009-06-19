/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.embedder;

import static org.jboss.netty.channel.Channels.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.handler.codec.base64.Base64Decoder;
import org.jboss.netty.handler.codec.string.StringDecoder;

/**
 * A helper that wraps a decoder so that it can be used without doing actual
 * I/O in unit tests or higher level codecs.  For example, you can decode a
 * Base64-encoded {@link ChannelBuffer} with {@link Base64Decoder} and
 * {@link StringDecoder} without setting up the {@link ChannelPipeline} and
 * other mock objects by yourself:
 * <pre>
 * ChannelBuffer base64Data = ChannelBuffer.copiedBuffer("Zm9vYmFy", "ASCII");
 *
 * DecoderEmbedder&lt;String&gt; embedder = new DecoderEmbedder&lt;String&gt;(
 *         new Base64Decoder(), new StringDecoder());
 *
 * embedded.offer(base64Data);
 *
 * String decoded = embedded.poll();
 * assert decoded.equals("foobar");
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @see EncoderEmbedder
 */
public class DecoderEmbedder<T> extends AbstractCodecEmbedder<T> {

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

    public boolean offer(Object input) {
        fireMessageReceived(getChannel(), input);
        return !super.isEmpty();
    }
}
