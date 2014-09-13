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
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.AsciiString;

import java.io.IOException;
import java.io.InputStream;

import com.twitter.hpack.Decoder;
import com.twitter.hpack.HeaderListener;

public class DefaultHttp2HeadersDecoder implements Http2HeadersDecoder {

    private final Decoder decoder;
    private int maxHeaderListSize = Integer.MAX_VALUE;

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
    public void maxHeaderListSize(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("maxHeaderListSize must be >= 0: " + max);
        }
        maxHeaderListSize = max;
    }

    @Override
    public int maxHeaderListSize() {
        return maxHeaderListSize;
    }

    @Override
    public Http2Headers decodeHeaders(ByteBuf headerBlock) throws Http2Exception {
        InputStream in = new ByteBufInputStream(headerBlock);
        try {
            final Http2Headers headers = new DefaultHttp2Headers();
            HeaderListener listener = new HeaderListener() {
                @Override
                public void addHeader(byte[] key, byte[] value, boolean sensitive) {
                    headers.add(new AsciiString(key, false), new AsciiString(value, false));
                }
            };

            decoder.decode(in, listener);
            boolean truncated = decoder.endHeaderBlock();
            if (truncated) {
                // TODO: what's the right thing to do here?
            }

            if (headers.size() > maxHeaderListSize) {
                throw protocolError("Number of headers (%d) exceeds maxHeaderListSize (%d)",
                        headers.size(), maxHeaderListSize);
            }

            return headers;
        } catch (IOException e) {
            throw new Http2Exception(COMPRESSION_ERROR, e.getMessage());
        } catch (Throwable e) {
            // Default handler for any other types of errors that may have occurred. For example,
            // the the Header builder throws IllegalArgumentException if the key or value was
            // invalid
            // for any reason (e.g. the key was an invalid pseudo-header).
            throw new Http2Exception(Http2Error.PROTOCOL_ERROR, e.getMessage(), e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                throw new Http2Exception(Http2Error.INTERNAL_ERROR, e.getMessage(), e);
            }
        }
    }
}
