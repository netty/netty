/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.util.internal.UnstableApi;
import org.jetbrains.annotations.VisibleForTesting;

import static io.netty5.handler.codec.http2.Http2Error.COMPRESSION_ERROR;
import static io.netty5.handler.codec.http2.Http2Exception.connectionError;
import static java.util.Objects.requireNonNull;

@UnstableApi
public class DefaultHttp2HeadersEncoder implements Http2HeadersEncoder, Http2HeadersEncoder.Configuration {
    private final HpackEncoder hpackEncoder;
    private final SensitivityDetector sensitivityDetector;
    private Buffer tableSizeChangeOutput;

    public DefaultHttp2HeadersEncoder() {
        this(NEVER_SENSITIVE);
    }

    public DefaultHttp2HeadersEncoder(SensitivityDetector sensitivityDetector) {
        this(sensitivityDetector, new HpackEncoder());
    }

    public DefaultHttp2HeadersEncoder(SensitivityDetector sensitivityDetector, boolean ignoreMaxHeaderListSize) {
        this(sensitivityDetector, new HpackEncoder(ignoreMaxHeaderListSize));
    }

    public DefaultHttp2HeadersEncoder(SensitivityDetector sensitivityDetector, boolean ignoreMaxHeaderListSize,
                                      int dynamicTableArraySizeHint) {
        this(sensitivityDetector, ignoreMaxHeaderListSize, dynamicTableArraySizeHint, HpackEncoder.HUFF_CODE_THRESHOLD);
    }

    public DefaultHttp2HeadersEncoder(SensitivityDetector sensitivityDetector, boolean ignoreMaxHeaderListSize,
                                      int dynamicTableArraySizeHint, int huffCodeThreshold) {
        this(sensitivityDetector,
                new HpackEncoder(ignoreMaxHeaderListSize, dynamicTableArraySizeHint, huffCodeThreshold));
    }

    /**
     * Exposed Used for testing only! Default values used in the initial settings frame are overridden intentionally
     * for testing but violate the RFC if used outside the scope of testing.
     */
    @VisibleForTesting
    DefaultHttp2HeadersEncoder(SensitivityDetector sensitivityDetector, HpackEncoder hpackEncoder) {
        this.sensitivityDetector = requireNonNull(sensitivityDetector, "sensitiveDetector");
        this.hpackEncoder = requireNonNull(hpackEncoder, "hpackEncoder");
    }

    @Override
    public void encodeHeaders(int streamId, Http2Headers headers, Buffer buffer) throws Http2Exception {
        try {
            // If there was a change in the table size, serialize the output from the hpackEncoder
            // resulting from that change.
            if (tableSizeChangeOutput != null && tableSizeChangeOutput.readableBytes() > 0) {
                buffer.writeBytes(tableSizeChangeOutput);
                tableSizeChangeOutput.resetOffsets();
            }

            hpackEncoder.encodeHeaders(streamId, buffer, headers, sensitivityDetector);
        } catch (Http2Exception e) {
            throw e;
        } catch (Throwable t) {
            throw connectionError(COMPRESSION_ERROR, t, "Failed encoding headers block: %s", t.getMessage());
        }
    }

    @Override
    public void maxHeaderTableSize(long max) throws Http2Exception {
        if (tableSizeChangeOutput == null) {
            tableSizeChangeOutput =  BufferAllocator.onHeapUnpooled().allocate(256);
        }
        hpackEncoder.setMaxHeaderTableSize(tableSizeChangeOutput, max);
    }

    @Override
    public long maxHeaderTableSize() {
        return hpackEncoder.getMaxHeaderTableSize();
    }

    @Override
    public void maxHeaderListSize(long max) throws Http2Exception {
        hpackEncoder.setMaxHeaderListSize(max);
    }

    @Override
    public long maxHeaderListSize() {
        return hpackEncoder.getMaxHeaderListSize();
    }

    @Override
    public Configuration configuration() {
        return this;
    }

    @Override
    public void close() {
        if (tableSizeChangeOutput != null) {
            tableSizeChangeOutput.close();
        }
    }
}
