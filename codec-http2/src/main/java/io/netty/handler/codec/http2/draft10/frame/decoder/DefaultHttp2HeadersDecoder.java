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

package io.netty.handler.codec.http2.draft10.frame.decoder;

import static io.netty.handler.codec.http2.draft10.connection.Http2ConnectionUtil.DEFAULT_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.draft10.connection.Http2ConnectionUtil.DEFAULT_MAX_HEADER_SIZE;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http2.draft10.Http2Error;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.Http2Headers;

import java.io.IOException;

import com.twitter.hpack.Decoder;
import com.twitter.hpack.HeaderListener;

public class DefaultHttp2HeadersDecoder implements Http2HeadersDecoder {

    private final Decoder decoder;

    public DefaultHttp2HeadersDecoder() {
        decoder = new Decoder(DEFAULT_MAX_HEADER_SIZE, DEFAULT_HEADER_TABLE_SIZE);
    }

    @Override
    public void setHeaderTableSize(int size) throws Http2Exception {
        // TODO: can we throw away the decoder and create a new one?
    }

    @Override
    public Http2Headers decodeHeaders(ByteBuf headerBlock) throws Http2Exception {
        try {
            final Http2Headers.Builder headersBuilder = new Http2Headers.Builder();
            HeaderListener listener = new HeaderListener() {
                @Override
                public void emitHeader(byte[] key, byte[] value) {
                    headersBuilder.addHeader(key, value);
                }
            };

            decoder.decode(new ByteBufInputStream(headerBlock), listener);
            decoder.endHeaderBlock(listener);

            return headersBuilder.build();
        } catch (IOException e) {
            throw new Http2Exception(Http2Error.COMPRESSION_ERROR, e.getMessage());
        }
    }
}
