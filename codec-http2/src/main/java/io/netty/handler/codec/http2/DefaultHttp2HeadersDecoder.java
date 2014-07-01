/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http2.Http2Error.COMPRESSION_ERROR;
import static io.netty.util.CharsetUtil.UTF_8;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

import java.io.IOException;

import com.twitter.hpack.Decoder;
import com.twitter.hpack.HeaderListener;

public class DefaultHttp2HeadersDecoder implements Http2HeadersDecoder {

    private final Decoder decoder;

    public DefaultHttp2HeadersDecoder() {
        this(DEFAULT_MAX_HEADER_SIZE, DEFAULT_HEADER_TABLE_SIZE);
    }

    public DefaultHttp2HeadersDecoder(int maxHeaderSize, int maxHeaderTableSize) {
        decoder = new Decoder(maxHeaderSize, maxHeaderTableSize);
    }

    @Override
    public void maxHeaderTableSize(int size) {
        decoder.setMaxHeaderTableSize(size);
    }

    @Override
    public int maxHeaderTableSize() {
        return decoder.getMaxHeaderTableSize();
    }

    @Override
    public Http2Headers decodeHeaders(ByteBuf headerBlock) throws Http2Exception {
        try {
            final DefaultHttp2Headers.Builder headersBuilder = new DefaultHttp2Headers.Builder();
            HeaderListener listener = new HeaderListener() {
                @Override
                public void emitHeader(byte[] key, byte[] value, boolean sensitive) {
                    headersBuilder.add(new String(key, UTF_8), new String(value, UTF_8));
                }
            };

            decoder.decode(new ByteBufInputStream(headerBlock), listener);
            boolean truncated = decoder.endHeaderBlock(listener);
            if (truncated) {
                // TODO: what's the right thing to do here?
            }

            return headersBuilder.build();
        } catch (IOException e) {
            throw new Http2Exception(COMPRESSION_ERROR, e.getMessage());
        }
    }
}
