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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.internal.hpack.Encoder;
import io.netty.util.internal.UnstableApi;

import static io.netty.handler.codec.http2.Http2Error.COMPRESSION_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

@UnstableApi
public class DefaultHttp2HeadersEncoder implements Http2HeadersEncoder, Http2HeadersEncoder.Configuration {
    private final Encoder encoder;
    private final SensitivityDetector sensitivityDetector;
    private final Http2HeaderTable headerTable;
    private final ByteBuf tableSizeChangeOutput = Unpooled.buffer();

    public DefaultHttp2HeadersEncoder() {
        this(NEVER_SENSITIVE);
    }

    public DefaultHttp2HeadersEncoder(SensitivityDetector sensitivityDetector) {
        this(sensitivityDetector, new Encoder());
    }

    public DefaultHttp2HeadersEncoder(SensitivityDetector sensitivityDetector, boolean ignoreMaxHeaderListSize) {
        this(sensitivityDetector, new Encoder(ignoreMaxHeaderListSize));
    }

    public DefaultHttp2HeadersEncoder(SensitivityDetector sensitivityDetector, boolean ignoreMaxHeaderListSize,
                                      int dynamicTableArraySizeHint) {
        this(sensitivityDetector, new Encoder(ignoreMaxHeaderListSize, dynamicTableArraySizeHint));
    }

    /**
     * Exposed Used for testing only! Default values used in the initial settings frame are overriden intentionally
     * for testing but violate the RFC if used outside the scope of testing.
     */
    DefaultHttp2HeadersEncoder(SensitivityDetector sensitivityDetector, Encoder encoder) {
        this.sensitivityDetector = checkNotNull(sensitivityDetector, "sensitiveDetector");
        this.encoder = checkNotNull(encoder, "encoder");
        headerTable = new Http2HeaderTableEncoder();
    }

    @Override
    public void encodeHeaders(Http2Headers headers, ByteBuf buffer) throws Http2Exception {
        try {
            // If there was a change in the table size, serialize the output from the encoder
            // resulting from that change.
            if (tableSizeChangeOutput.isReadable()) {
                buffer.writeBytes(tableSizeChangeOutput);
                tableSizeChangeOutput.clear();
            }

            encoder.encodeHeaders(buffer, headers, sensitivityDetector);
        } catch (Http2Exception e) {
            throw e;
        } catch (Throwable t) {
            throw connectionError(COMPRESSION_ERROR, t, "Failed encoding headers block: %s", t.getMessage());
        }
    }

    @Override
    public Http2HeaderTable headerTable() {
        return headerTable;
    }

    @Override
    public Configuration configuration() {
        return this;
    }

    /**
     * {@link Http2HeaderTable} implementation to support {@link Http2HeadersEncoder}
     */
    private final class Http2HeaderTableEncoder implements Http2HeaderTable {
        @Override
        public void maxHeaderTableSize(long max) throws Http2Exception {
            encoder.setMaxHeaderTableSize(tableSizeChangeOutput, max);
        }

        @Override
        public long maxHeaderTableSize() {
            return encoder.getMaxHeaderTableSize();
        }

        @Override
        public void maxHeaderListSize(long max) throws Http2Exception {
            encoder.setMaxHeaderListSize(max);
        }

        @Override
        public long maxHeaderListSize() {
            return encoder.getMaxHeaderListSize();
        }
    }
}
