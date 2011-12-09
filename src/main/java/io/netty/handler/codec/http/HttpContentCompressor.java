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
package io.netty.handler.codec.http;

import io.netty.buffer.ChannelBuffer;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.embedder.EncoderEmbedder;

/**
 * Compresses an {@link HttpMessage} and an {@link HttpChunk} in {@code gzip} or
 * {@code deflate} encoding while respecting the {@code "Accept-Encoding"} header.
 * If there is no matching encoding, no compression is done.  For more
 * information on how this handler modifies the message, please refer to
 * {@link HttpContentEncoder}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
public class HttpContentCompressor extends HttpContentEncoder {

    private final int compressionLevel;

    /**
     * Creates a new handler with the default compression level (<tt>6</tt>).
     */
    public HttpContentCompressor() {
        this(6);
    }

    /**
     * Creates a new handler with the specified compression level.
     *
     * @param compressionLevel
     *        {@code 1} yields the fastest compression and {@code 9} yields the
     *        best compression.  {@code 0} means no compression.  The default
     *        compression level is {@code 6}.
     */
    public HttpContentCompressor(int compressionLevel) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel +
                    " (expected: 0-9)");
        }
        this.compressionLevel = compressionLevel;
    }

    @Override
    protected Result beginEncode(HttpMessage msg, String acceptEncoding) throws Exception {
        String contentEncoding = msg.getHeader(HttpHeaders.Names.CONTENT_ENCODING);
        if (contentEncoding != null &&
            !HttpHeaders.Values.IDENTITY.equalsIgnoreCase(contentEncoding)) {
            return null;
        }
                
        ZlibWrapper wrapper = determineWrapper(acceptEncoding);
        if (wrapper == null) {
            return null;
        }

        String targetContentEncoding;
        switch (wrapper) {
        case GZIP:
            targetContentEncoding = "gzip";
            break;
        case ZLIB:
            targetContentEncoding = "deflate";
            break;
        default:
            throw new Error();
        }

        return new Result(
                targetContentEncoding,
                new EncoderEmbedder<ChannelBuffer>(
                        new ZlibEncoder(wrapper, compressionLevel)));
    }

    private ZlibWrapper determineWrapper(String acceptEncoding) {
        // FIXME: Use the Q value.
        if (acceptEncoding.indexOf("gzip") >= 0) {
            return ZlibWrapper.GZIP;
        }
        if (acceptEncoding.indexOf("deflate") >= 0) {
            return ZlibWrapper.ZLIB;
        }
        return null;
    }
}
