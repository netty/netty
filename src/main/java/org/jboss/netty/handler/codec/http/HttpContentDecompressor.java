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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.handler.codec.compression.ZlibDecoder;
import org.jboss.netty.handler.codec.compression.ZlibWrapper;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;

/**
 * Decompresses an {@link HttpMessage} and an {@link HttpChunk} compressed in
 * {@code gzip} or {@code deflate} encoding.  Insert this handler after
 * {@link HttpMessageDecoder} in the {@link ChannelPipeline}:
 * <pre>
 * ChannelPipeline p = ...;
 * ...
 * p.addLast("decoder", new HttpRequestDecoder());
 * p.addLast("inflater", <b>new HttpContentDecomperssor()</b>);
 * ...
 * p.addLast("encoder", new HttpResponseEncoder());
 * p.addLast("handler", new HttpRequestHandler());
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class HttpContentDecompressor extends HttpContentDecoder {
    @Override
    protected DecoderEmbedder<ChannelBuffer> newDecoder(String contentEncoding) throws Exception {
        if ("gzip".equalsIgnoreCase(contentEncoding) || "x-gzip".equalsIgnoreCase(contentEncoding)) {
            return new DecoderEmbedder<ChannelBuffer>(new ZlibDecoder(ZlibWrapper.GZIP));
        } else if ("deflate".equalsIgnoreCase(contentEncoding) || "x-deflate".equalsIgnoreCase(contentEncoding)) {
            return new DecoderEmbedder<ChannelBuffer>(new ZlibDecoder(ZlibWrapper.ZLIB));
        }

        // 'identity' or unsupported
        return null;
    }
}
