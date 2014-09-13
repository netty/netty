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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.BinaryHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import com.twitter.hpack.Encoder;

public class DefaultHttp2HeadersEncoder implements Http2HeadersEncoder {
    private final Encoder encoder;
    private final ByteArrayOutputStream tableSizeChangeOutput = new ByteArrayOutputStream();
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
        final OutputStream stream = new ByteBufOutputStream(buffer);
        try {
            if (headers.size() > maxHeaderListSize) {
                throw protocolError("Number of headers (%d) exceeds maxHeaderListSize (%d)",
                        headers.size(), maxHeaderListSize);
            }

            // If there was a change in the table size, serialize the output from the encoder
            // resulting from that change.
            if (tableSizeChangeOutput.size() > 0) {
                buffer.writeBytes(tableSizeChangeOutput.toByteArray());
                tableSizeChangeOutput.reset();
            }

            // Write pseudo headers first as required by the HTTP/2 spec.
            for (Http2Headers.PseudoHeaderName pseudoHeader : Http2Headers.PseudoHeaderName.values()) {
                AsciiString name = pseudoHeader.value();
                AsciiString value = headers.get(name);
                if (value != null) {
                    encodeHeader(name, value, stream);
                }
            }
            headers.forEachEntry(new BinaryHeaders.BinaryHeaderVisitor() {
                @Override
                public boolean visit(AsciiString name, AsciiString value) throws Exception {
                    if (!Http2Headers.PseudoHeaderName.isPseudoHeader(name)) {
                        encodeHeader(name, value, stream);
                    }
                    return true;
                }
            });
        } catch (IOException e) {
            throw Http2Exception.format(Http2Error.COMPRESSION_ERROR,
                    "Failed encoding headers block: %s", e.getMessage());
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                throw new Http2Exception(Http2Error.INTERNAL_ERROR, e.getMessage(), e);
            }
        }
    }

    @Override
    public void maxHeaderTableSize(int size) throws Http2Exception {
        try {
            // No headers should be emitted. If they are, we throw.
            encoder.setMaxHeaderTableSize(tableSizeChangeOutput, size);
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

    private void encodeHeader(AsciiString key, AsciiString value, OutputStream stream) throws IOException {
        boolean sensitive = sensitiveHeaders.contains(key);
        encoder.encodeHeader(stream, key.array(), value.array(), sensitive);
    }
}
