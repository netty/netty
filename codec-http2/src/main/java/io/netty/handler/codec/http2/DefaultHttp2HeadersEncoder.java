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
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.Http2Headers.PSEUDO_HEADER_PREFIX;
import static io.netty.util.CharsetUtil.UTF_8;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import com.twitter.hpack.Encoder;

public class DefaultHttp2HeadersEncoder implements Http2HeadersEncoder {
    private final Encoder encoder;
    private final ByteBuf tableSizeChangeOutput = Unpooled.buffer();
    private final Set<String> sensitiveHeaders = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    private int maxHeaderListSize = Integer.MAX_VALUE;

    public DefaultHttp2HeadersEncoder() {
        this(DEFAULT_HEADER_TABLE_SIZE, Collections.<String>emptySet());
    }

    public DefaultHttp2HeadersEncoder(int maxHeaderTableSize, Set<String> sensitiveHeaders) {
        encoder = new Encoder(maxHeaderTableSize);
        this.sensitiveHeaders.addAll(sensitiveHeaders);
    }

    @Override
    public void encodeHeaders(Http2Headers headers, ByteBuf buffer) throws Http2Exception {
        try {
            if (headers.size() > maxHeaderListSize) {
                throw protocolError("Number of headers (%d) exceeds maxHeaderListSize (%d)",
                        headers.size(), maxHeaderListSize);
            }

            // If there was a change in the table size, serialize the output from the encoder
            // resulting from that change.
            if (tableSizeChangeOutput.isReadable()) {
                buffer.writeBytes(tableSizeChangeOutput);
                tableSizeChangeOutput.clear();
            }

            OutputStream stream = new ByteBufOutputStream(buffer);
            // Write pseudo headers first as required by the HTTP/2 spec.
            for (Http2Headers.PseudoHeaderName pseudoHeader : Http2Headers.PseudoHeaderName.values()) {
                String name = pseudoHeader.value();
                String value = headers.get(name);
                if (value != null) {
                    encodeHeader(name, value, stream);
                }
            }
            for (Entry<String, String> header : headers) {
                if (!header.getKey().startsWith(PSEUDO_HEADER_PREFIX)) {
                    encodeHeader(header.getKey(), header.getValue(), stream);
                }
            }
        } catch (IOException e) {
            throw Http2Exception.format(Http2Error.COMPRESSION_ERROR,
                    "Failed encoding headers block: %s", e.getMessage());
        }
    }

    @Override
    public void maxHeaderTableSize(int size) throws Http2Exception {
        try {
            // No headers should be emitted. If they are, we throw.
            encoder.setMaxHeaderTableSize(new ByteBufOutputStream(tableSizeChangeOutput), size);
        } catch (IOException e) {
            throw new Http2Exception(Http2Error.COMPRESSION_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public int maxHeaderTableSize() {
        return encoder.getMaxHeaderTableSize();
    }

    @Override
    public void maxHeaderListSize(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("maxHeaderListSize must be positive: " + max);
        }
        maxHeaderListSize = max;
    }

    @Override
    public int maxHeaderListSize() {
        return maxHeaderListSize;
    }

    private void encodeHeader(String key, String value, OutputStream stream) throws IOException {
        boolean sensitive = sensitiveHeaders.contains(key);
        encoder.encodeHeader(stream, key.getBytes(UTF_8), value.getBytes(UTF_8), sensitive);
    }
}
